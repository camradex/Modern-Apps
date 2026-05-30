package com.vayunmathur.messages

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vayunmathur.library.ui.DynamicTheme
import com.vayunmathur.library.ui.PermissionsChecker
import com.vayunmathur.library.util.ListPage
import com.vayunmathur.library.util.MainNavigation
import com.vayunmathur.library.util.NavKey
import com.vayunmathur.library.util.rememberNavBackStack
import com.vayunmathur.messages.data.buildMessagesDatabase
import com.vayunmathur.messages.ui.ConversationScreen
import com.vayunmathur.messages.ui.InboxScreen
import com.vayunmathur.messages.ui.SettingsScreen
import com.vayunmathur.messages.ui.setup.MessagesPairingScreen
import com.vayunmathur.messages.ui.setup.VoiceLoginScreen
import com.vayunmathur.messages.util.MessagesViewModel
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DynamicTheme {
                // POST_NOTIFICATIONS is the only runtime perm we strictly
                // need — without it the foreground service runs but the
                // user sees no incoming-message alerts. READ_CONTACTS is
                // requested on-demand when the inbox actually tries to
                // resolve a phone number to a contact name.
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS,
                        // Used by ContactResolver to look up display names
                        // and photos for the conversation peers.
                        Manifest.permission.READ_CONTACTS,
                    )
                } else {
                    arrayOf(Manifest.permission.READ_CONTACTS)
                }
                PermissionsChecker(perms, getString(R.string.permissions_post_notifications)) {
                    val db = remember { buildMessagesDatabase(this@MainActivity) }
                    Navigation(db)
                }
            }
        }
    }
}

/** Navigation graph for the messages module. */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Inbox : Route
    @Serializable data class Conversation(val conversationId: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object PairMessages : Route
    @Serializable data object LoginVoice : Route
}

@Composable
private fun Navigation(
    db: com.vayunmathur.messages.data.MessagesDatabase,
    vm: MessagesViewModel = viewModel(),
) {
    val backStack = rememberNavBackStack<Route>(Route.Inbox)
    MainNavigation(backStack) {
        entry<Route.Inbox>(metadata = ListPage { }) {
            InboxScreen(backStack, vm, db)
        }
        entry<Route.Conversation> {
            ConversationScreen(backStack, vm, db, it.conversationId)
        }
        entry<Route.Settings> {
            SettingsScreen(backStack, vm)
        }
        entry<Route.PairMessages> {
            MessagesPairingScreen(backStack, vm)
        }
        entry<Route.LoginVoice> {
            VoiceLoginScreen(backStack)
        }
    }
}
