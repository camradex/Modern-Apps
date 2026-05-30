package com.vayunmathur.messages.gmessages

import android.content.Context
import android.util.Log
import client.Client.ListConversationsRequest
import client.Client.ListConversationsResponse
import client.Client.ListMessagesRequest
import client.Client.ListMessagesResponse
import com.vayunmathur.messages.data.MessageSource
import com.vayunmathur.messages.util.ContactResolver
import conversations.Conversations.Conversation
import conversations.Conversations.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import rpc.Rpc.ActionType
import rpc.Rpc.MessageType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-global owner of the Google-Messages-for-Web protocol session.
 *
 * Lifecycle:
 *  1. [init] is called once by the foreground service. We load any
 *     persisted [AuthData] off disk.
 *  2. [start] is idempotent: if persisted auth exists, opens the
 *     long-poll and emits Connected. Otherwise sits Idle.
 *  3. [startPair] hits Pairing/RegisterPhoneRelay, stores the initial
 *     tachyon token, opens the long-poll so we can receive the Paired
 *     event, then returns the QR URL for the UI to render.
 *  4. The long-poll dispatches a Paired event → we persist the new
 *     device info + tachyon token, transition to Connected, and trigger
 *     the conversation backfill.
 *  5. [stop] cancels everything and clears the persisted auth.
 *
 * For v1 we LIST_CONVERSATIONS on connect (for backfill / inbox
 * population). [sendMessage] is wired through but stubbed — building
 * the full `SendMessageRequest` payload structure requires more proto
 * coverage than what's needed for receiving and is deferred.
 */
object GMessagesClient {

    private const val TAG = "GMessagesClient"

    sealed interface State {
        data object Idle : State
        data class Pairing(val qrUrl: String) : State
        data object Connected : State
        data class Disconnected(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GMEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<GMEvent> = _events.asSharedFlow()

    val source: MessageSource = MessageSource.MESSAGES_WEB

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context
    private val rpc = RpcClient()
    @Volatile private var auth: AuthData = AuthData.generateInitial()
    private val sessionHandler = SessionHandler(rpc) { auth }
    private val longPoll = LongPoll(
        rpc = rpc,
        authProvider = { auth },
        sessionHandler = sessionHandler,
        onEvent = ::handleLongPollEvent,
    )
    private var backfillJob: Job? = null

    @Volatile private var conversationsFetchedOnce = false

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        Log.i(TAG, "init")
        runBlocking {
            AuthData.load(appContext)?.let { auth = it }
        }
        if (auth.isPaired()) {
            Log.i(TAG, "found persisted pair, resuming long-poll")
            longPoll.start(scope)
            _state.value = State.Connected
            kickoffBackfill()
        }
    }

    fun start() {
        if (!initialized.get()) return
        if (auth.isPaired() && _state.value !is State.Connected) {
            longPoll.start(scope)
            _state.value = State.Connected
            kickoffBackfill()
        }
    }

    fun stop() {
        Log.i(TAG, "stop — clearing pair")
        backfillJob?.cancel()
        longPoll.stop()
        sessionHandler.cancelAll()
        auth = AuthData.generateInitial()
        scope.launch { auth.save(appContext) }
        _state.value = State.Idle
    }

