package com.vayunmathur.email.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vayunmathur.email.EmailMessage
import com.vayunmathur.email.MainActivity
import com.vayunmathur.email.R

object EmailNotifications {

    private const val CHANNEL_ID = "new_mail"
    private const val CHANNEL_NAME = "New mail"

    /** Idempotent — safe to call from every worker run. */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for newly received emails."
                setShowBadge(true)
            }
        )
    }

    /**
     * Post one notification per [messages]. Tapping it launches MainActivity
     * with extras that navigate to the message thread (reusing the existing
     * intent-extra handling in `MainActivity.handleIntent`).
     *
     * Posted oldest-first so Android's shade — which sorts within a channel
     * by post-time, newest on top — renders newest email at the top of the
     * stack. The notification's displayed timestamp is also pinned to the
     * email's actual receive time via setWhen instead of the post wall-clock.
     */
    fun postForNewMessages(context: Context, accountEmail: String, messages: List<EmailMessage>) {
        if (messages.isEmpty()) return
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val ordered = messages.sortedBy { it.dateMillis }
        for (msg in ordered) {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("accountEmail", msg.accountEmail)
                putExtra("threadId", msg.threadId ?: msg.id.toString())
            }
            // Per-message request code so each PendingIntent is unique.
            val pi = PendingIntent.getActivity(
                context,
                msg.id.hashCode(),
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notif: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_mail)
                .setContentTitle(msg.from.substringBefore("<").trim().ifEmpty { msg.from })
                .setContentText(msg.subject.ifBlank { "(no subject)" })
                .setSubText(accountEmail)
                .setWhen(if (msg.dateMillis > 0L) msg.dateMillis else System.currentTimeMillis())
                .setShowWhen(true)
                // Stable sort key (lexicographic) so the shade keeps emails in
                // chronological order even if posts arrive simultaneously.
                .setSortKey(sortKey(msg))
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildString {
                            append(msg.subject.ifBlank { "(no subject)" })
                            msg.body?.takeIf { it.isNotBlank() }?.let { snippet ->
                                append("\n\n")
                                append(snippet.take(200))
                            }
                        }
                    )
                )
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            try {
                nm.notify(notificationId(msg), notif)
            } catch (_: SecurityException) {
                // Pre-permission grant on Android 13+ — silently drop.
            }
        }
    }

    private fun notificationId(msg: EmailMessage): Int {
        return ((msg.accountEmail.hashCode().toLong() * 31) xor msg.id).toInt()
    }

    /**
     * Lexicographic sort key descending in time: newer emails sort BEFORE
     * older ones in the shade. Subtract dateMillis from Long.MAX_VALUE so a
     * larger timestamp maps to a smaller numeric string after zero-padding.
     */
    private fun sortKey(msg: EmailMessage): String {
        val t = if (msg.dateMillis > 0L) msg.dateMillis else System.currentTimeMillis()
        val inverted = Long.MAX_VALUE - t
        return inverted.toString().padStart(20, '0')
    }
}
