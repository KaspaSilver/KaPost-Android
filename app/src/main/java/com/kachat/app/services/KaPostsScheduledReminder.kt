package com.kachat.app.services

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kachat.app.MainActivity
import com.kachat.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The reminder for a scheduled post only this phone holds (the indexer refused it): a local
 * notification at its time, since opening the app is what sends it (iOS KaPostsScheduledStore.swift
 * `scheduleReminder`). A one-time WorkManager job stands in for iOS's time-interval trigger - no
 * exact-alarm permission. Removed on cancel, on a local send, and once the indexer takes the post.
 */
@Singleton
class KaPostsScheduledReminder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Sets the reminder for [entry]'s time; a second call for the same post replaces the first. */
    fun schedule(entry: KaPostScheduledEntry) {
        try {
            val delayMs = maxOf(1_000L, entry.notBeforeMs - System.currentTimeMillis())
            val request = OneTimeWorkRequestBuilder<KaPostsScheduledReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TX_ID to entry.txId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(reminderId(entry.txId), ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            Log.w(TAG, "Scheduled-post reminder failed", e)
        }
    }

    /** Takes down the reminder still waiting for [txId] (iOS removePendingNotificationRequests). */
    fun cancel(txId: String) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(reminderId(txId))
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove the scheduled-post reminder", e)
        }
    }

    companion object {
        private const val TAG = "KaPostsScheduled"
        internal const val KEY_TX_ID = "tx_id"

        /** On the reminder's tap intent: open KaPosts' Scheduled list (iOS
         *  `KaPostsDeepLink.pendingOpenScheduled`). Honoured only with the internal token. */
        const val EXTRA_OPEN_SCHEDULED = "kapost_open_scheduled"

        /** iOS `reminderId`: one reminder per post. */
        internal fun reminderId(txId: String) = "kaposts-scheduled-$txId"
    }
}

/**
 * Runs at a phone-only scheduled post's time. First the background send iOS's app refresh does
 * (BackgroundTaskManager.swift: `sendDueLocally`, whatever the fetch setting says - it is the
 * author's own post), with a network to send on; then, if the post is still waiting on this
 * phone, the reminder itself. A post that went out, failed, or was handed to the indexer
 * needs no reminder - the store has already taken it down by then.
 */
@HiltWorker
class KaPostsScheduledReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduledStore: Provider<KaPostsScheduledStore>,
    /** Injected so its channels exist before this posts on one. */
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val txId = inputData.getString(KaPostsScheduledReminder.KEY_TX_ID) ?: return Result.success()
        val store = scheduledStore.get()
        try {
            store.reloadIfNeeded()
            // Offline, a submission could only fail - and a failed post stays failed - so the
            // reminder alone goes out and opening the app sends it. Signed out is skipped inside.
            // This job's own reminder is left alone: cancelling its unique work mid-run would
            // cancel this run, and the next due post in it would be cut off. It ends by itself.
            if (hasNetwork()) store.sendDueLocally(runningReminderTxId = txId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background send of scheduled posts failed", e)
        }
        // Only a post still waiting on this phone: one that went out, failed, was handed to the
        // indexer, or is gone (cancelled, or its account deleted) needs no reminder.
        val entry = store.entries.value.firstOrNull { it.txId == txId }
        if (entry == null || entry.status != KaPostScheduledEntry.STATUS_SCHEDULED || entry.onServer) {
            return Result.success()
        }
        showReminder(txId)
        return Result.success()
    }

    private fun hasNetwork(): Boolean = try {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        manager?.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (_: Exception) {
        false
    }

    private fun showReminder(txId: String) {
        val context = applicationContext
        // POST_NOTIFICATIONS not granted: skip rather than crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notificationId = KaPostsScheduledReminder.reminderId(txId).hashCode()
        // The tap opens the app on KaPosts' Scheduled list, which sends what is due as it comes
        // to the front (iOS KaPostsApp `kaposts-scheduled`). The token marks the intent as our
        // own: MainActivity is exported, and ignores the extra without it.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationHelper.EXTRA_INTERNAL_TOKEN, NotificationHelper.internalToken(context))
            putExtra(KaPostsScheduledReminder.EXTRA_OPEN_SCHEDULED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // iOS: the default sound.
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Scheduled post")
            .setContentText("Your post is ready to go out. Open KaPost to send it.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    private companion object {
        const val TAG = "KaPostsScheduled"
    }
}
