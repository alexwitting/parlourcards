package com.cardgames.core.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The chrome's sizes that differ between phone and tablet, one set per [FormFactor]; read the
 * current one with [chromeMetrics]. Corner radii that don't change stay in [ChromeShapes].
 */
@Immutable
data class ChromeMetrics(
    /** The top bar's filled icon buttons (Pause, Settings, Back). */
    val topBarButton: Dp,
    /** The top bar's side margins, and its gap below the status bar. */
    val topBarInset: Dp,
    val topBarTop: Dp,
    /** The game's score card at its widest when it shares the top bar's row (tablet). */
    val scoreCardMaxWidth: Dp,
    /** Buttons: on the felt (primary, and secondary), and the compact ones in a panel. */
    val buttonHeight: Dp,
    val secondaryButtonHeight: Dp,
    val compactButtonHeight: Dp,
    /** A tool's tile, and the tool's whole width (tile and label). */
    val toolTile: Dp,
    val toolWidth: Dp,
    val toolTileRadius: Dp,
    /** Between tools in the tablet's dock. */
    val dockGap: Dp,
    /** Floating controls (tablet): in from the sides, up from the bottom edge, their corners and padding. */
    val floatingInset: Dp,
    val floatingBottom: Dp,
    val floatingRadius: Dp,
    val floatingPadding: Dp,
    /** A popup card's margins from the screen's edges, and its widest; the pause menu's widest. */
    val dialogMargin: Dp,
    val dialogMaxWidth: Dp,
    val pauseMaxWidth: Dp,
    /** The scrim behind the pause menu. */
    val pauseScrimAlpha: Float,
    /** How to play: the sheet's height as a share of the window, or its top when set; its corners and side padding. */
    val sheetHeightFraction: Float,
    val sheetTop: Dp?,
    val sheetRadius: Dp,
    val sheetPadding: Dp,
) {
    companion object {
        val Phone =
            ChromeMetrics(
                topBarButton = 42.dp,
                topBarInset = 12.dp,
                topBarTop = 0.dp,
                scoreCardMaxWidth = 340.dp,
                buttonHeight = 60.dp,
                secondaryButtonHeight = 52.dp,
                compactButtonHeight = 48.dp,
                toolTile = 48.dp,
                toolWidth = 56.dp,
                toolTileRadius = 16.dp,
                dockGap = 8.dp,
                floatingInset = 16.dp,
                floatingBottom = 16.dp,
                floatingRadius = 26.dp,
                floatingPadding = 14.dp,
                dialogMargin = 28.dp,
                dialogMaxWidth = 420.dp,
                pauseMaxWidth = 420.dp,
                pauseScrimAlpha = 0.72f,
                sheetHeightFraction = 0.9f,
                sheetTop = null,
                sheetRadius = 26.dp,
                sheetPadding = 20.dp,
            )

        /** The tablet's (portrait) chrome: bigger controls, floating panels, wider popups. */
        val Tablet =
            Phone.copy(
                topBarButton = 44.dp,
                topBarInset = 24.dp,
                topBarTop = 12.dp,
                buttonHeight = 72.dp,
                secondaryButtonHeight = 64.dp,
                compactButtonHeight = 60.dp,
                toolTile = 56.dp,
                toolWidth = 64.dp,
                toolTileRadius = 18.dp,
                dockGap = 12.dp,
                floatingInset = 24.dp,
                floatingBottom = 28.dp,
                floatingRadius = 26.dp,
                floatingPadding = 16.dp,
                dialogMargin = 40.dp,
                dialogMaxWidth = 500.dp,
                pauseMaxWidth = 460.dp,
                pauseScrimAlpha = 0.6f,
                sheetTop = 150.dp,
                sheetRadius = 32.dp,
                sheetPadding = 40.dp,
            )

        /** The chrome for [formFactor]. */
        fun of(formFactor: FormFactor): ChromeMetrics =
            when (formFactor) {
                FormFactor.PHONE -> Phone
                FormFactor.TABLET -> Tablet
            }
    }
}

/** The [ChromeMetrics] for the current screen. */
val chromeMetrics: ChromeMetrics
    @Composable @ReadOnlyComposable
    get() = ChromeMetrics.of(CardGamesTheme.formFactor)
