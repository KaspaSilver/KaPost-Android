package com.kachat.app.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which KaPosts have been probed for "is this a thread root" (a first reply page fetched to see
 * whether the author continued their own post), and which of them turned out to be one.
 *
 * Persisted across launches: relaunching the app and re-asking for every commented post in the
 * feed was pure waste. FIFO-capped so the store cannot grow without bound; the oldest claims fall
 * off and simply get probed again someday. Mirrors iOS KaPostsView.ThreadProbeClaims.
 *
 * A root is for good, but "no thread here" is not: the author can add a self-reply later, and a
 * thread's root is often probed before its second part is indexed. Kept for good, that answer hid
 * "View thread" until 2,048 newer claims pushed it out. It now expires
 * ([noThreadAnswerLifetimeMs]), soon for a new post, after a day at most (iOS 369794e).
 */
@Singleton
class KaPostsThreadProbeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kaposts_thread_probes", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val order = ArrayDeque<String>()
    private val claimed = HashSet<String>()
    private val roots = HashSet<String>()
    /** When each claim's "no thread" answer stops counting (epoch ms). */
    private val expiry = HashMap<String, Long>()
    private var loaded = false
    private var saveJob: Job? = null

    @Synchronized
    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        readArray(CLAIMED_KEY).forEach { id ->
            if (claimed.add(id)) order.addLast(id)
        }
        roots.addAll(readArray(ROOTS_KEY))
        try {
            val stored = JSONObject(prefs.getString(EXPIRY_KEY, null) ?: "{}")
            stored.keys().forEach { id -> expiry[id] = stored.optLong(id) }
        } catch (_: Exception) {}
    }

    private fun readArray(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, null) ?: return emptyList())
        (0 until array.length()).map { array.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    /** Whether [remoteId] was asked and its answer still holds: a root for good, "no thread"
     *  until it expires. A claim saved before answers expired has no time and is asked once more. */
    @Synchronized
    fun isSettled(remoteId: String): Boolean {
        loadIfNeeded()
        if (remoteId !in claimed) return false
        if (remoteId in roots) return true
        val until = expiry[remoteId] ?: return false
        return until > System.currentTimeMillis()
    }

    /**
     * Claims [remoteId] for a probe unless its answer still holds ([isSettled]): true when it is
     * now claimed. [answerLifetimeMs]: how long a "no thread" answer to this claim holds.
     */
    @Synchronized
    fun claim(remoteId: String, answerLifetimeMs: Long = DAY_MS): Boolean {
        if (isSettled(remoteId)) return false
        expiry[remoteId] = System.currentTimeMillis() + answerLifetimeMs
        if (!claimed.add(remoteId)) {
            scheduleSave()
            return true
        }
        order.addLast(remoteId)
        while (order.size > CAPACITY) {
            val dropped = order.removeFirst()
            claimed.remove(dropped)
            roots.remove(dropped)
            expiry.remove(dropped)
        }
        scheduleSave()
        return true
    }

    /** A probe that never got to ask its question should be asked again later. */
    @Synchronized
    fun unclaim(remoteId: String) {
        loadIfNeeded()
        expiry.remove(remoteId)
        if (!claimed.remove(remoteId)) return
        order.remove(remoteId)
        scheduleSave()
    }

    @Synchronized
    fun markRoot(remoteId: String) {
        loadIfNeeded()
        roots.add(remoteId)
        scheduleSave()
    }

    /** Persisted positive results - what the feed's thread-root flags start from. */
    @Synchronized
    fun persistedRoots(): Set<String> {
        loadIfNeeded()
        return roots.toSet()
    }

    /** One write per burst of claims, not one per revealed row. */
    private fun scheduleSave() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            delay(1_000)
            val (claimedSnapshot, rootsSnapshot, expirySnapshot) = synchronized(this@KaPostsThreadProbeStore) {
                Triple(order.toList(), roots.toList(), expiry.toMap())
            }
            prefs.edit()
                .putString(CLAIMED_KEY, JSONArray(claimedSnapshot).toString())
                .putString(ROOTS_KEY, JSONArray(rootsSnapshot).toString())
                .putString(EXPIRY_KEY, JSONObject(expirySnapshot).toString())
                .apply()
        }
    }

    companion object {
        private const val CLAIMED_KEY = "claimed"
        private const val ROOTS_KEY = "roots"
        private const val EXPIRY_KEY = "expiry"
        private const val CAPACITY = 2048
        private const val DAY_MS = 86_400_000L

        /** How long "no thread" holds for a post this old: as long as the post has existed, from
         *  10 minutes to a day. */
        fun noThreadAnswerLifetimeMs(postAgeMs: Long): Long = postAgeMs.coerceIn(600_000L, DAY_MS)
    }
}
