package com.duzman46.gridbound.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duzman46.gridbound.R
import com.duzman46.gridbound.core.Constants
import com.duzman46.gridbound.data.gridboundDataStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * The one thing this app ever says while it is closed, and it says it rarely.
 *
 * The rule is deliberately hard to trip. The worker wakes about once a day; it sends nothing
 * unless the player has been away for [AWAY_DAYS] whole days, and having sent one it will not
 * send another for [QUIET_DAYS] no matter how long they stay away. So the most anyone can
 * receive is one notice a fortnight, and somebody who plays every few days receives none ever.
 *
 * That is the whole feature, and the restraint is the point of it. A game that reminds you
 * daily is a game you mute; a game that says something once, a week after you last opened it,
 * is a game you might open. There is no streak to protect, no daily reward to collect and no
 * timer counting down — those are the mechanics that force a notification a day, and this app
 * does not have them.
 *
 * **Why WorkManager and not an alarm.** The nudge has to survive a reboot and a doze, and it
 * must never wake the device to do it. A periodic worker is exactly that: the system runs it
 * when the phone is awake anyway, and it comes back after a restart without a boot receiver.
 */
class ComeBackWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val values = context.gridboundDataStore.data.first()
        val wanted = values[SETTING] ?: true
        if (!wanted || !Notifications.permitted(context)) return Result.success()

        val now = System.currentTimeMillis()
        val lastOpened = values[LAST_OPENED] ?: return Result.success()
        val lastNudged = values[LAST_NUDGED] ?: 0L

        val awayFor = now - lastOpened
        val sinceNudge = now - lastNudged
        if (awayFor < DAY * AWAY_DAYS || sinceNudge < DAY * QUIET_DAYS) return Result.success()

        Notifications.postComeBack(
            context = context,
            title = context.getString(R.string.notification_come_back_title),
            body = context.getString(R.string.notification_come_back_body),
        )
        context.gridboundDataStore.edit { it[LAST_NUDGED] = now }
        return Result.success()
    }

    companion object {
        private const val NAME = "koridor-come-back"
        private const val DAY = 24L * 60 * 60 * 1000

        /** How long somebody has to be away before the app says anything at all. */
        private const val AWAY_DAYS = 7

        /** And how long it then keeps quiet, whatever happens. */
        private const val QUIET_DAYS = 14

        private val SETTING = booleanPreferencesKey(Constants.Data.KEY_NOTIFICATIONS_ENABLED)
        private val LAST_OPENED = longPreferencesKey(Constants.Data.KEY_LAST_OPENED)
        private val LAST_NUDGED = longPreferencesKey(Constants.Data.KEY_LAST_NUDGED)

        /**
         * Records that the app is open, and makes sure the daily check is scheduled.
         *
         * Both belong together: the only thing the schedule reads is the timestamp this writes,
         * so an install that has never recorded one sends nothing — which is the right answer
         * for a player who has not finished their first session yet.
         */
        suspend fun onAppOpened(context: Context) {
            context.gridboundDataStore.edit { it[LAST_OPENED] = System.currentTimeMillis() }
            schedule(context)
        }

        private fun schedule(context: Context) {
            // The thirty-second one-shot that was briefly here, so the notice could be looked at
            // once on a real handset, is gone. It proved the whole path — worker, permission,
            // channel, icon, colour — and there is nothing about it worth keeping: a preview
            // that ships is a notification a minute after install.
            //
            // KEEP, not REPLACE: replacing on every launch restarts the period, and a period
            // that restarts every launch is a period that never elapses.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ComeBackWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build(),
            )
        }
    }
}
