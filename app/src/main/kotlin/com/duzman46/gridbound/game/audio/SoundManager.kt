package com.duzman46.gridbound.game.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

enum class SoundEffect {
    MOVE,
    WALL,
    ERROR,

    /** The move clock is about to run out. Kept apart from [ERROR]: nothing has gone wrong. */
    WARNING,
    WIN,
    LOSS,
}

/**
 * The move, wall and result sounds.
 *
 * The generator is built on the first sound rather than in the constructor, and a device that
 * refuses to hand one out — every audio session taken — makes the app silent rather than making
 * it crash on a move.
 */
@Singleton
class SoundManager @Inject constructor() {
    private var generator: ToneGenerator? = null

    @Synchronized
    fun play(effect: SoundEffect, enabled: Boolean) {
        if (!enabled) return
        val tones = tones() ?: return
        val (tone, duration) = when (effect) {
            SoundEffect.MOVE -> ToneGenerator.TONE_PROP_BEEP to Constants.Audio.MOVE_DURATION_MILLIS
            SoundEffect.WALL -> ToneGenerator.TONE_PROP_ACK to Constants.Audio.WALL_DURATION_MILLIS
            SoundEffect.ERROR -> ToneGenerator.TONE_PROP_NACK to Constants.Audio.ERROR_DURATION_MILLIS
            // The telephony "pip" is the countdown everyone already knows from a call about
            // to be cut off, which is exactly what the last seconds of a turn are.
            SoundEffect.WARNING -> ToneGenerator.TONE_SUP_PIP to Constants.Audio.WARNING_DURATION_MILLIS
            SoundEffect.WIN -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to Constants.Audio.VICTORY_DURATION_MILLIS
            SoundEffect.LOSS -> ToneGenerator.TONE_CDMA_ABBR_ALERT to Constants.Audio.VICTORY_DURATION_MILLIS
        }
        runCatching { tones.startTone(tone, duration) }
            .onFailure { AppLog.warn("tone-start", it) }
    }

    private fun tones(): ToneGenerator? {
        generator?.let { return it }
        generator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, Constants.Audio.VOLUME_PERCENT)
        }.onFailure { AppLog.warn("tone-generator", it) }.getOrNull()
        return generator
    }
}
