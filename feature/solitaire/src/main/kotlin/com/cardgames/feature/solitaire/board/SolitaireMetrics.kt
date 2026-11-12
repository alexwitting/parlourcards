package com.cardgames.feature.solitaire.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cardgames.core.domain.solitaire.KlondikeState
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * Every size and spacing the solitaire table is laid out with, one set per [FormFactor] — so a new
 * screen size is a new value here rather than edits across the board files. Cards are sized from the
 * column width ([columnWidthFor]); the other piles are multiples of it. Read the current set with
 * [solitaireMetrics].
 */
@Immutable
internal data class SolitaireMetrics(
    /** The board's side margins, outside the first and last column. */
    val boardMargin: Dp,
    /** Between columns. */
    val slotGap: Dp,
    /** Felt above the foundations. */
    val tableTop: Dp,
    /** Between the foundations and the tableau. */
    val foundationGap: Dp,
    /** The foundations' width, as a multiple of a column's. */
    val foundationScale: Float,
    /** The foundation row's extra inset inside the board margins, each side. */
    val foundationInset: Dp,
    /** The foundations centred this far apart; null spreads them across the board. */
    val foundationSpacing: Dp?,
    /** The stock and waste's width, as a multiple of a column's. */
    val drawScale: Float,
    /** Between the stock and the waste. */
    val drawGap: Dp,
    /** How far apart the draw-3 waste fans its cards. */
    val wasteFanStep: Dp,
    /** Under the draw row, above the panel. */
    val drawRowBottom: Dp,
    /** Felt kept under the draw row for the panel's status line to rise into. */
    val statusRoom: Dp,
    /** How much of a card shows under the next one in a column, face down and face up (share of its height). */
    val faceDownPeek: Float,
    val faceUpPeek: Float,
    /** How far a selected card rises. */
    val selectLift: Dp,
    /** How far the stock's second card shows behind the top one. */
    val stockPeek: Dp,
    /** Where the stock's count badge sits off its top-right corner. */
    val stockBadgeOverhang: DpOffset,
) {
    /** A tableau column's width on a [board] of this size: seven columns, [slotGap] apart, inside [boardMargin]s. */
    fun columnWidthFor(board: DpSize): Dp =
        (board.width - boardMargin * 2 - slotGap * (KlondikeState.TABLEAU_PILE_COUNT - 1)) / KlondikeState.TABLEAU_PILE_COUNT

    companion object {
        val Phone =
            SolitaireMetrics(
                boardMargin = 17.dp,
                slotGap = 5.dp,
                tableTop = 14.dp,
                foundationGap = 18.dp,
                foundationScale = 1.25f,
                foundationInset = 3.dp,
                foundationSpacing = null,
                drawScale = 1.5f,
                drawGap = 22.dp,
                wasteFanStep = 24.dp,
                drawRowBottom = 12.dp,
                statusRoom = 34.dp,
                faceDownPeek = 0.15f,
                faceUpPeek = 0.33f,
                selectLift = 6.dp,
                stockPeek = 3.dp,
                stockBadgeOverhang = DpOffset(14.dp, (-10).dp),
            )

        /**
         * The tablet's (portrait) table: every pile a column wide (96dp on an 800dp screen, scaling
         * with the width), the foundations centred over the tableau, the stock and waste centred above
         * the dock with a wider draw-3 fan, and no status line rising from a panel.
         */
        val Tablet =
            Phone.copy(
                boardMargin = 28.dp,
                slotGap = 12.dp,
                tableTop = 24.dp,
                foundationGap = 36.dp,
                foundationScale = 1f,
                foundationSpacing = 12.dp,
                drawScale = 1f,
                drawGap = 28.dp,
                wasteFanStep = 40.dp,
                // Raised clear of the dock, into the felt the tableau rarely reaches.
                drawRowBottom = 64.dp,
                statusRoom = 0.dp,
                selectLift = 18.dp,
            )

        /** The sizes for [formFactor]. */
        fun of(formFactor: FormFactor): SolitaireMetrics =
            when (formFactor) {
                FormFactor.PHONE -> Phone
                FormFactor.TABLET -> Tablet
            }
    }
}

/** The [SolitaireMetrics] for the current screen. */
internal val solitaireMetrics: SolitaireMetrics
    @Composable @ReadOnlyComposable
    get() = SolitaireMetrics.of(CardGamesTheme.formFactor)
