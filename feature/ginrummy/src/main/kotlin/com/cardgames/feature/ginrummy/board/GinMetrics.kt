package com.cardgames.feature.ginrummy.board

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.cards.CARD_ASPECT_RATIO
import com.cardgames.core.ui.layout.FormFactor

/**
 * Every size and spacing the gin table is laid out with — cards, piles, fans and the gaps between
 * them — so a new table size is one new value here rather than edits across the board files.
 * [Regular] is the design's (a 380 × 840dp phone); [Compact] trims the cards a little for a
 * shorter screen, so the whole table — opponent, piles, melds and hand — fits above the panel
 * without overlapping. Pick one with [forTable]. The finer dress of a card (ring widths, tag
 * padding, the dashed outline) stays with the component that draws it, and how far cards lift is
 * motion, in `GinMotionDistances`.
 */
internal data class GinMetrics(
    val pileWidth: Dp,
    val opponentCard: Dp,
    val opponentStep: Dp,
    /** The opponent's seat: name row, then the fan starting at [fanTop]. */
    val seatHeight: Dp,
    val fanTop: Dp,
    val fanDrop: Dp,
    val meldCard: Dp,
    val meldStep: Dp,
    val meldRowHeight: Dp,
    /** IN PLAY's cards when four or fewer are loose, and when more are. */
    val handCard: Dp,
    val handCardMany: Dp,
    /** The hand's fan with up to four loose cards, and with more — the hand reference (8 loose):
     * 66 × 92 cards 40dp apart, end cards at ±9.1°, ends 7dp lower. */
    val fewFan: FanShape = FanShape(overlap = 64f / 72f, turn = 4f, rest = 12.dp, arch = 0.dp, edgeTurn = 6f),
    val manyFan: FanShape = FanShape(overlap = 40f / 66f, turn = 4f, rest = 16.dp, arch = 7.dp, edgeTurn = 9.1f),
    /** How far the hand may reach past its own edges into its [handMargin] — to 4dp from the screen's. */
    val fanSpill: Dp = 12.dp,
    /** How far above a lifted hand card its points tag sits (the tag's top). */
    val tagRise: Dp = 30.dp,
    /** Side margin of the melds, hand and action feed. */
    val handMargin: Dp = 16.dp,
    /** From the opponent's fan down to the piles. */
    val fanToPiles: Dp = 20.dp,
    /** From the piles down to the action feed. */
    val feedGap: Dp = 4.dp,
    /** The melds-and-hand area's padding above and below. */
    val handAreaTop: Dp = 4.dp,
    val handAreaBottom: Dp = 6.dp,
    /** Side inset of the line between the melds and the hand. */
    val dividerInset: Dp = 18.dp,
    /** Between the stock and the discard pile. */
    val pileGap: Dp = 28.dp,
    /** Between a pile and its label. */
    val pileLabelGap: Dp = 8.dp,
    /** Each darker back under the stock shows this much more, right and down. */
    val stockPeek: Dp = 2.dp,
    /** The stock's count badge, and how far it overhangs the pile's corner. */
    val stockBadge: Dp = 30.dp,
    val stockBadgeOverhang: Dp = 10.dp,
    /** How far the "Makes a meld" chip hangs below the discard pile. */
    val meldChipDrop: Dp = 12.dp,
    /** Between melds in the meld row, and the row's top padding. */
    val meldGap: Dp = 14.dp,
    val meldRowTop: Dp = 4.dp,
    /** The piles' labels ("Stock", "Tap to discard", "Take K♣") and the "Makes a meld" chip; the tablet shows neither. */
    val pileLabels: Boolean = true,
    /** The upcard's Pass / Take under the piles (tablet), rather than in the panel. */
    val upcardOnTable: Boolean = false,
) {
    /** Every size [factor] times as big — the tablet's set fitted to a screen other than the design's 800dp. */
    fun scaled(factor: Float): GinMetrics =
        copy(
            pileWidth = pileWidth * factor,
            opponentCard = opponentCard * factor,
            opponentStep = opponentStep * factor,
            seatHeight = seatHeight * factor,
            fanTop = fanTop * factor,
            fanDrop = fanDrop * factor,
            meldCard = meldCard * factor,
            meldStep = meldStep * factor,
            meldRowHeight = meldRowHeight * factor,
            handCard = handCard * factor,
            handCardMany = handCardMany * factor,
            fewFan = fewFan.copy(rest = fewFan.rest * factor, arch = fewFan.arch * factor),
            manyFan = manyFan.copy(rest = manyFan.rest * factor, arch = manyFan.arch * factor),
            fanSpill = fanSpill * factor,
            tagRise = tagRise * factor,
            handMargin = handMargin * factor,
            fanToPiles = fanToPiles * factor,
            feedGap = feedGap * factor,
            dividerInset = dividerInset * factor,
            pileGap = pileGap * factor,
            stockBadge = stockBadge * factor,
            stockBadgeOverhang = stockBadgeOverhang * factor,
            meldGap = meldGap * factor,
        )

    /**
     * The hand's row height: the taller of its two fans, so the row keeps one height as melds form
     * and the hand switches fans (a change would move the melds and divider sharing its space).
     */
    val handHeight: Dp
        get() =
            maxOf(
                handCard / CARD_ASPECT_RATIO + fewFan.rest + fewFan.arch,
                handCardMany / CARD_ASPECT_RATIO + manyFan.rest + manyFan.arch,
            )

    companion object {
        val Regular =
            GinMetrics(
                pileWidth = 84.dp,
                opponentCard = 44.dp,
                opponentStep = 17.dp,
                seatHeight = 112.dp,
                fanTop = 46.dp,
                fanDrop = 8.dp,
                meldCard = 44.dp,
                meldStep = 22.dp,
                meldRowHeight = 72.dp,
                handCard = 72.dp,
                handCardMany = 66.dp,
            )

        val Compact =
            GinMetrics(
                pileWidth = 72.dp,
                opponentCard = 40.dp,
                opponentStep = 15.dp,
                seatHeight = 94.dp,
                fanTop = 36.dp,
                fanDrop = 6.dp,
                meldCard = 42.dp,
                meldStep = 21.dp,
                meldRowHeight = 64.dp,
                handCard = 66.dp,
                handCardMany = 58.dp,
            )

        /**
         * The tablet's (portrait) table on an 800dp-wide screen: 136dp piles, 128dp hand cards in a
         * steeper fan, 80dp meld cards, a 64dp opponent fan — a size up from the design's frames, to use
         * the tablet's height — with no pile labels, and Pass / Take under the piles. [forTable] scales
         * it to the real width.
         */
        val Tablet =
            GinMetrics(
                pileWidth = 136.dp,
                opponentCard = 64.dp,
                opponentStep = 30.dp,
                seatHeight = 172.dp,
                fanTop = 50.dp,
                fanDrop = 20.dp,
                meldCard = 80.dp,
                meldStep = 38.dp,
                meldRowHeight = 132.dp,
                handCard = 128.dp,
                handCardMany = 128.dp,
                fewFan = FanShape(overlap = 96f / 128f, turn = 5f, rest = 36.dp, arch = 16.dp, edgeTurn = 7.5f),
                manyFan = FanShape(overlap = 86f / 128f, turn = 4f, rest = 38.dp, arch = 60.dp, edgeTurn = 11f),
                fanSpill = 16.dp,
                tagRise = 40.dp,
                handMargin = 24.dp,
                fanToPiles = 40.dp,
                feedGap = 24.dp,
                dividerInset = 180.dp,
                pileGap = 36.dp,
                stockBadge = 38.dp,
                stockBadgeOverhang = 13.dp,
                meldGap = 28.dp,
                pileLabels = false,
                upcardOnTable = true,
            )

        /** The width the tablet set is drawn for; other tablets scale it (never below the phone's). */
        val TabletDesignWidth = 800.dp

        /** The table height below which [Compact] is used: what [Regular]'s table needs above the panel. */
        val RegularMinHeight = 554.dp

        /** The sizes for a table [width] × [height] (the felt above the panel) on [formFactor]. */
        fun forTable(
            formFactor: FormFactor,
            width: Dp,
            height: Dp,
        ): GinMetrics =
            when (formFactor) {
                FormFactor.PHONE -> if (height < RegularMinHeight) Compact else Regular
                FormFactor.TABLET -> Tablet.scaled((width / TabletDesignWidth).coerceIn(MIN_TABLET_SCALE, 1f))
            }

        /** The smallest the tablet set shrinks to: its hand cards are then still bigger than the phone's. */
        private const val MIN_TABLET_SCALE = 0.75f
    }
}

/** One of the design's two hand layouts: how far apart, how much each card turns, how they rest. */
@Immutable
internal data class FanShape(
    /** Step between cards as a share of a card's width. */
    val overlap: Float,
    /** Degrees each card turns per place from the middle. */
    val turn: Float,
    /** How far down a card rests (room above it for a lift). */
    val rest: Dp,
    /** How far the outermost cards sit below the middle one; 0 for a level row. */
    val arch: Dp,
    /** The most the end cards turn, however many there are; the per-place [turn] shrinks to fit. */
    val edgeTurn: Float,
) {
    /** The turn of the card at [index] of [count]: [turn] a place from the middle, ends at most [edgeTurn]. */
    fun turnOf(
        index: Int,
        count: Int,
    ): Float {
        val mid = (count - 1) / 2f
        if (mid <= 0f) return 0f
        if (count == 2) return (index - mid) * TWO_CARD_TURN
        return (index - mid) * minOf(turn, edgeTurn / mid)
    }
}

/** Two cards turn this far apart, rather than by the per-place turn. */
private const val TWO_CARD_TURN = 6f

internal val LocalGinMetrics = staticCompositionLocalOf { GinMetrics.Regular }
