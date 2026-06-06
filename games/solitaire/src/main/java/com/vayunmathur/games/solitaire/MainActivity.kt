package com.vayunmathur.games.solitaire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vayunmathur.games.solitaire.data.DrawMode
import com.vayunmathur.games.solitaire.data.GameMode
import com.vayunmathur.games.solitaire.ui.FreeCellBoard
import com.vayunmathur.games.solitaire.ui.GameActionBar
import com.vayunmathur.games.solitaire.ui.KlondikeBoard
import com.vayunmathur.games.solitaire.ui.SpiderBoard
import com.vayunmathur.games.solitaire.ui.WinOverlay
import com.vayunmathur.games.solitaire.util.AppBackupAgent
import com.vayunmathur.games.solitaire.util.SolitaireViewModel
import com.vayunmathur.library.ui.AchievementNotification
import com.vayunmathur.library.ui.DynamicTheme
import com.vayunmathur.library.ui.GameCenterScreen
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.MainNavigation
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.NavKey
import com.vayunmathur.library.util.rememberNavBackStack
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DynamicTheme {
                val viewModel: SolitaireViewModel = viewModel()
                Navigation(viewModel)
            }
        }
    }
}

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object ModeSelector : Route

    @Serializable
    data class Game(val mode: GameMode) : Route

    @Serializable
    data object GameCenter : Route
}

@Composable
fun Navigation(viewModel: SolitaireViewModel) {
    val backStack = rememberNavBackStack<Route>(Route.ModeSelector)
    val newAchievement by viewModel.achievementsManager.newAchievement.collectAsState()

    Box(Modifier.fillMaxSize()) {
        MainNavigation(backStack) {
            entry<Route.ModeSelector> {
                ModeSelectorScreen(backStack, viewModel)
            }
            entry<Route.Game> {
                GameScreen(backStack, viewModel, it.mode)
            }
            entry<Route.GameCenter> {
                GameCenterScreen(
                    backupAgent = AppBackupAgent(),
                    manager = viewModel.achievementsManager,
                    onBack = { backStack.pop() }
                )
            }
        }

        newAchievement?.let {
            AchievementNotification(it) {
                viewModel.dismissAchievementNotification()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectorScreen(backStack: NavBackStack<Route>, viewModel: SolitaireViewModel) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = { backStack.add(Route.GameCenter) }) {
                    Icon(painterResource(id = android.R.drawable.btn_star_big_on), "Achievements")
                }
                com.vayunmathur.library.ui.BackupButtons(prefNames = listOf("solitaire_stats"))
            }
        )
    }) { paddingValues ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = paddingValues + PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(GameMode.entries) { mode ->
                val stats = viewModel.getStats(mode)
                val modeName = when (mode) {
                    GameMode.KLONDIKE -> stringResource(R.string.klondike)
                    GameMode.SPIDER -> stringResource(R.string.spider)
                    GameMode.FREECELL -> stringResource(R.string.freecell)
                }
                Card(
                    Modifier.clickable {
                        viewModel.selectMode(mode)
                        backStack.add(Route.Game(mode))
                    },
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(modeName, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "${stats.gamesWon}/${stats.gamesPlayed} won",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (stats.bestTimeSeconds < Int.MAX_VALUE) {
                            Text(
                                "%02d:%02d".format(stats.bestTimeSeconds / 60, stats.bestTimeSeconds % 60),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(backStack: NavBackStack<Route>, viewModel: SolitaireViewModel, mode: GameMode) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(mode) {
        if (uiState.gameMode != mode) {
            viewModel.selectMode(mode)
        }
    }

    LaunchedEffect(uiState.gameMode) {
        while (true) {
            delay(1000)
            viewModel.incrementTimer()
        }
    }

    val isWon = when (mode) {
        GameMode.KLONDIKE -> uiState.klondike?.isWon == true
        GameMode.SPIDER -> uiState.spider?.isWon == true
        GameMode.FREECELL -> uiState.freeCell?.isWon == true
    }
    val moveCount = when (mode) {
        GameMode.KLONDIKE -> uiState.klondike?.moveCount ?: 0
        GameMode.SPIDER -> uiState.spider?.moveCount ?: 0
        GameMode.FREECELL -> uiState.freeCell?.moveCount ?: 0
    }
    val elapsed = when (mode) {
        GameMode.KLONDIKE -> uiState.klondike?.elapsedSeconds ?: 0
        GameMode.SPIDER -> uiState.spider?.elapsedSeconds ?: 0
        GameMode.FREECELL -> uiState.freeCell?.elapsedSeconds ?: 0
    }

    val modeName = when (mode) {
        GameMode.KLONDIKE -> stringResource(R.string.klondike)
        GameMode.SPIDER -> stringResource(R.string.spider)
        GameMode.FREECELL -> stringResource(R.string.freecell)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(modeName) },
            navigationIcon = { IconNavigation(backStack) }
        )
    }) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameActionBar(
                    moveCount = moveCount,
                    elapsedSeconds = elapsed,
                    onUndo = { viewModel.undo() },
                    onRestart = { viewModel.restart() },
                    onNewGame = { viewModel.selectMode(mode) },
                    undoEnabled = uiState.history.isNotEmpty() && !isWon,
                    extraContent = {
                        if (mode == GameMode.KLONDIKE) {
                            val drawLabel = if (uiState.klondike?.drawMode == DrawMode.DRAW_ONE)
                                stringResource(R.string.draw_one) else stringResource(R.string.draw_three)
                            Button(onClick = { viewModel.toggleDrawMode() }) {
                                Text(drawLabel)
                            }
                        }
                    }
                )

                when (mode) {
                    GameMode.KLONDIKE -> uiState.klondike?.let {
                        KlondikeBoard(it, viewModel, Modifier.fillMaxWidth())
                        if (!it.isWon && it.stock.isEmpty() && it.tableauPiles.none { p -> p.faceDown.isNotEmpty() }) {
                            Button(
                                onClick = { viewModel.klondikeAutoComplete() },
                                Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Auto Complete")
                            }
                        }
                    }
                    GameMode.SPIDER -> uiState.spider?.let {
                        SpiderBoard(it, viewModel, Modifier.fillMaxWidth())
                    }
                    GameMode.FREECELL -> uiState.freeCell?.let {
                        FreeCellBoard(it, viewModel, Modifier.fillMaxWidth())
                    }
                }
            }

            if (isWon) {
                WinOverlay(
                    elapsedSeconds = elapsed,
                    moveCount = moveCount,
                    onNewGame = { viewModel.selectMode(mode) },
                    onBack = { backStack.pop() }
                )
            }
        }
    }
}
