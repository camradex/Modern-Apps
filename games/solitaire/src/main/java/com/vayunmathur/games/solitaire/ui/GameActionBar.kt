package com.vayunmathur.games.solitaire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vayunmathur.games.solitaire.R

@Composable
fun GameActionBar(
    moveCount: Int,
    elapsedSeconds: Int,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onNewGame: () -> Unit,
    undoEnabled: Boolean,
    modifier: Modifier = Modifier,
    extraContent: @Composable () -> Unit = {}
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeText = "%02d:%02d".format(minutes, seconds)

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            timeText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "${stringResource(R.string.moves)}: $moveCount",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Button(onClick = onUndo, enabled = undoEnabled) {
            Text(stringResource(R.string.undo))
        }
        IconButton(onClick = onRestart) {
            Text("↻")
        }
        Button(onClick = onNewGame) {
            Text(stringResource(R.string.new_game))
        }
        extraContent()
    }
}