    suspend fun startPair(): String {
        // Fresh keys for a fresh pair.
        auth = AuthData.generateInitial()
        val result = PairFlow.registerAndBuildQrUrl(rpc, auth)
        auth = auth.copy(
            tachyonAuthTokenB64 = android.util.Base64.encodeToString(result.tachyonToken, android.util.Base64.NO_WRAP),
            tachyonTtlUs = result.tachyonTtlUs,
            tachyonExpiryMs = System.currentTimeMillis() + (result.tachyonTtlUs / 1000),
        )
        // Open the long-poll now so we receive the Paired event when the
        // user finishes scanning.
        longPoll.start(scope)
        _state.value = State.Pairing(result.qrUrl)
        return result.qrUrl
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun sendMessage(conversationId: String, body: String): Boolean {
        // TODO(messages-libgm): Building the full SendMessageRequest
        // payload requires the conversations.MessageContent + nested
        // MessagePayload structures. Deferred — receiving works first.
        if (_state.value !is State.Connected) return false
        Log.w(TAG, "sendMessage not yet implemented (received conv=$conversationId body=${body.length} chars)")
        return false
    }

    /**
     * Load the recent messages for [conversationId] via LIST_MESSAGES.
     * The response flows back through the long-poll into
     * [handleDataMessage] which calls [emitMessage] for each row.
     *
     * Idempotent on the wire — Room upsert deduplicates by message id.
     * Strip the source prefix because the relay only knows Google's
     * thread id.
     */
    fun fetchMessages(conversationId: String, count: Int = 100) {
        if (_state.value !is State.Connected) return
        scope.launch {
            val webId = conversationId.substringAfter(':', conversationId)
            Log.i(TAG, "fetchMessages convId=$webId count=$count")
            val req = ListMessagesRequest.newBuilder()
                .setConversationID(webId)
                .setCount(count.toLong())
                .build()
            val resp = sessionHandler.sendAndWait(ActionType.LIST_MESSAGES, req)
            if (resp == null) Log.w(TAG, "fetchMessages: no response")
        }
    }

    fun forceResync() {
        if (_state.value !is State.Connected) return
        kickoffBackfill()
    }

    // ----------------------------------------------------------------
    // Long-poll event handling
    // ----------------------------------------------------------------

    private suspend fun handleLongPollEvent(evt: LongPollEvent) {
        when (evt) {
            is LongPollEvent.Paired -> {
                // Hand off to our own scope: handlePaired calls longPoll.stop()
                // which would otherwise cancel the very coroutine we're in
                // (the long-poll's reader) mid-execution.
                scope.launch { handlePaired(evt) }
            }
            LongPollEvent.Revoked -> {
                Log.w(TAG, "pair revoked by phone")
                _state.value = State.Disconnected("Pair revoked")
            }
            is LongPollEvent.Data -> handleDataMessage(evt.msg)
        }
    }

    private suspend fun handlePaired(p: LongPollEvent.Paired) {
        Log.i(TAG, "received Paired event — switching to Connected (ttlUs=${p.tachyonTtlUs})")
        auth = auth.copy(
            mobileDeviceB64 = p.mobileDeviceB64,
            browserDeviceB64 = p.browserDeviceB64,
            tachyonAuthTokenB64 = p.tachyonTokenB64,
            tachyonTtlUs = p.tachyonTtlUs,
            tachyonExpiryMs = System.currentTimeMillis() + (p.tachyonTtlUs / 1000),
        )
        auth.save(appContext)
        _state.value = State.Connected

        // CRITICAL: the long-poll that received this Paired event is
        // still authenticated with the INITIAL (pre-pair) tachyon token.
        // The relay routes responses keyed by the long-poll's auth
        // token; subsequent SendMessage calls use the new PERMANENT
        // token after pair, so their responses get routed to a
        // long-poll that doesn't exist. We must close + reopen the
        // long-poll to start listening with the permanent token.
        //
        // Sleep 2 s first to let the phone persist the pair data — if
        // we reconnect too quickly the phone may not recognize the
        // session and silently unpair us (same trick libgm uses in
        // `pair.go` completePairing).
        Log.i(TAG, "sleeping 2s before reconnecting long-poll with permanent token")
        kotlinx.coroutines.delay(2_000)
        longPoll.stop()
        longPoll.start(scope)

        // Give the new long-poll a moment to actually open before the
        // wake-up + backfill RPCs depend on it for their responses.
        kotlinx.coroutines.delay(1_000)
        sessionHandler.setActiveSession(auth.sessionId)
        kotlinx.coroutines.delay(1_000)
        kickoffBackfill()
    }

    private suspend fun handleDataMessage(msg: IncomingRpc) {
        val data = msg.decryptedData ?: return
        when (msg.action) {
            ActionType.LIST_CONVERSATIONS -> {
                val resp = runCatching { ListConversationsResponse.parseFrom(data) }.getOrNull() ?: return
                Log.i(TAG, "backfill: ${resp.conversationsCount} conversations")
                for (i in 0 until resp.conversationsCount) emitConversation(resp.getConversations(i))
            }
            ActionType.LIST_MESSAGES -> {
                val resp = runCatching { ListMessagesResponse.parseFrom(data) }.getOrNull() ?: return
                Log.i(TAG, "thread fill: ${resp.messagesCount} messages")
                // Build all rows up-front then bulk-write in one Room
                // transaction. Per-message events would trigger one Flow
                // emission each — noticeably slow when opening a thread
                // with hundreds of historical messages.
                val rows = (0 until resp.messagesCount).map { idx ->
                    buildMessageRow(resp.getMessages(idx))
                }
                com.vayunmathur.messages.util.MessagesSessionManager.bulkUpsertMessages(rows)
            }
            else -> Log.d(TAG, "unhandled data action ${msg.action}")
        }
    }

    private suspend fun emitConversation(c: conversations.Conversations.Conversation) {
        // Collect the non-self participants — used for both phone-lookup
        // (1:1 chats) and group-display labeling.
        val otherParticipants = (0 until c.participantsCount)
            .map { c.getParticipants(it) }
            .filter { !it.isMe }

        val isGroup = c.isGroupChat || otherParticipants.size > 1
        val peerPhone = otherParticipants.firstOrNull { it.id.number.isNotBlank() }?.id?.number

        // Always prefer the device's contact database for naming + photos
        // (1:1 only). For groups we synthesize a "Alice, Bob & 2 others"
        // style label below.
        val contact = if (!isGroup) {
            peerPhone?.let { ContactResolver.lookup(appContext, it) }
        } else null

        val displayName = when {
            isGroup -> groupLabel(c, otherParticipants)
            else -> contact?.displayName ?: peerPhone ?: c.name.takeIf { it.isNotBlank() }
        }

        val type = when (c.type) {
            conversations.Conversations.ConversationType.SMS -> "SMS"
            conversations.Conversations.ConversationType.RCS -> "RCS"
            else -> null
        }

        _events.emit(
            GMEvent.ConversationUpdate(
                source = source,
                conversationId = c.conversationID,
                peerName = displayName,
                peerPhone = if (isGroup) null else peerPhone,
                avatarUrl = contact?.photoUri,
                lastPreview = if (c.hasLatestMessage()) c.latestMessage.displayContent.takeIf { it.isNotBlank() } else null,
                lastTimestamp = c.lastMessageTimestamp,
                unreadCount = if (c.unread) 1 else 0,
                isGroup = isGroup,
                participantCount = otherParticipants.size,
                conversationType = type,
            )
        )
    }

    /** Build a "Alice, Bob & 2 others" label for a group. Uses the
     *  device's contact name for each participant when available, else
     *  the participant's fullName from the relay, else their number. */
    private fun groupLabel(
        c: conversations.Conversations.Conversation,
        others: List<conversations.Conversations.Participant>,
    ): String {
        // Prefer the explicit thread name (RCS groups often have one).
        val explicit = c.name.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        val names = others.map { p ->
            val phone = p.id.number.takeIf { it.isNotBlank() }
            val deviceName = phone?.let { ContactResolver.lookup(appContext, it)?.displayName }
            deviceName
                ?: p.firstName.takeIf { it.isNotBlank() }
                ?: p.fullName.takeIf { it.isNotBlank() }
                ?: phone
                ?: "Unknown"
        }
        return when {
            names.isEmpty() -> "Group"
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " & ${names.size - 2} others"
        }
    }

    /** Pure transformation of one proto Message into a Room row.
     *  Used by the bulk LIST_MESSAGES path. */
    private fun buildMessageRow(m: conversations.Conversations.Message): com.vayunmathur.messages.data.Message {
        val body = (0 until m.messageInfoCount)
            .mapNotNull { idx ->
                val info = m.getMessageInfo(idx)
                if (info.hasMessageContent()) info.messageContent.content else null
            }
            .joinToString("\n")
        val outgoing = m.hasSenderParticipant() && m.senderParticipant.isMe
        val sourcePrefix = source.idPrefix
        return com.vayunmathur.messages.data.Message(
            id = "$sourcePrefix:${m.messageID}",
            conversationId = "$sourcePrefix:${m.conversationID}",
            body = body,
            direction = if (outgoing) com.vayunmathur.messages.data.MessageDirection.OUTGOING
                else com.vayunmathur.messages.data.MessageDirection.INCOMING,
            state = if (outgoing) com.vayunmathur.messages.data.MessageState.SENT
                else com.vayunmathur.messages.data.MessageState.DELIVERED,
            timestamp = m.timestamp,
            senderName = if (m.hasSenderParticipant()) {
                m.senderParticipant.fullName.takeIf { it.isNotBlank() }
                    ?: m.senderParticipant.firstName.takeIf { it.isNotBlank() }
            } else null,
            reactionsJson = extractReactionsJson(m),
        )
    }

    /** Roll up the per-emoji reaction entries on a Message into the
     *  [count: Int] aggregate we store. */
    private fun extractReactionsJson(m: conversations.Conversations.Message): String? {
        if (m.reactionsCount == 0) return null
        val reactions = (0 until m.reactionsCount).mapNotNull { idx ->
            val entry = m.getReactions(idx)
            val emoji = entry.data.unicode.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            com.vayunmathur.messages.data.Reaction(
                emoji = emoji,
                count = entry.participantIDsCount.coerceAtLeast(1),
            )
        }
        if (reactions.isEmpty()) return null
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                com.vayunmathur.messages.data.Reaction.serializer()
            ),
            reactions,
        )
    }

