package com.vayunmathur.messages.ui

import androidx.compose.foundation.background as foundationBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vayunmathur.library.ui.IconSettings
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.messages.R
import com.vayunmathur.messages.Route
import com.vayunmathur.messages.data.Conversation
import com.vayunmathur.messages.data.MessageSource
import com.vayunmathur.messages.data.MessagesDatabase
import com.vayunmathur.messages.util.MessagesSessionManager
import com.vayunmathur.messages.util.MessagesViewModel
import com.vayunmathur.messages.gmessages.GMessagesClient
import java.text.DateFormat
import java.util.Date

/**
 * Unified inbox over conversations from both sources. Sorted by recency.
 *
 * Source is surfaced as a small chip on each row so it's always obvious
 * whether a reply will go via the user's SIM (Messages) or their Voice
 * number. We deliberately don't merge by phone number — same peer on
 * two different lines is still two threads.
 *
 * When a source isn't connected (no pairing / no login), a "setup" card
 * shows above the list inviting the user to complete the flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    backStack: NavBackStack<Route>,
    vm: MessagesViewModel,
    db: MessagesDatabase,
) {
    val conversations by db.conversationDao().observeAll().collectAsState(initial = emptyList())
    val connectionStates by vm.connectionStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                actions = {
                    IconButton(onClick = { backStack.add(Route.Settings) }) {
                        IconSettings()
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Setup prompt for Messages-for-Web (only source today).
            SetupPrompts(
                states = connectionStates,
                onPairMessages = { backStack.add(Route.PairMessages) },
            )

            if (conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.inbox_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            onClick = { backStack.add(Route.Conversation(conv.id)) },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

/** Shows a source-specific setup card at the top of the inbox when the
 *  puppet is in NeedsSetup or Disconnected. */
@Composable
private fun SetupPrompts(
    states: Map<MessageSource, GMessagesClient.State>,
    onPairMessages: () -> Unit,
) {
    val msgsState = states[MessageSource.MESSAGES_WEB]
    // Show the setup card when there's no active pairing. Connected
    // and Pairing both mean "don't bug the user" (Pairing because the
    // pair screen is already up and driving the QR).
    if (msgsState !is GMessagesClient.State.Connected &&
        msgsState !is GMessagesClient.State.Pairing
    ) {
        SetupCard(
            title = stringResource(R.string.inbox_setup_messages_title),
            description = stringResource(R.string.inbox_setup_messages_desc),
            actionLabel = stringResource(R.string.inbox_setup_action),
            onAction = onPairMessages,
        )
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onAction),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.peerName ?: "(unknown)",
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // SMS / RCS chip (per-conversation transport). Hidden when
                // the relay didn't tell us — most rows will have one.
                conversation.conversationType?.let { TypeChip(it) }
            }
        },
        supportingContent = {
            conversation.lastMessagePreview?.let {
                Text(it, maxLines = 1)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatTimestamp(conversation.lastMessageTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    UnreadBadge(conversation.unreadCount)
                }
            }
        },
        leadingContent = { Avatar(conversation.peerName, conversation.avatarUrl, conversation.isGroup) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun TypeChip(label: String) {
    val color = when (label) {
        "RCS" -> MaterialTheme.colorScheme.primary
        "SMS" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SourceChip(source: MessageSource) {
    val (label, color) = when (source) {
        MessageSource.MESSAGES_WEB -> "Phone" to MaterialTheme.colorScheme.tertiary
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Avatar(name: String?, photoUrl: String?, isGroup: Boolean) {
    val color = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photoUrl != null -> coil.compose.AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            isGroup -> Text(
                "…",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            else -> {
                val initials = (name ?: "?").trim().take(2).uppercase()
                Text(
                    initials,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val daysAgo = (now - ts) / (24L * 60 * 60 * 1000)
    val date = Date(ts)
    return when {
        daysAgo < 1L -> DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        daysAgo < 7L -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(date)
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}

// Small helper so we can call Modifier.background() at the call sites
// without adding a separate import everywhere it's used in this file.
private fun Modifier.background(color: Color): Modifier = this then foundationBackground(color)
