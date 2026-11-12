package com.cardgames.feature.ginrummy

import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameFeedback
import com.cardgames.core.ui.feedback.GameHaptic

internal fun GameFeedback.reactTo(effect: GinEffect) {
    when (effect) {
        GinEffect.CardMoved -> play(CardSound.Place)
        GinEffect.NewHandDealt -> play(CardSound.Shuffle)
        GinEffect.HandBlocked -> play(CardSound.Shuffle)
        GinEffect.Knocked -> play(CardSound.Knock)
        GinEffect.HandWon -> {
            // The raps, then the flourish (its file starts with a beat of silence, so it follows them).
            play(CardSound.Knock)
            play(CardSound.HandWon)
        }
        GinEffect.MatchWon -> play(CardSound.Win)
        is GinEffect.ShowSnackbar -> return
        GinEffect.CantDiscard -> {
            haptic(GameHaptic.Reject)
            return
        }
    }
    haptic(
        when (effect) {
            GinEffect.MatchWon, GinEffect.Knocked, GinEffect.HandWon -> GameHaptic.Confirm
            else -> GameHaptic.Tick
        },
    )
}
