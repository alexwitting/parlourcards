package com.cardgames.feature.ginrummy.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * How far Gin's cards move when they lift or arc, one set per [FormFactor] (bigger cards on a
 * bigger table move further). Read the current set with [ginMotionDistances]; the timings stay in
 * [GinMotionTokens].
 */
@Immutable
internal data class GinMotionDistances(
    /** How far hand cards lift: the selected throw, the card just drawn, cards that meld with the pile. */
    val selectedLift: Dp,
    val drawnLift: Dp,
    val matchLift: Dp,
    /** How high a discard rises at mid-flight on its way to the pile. */
    val discardArc: Dp,
    /** The player's melds and hand while the opponent plays (the bigger tablet cards dim further, to read as locked). */
    val lockedHandAlpha: Float,
) {
    companion object {
        val Phone =
            GinMotionDistances(selectedLift = 14.dp, drawnLift = 12.dp, matchLift = 12.dp, discardArc = 20.dp, lockedHandAlpha = 0.9f)

        /** Longer paths, bigger lifts: the discard's arc is about 0.06 × its ~500dp path. */
        val Tablet =
            GinMotionDistances(selectedLift = 28.dp, drawnLift = 18.dp, matchLift = 18.dp, discardArc = 30.dp, lockedHandAlpha = 0.5f)

        /** The distances for [formFactor]. */
        fun of(formFactor: FormFactor): GinMotionDistances =
            when (formFactor) {
                FormFactor.PHONE -> Phone
                FormFactor.TABLET -> Tablet
            }
    }
}

/** The [GinMotionDistances] for the current screen. */
internal val ginMotionDistances: GinMotionDistances
    @Composable @ReadOnlyComposable
    get() = GinMotionDistances.of(CardGamesTheme.formFactor)
