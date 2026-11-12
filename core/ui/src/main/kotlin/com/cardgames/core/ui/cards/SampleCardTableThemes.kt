package com.cardgames.core.ui.cards

import com.cardgames.core.ui.theme.ParlourTheme

/**
 * The shipped theme packs' colors ([ParlourTheme]), without their images — for previews and
 * screenshot tests, which can't load a pack's art from the app's assets. Cards are drawn in code.
 */
object SampleCardTableThemes {
    val Parlour: CardTableTheme = ParlourTheme.Parlour.sample()
    val VioletPop: CardTableTheme = ParlourTheme.VioletPop.sample()
    val Lagoon: CardTableTheme = ParlourTheme.Lagoon.sample()
    val Midnight: CardTableTheme = ParlourTheme.Midnight.sample()

    /** Every pack, keyed by pack id (used in screenshot file names). */
    val All: Map<String, CardTableTheme> = ParlourTheme.entries.associate { it.id to it.sample() }

    private fun ParlourTheme.sample() = CardTableTheme(colors = colors)
}
