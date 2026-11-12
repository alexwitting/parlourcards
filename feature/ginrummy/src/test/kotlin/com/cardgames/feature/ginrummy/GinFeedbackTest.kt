package com.cardgames.feature.ginrummy

import com.cardgames.core.testing.ui.RecordingFeedback
import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameHaptic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GinFeedbackTest {
    @Test
    fun `CardMoved plays a place sound with a plain tick`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.CardMoved)
        assertEquals(CardSound.Place, feedback.lastSound)
        assertEquals(GameHaptic.Tick, feedback.lastHaptic)
    }

    @Test
    fun `NewHandDealt and HandBlocked both shuffle`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.NewHandDealt)
        assertEquals(CardSound.Shuffle, feedback.lastSound)

        feedback.reactTo(GinEffect.HandBlocked)
        assertEquals(CardSound.Shuffle, feedback.lastSound)
    }

    @Test
    fun `Knocked plays the knock sound and a confirm haptic`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.Knocked)
        assertEquals(CardSound.Knock, feedback.lastSound)
        assertEquals(GameHaptic.Confirm, feedback.lastHaptic)
    }

    @Test
    fun `HandWon knocks, then plays the hand-won flourish, with a confirm haptic`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.HandWon)
        assertEquals(listOf(CardSound.Knock, CardSound.HandWon), feedback.sounds)
        assertEquals(GameHaptic.Confirm, feedback.lastHaptic)
    }

    @Test
    fun `MatchWon plays the win sound and a confirm haptic`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.MatchWon)
        assertEquals(CardSound.Win, feedback.lastSound)
        assertEquals(GameHaptic.Confirm, feedback.lastHaptic)
    }

    @Test
    fun `ShowSnackbar plays no sound and no haptic — it's a UI-only effect`() {
        val feedback = RecordingFeedback()
        feedback.reactTo(GinEffect.ShowSnackbar("message"))
        assertNull(feedback.lastSound)
        assertNull(feedback.lastHaptic)
    }
}
