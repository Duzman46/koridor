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
 * A [ToneGenerator] takes its volume when it is built and never again, so a level the player can
 * move means building a new one each time they move it. That is cheap and it happens while a
 * finger is on a slider, so it is done lazily — the generator is replaced on the next sound
 * rather than on the drag, and a drag from eighty to twenty therefore costs one rebuild rather
 * than sixty.
 */
@Singleton
class SoundManager @Inject constructor() {
    private var generator: ToneGenerator? = null
    private var builtAt = -1

    @Synchronized
    fun play(effect: SoundEffect, volumePercent: Int) {
        val volume = volumePercent.coerceIn(0, 100)
        if (volume == 0) return
        val tones = generatorAt(volume) ?: return
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

    private fun generatorAt(volume: Int): ToneGenerator? {
        if (builtAt == volume) return generator
        runCatching { generator?.release() }
        // A device can refuse to hand one out when every audio session is taken. Silence is the
        // right answer to that, not a crash on a move.
        generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volume) }
            .onFailure { AppLog.warn("tone-generator", it) }
            .getOrNull()
        builtAt = if (generator != null) volume else -1
        return generator
    }
}
