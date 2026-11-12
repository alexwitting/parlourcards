package com.cardgames.core.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.cardgames.core.ui.R
import kotlin.random.Random

/**
 * The one real [GameFeedback]: SoundPool over the bundled synthesized WAVs (`tools/gen_sounds.js`)
 * + the system haptic types via [haptics] (`LocalHapticFeedback`), which follow the device's own
 * touch-feedback strength. Card sounds vary a little each time — one of several takes of a card
 * landing, at a slightly different pitch — so a run of moves doesn't sound mechanical. The enabled lambdas are consulted on every call so the
 * settings toggles apply instantly. Call [release] when the UI goes away.
 */
class SoundHapticGameFeedback(
    context: Context,
    private val haptics: HapticFeedback,
    private val isSoundEnabled: () -> Boolean,
    private val isHapticsEnabled: () -> Boolean,
) : GameFeedback {
    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()

    private val soundIds: Map<CardSound, List<Int>> =
        mapOf(
            CardSound.Place to listOf(R.raw.card_place_1, R.raw.card_place_2, R.raw.card_place_3),
            CardSound.Flip to listOf(R.raw.card_flip),
            CardSound.Deal to listOf(R.raw.card_deal),
            CardSound.Shuffle to listOf(R.raw.shuffle),
            CardSound.Knock to listOf(R.raw.knock),
            CardSound.HandWon to listOf(R.raw.hand_won),
            CardSound.Win to listOf(R.raw.win),
        ).mapValues { (_, takes) -> takes.map { soundPool.load(context, it, 1) } }

    override fun play(sound: CardSound) {
        if (!isSoundEnabled()) return
        val id = soundIds[sound]?.randomOrNull() ?: return
        val rate = if (sound in VARIED) 1f + Random.nextFloat() * PITCH_SPREAD * 2 - PITCH_SPREAD else 1f
        soundPool.play(id, VOLUME, VOLUME, 1, 0, rate)
    }

    override fun haptic(haptic: GameHaptic) {
        if (!isHapticsEnabled()) return
        haptics.performHapticFeedback(
            when (haptic) {
                GameHaptic.Tick -> HapticFeedbackType.SegmentTick
                GameHaptic.Lift -> HapticFeedbackType.GestureThresholdActivate
                GameHaptic.Reject -> HapticFeedbackType.Reject
                GameHaptic.Confirm -> HapticFeedbackType.Confirm
            },
        )
    }

    fun release() {
        soundPool.release()
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val VOLUME = 1f

        /** The card sounds, played up to this fraction above or below their pitch. */
        val VARIED = setOf(CardSound.Place, CardSound.Flip, CardSound.Deal)
        const val PITCH_SPREAD = 0.05f
    }
}
