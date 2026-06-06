package com.vayunmathur.games.solitaire.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class GameStats(
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val bestTimeSeconds: Int = Int.MAX_VALUE
)

class SolitaireStatsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("solitaire_stats", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getStats(mode: GameMode): GameStats {
        val raw = prefs.getString("stats_${mode.name}", null) ?: return GameStats()
        return json.decodeFromString(raw)
    }

    private fun saveStats(mode: GameMode, stats: GameStats) {
        prefs.edit().putString("stats_${mode.name}", json.encodeToString(stats)).apply()
    }

    fun recordGamePlayed(mode: GameMode) {
        val stats = getStats(mode)
        saveStats(mode, stats.copy(gamesPlayed = stats.gamesPlayed + 1))
    }

    fun recordGameWon(mode: GameMode, timeSeconds: Int, moves: Int) {
        val stats = getStats(mode)
        val newStreak = stats.currentWinStreak + 1
        saveStats(
            mode, stats.copy(
                gamesWon = stats.gamesWon + 1,
                currentWinStreak = newStreak,
                bestWinStreak = maxOf(stats.bestWinStreak, newStreak),
                bestTimeSeconds = minOf(stats.bestTimeSeconds, timeSeconds)
            )
        )
    }

    fun recordGameLost(mode: GameMode) {
        val stats = getStats(mode)
        saveStats(mode, stats.copy(currentWinStreak = 0))
    }

    fun getTotalGamesWon(): Int =
        GameMode.entries.sumOf { getStats(it).gamesWon }

    fun getBestWinStreak(): Int =
        GameMode.entries.maxOf { getStats(it).bestWinStreak }
}
