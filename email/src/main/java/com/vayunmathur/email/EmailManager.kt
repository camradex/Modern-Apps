package com.vayunmathur.email

import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store

class EmailManager {

    sealed class AuthType {
        data class Password(val value: String) : AuthType()
        data class OAuth2(val accessToken: String) : AuthType()
    }

    fun fetchLatestSubjects(
        host: String,
        user: String,
        auth: AuthType,
        onSuccess: (List<String>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Thread {
            try {
                val properties = Properties()
                properties["mail.store.protocol"] = "imaps"
                properties["mail.imaps.host"] = host
                properties["mail.imaps.port"] = "993"
                properties["mail.imaps.ssl.enable"] = "true"

                if (auth is AuthType.OAuth2) {
                    properties["mail.imaps.auth.mechanisms"] = "XOAUTH2"
                }

                val session = Session.getInstance(properties)
                val store: Store = session.getStore("imaps")

                when (auth) {
                    is AuthType.Password -> store.connect(host, user, auth.value)
                    is AuthType.OAuth2 -> store.connect(host, user, auth.accessToken)
                }

                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_ONLY)

                val messageCount = inbox.messageCount
                val start = if (messageCount > 10) messageCount - 9 else 1
                val messages = if (messageCount > 0) {
                    inbox.getMessages(start, messageCount)
                } else {
                    emptyArray()
                }

                val subjects = messages.reversedArray().map { it.subject ?: "(No Subject)" }

                onSuccess(subjects)

                inbox.close(false)
                store.close()
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }
}
