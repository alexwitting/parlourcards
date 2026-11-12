package com.cardgames.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start and play, each without and with the Baseline Profile, on a connected phone:
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * Results (startup times, frame durations and overruns, medians and percentiles) are printed and
 * written under `baselineprofile/build/outputs/connected_android_test_additional_output/`.
 */
@RunWith(AndroidJUnit4::class)
class AppBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun solitaireWithoutProfile() = solitaire(CompilationMode.None())

    @Test
    fun solitaireWithProfile() = solitaire(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun ginWithoutProfile() = gin(CompilationMode.None())

    @Test
    fun ginWithProfile() = gin(CompilationMode.Partial(BaselineProfileMode.Require))

    /**
     * Solitaire's New deal on its own — every card gathered to the stock, shuffled and dealt out:
     * the heaviest animation in the app.
     */
    @Test
    fun solitaireNewDeal() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = PLAY_ITERATIONS,
            setupBlock = {
                freshStart()
                device.playSolitaire(draws = 0)
            },
        ) {
            // An untouched deal: New deals straight away, with no "start over?" question.
            device.findObject(By.text("New"))?.click() ?: error("No New button")
            Thread.sleep(NEW_DEAL_MILLIS)
        }

    private fun startup(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            awaitHome()
        }

    /** A fresh Solitaire deal and six draws. */
    private fun solitaire(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = mode,
            iterations = PLAY_ITERATIONS,
            setupBlock = { freshStart() },
        ) {
            playSolitaire()
        }

    /** A new Gin match and the first turn, through the opponent's reply. */
    private fun gin(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = mode,
            iterations = PLAY_ITERATIONS,
            setupBlock = { freshStart() },
        ) {
            playGin()
        }

    private companion object {
        const val STARTUP_ITERATIONS = 10
        const val PLAY_ITERATIONS = 3

        /** Gather, shuffle and deal, with room to spare. */
        const val NEW_DEAL_MILLIS = 5_000L
    }
}
