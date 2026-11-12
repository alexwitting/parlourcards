package com.cardgames.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * The app under test: the plugin's build, installed as `com.cardgames.benchmark` (see
 * `app/build.gradle.kts`). Anything else is refused outright — the journeys clear the app's data and
 * the test run uninstalls it, which on the real app would lose a player's saves and stats.
 */
internal val targetPackage: String =
    checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) { "targetAppId not passed to the tests" }
        .also { check(it.endsWith(".benchmark")) { "Refusing to run against $it: only the .benchmark build may be cleared" } }

/** Clears [packageName]'s data — only ever a `.benchmark` install. */
internal fun UiDevice.clearData(packageName: String) {
    check(packageName.endsWith(".benchmark")) { "Refusing to clear $packageName" }
    executeShellCommand("pm clear $packageName")
}

internal fun UiDevice.awaitHome() {
    check(wait(Until.hasObject(By.text("Parlour Cards")), TIMEOUT)) { "Home didn't show" }
}

/** Opens Solitaire from Home (a new deal on a fresh start), lets the deal land, and draws [draws] times. */
internal fun UiDevice.playSolitaire(draws: Int = DRAWS) {
    findObject(By.text("Solitaire"))?.click() ?: error("No Solitaire row on Home")
    awaitSolitaireTable()
    // The new deal's gather, shuffle and deal.
    Thread.sleep(SETTLE_MILLIS)
    drawFromStock(draws)
}

internal fun UiDevice.awaitSolitaireTable() {
    check(wait(Until.hasObject(By.res("solitaire:stock")), DEAL_TIMEOUT)) { "The Solitaire table didn't show" }
}

internal fun UiDevice.drawFromStock(draws: Int) {
    repeat(draws) {
        findObject(By.res("solitaire:stock"))?.click() ?: error("No stock")
        Thread.sleep(MOVE_MILLIS)
    }
}

/**
 * Opens Gin from Home, deals a match (a fresh start may go through New match) and plays the player's
 * first turn: the upcard passed if offered, a draw from the stock, a discard — then waits for the
 * opponent's reply.
 */
internal fun UiDevice.playGin() {
    findObject(By.text("Gin Rummy"))?.click() ?: error("No Gin Rummy row on Home")
    wait(Until.findObject(By.text("Deal the cards")), TIMEOUT)?.click()
    check(wait(Until.hasObject(By.res("gin:stock")), DEAL_TIMEOUT)) { "The Gin table didn't show" }
    var discarded = false
    val deadline = System.currentTimeMillis() + TURN_TIMEOUT
    while (!discarded && System.currentTimeMillis() < deadline) {
        when {
            hasObject(By.text("Your pick!")) -> findObject(By.text("Pass"))?.click()
            hasObject(By.text("Your draw!")) -> findObject(By.res("gin:stock"))?.click()
            hasObject(By.text("Your move!")) -> {
                // Select a hand card, then throw it on the pile.
                findObject(By.res("gin:hand"))?.click()
                Thread.sleep(MOVE_MILLIS)
                findObject(By.res("gin:discard"))?.click()
                discarded = true
            }
        }
        Thread.sleep(MOVE_MILLIS)
    }
    check(discarded) { "Never reached a discard in Gin" }
    // The opponent's turn.
    check(wait(Until.hasObject(By.text("Your draw!")), TURN_TIMEOUT)) { "The opponent's turn didn't come back to the player" }
}

/** Back to Home from a game, through the pause menu's Leave. */
internal fun UiDevice.leaveGame() {
    pressBack()
    wait(Until.findObject(By.textStartsWith("Leave")), TIMEOUT)?.click()
    awaitHome()
}

// The same journeys inside a macrobenchmark or Baseline Profile run.

/** A first launch: the benchmark install's data cleared, then the app opened to Home. */
internal fun MacrobenchmarkScope.freshStart() {
    device.clearData(packageName)
    pressHome()
    startActivityAndWait()
    device.awaitHome()
}

internal fun MacrobenchmarkScope.awaitHome() = device.awaitHome()

internal fun MacrobenchmarkScope.playSolitaire() = device.playSolitaire()

internal fun MacrobenchmarkScope.playGin() = device.playGin()

internal fun MacrobenchmarkScope.leaveGame() = device.leaveGame()

internal const val TIMEOUT = 5_000L
internal const val DEAL_TIMEOUT = 15_000L
internal const val TURN_TIMEOUT = 20_000L
internal const val SETTLE_MILLIS = 3_000L
internal const val MOVE_MILLIS = 800L
private const val DRAWS = 6
