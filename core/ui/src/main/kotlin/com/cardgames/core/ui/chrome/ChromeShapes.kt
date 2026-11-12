package com.cardgames.core.ui.chrome

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The chrome's corner radii (see `ParlourChrome.kt` and `ChromeComponents.kt` for the pieces that use them). */
object ChromeShapes {
    /** Primary and secondary buttons. */
    val Button = RoundedCornerShape(20.dp)

    /** Tool tiles (Undo, Hint, New). */
    val ToolTile = RoundedCornerShape(16.dp)

    /** Soft surfaces on the felt: score cards, stat tiles, grouped lists. */
    val Soft = RoundedCornerShape(22.dp)

    /** Pills and chips. */
    val Pill = RoundedCornerShape(percent = 50)

    /** The bottom panel: rounded on top only. */
    val Panel = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Sheets and dialogs. */
    val Sheet = RoundedCornerShape(24.dp)

    /** Home game tiles. */
    val Tile = RoundedCornerShape(18.dp)

    /** Grouped settings and stats lists. */
    val Group = RoundedCornerShape(16.dp)

    /** Icon buttons on the table. */
    val Icon = RoundedCornerShape(14.dp)

    /** A segmented choice's track, and each option inside it. */
    val Segment = RoundedCornerShape(10.dp)
    val SegmentOption = RoundedCornerShape(8.dp)
}
