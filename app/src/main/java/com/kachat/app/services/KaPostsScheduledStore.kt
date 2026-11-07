package com.kachat.app.services

import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * A post scheduled for later (KAPOSTS_INDEXER.md section 5.10): built and signed when the author
 * picked the time, submitted by the indexer at that time - or by this phone, if the indexer could
 * not be reached, the next time the app runs after it.
 *
 * [txId] is the id the transaction will have on chain, computed before it is submitted (see
 * [com.kachat.app.util.KaspaTransactionId]) - the indexer keys its entry, its status and its
 * cancellation on it.
 */
data class KaPostScheduledEntry(
    val txId: String,
    val text: String,
    val notBeforeMs: Long,
    val createdAtMs: Long,
    /** "txid:index" of every coin this transaction spends - kept out of every other send until
     *  the post is on chain, or it would be invalid by the time its moment came. */
    val spentOutpoints: List<String>,
    /** The signed transaction itself, for the phone's own fallback submission. */
    val transaction: RawTransaction,
    /** Whether the indexer took it. False means this phone is the only thing that will send it. */
    val onServer: Boolean = false,
    val status: String = STATUS_SCHEDULED,
    val error: String? = null,
    val submittedAtMs: Long? = null,
) {
    companion object {
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
    }
}

/**
 * This wallet's scheduled posts, and the keeper of the coins they will spend.
 *
 * [reservedOutpoints] is read before inputs are chosen - by the wallet engine, and by `.kachat`
 * through `KachatNamesService.fundingUtxos` and the profile record - so a coin a scheduled post
 * depends on is never spent underneath it. The reservation lasts exactly as long as the entry is
 * `scheduled`; it's loaded for each wallet as the wallet loads (iOS 58a0b22, IOS-064). Mirrors
 * iOS's `KaPostsScheduledStore`.
 */
