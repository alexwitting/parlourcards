package com.cardgames.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile — the code that runs at launch and while playing, which
 * Android then compiles ahead of time at install instead of interpreting at first — and its startup
 * profile. Run with the phone connected, and commit the result (`app/src/release/generated/baselineProfiles/`):
 *
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * Regenerate after a big change to the screens' code.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Launch to Home only: the startup profile, which Android uses to lay the app's code out so
     * everything launch needs sits together. Kept to launch, it does that job best.
     */
    @Test
    fun startup() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            freshStart()
        }

    /** Both games played: the rest of the Baseline Profile (compiled ahead of time, not reordered). */
    @Test
    fun play() =
        rule.collect(packageName = targetPackage) {
            freshStart()
            playSolitaire()
            leaveGame()
            playGin()
            leaveGame()
        }
}
