package com.vayunmathur.games.solitaire.data

data class TableauPile(
    val faceDown: List<Card> = emptyList(),
    val faceUp: List<Card> = emptyList()
)

enum class GameMode { KLONDIKE, SPIDER, FREECELL }

enum class DrawMode { DRAW_ONE, DRAW_THREE }

data class KlondikeState(
    val stock: List<Card> = emptyList(),
    val waste: List<Card> = emptyList(),
    val tableauPiles: List<TableauPile> = List(7) { TableauPile() },
    val foundations: List<List<Card>> = List(4) { emptyList() },
    val drawMode: DrawMode = DrawMode.DRAW_ONE,
    val moveCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val usedUndo: Boolean = false,
    val isWon: Boolean = false
)

data class SpiderState(
    val tableauPiles: List<TableauPile> = List(10) { TableauPile() },
    val stockGroups: List<List<Card>> = emptyList(),
    val completedSuits: Int = 0,
    val moveCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val usedUndo: Boolean = false,
    val isWon: Boolean = false
)

data class FreeCellState(
    val tableauPiles: List<List<Card>> = List(8) { emptyList() },
    val freeCells: List<Card?> = List(4) { null },
    val foundations: List<List<Card>> = List(4) { emptyList() },
    val moveCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val usedUndo: Boolean = false,
    val isWon: Boolean = false
)

data class SolitaireUiState(
    val gameMode: GameMode? = null,
    val klondike: KlondikeState? = null,
    val spider: SpiderState? = null,
    val freeCell: FreeCellState? = null,
    val history: List<Any> = emptyList()
)
