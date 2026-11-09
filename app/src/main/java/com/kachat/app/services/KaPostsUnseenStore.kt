package com.kachat.app.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unseen-KaPosts-activity count: what the notifications tab's badge shows.
 *
 * A COUNT, not a feed. The rows are already served by the KaPosts notifications screen straight
 * from the indexer, so storing them a second time would only be a cache that could disagree with
 * it. What cannot be derived from the indexer is how many the user has not looked at yet, which
 * is what this persists.
 *
 * Matches iOS's KaPostsNotificationCenter.
 */
@Singleton
class KaPostsUnseenStore @Inject constructor(
    @ApplicationContext context: Context,
    private val walletManager: WalletManager,
) {
    private val prefs = context.getSharedPreferences("kaposts_unseen", Context.MODE_PRIVATE)

    private val _unseenCount = MutableStateFlow(0)

    /** How many notifications have arrived since the user last opened the KaPosts bell. */
    val unseenCount: StateFlow<Int> = _unseenCount.asStateFlow()

    private var loadedWallet: String? = null

    private fun walletAddressOrNull(): String? =
        try { walletManager.getAddress() } catch (_: Exception) { null }

    private fun key(wallet: String) = "unseen_$wallet"

    /** Delete Account: one wallet's count, from this device (iOS 9e22075). */
    fun removeAllData(walletAddress: String) {
        prefs.edit().remove(key(walletAddress)).apply()
        if (loadedWallet == walletAddress) {
            loadedWallet = null
            _unseenCount.value = 0
        }
    }

    /** Loads the active wallet's count; call before reading and on account switches. */
    @Synchronized
    fun reloadIfNeeded() {
        val wallet = walletAddressOrNull() ?: return
        if (loadedWallet == wallet) return
        loadedWallet = wallet
        _unseenCount.value = prefs.getInt(key(wallet), 0).coerceAtLeast(0)
    }

    /**
     * Adds newly-arrived activity to the count. [KaPostsNotificationPoller] calls it with the
     * wallet's own actions and hidden people already taken out.
     */
    @Synchronized
    fun recordArrivals(count: Int) {
        if (count <= 0) return
        reloadIfNeeded()
        val wallet = loadedWallet ?: return
        _unseenCount.value += count
        lastArrivalAt = System.currentTimeMillis()
        prefs.edit().putInt(key(wallet), _unseenCount.value).apply()
    }

    /** When activity last arrived (this run, epoch ms, 0 = none): the notifications screen
     *  reloads when it is newer than its own last load (iOS 33095ac). */
    @Volatile
    var lastArrivalAt: Long = 0L
        private set

    /** The user has opened the KaPosts notifications screen; nothing is unseen any more. */
    @Synchronized
    fun markAllSeen() {
        reloadIfNeeded()
        val wallet = loadedWallet ?: return
        if (_unseenCount.value == 0) return
        _unseenCount.value = 0
        prefs.edit().putInt(key(wallet), 0).apply()
    }

    /**
     * Capped in the label rather than in the stored value, so the real number survives a long
     * absence and only its rendering is abbreviated.
     */
    fun badgeText(count: Int): String = if (count > 99) "99+" else count.toString()
}
