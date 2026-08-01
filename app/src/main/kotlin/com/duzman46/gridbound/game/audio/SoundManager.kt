package com.duzman46.gridbound.game.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.duzman46.gridbound.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

enum class SoundEffect {
    MOVE,
    WALL,
    ERROR,
    WIN,
    LOSS,
}

@Singleton
class SoundManager @Inject constructor() {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, Constants.Audio.VOLUME_PERCENT)

    @Synchronized
    fun play(effect: SoundEffect, enabled: Boolean) {
        if (!enabled) return
        val (tone, duration) = when (effect) {
            SoundEffect.MOVE -> ToneGenerator.TONE_PROP_BEEP to Constants.Audio.MOVE_DURATION_MILLIS
            SoundEffect.WALL -> ToneGenerator.TONE_PROP_ACK to Constants.Audio.WALL_DURATION_MILLIS
            SoundEffect.ERROR -> ToneGenerator.TONE_PROP_NACK to Constants.Audio.ERROR_DURATION_MILLIS
            SoundEffect.WIN -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to Constants.Audio.VICTORY_DURATION_MILLIS
            SoundEffect.LOSS -> ToneGenerator.TONE_CDMA_ABBR_ALERT to Constants.Audio.VICTORY_DURATION_MILLIS
        }
        toneGenerator.startTone(tone, duration)
    }
}

