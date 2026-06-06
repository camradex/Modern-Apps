package com.vayunmathur.games.solitaire.data

enum class Suit(val symbol: String, val isRed: Boolean) {
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    SPADES("♠", false),
    CLUBS("♣", false)
}

enum class Rank(val display: String, val value: Int) {
    ACE("A", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13)
}

data class Card(val suit: Suit, val rank: Rank)

fun createDeck(): List<Card> =
    Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(suit, rank) } }

fun createShuffledDeck(seed: Long = System.currentTimeMillis()): List<Card> =
    createDeck().shuffled(java.util.Random(seed))

fun Card.isOneHigherThan(other: Card): Boolean =
    rank.value == other.rank.value + 1

fun Card.alternatesColorWith(other: Card): Boolean =
    suit.isRed != other.suit.isRed
