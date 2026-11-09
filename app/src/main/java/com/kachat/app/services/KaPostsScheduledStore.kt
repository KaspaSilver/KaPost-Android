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

    /** Posts being handed to the indexer, submitted or cancelled right now. Whichever started
     *  first decides, so a cancel can't race a submit and a second pass can't submit again (iOS
     *  ee5ec98). */
    private val inFlight: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

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
        inFlight.add(entry.txId)
        try {
            kaPostsService.get().scheduleOnServer(entry.txId, entry.transaction, notBeforeMs)
            entry = entry.copy(onServer = true)
            update(entry, wallet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Scheduling on the indexer failed, keeping it on the phone", e)
            reminder.schedule(entry)
        } finally {
            inFlight.remove(entry.txId)
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

    /** Cancels a post that has not gone out: told to the indexer if it holds it, then dropped
     *  here and its coins released. Returns what to tell the person when it couldn't be, with
     *  the post still scheduled: an indexer that didn't answer will still send it (iOS ee5ec98). */
    @androidx.annotation.StringRes
    suspend fun cancel(entry: KaPostScheduledEntry): Int? {
        if (!inFlight.add(entry.txId)) return com.kachat.app.R.string.kaposts_scheduled_on_its_way
        try {
            val current = _entries.value.firstOrNull { it.txId == entry.txId } ?: entry
            if (current.onServer && current.status == KaPostScheduledEntry.STATUS_SCHEDULED) {
                try {
                    kaPostsService.get().cancelScheduledOnServer(entry.txId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Cancelling a scheduled post failed", e)
                    return com.kachat.app.R.string.kaposts_scheduled_cancel_unreachable
                }
            }
            persist(_entries.value.filterNot { it.txId == entry.txId })
            reminder.cancel(entry.txId)
            return null
        } finally {
            inFlight.remove(entry.txId)
        }
    }

    /** Forgets a submitted/failed row - nothing is reserved by it any more. */
    fun remove(entry: KaPostScheduledEntry) {
        persist(_entries.value.filterNot { it.txId == entry.txId })
    }

    /**
     * Submits, from this phone, every post whose time has come and that the indexer isn't going
     * to send, and tries the indexer again for the ones still in the future. Called when the app
     * comes to the front, and from the background (the reminder's job, [SyncWorker]).
     *
     * [runningReminderTxId] is the post whose reminder job is making this call: cancelling that
     * job's unique work would cancel this very run, so its reminder is left to finish by itself.
     */
    suspend fun sendDueLocally(runningReminderTxId: String? = null) = sendDueMutex.withLock {
        // nothing goes out for an account behind the sign-in screen
        if (walletManager.isLoggedOut) return@withLock
        reloadIfNeeded()
        // What the indexer did with posts past their time comes first; with no answer from it,
        // the phone sends them (iOS ee5ec98).
        val now = System.currentTimeMillis()
        if (_entries.value.any { it.onServer && it.status == KaPostScheduledEntry.STATUS_SCHEDULED && it.notBeforeMs <= now }) {
            pullServerOutcomes()
        }
        submitDue(runningReminderTxId)
    }

    /** Runs under [sendDueMutex]. */
    private suspend fun submitDue(runningReminderTxId: String?) {
        // The wallet these entries belong to: every outcome is written back to it, even if the
        // account changes while a submission is out.
        val wallet = loadedWallet ?: return
        val now = System.currentTimeMillis()
        // Each entry read fresh: a run that waited on the lock must not submit what the run
        // before it already sent.
        for (snapshot in _entries.value) {
            // A cancelled run stops between posts; the next run sends the rest.
            currentCoroutineContext().ensureActive()
            val entry = (if (wallet == loadedWallet) _entries.value else read(wallet))
                .firstOrNull { it.txId == snapshot.txId } ?: continue
            if (entry.status != KaPostScheduledEntry.STATUS_SCHEDULED) continue
            // The indexer's to send until it has had its tick. After that an indexer that's
            // down would strand the post, so the phone sends the same signed transaction.
            if (entry.onServer && entry.notBeforeMs + INDEXER_GRACE_MS > now) continue
            if (!inFlight.add(entry.txId)) continue
            try {
                if (entry.notBeforeMs <= now) {
                    submitNow(entry, wallet, runningReminderTxId)
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
            } finally {
                inFlight.remove(entry.txId)
            }
        }
    }

    /**
     * A submission that started always records how it ended: cancelled halfway, the post would
     * stay "scheduled" with its coins held, or - caught as a failure - be marked failed with its
     * coins released after it had already reached the node.
     */
    private suspend fun submitNow(entry: KaPostScheduledEntry, wallet: String, runningReminderTxId: String?) {
        withContext(NonCancellable) {
            try {
                val txId = kaPostsService.get().submitScheduledLocally(entry.transaction)
                Log.i(TAG, "Scheduled post submitted from the phone: ${txId.take(12)}")
                update(entry.copy(status = KaPostScheduledEntry.STATUS_SUBMITTED, submittedAtMs = System.currentTimeMillis()), wallet)
            } catch (e: CancellationException) {
                // Not a failure (a timeout inside the submission): it stays scheduled,
                // coins held, for the next run.
                throw e
            } catch (e: NodeRejectedTransactionException) {
                Log.w(TAG, "Scheduled post rejected", e)
                update(entry.copy(status = KaPostScheduledEntry.STATUS_FAILED, error = e.message), wallet)
            } catch (e: Exception) {
                // Offline, still connecting, or no answer: it stays scheduled, its coins held,
                // and goes again next time. The same signed transaction can't pay twice (iOS ee5ec98).
                Log.w(TAG, "Scheduled post not sent yet", e)
                return@withContext
            }
            // Sent or rejected, it no longer waits on the phone (iOS KaPostsScheduledStore.swift).
            if (entry.txId != runningReminderTxId) reminder.cancel(entry.txId)
        }
    }

    /** One pass at a time: two at once (opening the list from the menu ran it twice) could
     *  submit an entry twice, or mark a just-submitted one failed. */
    private val sendDueMutex = kotlinx.coroutines.sync.Mutex()

    /** The indexer's view of this wallet's scheduled posts, then whatever is left for the phone. */
    suspend fun refreshFromServer(): Unit = sendDueMutex.withLock {
        reloadIfNeeded()
        pullServerOutcomes()
        if (!walletManager.isLoggedOut) submitDue(null)
    }

    /**
     * Submitted and failed outcomes land here. A post past its time that the indexer no longer
     * knows turns local, so [submitDue] settles it: on chain, or rejected and its coins freed.
     */
    private suspend fun pullServerOutcomes() {
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
        val byId = remote.associateBy { it.txId }
        val now = System.currentTimeMillis()
        var changed = false
        val updated = _entries.value.map { entry ->
            if (!entry.onServer || entry.txId in inFlight) return@map entry
            val server = byId[entry.txId]
            if (server == null) {
                if (entry.status == KaPostScheduledEntry.STATUS_SCHEDULED && entry.notBeforeMs <= now) {
                    changed = true
                    return@map entry.copy(onServer = false)
                }
                return@map entry
            }
            val status = server.status?.takeIf { it in KNOWN_STATUSES } ?: entry.status
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
        /** How long past its time a post is left to the indexer (it ticks once a minute, section
         *  5.10) before this phone sends it (iOS ee5ec98). */
        private const val INDEXER_GRACE_MS = 150_000L
        private val KNOWN_STATUSES = setOf(
            KaPostScheduledEntry.STATUS_SCHEDULED, KaPostScheduledEntry.STATUS_SUBMITTED,
            KaPostScheduledEntry.STATUS_FAILED, KaPostScheduledEntry.STATUS_CANCELLED,
        )
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
