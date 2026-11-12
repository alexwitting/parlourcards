package com.cardgames.feature.solitaire

import com.cardgames.core.ui.feedback.CardSound
import com.cardgames.core.ui.feedback.GameFeedback
import com.cardgames.core.ui.feedback.GameHaptic

/** Each solitaire effect's sound and haptic. */
internal fun GameFeedback.reactTo(effect: SolitaireEffect) {
    when (effect) {
        SolitaireEffect.CardMoved -> play(CardSound.Place)
        SolitaireEffect.CardFlipped -> play(CardSound.Flip)
        SolitaireEffect.CardsDealt -> play(CardSound.Deal)
        SolitaireEffect.Shuffled -> play(CardSound.Shuffle)
        SolitaireEffect.GameWon -> play(CardSound.Win)
        is SolitaireEffect.ShowSnackbar -> return
    }
    haptic(if (effect == SolitaireEffect.GameWon) GameHaptic.Confirm else GameHaptic.Tick)
}
