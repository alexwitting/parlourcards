package com.cardgames.core.ui.feedback

import androidx.compose.runtime.staticCompositionLocalOf

/** Semantic game sounds. Which file plays is the implementation's business. */
enum class CardSound {
    /** A card landing on a pile (move, drop, undo). */
    Place,

    /** A face-down card turning over. */
    Flip,

    /** Dealing from the stock. */
    Deal,

    /** A new deal or recycling the waste. */
    Shuffle,

    /** Gin: a knock (including gin). */
    Knock,

    /** Gin: the player won the hand — a short flourish, after the knock's raps. */
    HandWon,

    /** A game or a match won. */
    Win,
}

/** Semantic haptic moments — the system haptic types the motion specs name. */
enum class GameHaptic {
    /** A small step: a card placed, the upcard turning. */
    Tick,

    /** A card picked up to drag. */
    Lift,

    /** An illegal move flying back. */
    Reject,

    /** A big moment landing: a knock, a win, a hand's result. */
    Confirm,
}

/**
 * One implementation (SoundPool + the system haptics, `:core:ui`) serves every game; ViewModels emit
 * semantic Effects and screens map them here — engines stay silent. Implementations respect the
 * sound/haptics settings toggles themselves; callers just call.
 */
interface GameFeedback {
    fun play(sound: CardSound)

    fun haptic(haptic: GameHaptic)
}

/** Default: silence — previews/tests and anything outside the app shell's provider. */
object NoOpGameFeedback : GameFeedback {
    override fun play(sound: CardSound) = Unit

    override fun haptic(haptic: GameHaptic) = Unit
}

val LocalGameFeedback = staticCompositionLocalOf<GameFeedback> { NoOpGameFeedback }
