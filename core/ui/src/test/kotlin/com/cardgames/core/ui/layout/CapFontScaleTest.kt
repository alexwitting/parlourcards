package com.cardgames.core.ui.layout

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class CapFontScaleTest {
    @get:Rule
    val rule = createComposeRule()

    /** The font scale content inside [CapFontScale] sees, with the phone set to [phone]. */
    private fun scaleInside(phone: Float): Float {
        RuntimeEnvironment.setFontScale(phone)
        var seen = 0f
        rule.setContent { CapFontScale { seen = LocalDensity.current.fontScale } }
        rule.waitForIdle()
        return seen
    }

    @Test
    fun `a font size up to the cap passes through`() {
        assertEquals(1.15f, scaleInside(1.15f), 0.001f)
    }

    @Test
    fun `a larger font size is held to the cap`() {
        assertEquals(TABLE_MAX_FONT_SCALE, scaleInside(2f), 0.001f)
    }
}