@Singleton
class KaPostsScheduledStore @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
    /** Lazy: the service is what talks to the indexer, and it must not need this store to exist. */
    private val kaPostsService: Provider<KaPostsService>,
    /** The local notification for a post only this phone holds (iOS `scheduleReminder`). */
    private val reminder: KaPostsScheduledReminder,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow<List<KaPostScheduledEntry>>(emptyList())
    val entries: StateFlow<List<KaPostScheduledEntry>> = _entries.asStateFlow()

    @Volatile
    private var loadedWallet: String? = null

    init {
        instance = this
        // The coins scheduled KaPosts hold, for every builder from the moment a wallet loads -
        // not first when the KaPosts page renders, and never the previous wallet's (iOS 58a0b22, IOS-064).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            walletManager.activeAddressFlow.collect { runCatching { reloadIfNeeded() } }
        }
    }

    private fun walletAddressOrNull(): String? =
        try { walletManager.getAddress() } catch (_: Exception) { null }

    /** Loads the active wallet's entries; call before reading and on account switches. */
    @Synchronized
    fun reloadIfNeeded() {
        val wallet = walletAddressOrNull()
        if (wallet == null) {
            _entries.value = emptyList()
            loadedWallet = null
            publishReserved()
            return
        }
        if (loadedWallet == wallet) return
        loadedWallet = wallet
        _entries.value = read(wallet)
        publishReserved()
    }

    private fun read(wallet: String): List<KaPostScheduledEntry> = readEntries(prefs, wallet)

    /** Writes [entries] as the loaded wallet's - the wallet they were read for, not whichever
     *  one is active by now (an account switch lands in [reloadIfNeeded] a moment later). */
    @Synchronized
    private fun persist(entries: List<KaPostScheduledEntry>) {
        val wallet = loadedWallet ?: return
        val sorted = entries.sortedBy { it.notBeforeMs }
        _entries.value = sorted
        prefs.edit().putString(key(wallet), gson.toJson(sorted)).apply()
        publishReserved()
    }

    private fun publishReserved() {
        reservedOutpoints = _entries.value
            .filter { it.status == KaPostScheduledEntry.STATUS_SCHEDULED }
            .flatMap { it.spentOutpoints }
            .toSet()
    }

    // MARK: - Scheduling

    /**
     * Records a signed post for [notBeforeMs], hands it to the indexer, and - when the indexer
     * cannot be reached - keeps it here for this phone to submit once the time has passed.
     */
    suspend fun add(scheduled: KaPostsService.ScheduledTransaction, text: String, notBeforeMs: Long): KaPostScheduledEntry {
        reloadIfNeeded()
        val wallet = loadedWallet
        var entry = KaPostScheduledEntry(
            txId = scheduled.txId,
            text = text,
            notBeforeMs = notBeforeMs,
            createdAtMs = System.currentTimeMillis(),
            spentOutpoints = scheduled.spentOutpoints,
            transaction = scheduled.transaction,
        )
        persist(_entries.value.filterNot { it.txId == entry.txId } + entry)
        try {
            kaPostsService.get().scheduleOnServer(entry.txId, entry.transaction, notBeforeMs)
            entry = entry.copy(onServer = true)
            update(entry, wallet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Scheduling on the indexer failed, keeping it on the phone", e)
            reminder.schedule(entry)
        }
        return entry
    }

    /**
     * Replaces [entry] in [wallet]'s list - the wallet it was read from, captured before the
     * network call. An account switch during that call must not write it into the wallet now
     * loaded; it goes to its own wallet's key instead. An entry that is gone by now (cancelled,
     * or its account deleted) stays gone.
     */
    @Synchronized
    private fun update(entry: KaPostScheduledEntry, wallet: String? = loadedWallet) {
        if (wallet == null) return
        if (wallet != loadedWallet) {
            val stored = read(wallet)
            if (stored.none { it.txId == entry.txId }) return
            val sorted = (stored.filterNot { it.txId == entry.txId } + entry).sortedBy { it.notBeforeMs }
            prefs.edit().putString(key(wallet), gson.toJson(sorted)).apply()
            return
        }
        if (_entries.value.none { it.txId == entry.txId }) return
        val without = _entries.value.filterNot { it.txId == entry.txId }
        persist(without + entry)
    }

    /** Cancels a post that has not gone out: told to the indexer if it holds it, dropped here,
     *  and its coins released. */
    suspend fun cancel(entry: KaPostScheduledEntry) {
        if (entry.onServer && entry.status == KaPostScheduledEntry.STATUS_SCHEDULED) {
            try { kaPostsService.get().cancelScheduledOnServer(entry.txId) } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cancelling on the indexer failed; dropping it locally anyway", e)
            }
        }
        persist(_entries.value.filterNot { it.txId == entry.txId })
        reminder.cancel(entry.txId)
    }

    /** Forgets a submitted/failed row - nothing is reserved by it any more. */
    fun remove(entry: KaPostScheduledEntry) {
        persist(_entries.value.filterNot { it.txId == entry.txId })
    }

    /**
     * Submits, from this phone, every post the indexer never took whose time has come, and tries
     * the indexer again for the ones still in the future. Called when the app comes to the
     * front, and from the background (the reminder's job, [SyncWorker]).
     *
     * [runningReminderTxId] is the post whose reminder job is making this call: cancelling that
     * job's unique work would cancel this very run, so its reminder is left to finish by itself.
     */
    suspend fun sendDueLocally(runningReminderTxId: String? = null) = sendDueMutex.withLock {
        // nothing goes out for an account behind the sign-in screen
        if (walletManager.isLoggedOut) return@withLock
        reloadIfNeeded()
        // The wallet these entries belong to: every outcome is written back to it, even if the
        // account changes while a submission is out.
        val wallet = loadedWallet ?: return@withLock
        val now = System.currentTimeMillis()
        // Each entry read fresh: a run that waited on the lock must not submit what the run
        // before it already sent.
        for (snapshot in _entries.value) {
            // A cancelled run stops between posts; the next run sends the rest.
            currentCoroutineContext().ensureActive()
            val entry = (if (wallet == loadedWallet) _entries.value else read(wallet))
                .firstOrNull { it.txId == snapshot.txId } ?: continue
            if (entry.status != KaPostScheduledEntry.STATUS_SCHEDULED || entry.onServer) continue
            if (entry.notBeforeMs <= now) {
                // A submission that started always records how it ended: cancelled halfway, the
                // post would stay "scheduled" with its coins held, or - caught as a failure -
                // be marked failed with its coins released after it had already reached the node.
                withContext(NonCancellable) {
                    try {
                        val txId = kaPostsService.get().submitScheduledLocally(entry.transaction)
                        Log.i(TAG, "Scheduled post submitted from the phone: ${txId.take(12)}")
                        update(entry.copy(status = KaPostScheduledEntry.STATUS_SUBMITTED, submittedAtMs = System.currentTimeMillis()), wallet)
                    } catch (e: CancellationException) {
                        // Not a failure (a timeout inside the submission): it stays scheduled,
                        // coins held, for the next run.
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Scheduled post failed to submit", e)
                        update(entry.copy(status = KaPostScheduledEntry.STATUS_FAILED, error = e.message), wallet)
                    }
                    // Sent or failed, it no longer waits on the phone (iOS KaPostsScheduledStore.swift).
                    if (entry.txId != runningReminderTxId) reminder.cancel(entry.txId)
                }
            } else {
                // Still in the future and still only here: offer it to the indexer again.
                try {
                    kaPostsService.get().scheduleOnServer(entry.txId, entry.transaction, entry.notBeforeMs)
                    update(entry.copy(onServer = true), wallet)
                    // The indexer sends it now; no reminder to open the app.
                    if (entry.txId != runningReminderTxId) reminder.cancel(entry.txId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Still unreachable; this phone remains the fallback.
                }
            }
        }
    }

    /** One [sendDueLocally] at a time: two at once (opening the list from the menu ran it twice)
     *  could submit an entry twice, or mark a just-submitted one failed. */
    private val sendDueMutex = kotlinx.coroutines.sync.Mutex()

    /** Takes the indexer's view of what it holds: submitted and failed outcomes land here. */
    suspend fun refreshFromServer() {
        reloadIfNeeded()
        // Only posts the server holds have anything to learn from it (iOS e5a9b11).
        if (_entries.value.none { it.onServer }) return
        val wallet = loadedWallet
        val remote = try {
            kaPostsService.get().fetchScheduledPosts()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        // An account switch during the fetch: this answer is the old wallet's.
        if (wallet != loadedWallet) return
        if (remote.isEmpty()) return
        val byId = remote.associateBy { it.txId }
        var changed = false
        val updated = _entries.value.map { entry ->
            if (!entry.onServer) return@map entry
            val server = byId[entry.txId] ?: return@map entry
            val status = server.status ?: entry.status
            if (status == entry.status && server.error == entry.error) return@map entry
            changed = true
            entry.copy(status = status, error = server.error, submittedAtMs = server.submittedAt ?: entry.submittedAtMs)
        }
        if (changed) persist(updated)
    }

    /** Drops [wallet]'s list from memory when it is the loaded one (its account was deleted). */
    @Synchronized
    private fun forget(wallet: String) {
        if (loadedWallet != wallet) return
        _entries.value = emptyList()
        publishReserved()
    }

    companion object {
        private const val TAG = "KaPostsScheduled"
        private const val PREFS_NAME = "kaposts_scheduled"
        private val gson = Gson()

        /** The store this process made, for [deleteAccount]'s in-memory half. */
        @Volatile
        private var instance: KaPostsScheduledStore? = null

        private fun key(wallet: String) = "scheduled_${wallet.replace(":", "_")}"

        private fun readEntries(prefs: android.content.SharedPreferences, wallet: String): List<KaPostScheduledEntry> {
            val raw = prefs.getString(key(wallet), null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<KaPostScheduledEntry>>() {}.type
                gson.fromJson<List<KaPostScheduledEntry>>(raw, type).orEmpty().sortedBy { it.notBeforeMs }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read scheduled posts", e)
                emptyList()
            }
        }

        /**
         * Delete Account: every scheduled post [address] holds on this phone, under each
         * network's form of the address, goes - its reminder taken down and its entry removed.
         */
        fun deleteAccount(context: Context, address: String) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val reminder = KaPostsScheduledReminder(appContext)
            for (wallet in com.kachat.app.util.KaspaNetwork.accountAddressVariants(address)) {
                readEntries(prefs, wallet).forEach { reminder.cancel(it.txId) }
                prefs.edit().remove(key(wallet)).apply()
                instance?.forget(wallet)
            }
        }

        /**
         * The coins no other transaction may spend right now - every scheduled post's inputs.
         * Read by [KaspaWalletEngine] before it selects inputs; written only here.
         */
        @Volatile
        var reservedOutpoints: Set<String> = emptySet()
            private set

        /** Drops every coin a scheduled post is waiting to spend. */
        fun filterReserved(utxos: List<UtxoEntry>): List<UtxoEntry> {
            val reserved = reservedOutpoints
            if (reserved.isEmpty()) return utxos
            return utxos.filterNot { "${it.outpoint.transactionId}:${it.outpoint.index}" in reserved }
        }
    }
}
