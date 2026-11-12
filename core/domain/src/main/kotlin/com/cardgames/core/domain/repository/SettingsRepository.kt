package com.cardgames.core.domain.repository

import com.cardgames.core.domain.ginrummy.GinMatchRules
import com.cardgames.core.domain.ginrummy.ai.GinDifficulty
import kotlinx.coroutines.flow.Flow

/**
 * Global app settings. [drawCount] applies to new solitaire deals only — a dealt game freezes
 * its own copy (KlondikeState.drawCount). [themeId] null means "never chosen": use the default
 * pack, falling back to the dev placeholder.
 */
data class Settings(
    val drawCount: Int = DEFAULT_DRAW_COUNT,
    val themeId: String? = null,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** On by default — pre-bakes winnable deals in the background; off = plain random deals, zero solver overhead. */
    val useSolverForWinnableDeals: Boolean = true,
    /** Gin's match setup and helpers — see [GinOptions] for which apply when. */
    val gin: GinOptions = GinOptions(),
    /** Observed live — a change applies to the very next animation. */
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    /** Observed live: card flights become short cross-fades in place, with no flips and no overshoot. */
    val reduceMotion: Boolean = false,
    /** Solitaire hints per deal; read when the player asks for a hint, so a change applies at once. */
    val solitaireHints: HintLimit = HintLimit.THREE,
) {
    companion object {
        const val DEFAULT_DRAW_COUNT = 3
    }
}

/**
 * Gin's options. [difficulty] (the opponent: Milo, Rosa or Vex) and [rules] are read once per
 * match — a change applies from the next match, never to one in progress. [hints] (per hand),
 * [confirmKnock] (ask before knocking) and [meldHints] (outline a pile card that makes a meld)
 * apply at once.
 */
data class GinOptions(
    val difficulty: GinDifficulty = GinDifficulty.MEDIUM,
    val hints: HintLimit = HintLimit.THREE,
    val rules: GinMatchRules = GinMatchRules(),
    val confirmKnock: Boolean = false,
    val meldHints: Boolean = true,
)

/** Backed by DataStore Preferences in `:core:data`; emits defaults until first write. */
interface SettingsRepository {
    fun observeSettings(): Flow<Settings>

    suspend fun setDrawCount(drawCount: Int)

    suspend fun setThemeId(themeId: String)

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setUseSolverForWinnableDeals(enabled: Boolean)

    suspend fun setAnimationSpeed(speed: AnimationSpeed)

    suspend fun setReduceMotion(enabled: Boolean)

    suspend fun setSolitaireHints(limit: HintLimit)

    suspend fun setGinOptions(options: GinOptions)
}
