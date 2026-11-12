package com.cardgames.baselineprofile

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end journeys on a real phone, against the minified release build (installed as
 * `com.cardgames.benchmark`, cleared before each journey and uninstalled after the run): what a player
 * does, with the app killed where a phone would kill it, and the results checked on screen.
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.cardgames.baselineprofile.AppJourneyTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class AppJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun freshInstall() {
        device.clearData(targetPackage)
        launch()
    }

    @Test
    fun aSolitaireGameResumesWhereItWasAfterTheAppIsKilled() {
        device.playSolitaire(draws = 3)
        val moves = solitaireMoves()

        kill()
        launch()
        device.findObject(By.text("Continue deal"))?.click() ?: error("No Continue card for the Solitaire game")
        device.awaitSolitaireTable()

        assertEquals("the moves after resuming", moves, awaitText("solitaire:moves"))
        device.findObject(By.text("Undo"))?.click() ?: error("No Undo")
        Thread.sleep(MOVE_MILLIS * 2)
        assertNotEquals("Undo after resuming", moves, solitaireMoves())
    }

    @Test
    fun aGinMatchResumesWhereItWasAfterTheAppIsKilled() {
        device.playGin()
        val hand = device.findObject(By.textStartsWith("Hand "))?.text ?: error("No hand pill")

        kill()
        launch()
        device.findObject(By.text("Continue match"))?.click() ?: error("No Continue card for the Gin match")

        assertTrue("the Gin table after resuming", device.wait(Until.hasObject(By.res("gin:stock")), DEAL_TIMEOUT))
        assertEquals("the hand after resuming", hand, device.findObject(By.textStartsWith("Hand "))?.text)
        assertTrue("the player's turn after resuming", device.wait(Until.hasObject(By.text("Your draw!")), TIMEOUT))
    }

    @Test
    fun settingsSurviveARestart() {
        openSettings()
        device.findObject(By.text("Fast"))?.click() ?: error("No Fast card speed")
        device.findObject(By.text("Midnight"))?.click() ?: error("No Midnight theme")
        device.findObject(By.text("Gin Rummy"))?.click() ?: error("No Gin Rummy settings row")
        device.wait(Until.findObject(By.text("Vex")), TIMEOUT)?.click() ?: error("No Vex opponent")
        Thread.sleep(MOVE_MILLIS)

        kill()
        launch()
        openSettings()

        assertTrue("Fast card speed kept", isChosen("Fast"))
        assertTrue("Midnight theme kept", isChosen("Midnight"))
        device.findObject(By.text("Gin Rummy"))?.click() ?: error("No Gin Rummy settings row")
        device.wait(Until.hasObject(By.text("Vex")), TIMEOUT)
        assertTrue("Vex kept as the opponent", isChosen("Vex"))
    }

    @Test
    fun abandoningAGameCountsOneLossThatSurvivesARestart() {
        device.playSolitaire(draws = 1)
        newDeal()
        device.leaveGame()

        assertEquals("1 deal", solitaireStatsCaption())
        kill()
        launch()
        assertEquals("1 deal", solitaireStatsCaption())
    }

    @Test
    fun leavingDuringTheNewDealOpensTheNewDealAndCountsTheLossOnce() {
        device.playSolitaire(draws = 1)
        newDeal()
        // Out before the new deal's cards have gathered and dealt.
        device.leaveGame()
        kill()
        launch()

        device.findObject(By.text("Continue deal"))?.click() ?: error("No Continue card for the new deal")
        device.awaitSolitaireTable()
        assertEquals("the new deal, untouched", "0", awaitText("solitaire:moves"))
        device.leaveGame()
        assertEquals("1 deal", solitaireStatsCaption())
    }

    private fun launch() {
        val context = instrumentation.context
        val intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(targetPackage)) { "$targetPackage isn't installed" }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.awaitHome()
    }

    /** As a phone kills a backgrounded app: everything stops, nothing gets a chance to save. */
    private fun kill() {
        device.executeShellCommand("am force-stop $targetPackage")
        Thread.sleep(MOVE_MILLIS)
    }

    private fun openSettings() {
        device.findObject(By.text("Settings"))?.click() ?: error("No Settings button on Home")
        check(device.wait(Until.hasObject(By.text("Card speed")), TIMEOUT)) { "Settings didn't open" }
    }

    /** Starts a new Solitaire deal from the panel, confirming that the game in progress counts as a loss. */
    private fun newDeal() {
        device.findObject(By.text("New"))?.click() ?: error("No New button")
        device.wait(Until.findObject(By.text("New game")), TIMEOUT)?.click() ?: error("No New game confirmation")
    }

    private fun solitaireMoves(): String = awaitText("solitaire:moves")

    private fun awaitText(tag: String): String = textOf(By.res(tag))

    /**
     * The text of the first node matching [selector], read again if the node is replaced while being
     * read (a screen still sliding in recomposes under it).
     */
    private fun textOf(selector: BySelector): String {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (true) {
            try {
                return checkNotNull(device.wait(Until.findObject(selector), TIMEOUT)) { "Nothing on screen matches $selector" }.text
            } catch (stale: StaleObjectException) {
                if (System.currentTimeMillis() > deadline) throw stale
            }
        }
    }

    /** The Solitaire row's caption on the Stats page ("1 deal", "4 deals · best 3:18"), up to the first " · ". */
    private fun solitaireStatsCaption(): String {
        device.findObject(By.text("Stats"))?.click() ?: error("No Stats button on Home")
        val caption = textOf(By.textContains(" deal"))
        device.pressBack()
        device.awaitHome()
        return caption.substringBefore(" · ")
    }

    /** Whether the option labelled [label] is the chosen one (a radio option or a segment). */
    private fun isChosen(label: String): Boolean {
        var node: UiObject2? = device.findObject(By.text(label)) ?: error("No $label")
        while (node != null && !node.isCheckable) node = node.parent
        return node?.isChecked == true
    }
}
