package com.cardgames.core.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot baselines of [DesignSystemCatalog] — every token and shared component, per Parlour theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3600dp-xxhdpi")
class DesignSystemCatalogScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(theme: CardTableTheme) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            AppTheme(cardTableTheme = theme) { DesignSystemCatalog(modifier = Modifier.fillMaxWidth()) }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun catalog_parlour() = capture(SampleCardTableThemes.Parlour)

    @Test
    fun catalog_violet_pop() = capture(SampleCardTableThemes.VioletPop)

    @Test
    fun catalog_lagoon() = capture(SampleCardTableThemes.Lagoon)

    @Test
    fun catalog_midnight() = capture(SampleCardTableThemes.Midnight)
}
