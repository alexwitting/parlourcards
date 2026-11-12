package com.cardgames.core.ui.chrome

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * The cream panel docked at the bottom of a game screen: 28dp top corners, padded clear of the
 * navigation bar. Row 1 is usually the status line, row 2 the actions.
 */
@Composable
fun BottomPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardGamesTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(PANEL_ELEVATION, ChromeShapes.Panel)
                .background(colors.panel, ChromeShapes.Panel)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(PANEL_PADDING),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        CompositionLocalProvider(LocalCompactButtons provides true) { content() }
    }
}

/**
 * The tablet's controls, floating over the felt along the bottom: the [dock] of tools on the left and
 * the game's panel ([status] over [actions]) on the right, as wide as [panelWidth] says. Each is a
 * cream card held clear of the edges and the navigation bar ([ChromeMetrics.floatingInset],
 * [ChromeMetrics.floatingBottom]); both rest on the same bottom line.
 */
@Composable
fun FloatingControls(
    dock: (@Composable RowScope.() -> Unit)?,
    status: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    panelWidth: PanelWidth,
    modifier: Modifier = Modifier,
) {
    val metrics = chromeMetrics
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = metrics.floatingInset, end = metrics.floatingInset, bottom = metrics.floatingBottom),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (dock != null) {
            FloatingCard {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dockGap), verticalAlignment = Alignment.Top, content = dock)
            }
        }
        if (status == null && actions == null) return@Row
        val panel =
            when (panelWidth) {
                is PanelWidth.Fixed -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Modifier.width(panelWidth.width)
                }
                PanelWidth.BesideDock -> {
                    if (dock != null) Spacer(modifier = Modifier.width(metrics.dockGap))
                    Modifier.weight(1f)
                }
            }
        FloatingCard(modifier = panel) {
            Column(verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
                if (status != null) {
                    Box(modifier = Modifier.fillMaxWidth().animateContentSize(CardGamesMotion.StatusFade.tween())) { status() }
                }
                if (actions != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = panelActionsMinHeight()),
                        verticalAlignment = Alignment.Top,
                        content = actions,
                    )
                }
            }
        }
    }
}

/** How wide the tablet's game panel is: [Fixed] at the right, or everything [BesideDock] (all of the row without one). */
sealed interface PanelWidth {
    data class Fixed(
        val width: Dp,
    ) : PanelWidth

    data object BesideDock : PanelWidth
}

/** One of the tablet's floating cream cards. */
@Composable
private fun FloatingCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val metrics = chromeMetrics
    val shape = RoundedCornerShape(metrics.floatingRadius)
    Box(
        modifier =
            modifier
                .shadow(FLOATING_ELEVATION, shape)
                .background(CardGamesTheme.colors.panel, shape)
                .padding(metrics.floatingPadding),
    ) {
        CompositionLocalProvider(LocalCompactButtons provides true) { content() }
    }
}

/**
 * The bottom panel's actions row at its tallest usual height — a tool tile and its caption — so
 * every panel mode (tools, a pair of buttons, an offer) is the same height and switching between
 * them never moves the panel, or the table above it.
 */
@Composable
internal fun panelActionsMinHeight(): Dp =
    chromeMetrics.toolTile + TOOL_LABEL_GAP +
        with(LocalDensity.current) {
            CardGamesTheme.type.toolLabel.lineHeight
                .toDp()
        }

private val PANEL_ELEVATION: Dp = 8.dp
private val PANEL_GAP: Dp = 8.dp
private val FLOATING_ELEVATION: Dp = 12.dp
private val PANEL_PADDING = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 8.dp)
