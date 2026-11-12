package com.cardgames.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Against a real, file-backed DataStore: the keys are the app's storage contract. */
class DataStoreSettingsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
    }

    private val repository by lazy { DataStoreSettingsRepository(dataStore) }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private fun stored(): Settings = runBlocking { repository.observeSettings().first() }

    @Test
    fun `a fresh install reads every default`() {
        assertEquals(Settings(), stored())
    }

    @Test
    fun `every setting written reads back`() {
        val gin =
            GinOptions(
                difficulty = GinDifficulty.HARD,
                hints = HintLimit.UNLIMITED,
                rules = GinMatchRules(target = 250, ginBonus = false, bigGin = true),
                confirmKnock = true,
                meldHints = false,
            )
        runBlocking {
            repository.setDrawCount(1)
            repository.setThemeId("lagoon")
            repository.setSoundEnabled(false)
            repository.setHapticsEnabled(false)
            repository.setUseSolverForWinnableDeals(false)
            repository.setAnimationSpeed(AnimationSpeed.SLOW)
            repository.setReduceMotion(true)
            repository.setSolitaireHints(HintLimit.OFF)
            repository.setGinOptions(gin)
        }

        val settings = stored()

        assertEquals(1, settings.drawCount)
        assertEquals("lagoon", settings.themeId)
        assertFalse(settings.soundEnabled)
        assertFalse(settings.hapticsEnabled)
        assertFalse(settings.useSolverForWinnableDeals)
        assertEquals(AnimationSpeed.SLOW, settings.animationSpeed)
        assertTrue(settings.reduceMotion)
        assertEquals(HintLimit.OFF, settings.solitaireHints)
        assertEquals(gin, settings.gin)
    }

    @Test
    fun `values this version doesn't know fall back to defaults instead of crashing`() {
        // As after a downgrade, or a damaged file.
        runBlocking {
            dataStore.edit {
                it[stringPreferencesKey("gin.difficulty")] = "LEGENDARY"
                it[stringPreferencesKey("animation.speed")] = "LUDICROUS"
                it[stringPreferencesKey("solitaire.hints")] = "FIVE"
                it[intPreferencesKey("gin.target")] = 75
                it[intPreferencesKey("solitaire.drawCount")] = 2
            }
        }

        val settings = stored()

        assertEquals(GinDifficulty.MEDIUM, settings.gin.difficulty)
        assertEquals(AnimationSpeed.NORMAL, settings.animationSpeed)
        assertEquals(HintLimit.THREE, settings.solitaireHints)
        assertEquals(Settings().gin.rules.target, settings.gin.rules.target)
        assertEquals(Settings.DEFAULT_DRAW_COUNT, settings.drawCount)
    }
}
