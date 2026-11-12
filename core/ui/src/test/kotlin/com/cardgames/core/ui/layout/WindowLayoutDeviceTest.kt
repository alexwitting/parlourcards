package com.cardgames.core.ui.layout

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cardgames.core.ui.theme.AppTheme
import com.cardgames.core.ui.theme.CardGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AppTheme's [WindowLayout] is the real window's size — including after a test switches screen
 * size before rendering, which `:core:testing-ui`'s `snapshot(size = …)` relies on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class WindowLayoutDeviceTest {
    @get:Rule
    val rule = createComposeRule()

    private fun windowSeen(): WindowLayout {
        var seen: WindowLayout? = null
        rule.setContent { AppTheme { seen = CardGamesTheme.window } }
        rule.waitForIdle()
        return checkNotNull(seen)
    }

    @Test
    fun `the window is the test's screen`() {
        assertEquals(WindowLayout(360.dp, 780.dp), windowSeen())
    }

    @Test
    fun `the window follows a screen size set before rendering`() {
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land-xhdpi")

        val window = windowSeen()

        assertEquals(WindowLayout(1280.dp, 800.dp), window)
        assertEquals(WindowSizeClass.EXPANDED, window.widthClass)
    }
}
