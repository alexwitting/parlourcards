package com.cardgames.core.testing.ui

/**
 * Robolectric `@Config(qualifiers = …)` for the screen sizes a test renders at. Robolectric's
 * default screen is far smaller than any phone — content laid out for a real screen can measure
 * as "not displayed" there — so a test that taps or reads the layout picks one of these.
 */
object TestDevices {
    /** A typical phone, portrait (the S24+ class: 360×780dp at xxhdpi). */
    const val PHONE = "w360dp-h780dp-xxhdpi"

    /** A 10–11" tablet, portrait. */
    const val TABLET = "w800dp-h1280dp-xhdpi"
}

/**
 * The [TestDevices] sizes as values, for a screenshot test that captures at each one: run it with
 * Robolectric's parameterized runner over [entries] and pass the size to `snapshot(size = …)`.
 * Each capture is named after the test and the size (`board[TABLET]`).
 *
 * ```
 * @RunWith(ParameterizedRobolectricTestRunner::class)
 * class BoardSizesScreenshotTest(private val size: ScreenSize) {
 *     companion object {
 *         @JvmStatic
 *         @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
 *         fun sizes() = ScreenSize.entries
 *     }
 *
 *     @Test
 *     fun board() = composeTestRule.snapshot(size = size) { … }
 * }
 * ```
 */
enum class ScreenSize(
    val qualifiers: String,
) {
    PHONE(TestDevices.PHONE),
    TABLET(TestDevices.TABLET),
}
