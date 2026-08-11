package com.duzman46.gridbound.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duzman46.gridbound.MainActivity
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.AppLog

/**
 * What the app is allowed to put in the notification shade, and how seldom.
 *
 * **One channel and one kind of message.** A game that files itself under four channels is a
 * game asking to be muted wholesale; a player who wants fewer of these wants fewer of *all* of
 * these, so there is one switch in the app, one channel in the system, and nothing that can
 * surprise them from a second direction.
 *
 * **Low importance, deliberately.** `IMPORTANCE_DEFAULT` makes a sound and slides a heads-up
 * card over whatever is on screen. Nothing this app has to say is worth interrupting a phone
 * call for. `IMPORTANCE_LOW` puts it in the shade, silently, where it waits — which is exactly
 * what "üst panelden güzel bildirimler" means when it also says "çok nadir".
 *
 * **And three gates, all of which must be open.** The player's own switch in Settings, the
 * operating system's permission, and the channel not having been blocked in system settings.
 * The app checks all three before it builds anything, because a notification posted into a
 * blocked channel is not an error — it just silently does not happen, and silently is how a
 * feature becomes impossible to debug.
 */
object Notifications {

    const val CHANNEL_ID = "koridor_nudges"

    /** Ids, so a second notice of the same kind replaces the first rather than stacking. */
    private const val ID_COME_BACK = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Whether the operating system currently lets this app post anything. */
    fun permitted(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * The one notice this app sends: a nudge, for somebody who has not played in a while.
     *
     * It opens the app and nothing more specific. A deep link into a screen is a promise about
     * where the player wanted to go, and a nudge has no idea.
     */
    fun postComeBack(context: Context, title: String, body: String) {
        if (!permitted(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        // An explicit try/catch rather than runCatching, and that is for the reader rather than
        // the runtime: [permitted] has already checked the grant, but lint cannot see through a
        // helper and it is right not to try — the grant can be revoked between the check and
        // the call, and the only correct answer to that is to say nothing.
        try {
            NotificationManagerCompat.from(context).notify(ID_COME_BACK, notification)
        } catch (denied: SecurityException) {
            AppLog.warn("notify-come-back", denied)
        }
    }
}