    private suspend fun emitMessage(m: conversations.Conversations.Message) {
        // Concatenate all text-bearing MessageInfo parts. Most messages
        // have exactly one; RCS / MMS can have multiple parts (e.g. a
        // caption + an attachment we don't render yet).
        val body = (0 until m.messageInfoCount)
            .mapNotNull { idx ->
                val info = m.getMessageInfo(idx)
                if (info.hasMessageContent()) info.messageContent.content else null
            }
            .joinToString("\n")
            .ifBlank { "" }
        // The Message proto has no top-level fromMe boolean, but its
        // senderParticipant carries an `isMe` flag the relay populates
        // for outgoing messages. Falls back to incoming when missing.
        val outgoing = m.hasSenderParticipant() && m.senderParticipant.isMe
        _events.emit(
            GMEvent.MessageUpdate(
                source = source,
                conversationId = m.conversationID,
                messageId = m.messageID,
                body = body,
                outgoing = outgoing,
                timestamp = m.timestamp,
                senderName = if (m.hasSenderParticipant()) {
                    m.senderParticipant.fullName.takeIf { it.isNotBlank() }
                        ?: m.senderParticipant.firstName.takeIf { it.isNotBlank() }
                } else null,
                reactionsJson = extractReactionsJson(m),
            )
        )
        if (!outgoing && body.isNotEmpty()) {
            _events.emit(
                GMEvent.IncomingMessage(
                    source = source,
                    conversationId = m.conversationID,
                    messageId = m.messageID,
                    body = body,
                    peerName = if (m.hasSenderParticipant()) {
                        m.senderParticipant.fullName.takeIf { it.isNotBlank() }
                    } else null,
                    peerPhone = if (m.hasSenderParticipant()) {
                        m.senderParticipant.id.number.takeIf { it.isNotBlank() }
                    } else null,
                    timestamp = m.timestamp,
                )
            )
        }
    }

    private fun kickoffBackfill() {
        backfillJob?.cancel()
        backfillJob = scope.launch {
            // First call uses BUGLE_ANNOTATION as a "give me everything"
            // hint to the relay; subsequent calls use BUGLE_MESSAGE. See
            // libgm `methods.go.ListConversations` for the same trick.
            val msgType = if (!conversationsFetchedOnce) {
                conversationsFetchedOnce = true
                MessageType.BUGLE_ANNOTATION
            } else {
                MessageType.BUGLE_MESSAGE
            }
            Log.i(TAG, "kicking off LIST_CONVERSATIONS (messageType=$msgType)")
            val req = ListConversationsRequest.newBuilder()
                .setCount(50)
                .build()
            val resp = sessionHandler.sendAndWait(
                ActionType.LIST_CONVERSATIONS,
                req,
                messageType = msgType,
            )
            if (resp == null) {
                Log.w(TAG, "backfill: no response (timeout?)")
            } else {
                Log.i(TAG, "backfill response received (decryptedBytes=${resp.decryptedData?.size ?: 0})")
            }
        }
    }
}
