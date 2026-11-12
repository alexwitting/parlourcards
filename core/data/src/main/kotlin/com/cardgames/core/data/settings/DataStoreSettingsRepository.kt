package com.cardgames.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.GinState
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import com.cardgames.core.domain.repository.AnimationSpeed
import com.cardgames.core.domain.repository.GinOptions
import com.cardgames.core.domain.repository.HintLimit
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.solitaire.KlondikeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Key names are the public storage contract — never rename once shipped. */
@Singleton
internal class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object Keys {
            val drawCount = intPreferencesKey("solitaire.drawCount")
            val themeId = stringPreferencesKey("theme.id")
            val soundEnabled = booleanPreferencesKey("sound.enabled")
            val hapticsEnabled = booleanPreferencesKey("haptics.enabled")
            val useSolverForWinnableDeals = booleanPreferencesKey("solitaire.useSolverForWinnableDeals")
            val ginDifficulty = stringPreferencesKey("gin.difficulty")
            val animationSpeed = stringPreferencesKey("animation.speed")
            val reduceMotion = booleanPreferencesKey("motion.reduce")
            val solitaireHints = stringPreferencesKey("solitaire.hints")
            val ginHints = stringPreferencesKey("gin.hints")
            val ginTarget = intPreferencesKey("gin.target")
            val ginBonus = booleanPreferencesKey("gin.ginBonus")
            val ginBigGin = booleanPreferencesKey("gin.bigGin")
            val ginConfirmKnock = booleanPreferencesKey("gin.confirmKnock")
            val ginMeldHints = booleanPreferencesKey("gin.meldHints")
        }

        override fun observeSettings(): Flow<Settings> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs ->
                    Settings(
                        // Validated like the rest: a deal requires 1 or 3, so anything else would crash it.
                        drawCount =
                            prefs[Keys.drawCount]?.takeIf {
                                it in KlondikeState.ALLOWED_DRAW_COUNTS
                            } ?: Settings.DEFAULT_DRAW_COUNT,
                        themeId = prefs[Keys.themeId],
                        soundEnabled = prefs[Keys.soundEnabled] ?: true,
                        hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
                        useSolverForWinnableDeals = prefs[Keys.useSolverForWinnableDeals] ?: true,
                        gin =
                            GinOptions(
                                difficulty = parseGinDifficulty(prefs[Keys.ginDifficulty]),
                                hints = parseHintLimit(prefs[Keys.ginHints]),
                                rules =
                                    GinMatchRules(
                                        target = prefs[Keys.ginTarget]?.takeIf { it in GinMatchRules.TARGETS } ?: GinState.MATCH_TARGET,
                                        ginBonus = prefs[Keys.ginBonus] ?: true,
                                        bigGin = prefs[Keys.ginBigGin] ?: false,
                                    ),
                                confirmKnock = prefs[Keys.ginConfirmKnock] ?: false,
                                meldHints = prefs[Keys.ginMeldHints] ?: true,
                            ),
                        animationSpeed = parseAnimationSpeed(prefs[Keys.animationSpeed]),
                        reduceMotion = prefs[Keys.reduceMotion] ?: false,
                        solitaireHints = parseHintLimit(prefs[Keys.solitaireHints]),
                    )
                }

        /** Falls back to the default on a missing/unrecognized value rather than crashing — e.g. a future downgrade. */
        private fun parseGinDifficulty(stored: String?): GinDifficulty =
            stored?.let { name -> runCatching { GinDifficulty.valueOf(name) }.getOrNull() } ?: GinDifficulty.MEDIUM

        /** Same lenient fallback as [parseGinDifficulty]. */
        private fun parseAnimationSpeed(stored: String?): AnimationSpeed =
            stored?.let { name -> runCatching { AnimationSpeed.valueOf(name) }.getOrNull() } ?: AnimationSpeed.NORMAL

        /** Same lenient fallback as [parseGinDifficulty]. */
        private fun parseHintLimit(stored: String?): HintLimit =
            stored?.let { name -> runCatching { HintLimit.valueOf(name) }.getOrNull() } ?: HintLimit.THREE

        override suspend fun setDrawCount(drawCount: Int) {
            dataStore.edit { it[Keys.drawCount] = drawCount }
        }

        override suspend fun setThemeId(themeId: String) {
            dataStore.edit { it[Keys.themeId] = themeId }
        }

        override suspend fun setSoundEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.soundEnabled] = enabled }
        }

        override suspend fun setHapticsEnabled(enabled: Boolean) {
            dataStore.edit { it[Keys.hapticsEnabled] = enabled }
        }

        override suspend fun setUseSolverForWinnableDeals(enabled: Boolean) {
            dataStore.edit { it[Keys.useSolverForWinnableDeals] = enabled }
        }

        override suspend fun setAnimationSpeed(speed: AnimationSpeed) {
            dataStore.edit { it[Keys.animationSpeed] = speed.name }
        }

        override suspend fun setReduceMotion(enabled: Boolean) {
            dataStore.edit { it[Keys.reduceMotion] = enabled }
        }

        override suspend fun setSolitaireHints(limit: HintLimit) {
            dataStore.edit { it[Keys.solitaireHints] = limit.name }
        }

        override suspend fun setGinOptions(options: GinOptions) {
            dataStore.edit {
                it[Keys.ginDifficulty] = options.difficulty.name
                it[Keys.ginHints] = options.hints.name
                it[Keys.ginTarget] = options.rules.target
                it[Keys.ginBonus] = options.rules.ginBonus
                it[Keys.ginBigGin] = options.rules.bigGin
                it[Keys.ginConfirmKnock] = options.confirmKnock
                it[Keys.ginMeldHints] = options.meldHints
            }
        }
    }
