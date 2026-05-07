package com.vayunmathur.games.wordmaker.util

import java.util.Random

class LevelGenerator(private val wordList: List<String>) {
    private val random = Random()

    fun generateLevel(level: Int): String {
        // Retry until a valid level is generated
        repeat(100) {
            val grid = Array(8) { CharArray(10) { ' ' } }
            val wordsToPlace = selectCandidateWords(level)
            val placedWords = mutableListOf<String>()

            // Try to place words
            if (placeWords(grid, wordsToPlace, placedWords)) {
                // 1 in 3 chance for a disconnected word
                if (level % 3 == 0) {
                    addDisconnectedWord(grid, placedWords, level)
                }
                
                if (placedWords.size >= 4) {
                    return gridToString(grid)
                }
            }
        }
        // Fallback (should ideally not be reached with enough retries)
        return "ERROR\nLEVEL"
    }

    private fun selectCandidateWords(level: Int): List<String> {
        val baseOffset = (level - 861) * 10
        val start = minOf(baseOffset, wordList.size - 5000)
        val end = minOf(start + 5000, wordList.size)
        
        val candidates = mutableListOf<String>()
        repeat(50) {
            val index = if (random.nextFloat() < 0.7f) {
                random.nextInt(minOf(5000, wordList.size))
            } else {
                random.nextInt(end - start) + start
            }
            val word = wordList[index].uppercase()
            if (word.length in 3..8 && word !in candidates) {
                candidates.add(word)
            }
        }
        return candidates.shuffled()
    }

    private fun placeWords(grid: Array<CharArray>, words: List<String>, placed: MutableList<String>): Boolean {
        if (words.isEmpty()) return true
        
        // Place first word
        val firstWord = words[0]
        if (placeFirstWord(grid, firstWord)) {
            placed.add(firstWord)
            
            // Try to place remaining words
            for (i in 1 until words.size) {
                val word = words[i]
                if (tryPlaceIntersecting(grid, word, placed)) {
                    placed.add(word)
                }
                if (placed.size >= 8) break // Enough words
            }
            return placed.size >= 4
        }
        return false
    }

    private fun placeFirstWord(grid: Array<CharArray>, word: String): Boolean {
        val horizontal = random.nextBoolean()
        val r = random.nextInt(8)
        val c = random.nextInt(10)
        
        if (canPlace(grid, word, r, c, horizontal)) {
            doPlace(grid, word, r, c, horizontal)
            return true
        }
        return false
    }

    private fun tryPlaceIntersecting(grid: Array<CharArray>, word: String, placed: List<String>): Boolean {
        // Find all possible intersections
        val possiblePlacements = mutableListOf<Placement>()
        
        for (placedWord in placed) {
            // This is simplified: we just iterate the grid
            for (r in 0 until 8) {
                for (c in 0 until 10) {
                    // Try horizontal
                    if (canPlace(grid, word, r, c, true) && intersects(grid, word, r, c, true)) {
                        possiblePlacements.add(Placement(r, c, true))
                    }
                    // Try vertical
                    if (canPlace(grid, word, r, c, false) && intersects(grid, word, r, c, false)) {
                        possiblePlacements.add(Placement(r, c, false))
                    }
                }
            }
        }
        
        if (possiblePlacements.isNotEmpty()) {
            val p = possiblePlacements[random.nextInt(possiblePlacements.size)]
            doPlace(grid, word, p.r, p.c, p.horizontal)
            return true
        }
        return false
    }

    private fun addDisconnectedWord(grid: Array<CharArray>, placed: MutableList<String>, level: Int) {
        val word = selectCandidateWords(level).firstOrNull { it !in placed } ?: return
        val row = if (random.nextBoolean()) 0 else 7
        
        val possibleCols = mutableListOf<Int>()
        for (c in 0..10 - word.length) {
            if (canPlace(grid, word, row, c, true, isolated = true)) {
                possibleCols.add(c)
            }
        }
        
        if (possibleCols.isNotEmpty()) {
            val c = possibleCols[random.nextInt(possibleCols.size)]
            doPlace(grid, word, row, c, true)
            placed.add(word)
        }
    }

    private fun canPlace(grid: Array<CharArray>, word: String, r: Int, c: Int, horizontal: Boolean, isolated: Boolean = false): Boolean {
        if (horizontal) {
            if (c + word.length > 10) return false
            for (i in 0 until word.length) {
                val char = grid[r][c + i]
                if (char != ' ' && char != word[i]) return false
                // Check neighbors to avoid accidental words
                if (char == ' ') {
                    if (!checkNeighbors(grid, r, c + i, horizontal, word[i])) return false
                }
            }
            // Check ends
            if (c > 0 && grid[r][c - 1] != ' ') return false
            if (c + word.length < 10 && grid[r][c + word.length] != ' ') return false
        } else {
            if (r + word.length > 8) return false
            for (i in 0 until word.length) {
                val char = grid[r + i][c]
                if (char != ' ' && char != word[i]) return false
                if (char == ' ') {
                    if (!checkNeighbors(grid, r + i, c, horizontal, word[i])) return false
                }
            }
            if (r > 0 && grid[r - 1][c] != ' ') return false
            if (r + word.length < 8 && grid[r + word.length][c] != ' ') return false
        }
        
        if (isolated) {
            // Ensure no intersections at all
            for (i in 0 until word.length) {
                val currR = if (horizontal) r else r + i
                val currC = if (horizontal) c + i else c
                if (hasAnyNeighbor(grid, currR, currC)) return false
            }
        }
        
        return true
    }

    private fun checkNeighbors(grid: Array<CharArray>, r: Int, c: Int, horizontal: Boolean, char: Char): Boolean {
        // If placing horizontal, check vertical neighbors and vice-versa
        if (horizontal) {
            if (r > 0 && grid[r - 1][c] != ' ') return false
            if (r < 7 && grid[r + 1][c] != ' ') return false
        } else {
            if (c > 0 && grid[r][c - 1] != ' ') return false
            if (c < 9 && grid[r][c + 1] != ' ') return false
        }
        return true
    }
    
    private fun hasAnyNeighbor(grid: Array<CharArray>, r: Int, c: Int): Boolean {
        val dr = intArrayOf(-1, 1, 0, 0)
        val dc = intArrayOf(0, 0, -1, 1)
        for (i in 0 until 4) {
            val nr = r + dr[i]
            val nc = c + dc[i]
            if (nr in 0 until 8 && nc in 0 until 10 && grid[nr][nc] != ' ') return true
        }
        return false
    }

    private fun intersects(grid: Array<CharArray>, word: String, r: Int, c: Int, horizontal: Boolean): Boolean {
        for (i in 0 until word.length) {
            val currR = if (horizontal) r else r + i
            val currC = if (horizontal) c + i else c
            if (grid[currR][currC] == word[i]) return true
        }
        return false
    }

    private fun doPlace(grid: Array<CharArray>, word: String, r: Int, c: Int, horizontal: Boolean) {
        for (i in 0 until word.length) {
            if (horizontal) grid[r][c + i] = word[i] else grid[r + i][c] = word[i]
        }
    }

    private fun gridToString(grid: Array<CharArray>): String {
        return grid.joinToString("\n") { it.joinToString("") }
    }

    data class Placement(val r: Int, val c: Int, val horizontal: Boolean)
}
