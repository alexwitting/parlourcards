package com.cardgames.core.testing.ui

import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameFeedback
import com.cardgames.core.ui.feedback.GameHaptic

/** A [GameFeedback] that remembers the sounds (in order) and the last haptic it was asked for. */
class RecordingFeedback : GameFeedback {
    private val played = mutableListOf<CardSound>()

    /** Every sound asked for, in order. */
    val sounds: List<CardSound> get() = played

    var lastSound: CardSound? = null
        private set
    var lastHaptic: GameHaptic? = null
        private set

    override fun play(sound: CardSound) {
        played += sound
        lastSound = sound
    }

    override fun haptic(haptic: GameHaptic) {
        lastHaptic = haptic
    }
}
