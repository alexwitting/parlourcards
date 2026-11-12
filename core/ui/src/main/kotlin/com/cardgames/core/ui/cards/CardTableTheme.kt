package com.cardgames.core.ui.cards

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.cardgames.core.domain.cards.Card
import com.cardgames.core.ui.theme.ParlourTheme

/**
 * The active theme pack's colors — the Parlour design's roles (see docs/THEME_SPEC.md `colors`).
 * Every color in the app comes from here; features never hardcode one.
 */
@Immutable
data class CardTableColors(
    // Table
    val tableLight: Color,
    val table: Color,
    val tableDark: Color,
    val onTable: Color,
    val onTableMuted: Color,
    val chipOnTable: Color,
    val lineOnTable: Color,
    val scrim: Color,
    // The cream bottom panel, sheets and dialogs
    val panel: Color,
    val panelLine: Color,
    val onPanel: Color,
    val onPanelMuted: Color,
    // Cards
    val cardFace: Color,
    val suitRed: Color,
    val suitBlack: Color,
    val cardBack: Color,
    val cardBackLattice: Color,
    val cardBackBorder: Color,
    /** Accents. Brass: selection, the pile card that makes a meld, the player's score. */
    val accent: Color,
    val onAccent: Color,
    val accentDeep: Color,
    /** Primary button fill. */
    val action: Color,
    val onAction: Color,
    /** Sage: valid destination, matching cards in hand. */
    val hint: Color,
    /** Meld edge colours, assigned in order; the newest meld uses [accent]. */
    val meld: List<Color>,
    // States
    val good: Color,
    val goodBg: Color,
    val bad: Color,
    val badBg: Color,
    // Opponent
    val opponent: Color,
    val opponentScore: Color,
    /**
     * Game chrome (the signed-off Gin chrome, used on every screen; these defaults derive from the
     * roles above). Top-bar buttons, the centre pill, chips on the felt.
     */
    val chipOnTableSoft: Color = Color(CHIP_SOFT),
    /** Score cards, stat tiles and grouped lists on the felt. */
    val surfaceOnTable: Color = Color(SURFACE_SOFT),
    /** The action-message pill ("Rosa discarded K♣"). */
    val feedPill: Color = Color(FEED_PILL),
    /** Bold card names inside the action pill. */
    val feedEmphasis: Color = hint,
    /**
     * Brass text straight on the felt (Home's "Rosa leads 8 – 0", a stats headline figure) — the
     * accent unless a pack's felt is too light for it to read.
     */
    val accentOnTable: Color = accent,
    /** The darkest chip on the felt ("10 cards"). */
    val deepChip: Color = tableDark,
    /** Tool tiles, secondary buttons and the disabled primary. */
    val toolTile: Color = panelLine,
    /** Inner rows on cream cards. */
    val panelSoft: Color = panelLine,
    val onPanelDisabled: Color = onPanelMuted,
    /** Deadwood meter segments while over the knock limit. */
    val meterOver: Color = badBg,
    /** The base shadow under the primary button. */
    val actionBase: Color = accentDeep,
)

private const val CHIP_SOFT = 0x29FFFFFF
private const val SURFACE_SOFT = 0x1FFFFFFF
private const val FEED_PILL = 0x59000000

/**
 * Image assets of the active theme pack. Implemented by the theme loader, which decodes
 * subsampled to on-screen size. A null [CardTableTheme.images] means "no pack art": cards are
 * then drawn in code from the colors.
 */
interface CardImages {
    /** [small] asks for the pip-less face used under 60dp wide; packs without one return the full face. */
    fun face(
        card: Card,
        small: Boolean = false,
    ): ImageBitmap

    fun back(): ImageBitmap

    /** Table art; null means the felt gradient (`Modifier.felt`). */
    fun tableBackground(): ImageBitmap?
}

@Immutable
data class CardTableTheme(
    val colors: CardTableColors,
    val images: CardImages? = null,
)

/**
 * The theme before a pack has loaded (and in previews and tests): Parlour's colors, with cards
 * drawn in code.
 */
val PlaceholderCardTableTheme: CardTableTheme = CardTableTheme(colors = ParlourTheme.Parlour.colors)

val LocalCardTableTheme = staticCompositionLocalOf { PlaceholderCardTableTheme }
