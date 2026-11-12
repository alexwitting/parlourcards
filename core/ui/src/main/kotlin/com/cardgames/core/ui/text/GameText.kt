package com.cardgames.core.ui.text

import com.cardgames.core.domain.game.GameId
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.stats.Record

/** The one user-facing name for each game — shared by Home, Stats and the game screens. */
val GameId.displayName: String
    get() =
        when (this) {
            GameId.KLONDIKE -> "Solitaire"
            GameId.GIN_RUMMY -> "Gin Rummy"
        }

/** The Gin opponent's name at each difficulty — Milo, Rosa, Vex. */
val GinDifficulty.opponentName: String
    get() =
        when (this) {
            GinDifficulty.EASY -> "Milo"
            GinDifficulty.MEDIUM -> "Rosa"
            GinDifficulty.HARD -> "Vex"
        }

/** The difficulty as a word ("Medium") — the opponent's chip reads "Medium bot". */
val GinDifficulty.levelName: String
    get() =
        when (this) {
            GinDifficulty.EASY -> "Easy"
            GinDifficulty.MEDIUM -> "Medium"
            GinDifficulty.HARD -> "Hard"
        }

/** "Ro" — the opponent's avatar. */
val GinDifficulty.initials: String
    get() = opponentName.take(2)

/** The record against [name] in a line: "You lead Rosa 12 – 10", "Rosa leads 10 – 8", "Level with Rosa 3 – 3". */
fun Record.lineAgainst(name: String): String =
    when {
        wins > losses -> "You lead $name $wins – $losses"
        wins < losses -> "$name leads $losses – $wins"
        else -> "Level with $name $wins – $losses"
    }

/** The record on a rival's own card, where the name is already shown: "You lead 8 – 2", "Leads 4 – 1". */
fun Record.shortLine(): String =
    when {
        played == 0 -> "Not played yet"
        wins > losses -> "You lead $wins – $losses"
        wins < losses -> "Leads $losses – $wins"
        else -> "Level $wins – $losses"
    }

/** "m:ss" for a play time or best time. */
fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}

private const val SECONDS_PER_MINUTE = 60
