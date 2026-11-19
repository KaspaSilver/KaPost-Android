package com.kachat.app.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the follow sync keeps between runs, per account (iOS 369794e `KaPostsFollowStore`'s
 * `kachat_kaposts_following_chain_` and `kachat_kaposts_following_pending_` keys):
 *
 * - the follow list the last complete chain read gave: an address it listed that the next one
 *   doesn't was unfollowed elsewhere (another device, KaChat);
 * - follows that couldn't go on chain: no post names the person's key yet, and Follow never signs
 *   a guessed 02/03 (IOS-099). Each chain sync tries them again.
 */
@Singleton
class KaPostsFollowSyncStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The last complete chain read's follow list, or null before the first one. */
    @Synchronized
    fun lastChainList(wallet: String): Set<String>? =
        prefs.getStringSet(chainKey(wallet), null)?.toSet()

    @Synchronized
    fun setLastChainList(wallet: String, addresses: Set<String>) {
        prefs.edit().putStringSet(chainKey(wallet), addresses.toSet()).apply()
    }

    /** Follows waiting for the person's key. */
    @Synchronized
    fun pendingFollows(wallet: String): Set<String> =
        prefs.getStringSet(pendingKey(wallet), null)?.toSet() ?: emptySet()

    /** Marks a follow as waiting for the person's key ([following]), or no longer waiting. */
    @Synchronized
    fun setAwaitingKey(wallet: String, address: String, following: Boolean) {
        val pending = pendingFollows(wallet).toMutableSet()
        val changed = if (following) pending.add(address) else pending.remove(address)
        if (!changed) return
        prefs.edit().putStringSet(pendingKey(wallet), pending).apply()
    }

    companion object {
        private const val PREFS_NAME = "kaposts_follow_sync"

        private fun normalized(wallet: String) = wallet.trim().lowercase()
        private fun chainKey(wallet: String) = "kachat_kaposts_following_chain_${normalized(wallet)}"
        private fun pendingKey(wallet: String) = "kachat_kaposts_following_pending_${normalized(wallet)}"

        /** Delete Account: one wallet's follow sync state, from this device. */
        fun removeAllData(context: Context, wallet: String) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(chainKey(wallet))
                .remove(pendingKey(wallet))
                .apply()
        }
    }
}
