package com.vayunmathur.messages.util

import android.content.Context
import android.util.Log
import com.vayunmathur.messages.data.Conversation
import com.vayunmathur.messages.data.Message
import com.vayunmathur.messages.data.MessageDirection
import com.vayunmathur.messages.data.MessageSource
import com.vayunmathur.messages.data.MessageState
import com.vayunmathur.messages.data.MessagesDatabase
import com.vayunmathur.messages.data.buildMessagesDatabase
import com.vayunmathur.messages.gmessages.GMEvent
import com.vayunmathur.messages.gmessages.GMessagesClient
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridges [GMessagesClient]'s state + event streams into Room writes and
 * notification triggers.
 *
 * Replaces the prior WebView-puppet-based session manager. Same
 * external API (so the foreground service / UI keep compiling without
 * rewiring): [connectionStates] is the per-source state map, [incoming]
 * is the new-message fanout for the notification path.
 */
object MessagesSessionManager {

    private const val TAG = "MessagesSession"

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var appContext: Context
    private lateinit var db: MessagesDatabase

    /** Per-source state map. Today there's only MESSAGES_WEB; the map
     *  keeps the same shape the inbox/service expect. */
    private val _connectionStates = MutableStateFlow<Map<MessageSource, GMessagesClient.State>>(
        mapOf(MessageSource.MESSAGES_WEB to GMessagesClient.State.Idle)
    )
    val connectionStates: StateFlow<Map<MessageSource, GMessagesClient.State>> =
        _connectionStates.asStateFlow()

    /** Stream of "you just got a new message" events for the service to
     *  turn into MessagingStyle notifications. */
    private val _incoming = MutableSharedFlow<GMEvent.IncomingMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<GMEvent.IncomingMessage> = _incoming.asSharedFlow()

    private var stateCollectorJob: Job? = null
    private var eventCollectorJob: Job? = null

    /** Don't fire incoming-message notifications during the initial scan. */
    private val backfillComplete = mutableMapOf(MessageSource.MESSAGES_WEB to false)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        db = buildMessagesDatabase(appContext)
        GMessagesClient.init(appContext)
        Log.i(TAG, "init")
        wireCollectors()
    }

    fun database(): MessagesDatabase = db

    fun start() {
        if (!initialized.get()) return
        GMessagesClient.start()
    }

    fun stop() {
        GMessagesClient.stop()
        backfillComplete[MessageSource.MESSAGES_WEB] = false
    }

    suspend fun sendMessage(conversationId: String, body: String): Boolean {
        val source = sourceFor(conversationId) ?: return false
        // Insert PENDING row immediately so the UI updates.
        val pendingId = "${source.idPrefix}:pending:${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        db.messageDao().upsert(
            Message(
                id = pendingId,
                conversationId = conversationId,
                body = body,
                direction = MessageDirection.OUTGOING,
                state = MessageState.PENDING,
                timestamp = now,
                senderName = null,
            )
        )
        val ok = GMessagesClient.sendMessage(conversationId, body)
        db.messageDao().updateState(
            pendingId,
            if (ok) MessageState.SENT else MessageState.FAILED,
        )
        return ok
    }

    suspend fun markRead(conversationId: String) {
        db.conversationDao().markRead(conversationId)
    }

    /**
     * Bulk-write a backfill batch of messages in ONE transaction.
     * Called by [GMessagesClient] when LIST_MESSAGES returns — saves
     * dozens of separate Flow notifications when populating a thread.
     */
    suspend fun bulkUpsertMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        db.messageDao().upsertAll(messages)
    }

    fun forceResync() {
        GMessagesClient.forceResync()
    }

    private fun wireCollectors() {
        stateCollectorJob?.cancel()
        stateCollectorJob = scope.launch {
            GMessagesClient.state.collect { s ->
                _connectionStates.value = _connectionStates.value + (MessageSource.MESSAGES_WEB to s)
            }
        }
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            GMessagesClient.events.collect { handleEvent(it) }
        }
    }

    private suspend fun handleEvent(event: GMEvent) {
        when (event) {
            is GMEvent.ConversationUpdate -> {
                val id = "${event.source.idPrefix}:${event.conversationId}"
                val existing = db.conversationDao().get(id)
                val merged = Conversation(
                    id = id,
                    source = event.source,
                    peerName = event.peerName ?: existing?.peerName,
                    peerPhoneE164 = event.peerPhone ?: existing?.peerPhoneE164,
                    avatarUrl = event.avatarUrl ?: existing?.avatarUrl,
                    lastMessagePreview = event.lastPreview ?: existing?.lastMessagePreview,
                    lastMessageTimestamp = maxOf(event.lastTimestamp, existing?.lastMessageTimestamp ?: 0L),
                    unreadCount = event.unreadCount,
                    isGroup = event.isGroup,
                    participantCount = event.participantCount,
                    conversationType = event.conversationType,
                )
                db.conversationDao().upsert(merged)
                backfillComplete[event.source] = true
            }
            is GMEvent.MessageUpdate -> {
                val convId = "${event.source.idPrefix}:${event.conversationId}"
                val msgId = "${event.source.idPrefix}:${event.messageId}"
                db.messageDao().upsert(
                    Message(
                        id = msgId,
                        conversationId = convId,
                        body = event.body,
                        direction = if (event.outgoing) MessageDirection.OUTGOING else MessageDirection.INCOMING,
                        state = if (event.outgoing) MessageState.SENT else MessageState.DELIVERED,
                        timestamp = event.timestamp,
                        senderName = event.senderName,
                        reactionsJson = event.reactionsJson,
                    )
                )
            }
            is GMEvent.IncomingMessage -> {
                if (backfillComplete[event.source] == true) {
                    _incoming.tryEmit(event)
                }
                val convId = "${event.source.idPrefix}:${event.conversationId}"
                val msgId = "${event.source.idPrefix}:${event.messageId}"
                db.messageDao().upsert(
                    Message(
                        id = msgId,
                        conversationId = convId,
                        body = event.body,
                        direction = MessageDirection.INCOMING,
                        state = MessageState.DELIVERED,
                        timestamp = event.timestamp,
                        senderName = event.peerName,
                    )
                )
            }
        }
    }

    private fun sourceFor(conversationId: String): MessageSource? = when {
        conversationId.startsWith("${MessageSource.MESSAGES_WEB.idPrefix}:") -> MessageSource.MESSAGES_WEB
        else -> null
    }
}
