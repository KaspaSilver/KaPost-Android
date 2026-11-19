package com.kachat.app.services.kachatnames

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// KaChat's .kachat notifier (Profile-bell rows for offers, sales, renewals and expiries) is gone:
// KaPost shows no notifications about domains and sends no .kachat transaction by itself, so
// nothing compares the registry or returns offers in the background (iOS b78fdb0).

/**
 * The local reminders an earlier build scheduled for .kachat names (renewal opens, expired, a day
 * before grace ends; iOS d1026de `KachatNameReminders`). KaPost shows no notifications about
 * domains: nothing schedules them any more, the app cancels any left at launch ([cancelAll]), and
 * a wallet leaving this phone drops its own ([removeAll]). Names are
 * `kachat-reminder-<network>-<wallet tag>-<name>-<expiresAt>-<kind>` (iOS 6128f1e, IOS-088).
 */
@Singleton
class KachatNameReminders @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [removeAll] reads and rewrites the one saved map (iOS: @MainActor). */
    private val mutex = Mutex()

    /** A short, stable tag for a wallet in the names (the address's last 12 characters). */
    private fun walletTag(wallet: String): String = wallet.lowercase().takeLast(12)

    companion object {
        private const val PREFS = "kachat_name_reminders"
        private const val KEY = "scheduled"
        /** `kachat-reminder-<network>-<wallet tag>-<name>-<expiresAt>-<kind>` (IOS-088). */
        private const val ROOT = "kachat-reminder-"

        /** Every reminder, every wallet and network, the first build's too: KaPost shows no
         *  notifications about domains. */
        fun cancelAll(context: Context) {
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            work.cancelAllWorkByTag(ROOT)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY, null) ?: return
            runCatching { JsonParser.parseString(raw).asJsonObject.keySet().toList() }.getOrNull()
                ?.forEach { work.cancelUniqueWork(it) }
            prefs.edit().remove(KEY).apply()
        }
    }

    /** A wallet's reminders on every network, for when it leaves this phone (iOS 6128f1e). */
    suspend fun removeAll(forWallet: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return@withLock
            val tag = "-${walletTag(forWallet)}-"
            val pending = scheduled()
            val ids = pending.keys.filter { it.startsWith(ROOT) && it.contains(tag) }
            if (ids.isEmpty()) return@withLock
            for (id in ids) work.cancelUniqueWork(id)
            save(pending - ids.toSet())
        }
    }

    /** The reminders scheduled so far: unique work name -> when it fires (unix ms). */
    private fun scheduled(): Map<String, Long> = runCatching {
        val root = JsonParser.parseString(prefs.getString(KEY, null) ?: return emptyMap()).asJsonObject
        root.entrySet().associate { (k, v) -> k to v.asLong }
    }.getOrNull() ?: emptyMap()

    private fun save(map: Map<String, Long>) {
        val root = JsonObject()
        for ((k, v) in map) root.addProperty(k, v)
        prefs.edit().putString(KEY, root.toString()).apply()
    }
}

/** A [KachatNameReminders] reminder coming due (iOS: the scheduled local notification, thread
 *  "kachat-names"). KaPost shows no notifications about domains, so one an earlier build
 *  scheduled, running before the launch cancels it, ends without showing anything. */
@HiltWorker
class KachatNameReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_NAME = "name"
    }
}
