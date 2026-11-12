package com.cardgames.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.ui.cards.CardTableColors
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.LocalCardTableTheme
import com.cardgames.core.ui.cards.PlaceholderCardTableTheme
import com.cardgames.core.ui.layout.FormFactor
import com.cardgames.core.ui.layout.LocalWindowLayout
import com.cardgames.core.ui.layout.WindowLayout
import com.cardgames.core.ui.layout.currentWindowLayout
import com.cardgames.core.ui.motion.LocalAnimationSpeed
import com.cardgames.core.ui.motion.LocalReduceMotion

/**
 * The app's root theme — the one place the design system is provided. [cardTableTheme] is the
 * active theme pack (colors and art), [animationSpeed] and [reduceMotion] the player's settings;
 * previews and tests can leave them at their defaults (Parlour drawn in code, normal motion).
 *
 * Material's own color scheme is built from the pack's colors, so stock Material parts (ripples,
 * text fields, anything not explicitly colored) match the theme instead of the wallpaper.
 */
@Composable
fun AppTheme(
    cardTableTheme: CardTableTheme = PlaceholderCardTableTheme,
    animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(cardTableTheme.colors) { cardTableTheme.colors.toMaterialColorScheme() }
    val window = currentWindowLayout()
    CompositionLocalProvider(
        LocalCardTableTheme provides cardTableTheme,
        LocalAnimationSpeed provides animationSpeed,
        LocalReduceMotion provides reduceMotion,
        LocalCardGamesTypography provides DefaultCardGamesTypography,
        LocalSpacing provides Spacing.of(window.formFactor),
        LocalWindowLayout provides window,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
    }
}

/**
 * Read access to the design system: colors and art from the active theme pack, plus the app-wide
 * typography roles, spacing and the window's size. Motion lives in [com.cardgames.core.ui.motion.CardGamesMotion].
 */
object CardGamesTheme {
    val colors: CardTableColors
        @Composable @ReadOnlyComposable
        get() = LocalCardTableTheme.current.colors

    val table: CardTableTheme
        @Composable @ReadOnlyComposable
        get() = LocalCardTableTheme.current

    val type: CardGamesTypography
        @Composable @ReadOnlyComposable
        get() = LocalCardGamesTypography.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    /** The window's size and size bands — for layout decisions about the screen as a whole. */
    val window: WindowLayout
        @Composable @ReadOnlyComposable
        get() = LocalWindowLayout.current

    /** Phone or tablet layout ([WindowLayout.formFactor]). */
    val formFactor: FormFactor
        @Composable @ReadOnlyComposable
        get() = LocalWindowLayout.current.formFactor
}

private fun CardTableColors.toMaterialColorScheme(): ColorScheme =
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accentDeep,
        onSecondary = panel,
        background = table,
        onBackground = onTable,
        surface = panel,
        onSurface = onPanel,
        surfaceVariant = panelSoft,
        onSurfaceVariant = onPanelMuted,
        outline = panelLine,
        outlineVariant = panelLine,
    )
