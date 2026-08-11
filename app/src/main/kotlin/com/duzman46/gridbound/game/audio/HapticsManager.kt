package com.duzman46.gridbound.game.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.duzman46.gridbound.core.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The vibration under a move, a refused wall and the end of a match.
 *
 * **Why this exists at all, when Compose has haptics.** It used to go through
 * `LocalHapticFeedback`, which is `View.performHapticFeedback`, and on the owner's handset
 * nothing happened. The reason is not a bug in the app: the platform gates that call on
 * `Settings.System.haptic_feedback_enabled`, the system-wide *touch feedback* switch, and on
 * that phone it reads 0. Measured, not guessed — the vibrator itself is live and its touch
 * intensity is MEDIUM, so the hardware was willing and the call was being dropped on the floor.
 *
 * **Why using the vibrator directly is the right answer rather than a workaround.** The system
 * switch is about *touch feedback*: the tick under a keyboard key, the bump when a picker snaps.
 * A pawn landing and a wall being refused are not touch feedback, they are the game telling you
 * what happened — the same category as its sounds, which nobody expects the keyboard-click
 * setting to silence. So the app's own switch governs them, and the platform's governs the
 * platform's. What this deliberately does *not* do is pass FLAG_IGNORE_GLOBAL_SETTING to force a
 * touch haptic through: that overrides a choice the player made about their whole phone.
 *
 * The vibrator's own intensity settings still apply. Somebody who has turned vibration off at
 * the device level gets nothing from here either, which is correct.
 */
@Singleton
class HapticsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
        }.onFailure { AppLog.warn("vibrator-service", it) }.getOrNull()
            ?.takeIf { it.hasVibrator() }
    }

    /**
     * What the player asked for, pushed in from the settings collector.
     *
     * Held here rather than passed at every call so that the lobby's long-press copy — which is
     * nowhere near the game state — obeys the same switch as everything else.
     */
    @Volatile
    var enabled: Boolean = true

    fun play(effect: SoundEffect) {
        if (!enabled) return
        val device = vibrator ?: return
        val pattern = when (effect) {
            // A move landing is the most frequent thing in the game, so it is the lightest
            // thing in here: any more and it becomes the sensation of the app rather than an
            // accent on it.
            SoundEffect.MOVE -> Pulse(longArrayOf(0, 12), intArrayOf(0, 90))
            SoundEffect.WALL -> Pulse(longArrayOf(0, 20), intArrayOf(0, 150))
            // Two short knocks: the shape of "no" in every haptic vocabulary there is.
            SoundEffect.ERROR -> Pulse(longArrayOf(0, 18, 60, 18), intArrayOf(0, 180, 0, 180))
            SoundEffect.WARNING -> Pulse(longArrayOf(0, 10), intArrayOf(0, 110))
            SoundEffect.WIN -> Pulse(longArrayOf(0, 24, 70, 48), intArrayOf(0, 160, 0, 220))
            SoundEffect.LOSS -> Pulse(longArrayOf(0, 70), intArrayOf(0, 130))
        }
        vibrate(device, pattern)
    }

    /** The bump under a long press that copied something. */
    fun tick() {
        if (!enabled) return
        val device = vibrator ?: return
        vibrate(device, Pulse(longArrayOf(0, 16), intArrayOf(0, 140)))
    }

    private fun vibrate(device: Vibrator, pulse: Pulse) {
        runCatching {
            // Amplitude control is what makes a light tick light. Where the hardware has none,
            // the same timings still run at whatever single strength it has.
            val vibration = if (device.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(pulse.timings, pulse.amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(pulse.timings, -1)
            }
            device.vibrate(vibration)
        }.onFailure { AppLog.warn("vibrate", it) }
    }

    private class Pulse(val timings: LongArray, val amplitudes: IntArray)
}

/**
 * The manager, reachable from a composable without threading it through every screen.
 *
 * A composition local rather than a parameter because the callers are leaves — one long-press on
 * a room code, deep inside the lobby — and a singleton the whole app shares has nothing to say
 * about where in the tree it is used. Provided once, in the activity.
 */
val LocalHapticsManager = staticCompositionLocalOf<HapticsManager> {
    error("LocalHapticsManager used outside the activity's content")
}
