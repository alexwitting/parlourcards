package com.cardgames.core.domain.theme

import com.cardgames.core.domain.cards.Card

/** The pack designated as the app default — Parlour. */
const val DEFAULT_THEME_ID: String = "parlour"

/**
 * Manifest `colors` (schema 2, see docs/THEME_SPEC.md), each as ARGB packed into a Long — alpha
 * included, since several roles are translucent. These are the Parlour design's colour roles.
 */
data class ThemePackColors(
    // Table (felt: a radial gradient light → base → dark when the pack has no table image)
    val tableLight: Long,
    val table: Long,
    val tableDark: Long,
    val onTable: Long,
    val onTableMuted: Long,
    val chipOnTable: Long,
    val lineOnTable: Long,
    val scrim: Long,
    // The cream bottom panel, sheets and dialogs
    val panel: Long,
    val panelLine: Long,
    val onPanel: Long,
    val onPanelMuted: Long,
    // Cards (used to draw cards when a pack ships no card art)
    val cardFace: Long,
    val suitRed: Long,
    val suitBlack: Long,
    val cardBack: Long,
    val cardBackLattice: Long,
    val cardBackBorder: Long,
    // Accents
    val accent: Long,
    val onAccent: Long,
    val accentDeep: Long,
    val action: Long,
    val onAction: Long,
    val hint: Long,
    /** Meld edge colours, assigned in order (the newest meld uses [accent]). */
    val meld: List<Long>,
    // States
    val good: Long,
    val goodBg: Long,
    val bad: Long,
    val badBg: Long,
    // Opponent
    val opponent: Long,
    val opponentScore: Long,
    /**
     * The game-chrome roles (see docs/THEME_SPEC.md) — optional in a manifest; null means the
     * app derives it from the roles above.
     */
    val chrome: ThemeChromeColors = ThemeChromeColors(),
)

/** Optional manifest overrides for the game chrome; each null is derived by the app. */
data class ThemeChromeColors(
    val chipOnTableSoft: Long? = null,
    val surfaceOnTable: Long? = null,
    val feedPill: Long? = null,
    val feedEmphasis: Long? = null,
    val accentOnTable: Long? = null,
    val deepChip: Long? = null,
    val toolTile: Long? = null,
    val panelSoft: Long? = null,
    val onPanelDisabled: Long? = null,
    val meterOver: Long? = null,
    val actionBase: Long? = null,
)

/**
 * One validated theme pack (docs/THEME_SPEC.md): manifest colors plus asset-relative paths to
 * its images, with `facesFrom` already resolved — [facePaths] always points at real files, own
 * or donated. Decoding those paths into bitmaps is presentation-layer work, not this model's.
 */
data class ThemePack(
    val id: String,
    val displayName: String,
    val colors: ThemePackColors,
    val cardBackPath: String,
    /** All 52 faces, keyed by card. */
    val facePaths: Map<Card, String>,
    /**
     * Optional faces for cards drawn under 60dp wide — the same design without the centre pip,
     * which only appears on bigger cards. Null means [facePaths] are used at every size.
     */
    val smallFacePaths: Map<Card, String>? = null,
    /** Optional table art; null means the app draws the felt gradient from [colors]. */
    val tableBackgroundPath: String? = null,
)

/** Enumerates and validates installed packs. Implemented in `:core:data`. */
interface ThemeRepository {
    /** Every pack that passed validation, in stable (id) order. Empty when none are installed. */
    suspend fun availablePacks(): List<ThemePack>
}

/**
 * The pack the app shows for a stored [themeId]: that pack if it's installed, else the default
 * ([DEFAULT_THEME_ID]), else any; null only when no packs are installed. The app shell and the
 * Settings picker both use this, so the pack marked selected is the one showing.
 */
fun List<ThemePack>.resolve(themeId: String?): ThemePack? =
    firstOrNull { it.id == themeId } ?: firstOrNull { it.id == DEFAULT_THEME_ID } ?: firstOrNull()
