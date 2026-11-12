package com.cardgames.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.cardgames.core.ui.R

/** Parlour display face: titles, scores, buttons, card indices — Spectral (SIL OFL, `res/raw/ofl_spectral.txt`). */
val SpectralFontFamily =
    FontFamily(
        Font(R.font.spectral_medium, FontWeight.Medium),
        Font(R.font.spectral_medium_italic, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.spectral_semibold, FontWeight.SemiBold),
        Font(R.font.spectral_bold, FontWeight.Bold),
        Font(R.font.spectral_extrabold, FontWeight.ExtraBold),
    )

/** Parlour UI face — Instrument Sans (SIL OFL, `res/raw/ofl_instrument_sans.txt`). */
val InstrumentSansFontFamily =
    FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
        Font(R.font.instrument_sans_bold, FontWeight.Bold),
    )

/**
 * Material's type scale with Parlour's two families swapped in: display, headline, title and label
 * styles in Spectral semibold, body styles in Instrument Sans. Sizes stay Material's; the design's
 * own sizes are the roles in [CardGamesTypography].
 */
internal val AppTypography: Typography =
    Typography().run {
        fun TextStyle.heading(weight: FontWeight) = copy(fontFamily = SpectralFontFamily, fontWeight = weight)

        fun TextStyle.body() = copy(fontFamily = InstrumentSansFontFamily)
        copy(
            displayLarge = displayLarge.heading(FontWeight.SemiBold),
            displayMedium = displayMedium.heading(FontWeight.SemiBold),
            displaySmall = displaySmall.heading(FontWeight.SemiBold),
            headlineLarge = headlineLarge.heading(FontWeight.SemiBold),
            headlineMedium = headlineMedium.heading(FontWeight.SemiBold),
            headlineSmall = headlineSmall.heading(FontWeight.SemiBold),
            titleLarge = titleLarge.heading(FontWeight.SemiBold),
            titleMedium = titleMedium.heading(FontWeight.SemiBold),
            titleSmall = titleSmall.heading(FontWeight.SemiBold),
            bodyLarge = bodyLarge.body(),
            bodyMedium = bodyMedium.body(),
            bodySmall = bodySmall.body(),
            labelLarge = labelLarge.heading(FontWeight.SemiBold),
            labelMedium = labelMedium.heading(FontWeight.SemiBold),
            labelSmall = labelSmall.heading(FontWeight.SemiBold),
        )
    }
