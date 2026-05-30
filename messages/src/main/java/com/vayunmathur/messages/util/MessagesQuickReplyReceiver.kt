package com.vayunmathur.messages.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles `RemoteInput` quick-reply taps from the new-message notification.
 *
 * Reads the user's typed reply, hands it to [MessagesSessionManager.sendMessage],
 * then dismisses the notification on success. Failures leave the
 * notification up so the user can retry from inside the app.
 */
class MessagesQuickReplyReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessagesService.QUICK_REPLY_KEY)
            ?.toString()
            .orEmpty()
        if (text.isBlank()) return

        // The session manager is a singleton; if the service is alive it
        // already has the puppets. If the service was killed, we'd ideally
        // restart it — for v1 we just rely on the user re-launching the app.
        scope.launch {
            val ok = MessagesSessionManager.sendMessage(conversationId, text)
            if (ok) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(conversationId, conversationId.hashCode())
            } else {
                Log.w(TAG, "quick-reply send failed for $conversationId")
            }
        }
    }

    companion object {
        private const val TAG = "QuickReply"
        const val ACTION_REPLY = "com.vayunmathur.messages.QUICK_REPLY"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
