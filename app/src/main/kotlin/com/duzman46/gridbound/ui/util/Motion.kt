package com.duzman46.gridbound.ui.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether this device wants things to move.
 *
 * Android's answer to a reduced-motion preference is the animator duration scale, which the
 * accessibility settings zero out under "remove animations". A player who asked for that
 * usually asked because movement makes them ill, so anything decorative — a loop that never
 * ends, a panel sliding in over what they were reading — has to take the setting seriously
 * rather than merely shortening itself.
 *
 * Previews report false so a screenshot of a screen is the same screenshot every time.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        !inspecting && Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}
