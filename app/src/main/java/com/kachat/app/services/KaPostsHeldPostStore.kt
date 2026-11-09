package com.kachat.app.services

import android.content.Context
import com.google.gson.Gson
import com.kachat.app.ui.screens.KaPostDraftStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The words of a post that isn't on chain yet: held behind the 5-second Undo, going out, or
 * failed (iOS ee5ec98 `KaPostsHeldPostStore`). Those cards live only in memory, so this keeps
 * their text until the post lands. While the app runs the card is the way back (Undo, Retry), so
 * nothing shows anywhere; what a kill leaves behind comes back as an ordinary draft on the next
 * launch ([recover]).
 */
@Singleton
class KaPostsHeldPostStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletManager: WalletManager,
) {
    private data class Entry(
        val id: String,
        val text: String,
        val threadSegments: List<String>,
        val quotedRemoteId: String?,
        val replyRemoteId: String?,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The posts this run is looking after, with the account each was written as: its entry
     *  stays under that account even after a switch. */
    private val active = mutableMapOf<String, String>()

    private fun currentAddress(): String? =
        try { walletManager.getAddress().takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    private fun currentKey(): String? = currentAddress()?.let { key(it) }

    private fun load(key: String): List<Entry> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            gson.fromJson(raw, Array<Entry>::class.java)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun store(entries: List<Entry>, key: String) {
        if (entries.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, gson.toJson(entries)).apply()
        }
    }

    /** Starts keeping a post's text, or replaces what's kept (a thread as it goes out). */
    @Synchronized
    fun hold(
        id: String,
        text: String,
        threadSegments: List<String> = emptyList(),
        quotedRemoteId: String? = null,
        replyRemoteId: String? = null,
    ) {
        val key = active[id] ?: currentKey() ?: return
        active[id] = key
        val entries = load(key).filterNot { it.id == id } +
            Entry(id, text, threadSegments, quotedRemoteId, replyRemoteId)
        store(entries, key)
    }

    /** A thread's unposted segments, held the way the composer holds them: the last one is the
     *  text, the others are stacked above it. */
    fun holdThread(id: String, segments: List<String>, replyRemoteId: String? = null) {
        val last = segments.lastOrNull() ?: return
        hold(id, last, threadSegments = segments.dropLast(1), replyRemoteId = replyRemoteId)
    }

    /** The post landed, or was taken back: nothing left to keep. */
    @Synchronized
    fun release(id: String) {
        val key = active.remove(id) ?: return
        store(load(key).filterNot { it.id == id }, key)
    }

    /** Hands one kept post to the Drafts screen now. Stays kept if its account isn't the open one. */
    @Synchronized
    fun saveAsDraft(id: String) {
        val key = active[id] ?: return
        val address = currentAddress() ?: return
        if (key != key(address)) return
        val entry = load(key).firstOrNull { it.id == id } ?: return
        saveDraft(entry, address)
        release(id)
    }

    /** What an earlier run left behind becomes drafts. Posts this run still has on screen stay. */
    @Synchronized
    fun recover() {
        val address = currentAddress() ?: return
        val key = key(address)
        val entries = load(key)
        val leftovers = entries.filter { it.id !in active }
        if (leftovers.isEmpty()) return
        leftovers.forEach { saveDraft(it, address) }
        store(entries.filter { it.id in active }, key)
    }

    private fun saveDraft(entry: Entry, address: String) {
        KaPostDraftStore.save(
            context, address, entry.id, entry.text, entry.threadSegments,
            replyRemoteId = entry.replyRemoteId,
            quotedRemoteId = entry.quotedRemoteId,
        )
    }

    companion object {
        private const val PREFS_NAME = "kaposts_held_posts"
        private val gson = Gson()

        private fun key(address: String): String =
            "kachat_kaposts_held_" + address.trim().lowercase()

        /** Delete Account: one wallet's kept posts, from this device. */
        fun removeAllData(context: Context, walletAddress: String) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(key(walletAddress)).apply()
        }
    }
}
