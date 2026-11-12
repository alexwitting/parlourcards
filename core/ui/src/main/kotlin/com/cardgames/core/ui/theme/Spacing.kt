package com.cardgames.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardgames.core.ui.layout.FormFactor

/**
 * The spacing scale, plus the few layout measures shared across screens. Screens use these for
 * padding and gaps; geometry intrinsic to one component (a card's size, a fan's arc) stays with
 * that component. One set per [FormFactor] ([of]); read the current one through
 * [CardGamesTheme.spacing].
 */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 28.dp,
    /** Side margin of scrolling page content (Home, Settings, Stats). */
    val pageGutter: Dp = 16.dp,
    /** Inner padding of a page panel (a Settings section, a Stats card, a home game card). */
    val panelPadding: Dp = 20.dp,
    /** Gap between stacked page panels. */
    val panelGap: Dp = 12.dp,
    /** A board row never grows past this — keeps cards sane on a wide tablet. */
    val maxBoardWidth: Dp = 640.dp,
    /** Extra space above a popup's primary action, setting it apart from the details. */
    val actionGap: Dp = 6.dp,
    /** Minimum touch height of a settings-style row. */
    val rowMinHeight: Dp = 48.dp,
) {
    companion object
}

internal val DefaultSpacing = Spacing()

/** The spacing for [formFactor]. */
internal fun Spacing.Companion.of(formFactor: FormFactor): Spacing =
    when (formFactor) {
        FormFactor.PHONE -> DefaultSpacing
        // The tablet's tables span the screen; the cards scale with its width instead of stopping.
        FormFactor.TABLET -> DefaultSpacing.copy(maxBoardWidth = TABLET_MAX_BOARD_WIDTH)
    }

private val TABLET_MAX_BOARD_WIDTH = 1000.dp

internal val LocalSpacing = staticCompositionLocalOf { DefaultSpacing }
