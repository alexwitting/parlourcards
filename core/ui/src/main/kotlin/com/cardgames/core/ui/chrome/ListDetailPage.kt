package com.cardgames.core.ui.chrome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.icons.ParlourIcons
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.motion.CardGamesMotion
import com.cardgames.core.ui.motion.LocalReduceMotion
import com.cardgames.core.ui.motion.tween
import com.cardgames.core.ui.theme.CardGamesTheme

/**
 * A page with a list and a detail — Settings and its games, Stats and its games — laid out for the
 * form factor:
 * - Phone: one page at a time ([FeltPage]): the list when [detail] is null, else that detail under
 *   its [detailHeader]. Choosing a row opens the detail as a screen of its own (the caller navigates).
 * - Tablet: both at once. The list, [listWidth] wide, on the left, a hairline, and [detail] on the
 *   right. Choosing a row changes [detail] in place: the old detail fades out over `Instant`, the
 *   new one fades in over `Quick` while sliding in from the right. The tablet always shows a detail,
 *   so pass one there.
 */
@Composable
fun <T : Any> ListDetailPage(
    title: String,
    onNavigateBack: () -> Unit,
    detail: T?,
    detailHeader: (T) -> PaneHeader,
    listWidth: Dp,
    list: @Composable ColumnScope.() -> Unit,
    detailContent: @Composable ColumnScope.(T) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    if (CardGamesTheme.formFactor == FormFactor.PHONE || detail == null) {
        if (detail == null) {
            FeltPage(
                title = title,
                onNavigateBack = onNavigateBack,
                snackbarHostState = snackbarHostState,
                modifier = modifier,
                content = list,
            )
        } else {
            val header = detailHeader(detail)
            FeltPage(
                title = header.title,
                overline = header.overline,
                onNavigateBack = onNavigateBack,
                snackbarHostState = snackbarHostState,
                modifier = modifier,
            ) { detailContent(detail) }
        }
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        TableBackground(modifier = Modifier.matchParentSize())
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = PANE_SIDE, end = PANE_SIDE, top = PANE_TOP),
        ) {
            Pane(modifier = Modifier.width(listWidth)) {
                Row(
                    modifier = Modifier.height(PANE_HEADER),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
                ) {
                    TableIconButton(ParlourIcons.Back, contentDescription = "Back", onClick = onNavigateBack)
                    Text(title, color = CardGamesTheme.colors.onTable, style = CardGamesTheme.type.heading)
                }
                list()
            }
            Spacer(modifier = Modifier.width(PANE_GAP))
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(CardGamesTheme.colors.lineOnTable))
            Spacer(modifier = Modifier.width(PANE_GAP))
            val slide = with(LocalDensity.current) { PANE_SLIDE.roundToPx() }
            val reduceMotion = LocalReduceMotion.current
            val fadeOut = fadeOut(CardGamesMotion.Instant.tween())
            val fadeIn = fadeIn(CardGamesMotion.Quick.tween(delay = CardGamesMotion.Instant))
            val slideIn = slideInHorizontally(CardGamesMotion.Quick.tween(delay = CardGamesMotion.Instant)) { slide }
            AnimatedContent(
                targetState = detail,
                modifier = Modifier.weight(1f),
                transitionSpec = { (if (reduceMotion) fadeIn else fadeIn + slideIn) togetherWith fadeOut },
                label = "detail",
            ) { shown ->
                Pane {
                    DetailHeader(detailHeader(shown))
                    detailContent(shown)
                }
            }
        }
        snackbarHostState?.let { SnackbarHost(it, modifier = Modifier.align(Alignment.BottomCenter)) }
    }
}

/** One side of the tablet's split: a column of groups that scrolls on its own. */
@Composable
private fun Pane(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = PANE_BOTTOM),
        verticalArrangement = Arrangement.spacedBy(PANE_BLOCK_GAP),
        content = content,
    )
}

/** The right pane's heading: the overline over the title, after a back chevron when there is one. */
@Composable
private fun DetailHeader(header: PaneHeader) {
    val colors = CardGamesTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(PANE_HEADER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CardGamesTheme.spacing.md),
    ) {
        header.onBack?.let { TableIconButton(ParlourIcons.Back, contentDescription = "Back", onClick = it) }
        Column {
            header.overline?.let { Text(it.uppercase(), color = colors.onTableMuted, style = CardGamesTheme.type.overline) }
            Text(header.title, color = colors.onTable, style = CardGamesTheme.type.heading)
        }
    }
}

private val PANE_SIDE = 24.dp
private val PANE_TOP = 12.dp
private val PANE_BOTTOM = 34.dp
private val PANE_GAP = 20.dp
private val PANE_HEADER = 52.dp
private val PANE_BLOCK_GAP = 18.dp

/** How far a new detail slides in from the right. */
private val PANE_SLIDE = 8.dp
