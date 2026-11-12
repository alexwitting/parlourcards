package com.cardgames.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Every text style the app uses, by what it's for — screens pick a role here and never adjust
 * size, weight or style themselves, so a typography change in the design system is a change to
 * this one table — the Parlour design's own scale. Read it through [CardGamesTheme.type].
 */
@Immutable
data class CardGamesTypography(
    /** A big title. Spectral 600, 46sp. */
    val display: TextStyle,
    /** The one word of a win ("Solved"). Spectral 600, 52sp. */
    val victory: TextStyle,
    /** Gin's shout ("GIN!"). Spectral 800, 120sp. */
    val shout: TextStyle,
    /** A result page's headline ("You win the hand!", "Rosa takes this one"). Spectral 800, 40sp. */
    val resultTitle: TextStyle,
    /** A page or popup title. Spectral 800, 26sp. */
    val heading: TextStyle,
    /** The tablet's large titles: Home's "Parlour Cards", a game's name on its Home card. Spectral 800, 34sp. */
    val title: TextStyle,
    /** The tablet's stats headline figure ("55%"). Spectral 800, 76sp, fixed-width digits. */
    val figure: TextStyle,
    /** The game name in the top bar, a group heading. Spectral 600, 19sp. */
    val subhead: TextStyle,
    /** Scores. Spectral 800, 24sp, fixed-width digits. */
    val score: TextStyle,
    /** A running count that matters at a glance (Gin's deadwood). Spectral 800, 22sp, fixed-width digits. */
    val tally: TextStyle,
    /** Primary and secondary button labels. Spectral 800, 22sp. */
    val actionLabel: TextStyle,
    /** A button label in the bottom panel, beside the tool tiles. Spectral 800, 18sp. */
    val panelAction: TextStyle,
    /** Running text. Instrument Sans 400, 15sp. */
    val body: TextStyle,
    /** Secondary text, status lines. Instrument Sans 500, 13sp. */
    val bodySmall: TextStyle,
    /** Tool-button captions. Instrument Sans 600, 10sp. */
    val label: TextStyle,
    /** Section labels and small capitals. Instrument Sans 800, 12sp, 0.08em. */
    val overline: TextStyle,
    /** The top bar's centre pill ("Hand 5 · First to 100"). Instrument Sans 700, 13sp. */
    val pill: TextStyle,
    /** The line under a primary button's label ("Throw 8♥ and go out"). Instrument Sans 700, 12sp. */
    val actionSub: TextStyle,
    /** A tool tile's label. Instrument Sans 700, 11sp. */
    val toolLabel: TextStyle,
    /** Small chips on the felt ("Medium bot", "Makes a meld"). Instrument Sans 800, 11sp. */
    val chip: TextStyle,
    /** A count badge on a pile or tool. Spectral 800, 14sp. */
    val badge: TextStyle,
    /** A settings or stats row's label. Instrument Sans 600, 15sp. */
    val rowLabel: TextStyle,
    /** The line under a row's label. Instrument Sans 400, 12sp. */
    val rowCaption: TextStyle,
    /** A segmented choice's options. Instrument Sans 600, 13sp. */
    val segment: TextStyle,
)

private const val TABULAR_FIGURES = "tnum"
private const val OVERLINE_TRACKING = 0.96f
private const val SHOUT_TRACKING = -0.03f

private fun parlour(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    line: Int,
) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

/** The roles as the app currently ships them. */
internal val DefaultCardGamesTypography: CardGamesTypography =
    CardGamesTypography(
        display = parlour(SpectralFontFamily, FontWeight.SemiBold, size = 46, line = 46),
        victory = parlour(SpectralFontFamily, FontWeight.SemiBold, size = 52, line = 52),
        shout = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 120, line = 114).copy(letterSpacing = SHOUT_TRACKING.em),
        resultTitle = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 40, line = 42),
        heading = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 26, line = 29),
        title = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 34, line = 37),
        figure = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 76, line = 76).copy(fontFeatureSettings = TABULAR_FIGURES),
        subhead = parlour(SpectralFontFamily, FontWeight.SemiBold, size = 19, line = 22),
        score = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 24, line = 24).copy(fontFeatureSettings = TABULAR_FIGURES),
        tally = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 22, line = 24).copy(fontFeatureSettings = TABULAR_FIGURES),
        actionLabel = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 22, line = 24),
        panelAction = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 18, line = 20),
        body = parlour(InstrumentSansFontFamily, FontWeight.Normal, size = 15, line = 21),
        bodySmall = parlour(InstrumentSansFontFamily, FontWeight.Medium, size = 13, line = 18),
        label = parlour(InstrumentSansFontFamily, FontWeight.SemiBold, size = 10, line = 12),
        overline =
            parlour(
                InstrumentSansFontFamily,
                FontWeight.ExtraBold,
                size = 12,
                line = 14,
            ).copy(letterSpacing = OVERLINE_TRACKING.sp),
        pill = parlour(InstrumentSansFontFamily, FontWeight.Bold, size = 13, line = 16),
        actionSub = parlour(InstrumentSansFontFamily, FontWeight.Bold, size = 12, line = 14),
        toolLabel = parlour(InstrumentSansFontFamily, FontWeight.Bold, size = 11, line = 13),
        chip = parlour(InstrumentSansFontFamily, FontWeight.ExtraBold, size = 11, line = 13),
        badge = parlour(SpectralFontFamily, FontWeight.ExtraBold, size = 14, line = 14),
        rowLabel = parlour(InstrumentSansFontFamily, FontWeight.SemiBold, size = 15, line = 20),
        rowCaption = parlour(InstrumentSansFontFamily, FontWeight.Normal, size = 12, line = 16),
        segment = parlour(InstrumentSansFontFamily, FontWeight.SemiBold, size = 13, line = 16),
    )

internal val LocalCardGamesTypography = staticCompositionLocalOf { DefaultCardGamesTypography }
