package com.cardgames.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cardgames.core.ui.cards.SampleCardTableThemes

@Preview(name = "Design system — Parlour", widthDp = 360, heightDp = 3600)
@Composable
private fun DesignSystemParlourPreview() {
    AppTheme(cardTableTheme = SampleCardTableThemes.Parlour) { DesignSystemCatalog() }
}

@Preview(name = "Design system — Violet Pop", widthDp = 360, heightDp = 3600)
@Composable
private fun DesignSystemVioletPopPreview() {
    AppTheme(cardTableTheme = SampleCardTableThemes.VioletPop) { DesignSystemCatalog() }
}

@Preview(name = "Design system — Lagoon", widthDp = 360, heightDp = 3600)
@Composable
private fun DesignSystemLagoonPreview() {
    AppTheme(cardTableTheme = SampleCardTableThemes.Lagoon) { DesignSystemCatalog() }
}

@Preview(name = "Design system — Midnight", widthDp = 360, heightDp = 3600)
@Composable
private fun DesignSystemMidnightPreview() {
    AppTheme(cardTableTheme = SampleCardTableThemes.Midnight) { DesignSystemCatalog() }
}
