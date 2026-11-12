package com.cardgames.feature.solitaire

import com.cardgames.core.testing.ui.RecordingFeedback
import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameHaptic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SolitaireFeedbackTest {
    private fun reactionTo(effect: SolitaireEffect) = RecordingFeedback().apply { reactTo(effect) }

    @Test
    fun `each card effect plays its sound with a tick`() {
        listOf(
            SolitaireEffect.CardMoved to CardSound.Place,
            SolitaireEffect.CardFlipped to CardSound.Flip,
            SolitaireEffect.CardsDealt to CardSound.Deal,
            SolitaireEffect.Shuffled to CardSound.Shuffle,
        ).forEach { (effect, sound) ->
            val feedback = reactionTo(effect)
            assertEquals(sound, feedback.lastSound, "$effect")
            assertEquals(GameHaptic.Tick, feedback.lastHaptic, "$effect")
        }
    }

    @Test
    fun `a win plays the win sound with a confirm`() {
        val feedback = reactionTo(SolitaireEffect.GameWon)

        assertEquals(CardSound.Win, feedback.lastSound)
        assertEquals(GameHaptic.Confirm, feedback.lastHaptic)
    }

    @Test
    fun `a snackbar is silent`() {
        val feedback = reactionTo(SolitaireEffect.ShowSnackbar("Couldn't save your game"))

        assertNull(feedback.lastSound)
        assertNull(feedback.lastHaptic)
    }
}
