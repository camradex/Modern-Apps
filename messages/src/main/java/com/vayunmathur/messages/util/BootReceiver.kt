package com.vayunmathur.messages.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * On device boot, kick the foreground service back up so the user
 * doesn't have to open the app for messages to start flowing again.
 *
 * The service is responsible for deciding whether either source is
 * actually configured — if neither pairing is in place it'll just sit
 * idle (puppets in their NeedsSetup state) until the user opens the app.
 * We unconditionally start it after boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MessagesService.start(context)
        }
    }
}
