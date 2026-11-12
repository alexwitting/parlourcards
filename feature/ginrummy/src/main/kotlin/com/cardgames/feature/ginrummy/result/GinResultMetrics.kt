package com.cardgames.feature.ginrummy.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The sizes of Gin's result and setup screens, one set per [FormFactor]; read the current one with
 * [ginResultMetrics]. Type and colour are the same everywhere; the tablet's pages are wider-margined,
 * more spaced, and show bigger cards and avatars.
 */
@Immutable
internal data class GinResultMetrics(
    /** The page's side padding (results, and New match), and the gap between its blocks. */
    val pagePadding: Dp,
    val setupPadding: Dp,
    val blockGap: Dp,
    /** A hand laid out on a result page: its cards, the gap between its groups, and its owner's avatar. */
    val summaryCard: Dp,
    val summaryGroupGap: Dp,
    val smallAvatar: Dp,
    /** The match result's avatar. */
    val heroAvatar: Dp,
    /** The GIN! fan: its cards, their step and the gap between melds, and the row it sits in. */
    val ginFanCard: Dp,
    val ginFanStep: Dp,
    val ginFanGroupGap: Dp,
    val ginFanTop: Dp,
    val ginFanHeight: Dp,
    /** The square Home button beside a result's primary action. */
    val homeTile: Dp,
    /** A rival's avatar on New match. */
    val rivalAvatar: Dp,
    /** A page's content sits centred in the space above its buttons (tablet), not at the top. */
    val centreBody: Boolean,
) {
    companion object {
        val Phone =
            GinResultMetrics(
                pagePadding = 16.dp,
                setupPadding = 16.dp,
                blockGap = 8.dp,
                summaryCard = 30.dp,
                summaryGroupGap = 10.dp,
                smallAvatar = 30.dp,
                heroAvatar = 80.dp,
                ginFanCard = 56.dp,
                ginFanStep = 30.dp,
                ginFanGroupGap = 12.dp,
                ginFanTop = 8.dp,
                ginFanHeight = 130.dp,
                homeTile = 60.dp,
                rivalAvatar = 48.dp,
                centreBody = false,
            )

        /** The tablet's (portrait): 40dp margins, 22dp between blocks, hands and the GIN! fan a size up. */
        val Tablet =
            GinResultMetrics(
                pagePadding = 40.dp,
                setupPadding = 32.dp,
                blockGap = 22.dp,
                summaryCard = 64.dp,
                summaryGroupGap = 20.dp,
                smallAvatar = 40.dp,
                heroAvatar = 120.dp,
                ginFanCard = 104.dp,
                ginFanStep = 54.dp,
                ginFanGroupGap = 22.dp,
                ginFanTop = 14.dp,
                ginFanHeight = 240.dp,
                homeTile = 72.dp,
                rivalAvatar = 72.dp,
                centreBody = true,
            )

        /** The sizes for [formFactor]. */
        fun of(formFactor: FormFactor): GinResultMetrics =
            when (formFactor) {
                FormFactor.PHONE -> Phone
                FormFactor.TABLET -> Tablet
            }
    }
}

/** The [GinResultMetrics] for the current screen. */
internal val ginResultMetrics: GinResultMetrics
    @Composable @ReadOnlyComposable
    get() = GinResultMetrics.of(CardGamesTheme.formFactor)

/**
 * A result or setup page's content, between its header and its pinned buttons: it takes the room
 * left, scrolls when it doesn't fit, and sits at the top (phone) or centred in that room
 * ([GinResultMetrics.centreBody], tablet).
 */
@Composable
internal fun ColumnScope.ResultBody(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = if (ginResultMetrics.centreBody) Alignment.Center else Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).then(modifier),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
