package com.duzman46.gridbound.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppLog
import com.duzman46.gridbound.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The loop under the menus.
 *
 * **Why it is a manager rather than a service.** Nothing about this music should survive the app
 * being put away: it is background to looking at the app, not something a player is listening
 * to. So there is no notification, no media session and no foreground service — the activity
 * says when it is on screen and the loop follows.
 *
 * **Why it is quiet.** [LEVEL] is a third of full, and that is not timidity: the sound effects
 * are played through a tone generator at the level the player chose, and a pad at equal volume
 * would bury the one sound in the app that carries information. Music you notice you are
 * hearing is music somebody turns off.
 *
 * **Two switches, and both must be on.** [enabled] is what the player asked for and [visible] is
 * whether the app is in front of them; the loop plays only when both agree. Keeping them apart
 * means neither has to know about the other — the activity does not read settings, and the
 * setting does not know what an activity is.
 */
@Singleton
class MusicManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var player: MediaPlayer? = null
    private var enabled = false
    private var visible = false

    /** The player's answer, from settings. */
    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        apply()
    }

    /** The activity's answer: whether the app is on screen at all. */
    @Synchronized
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        apply()
    }

    private fun apply() {
        if (enabled && visible) start() else stop()
    }

    private fun start() {
        // Resumed rather than rebuilt: a player that is only paused keeps its position, so
        // coming back from the home screen does not restart the loop from the top.
        player?.let {
            if (!it.isPlaying) it.start()
            return
        }
        player = runCatching {
            MediaPlayer.create(context, R.raw.menu_loop)?.apply {
                isLooping = true
                setVolume(LEVEL, LEVEL)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                start()
            }
        }.onFailure {
            // A device that cannot decode a mono WAV is not a device to crash over.
            AppLog.warn("music-create", it)
        }.getOrNull()
    }

    private fun stop() {
        val current = player ?: return
        runCatching { if (current.isPlaying) current.pause() }
            .onFailure { AppLog.warn("music-pause", it) }
    }

    /** Called when the activity is finishing for good. */
    @Synchronized
    fun release() {
        runCatching { player?.release() }.onFailure { AppLog.warn("music-release", it) }
        player = null
        visible = false
    }

    private companion object {
        /** See the note above: a third of full, so the effects stay the loudest thing. */
        const val LEVEL = Constants.Audio.MUSIC_LEVEL
    }
}
