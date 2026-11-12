package com.cardgames.core.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A window dimension's size band, at Material's breakpoints. */
enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Which layout the app draws: the phone's, or the tablet's (portrait) when the window's shorter side
 * is [TABLET_MIN_SIDE] or more. Screens don't branch on it directly; they read the specs it selects
 * (`docs/TABLET_PLAN.md` → Architecture).
 */
enum class FormFactor { PHONE, TABLET }

/**
 * The window the app is drawn in, and the bands its sides fall in — the one place a layout asks
 * "how big is the screen?". [AppTheme][com.cardgames.core.ui.theme.AppTheme] provides it from the
 * real window, so it follows rotation and resizing; read it through
 * [CardGamesTheme.window][com.cardgames.core.ui.theme.CardGamesTheme.window].
 */
@Immutable
data class WindowLayout(
    val width: Dp,
    val height: Dp,
) {
    /** Phones are [WindowSizeClass.COMPACT] wide held upright; tablets [WindowSizeClass.MEDIUM] and up. */
    val widthClass: WindowSizeClass
        get() =
            when {
                width < MEDIUM_WIDTH -> WindowSizeClass.COMPACT
                width < EXPANDED_WIDTH -> WindowSizeClass.MEDIUM
                else -> WindowSizeClass.EXPANDED
            }

    /** A phone on its side is [WindowSizeClass.COMPACT] tall. */
    val heightClass: WindowSizeClass
        get() =
            when {
                height < MEDIUM_HEIGHT -> WindowSizeClass.COMPACT
                height < EXPANDED_HEIGHT -> WindowSizeClass.MEDIUM
                else -> WindowSizeClass.EXPANDED
            }

    /** The tablet layout once the shorter side reaches [TABLET_MIN_SIDE], whichever way the window is held. */
    val formFactor: FormFactor
        get() = if (minOf(width, height) >= TABLET_MIN_SIDE) FormFactor.TABLET else FormFactor.PHONE

    companion object {
        /** What previews and tests see outside [AppTheme][com.cardgames.core.ui.theme.AppTheme]: the S24+ class of phone. */
        val Phone = WindowLayout(width = 360.dp, height = 780.dp)

        /** The shorter side from which the tablet layout applies. */
        val TABLET_MIN_SIDE = 600.dp

        private val MEDIUM_WIDTH = 600.dp
        private val EXPANDED_WIDTH = 840.dp
        private val MEDIUM_HEIGHT = 480.dp
        private val EXPANDED_HEIGHT = 900.dp
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Phone }

/** The window as it is now; [WindowLayout.Phone] where there's no window to measure yet. */
@Composable
@ReadOnlyComposable
internal fun currentWindowLayout(): WindowLayout {
    val size = LocalWindowInfo.current.containerSize
    if (size.width == 0 || size.height == 0) return WindowLayout.Phone
    return with(LocalDensity.current) { WindowLayout(size.width.toDp(), size.height.toDp()) }
}
