package com.cardgames.core.testing.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.SampleCardTableThemes
import com.cardgames.core.ui.theme.AppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.RuntimeEnvironment

/**
 * Renders [content] on [theme] with the clock frozen, lets every entry animation finish (or stops
 * part-way, with a shorter [settleMillis]), and captures the screen as this test's screenshot
 * (named after the test). The clock never auto-advances, so looping animations (confetti, fades)
 * land on the same frame every run.
 *
 * [size] renders at that screen size instead of the test's `@Config` one — see [ScreenSize] for
 * capturing one test at every size.
 */
fun ComposeContentTestRule.snapshot(
    theme: CardTableTheme = SampleCardTableThemes.Parlour,
    settleMillis: Long = SETTLE_MILLIS,
    size: ScreenSize? = null,
    content: @Composable () -> Unit,
) {
    // Before setContent, so the window (and AppTheme's WindowLayout) is already this size.
    size?.let { RuntimeEnvironment.setQualifiers(it.qualifiers) }
    mainClock.autoAdvance = false
    setContent {
        AppTheme(cardTableTheme = theme) { content() }
    }
    mainClock.advanceTimeBy(settleMillis)
    onRoot().captureRoboImage()
}

/** Comfortably longer than any entry animation or popup delay at normal speed. */
private const val SETTLE_MILLIS = 3_000L
