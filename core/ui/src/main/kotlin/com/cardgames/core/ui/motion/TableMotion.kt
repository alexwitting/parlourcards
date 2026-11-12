package com.cardgames.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The card-table motion values that depend on the screen's size, one set per [FormFactor]; read the
 * current one with [CardGamesMotion.table]. Everything else about motion (durations, curves,
 * springs, staggers) is the same everywhere and stays in [CardGamesMotion].
 */
@Immutable
data class TableMotion(
    /** The longest a flight stretches to, as a multiple of [CardGamesMotion.Flight] (see [flightMillisFor]). */
    val flightMaxStretch: Float,
    /** How far an illegal move shakes the card, [CardGamesMotion.SHAKE_CYCLES] times. */
    val shakeDistance: Dp,
) {
    /**
     * A flight's length at normal speed for a path [distance] long: [CardGamesMotion.Flight] up to
     * 300dp, then stretched with the distance, up to [flightMaxStretch].
     */
    fun flightMillisFor(distance: Dp): Int =
        (CardGamesMotion.Flight.millis * (distance / FLIGHT_REFERENCE).coerceIn(1f, flightMaxStretch)).toInt()

    companion object {
        val Phone = TableMotion(flightMaxStretch = 1.375f, shakeDistance = 6.dp)

        /** Paths about twice the phone's: flights stretch to 480ms; a bigger card shakes further. */
        val Tablet = TableMotion(flightMaxStretch = 1.5f, shakeDistance = 8.dp)

        /** The values for [formFactor]. */
        fun of(formFactor: FormFactor): TableMotion =
            when (formFactor) {
                FormFactor.PHONE -> Phone
                FormFactor.TABLET -> Tablet
            }

        private val FLIGHT_REFERENCE = 300.dp
    }
}

/** The [TableMotion] for the current screen. */
val CardGamesMotion.table: TableMotion
    @Composable @ReadOnlyComposable
    get() = TableMotion.of(CardGamesTheme.formFactor)
