package com.vayunmathur.email

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.MimeMultipart
import javax.mail.search.*

class EmailManager {

    sealed class AuthType {
        data class Password(val value: String) : AuthType()
        data class OAuth2(val accessToken: String) : AuthType()
    }

    private fun getSession(auth: AuthType, host: String): Session {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

        // Performance Tweaks for the underlying socket layer
        properties["mail.imaps.fetchsize"] = "1048576" // 1MB buffer allocation for fast text streaming
        properties["mail.imaps.partialfetch"] = "true"  // Allows downloading text without attachments

        if (auth is AuthType.OAuth2) {
            properties["mail.imaps.auth.mechanisms"] = "XOAUTH2"
        }
        return Session.getInstance(properties)
    }

    private suspend fun <T> withStore(host: String, user: String, auth: AuthType, block: (Store) -> T): T = withContext(Dispatchers.IO) {
        val session = getSession(auth, host)
        val store = session.getStore("imaps")
        try {
            when (auth) {
                is AuthType.Password -> store.connect(host, user, auth.value)
                is AuthType.OAuth2 -> store.connect(host, user, auth.accessToken)
            }
            block(store)
        } finally {
            store.close()
        }
    }

    // SPEEDUP: Replaced deep recursion with a single wildcard batch request
    suspend fun fetchFolders(host: String, user: String, auth: AuthType): List<EmailFolder> = withStore(host, user, auth) { store ->
        // Passing "%" or "*" fetches the complete directory hierarchy from the root in one single server response
        val folders = store.defaultFolder.list("*")

        folders.map { folder ->
            EmailFolder(
                accountEmail = user,
                fullName = folder.fullName,
                name = folder.name,
                parentFullName = folder.parent?.fullName?.takeIf { it.isNotEmpty() },
                holdsMessages = (folder.type and Folder.HOLDS_MESSAGES) != 0,
                delimiter = folder.separator.toString()
            )
        }
    }

    // SPEEDUP: Added Batch FetchProfile profiling
    suspend fun fetchMessages(
        host: String,
        user: String,
        auth: AuthType,
        folderName: String,
        limit: Int,
        offset: Int,
        fetchBodies: Boolean = false
    ): List<EmailMessage> = withStore(host, user, auth) { store ->
        val folder = store.getFolder(folderName)
        if ((folder.type and Folder.HOLDS_MESSAGES) == 0) return@withStore emptyList()

        folder.open(Folder.READ_ONLY)
        try {
            val totalMessages = folder.messageCount
            if (totalMessages == 0) return@withStore emptyList()

            val end = (totalMessages - offset).coerceAtLeast(1)
            val start = (end - limit + 1).coerceAtLeast(1)

            if (end < 1) return@withStore emptyList()

            val messages = folder.getMessages(start, end)

            // Build a strict optimization profile
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)       // Batch loads Dates, Subjects, and From senders
                add(UIDFolder.FetchProfileItem.UID)   // Batch loads IMAP Unique IDs
                if (fetchBodies) {
                    add(FetchProfile.Item.CONTENT_INFO) // Pre-scans structural MIME layout
                }
            }

            // Forces the driver to execute a bulk network fetch for the metadata matching the profile
            folder.fetch(messages, fp)

            val uidFolder = folder as? UIDFolder
            messages.reversedArray().map { msg ->
                val (body, isHtml) = if (fetchBodies) getTextFromMessage(msg) else null to false
                EmailMessage(
                    accountEmail = user,
                    id = uidFolder?.getUID(msg) ?: -1L,
                    folderName = folderName,
                    subject = msg.subject ?: "(No Subject)",
                    from = msg.from?.firstOrNull()?.toString() ?: "Unknown",
                    date = msg.sentDate?.toString() ?: "",
                    body = body,
                    isHtml = isHtml
                )
            }
        } finally {
            folder.close(false)
        }
    }

    // SPEEDUP: Profiling applied to search loops
    suspend fun searchMessages(host: String, user: String, auth: AuthType, folderName: String, query: String): List<EmailMessage> = withStore(host, user, auth) { store ->
        val folder = store.getFolder(folderName)
        folder.open(Folder.READ_ONLY)
        try {
            val searchTerm = OrTerm(
                arrayOf(
                    SubjectTerm(query),
                    FromStringTerm(query),
                    BodyTerm(query)
                )
            )
            val messages = folder.search(searchTerm)

            // Minimize latency downstream by profiling target items found by search match
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(UIDFolder.FetchProfileItem.UID)
            }
            folder.fetch(messages, fp)

            val uidFolder = folder as? UIDFolder
            messages.reversedArray().map { msg ->
                EmailMessage(
                    accountEmail = user,
                    id = uidFolder?.getUID(msg) ?: -1L,
                    folderName = folderName,
                    subject = msg.subject ?: "(No Subject)",
                    from = msg.from?.firstOrNull()?.toString() ?: "Unknown",
                    date = msg.sentDate?.toString() ?: ""
                )
            }
        } finally {
            folder.close(false)
        }
    }

    private fun getTextFromMessage(message: Message): Pair<String, Boolean> {
        return try {
            if (message.isMimeType("text/plain")) {
                message.content.toString() to false
            } else if (message.isMimeType("text/html")) {
                message.content.toString() to true
            } else if (message.isMimeType("multipart/*")) {
                getTextFromMimeMultipart(message.content as MimeMultipart)
            } else {
                "" to false
            }
        } catch (e: Exception) {
            "Error loading content: ${e.message}" to false
        }
    }

    private fun getTextFromMimeMultipart(mimeMultipart: MimeMultipart): Pair<String, Boolean> {
        var plainText = ""
        var htmlText = ""
        val count = mimeMultipart.count
        for (i in 0 until count) {
            val bodyPart = mimeMultipart.getBodyPart(i)
            if (bodyPart.isMimeType("text/plain")) {
                plainText += bodyPart.content
            } else if (bodyPart.isMimeType("text/html")) {
                htmlText += bodyPart.content
            } else if (bodyPart.content is MimeMultipart) {
                val (nestedText, nestedIsHtml) = getTextFromMimeMultipart(bodyPart.content as MimeMultipart)
                if (nestedIsHtml) htmlText += nestedText else plainText += nestedText
            }
        }
        return if (htmlText.isNotEmpty()) htmlText to true else plainText to false
    }
}