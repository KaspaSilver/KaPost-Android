package com.kachat.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.stream.MalformedJsonException
import com.kachat.app.services.grpc.KaspadConnection
import com.kachat.app.util.KaPostsProtocol
import com.kachat.app.util.KaspaMessageSigner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import protowire.Rpc
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

// KaPost with the indexer down (iOS ee5ec98). The indexer is what lists posts; the posts themselves
// are on chain. So while it doesn't answer: every read shows the page it last got
// ([KaPostsPageCache]), a person's posts come from Kaspa REST ([KaPostsChainPosts]), new posts come
// off the node's block stream ([KaPostsLiveChainFeed]), and it all goes back to the indexer by
// itself when it answers again ([KaPostsIndexerHealth]). Writes never needed it.

/**
 * Whether the KaPost indexer is answering. Every read and write reports here. One failed request
 * is a phone's bad moment, not an outage: it takes [FAILURES_TO_GO_DOWN] in a row inside
 * [FAILURE_WINDOW_MS], and any answer resets the count (the same rule as KaChat). Until then the
 * indexer stays the only source. While it's down one small request a minute looks for its return.
 */
@Singleton
class KaPostsIndexerHealth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liveFeed: KaPostsLiveChainFeed,
    /** Lazy: the service reports here, and is what asks the indexer. */
    private val service: Provider<KaPostsService>,
    private val notificationHelper: NotificationHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("KaPostsIndexerHealth"))

    private val _isDown = MutableStateFlow(false)
    val isDown: StateFlow<Boolean> = _isDown.asStateFlow()

    private val _availabilityChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /** [isDown] changed (iOS `.kaPostsIndexerAvailabilityChanged`). */
    val availabilityChanged: SharedFlow<Unit> = _availabilityChanged

    private var probeJob: Job? = null
    private var failuresInARow = 0
    private var lastFailureAt = 0L
    /** Probes answered in a row while down, with no failed read between them (iOS 369794e). The
     *  probe is one tiny read: in a partial outage it can pass while real pages still fail, and
     *  ending the outage on one pass reloaded everything, failed three times and went down again
     *  about once a minute. Two passes in a row with no failure between (or one real read that
     *  succeeds, [noteAnswered]) end it. */
    private var probePassesInARow = 0

    /** A request that failed: counts against the indexer only if it says so ([meansDown]). */
    fun note(error: Throwable) {
        if (meansDown(error)) noteFailure()
    }

    @Synchronized
    fun noteFailure() {
        // A read that fails while down breaks the probe's run, even inside the spacing below.
        probePassesInARow = 0
        val now = System.currentTimeMillis()
        if (now - lastFailureAt < MIN_FAILURE_SPACING_MS) return
        if (now - lastFailureAt > FAILURE_WINDOW_MS) failuresInARow = 0
        lastFailureAt = now
        failuresInARow += 1
        if (failuresInARow >= FAILURES_TO_GO_DOWN) markDown()
    }

    /** A body that wouldn't decode: the indexer did answer, so no outage, and no failure run. */
    @Synchronized
    fun noteUnreadableAnswer() {
        failuresInARow = 0
    }

    /** The indexer gave an answer of its own, even an error about the request. */
    @Synchronized
    fun noteAnswered() {
        failuresInARow = 0
        if (!_isDown.value) return
        _isDown.value = false
        probeJob?.cancel()
        probeJob = null
        Log.i(TAG, "The indexer is answering again")
        liveFeed.setActive(false)
        _availabilityChanged.tryEmit(Unit)
    }

    private fun markDown() {
        if (_isDown.value) return
        _isDown.value = true
        probePassesInARow = 0
        Log.i(TAG, "The indexer isn't answering: saved pages and the chain from here")
        liveFeed.setActive(true)
        _availabilityChanged.tryEmit(Unit)
        probeJob = scope.launch {
            while (isActive) {
                delay(PROBE_INTERVAL_MS)
                if (!notificationHelper.isAppInForeground) continue
                val passed = service.get().probeIndexer()
                if (!isActive || !_isDown.value) return@launch
                if (!passed) {
                    synchronized(this@KaPostsIndexerHealth) { probePassesInARow = 0 }
                    continue
                }
                val recovered = synchronized(this@KaPostsIndexerHealth) {
                    probePassesInARow += 1
                    probePassesInARow >= PROBE_PASSES_TO_RECOVER
                }
                if (recovered) noteAnswered()
            }
        }
    }

    /** A phone that's offline says nothing about the indexer, and neither does a cancelled request. */
    fun meansDown(error: Throwable): Boolean =
        error is IOException && !isUnreadableBody(error) && hasConnection()

    /** The phone has no connection: nothing could answer, so a saved page takes nothing away from
     *  the indexer. */
    fun meansOffline(error: Throwable): Boolean =
        error is IOException && !isUnreadableBody(error) && !hasConnection()

    private fun hasConnection(): Boolean = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager?.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }

    companion object {
        private const val TAG = "KaPostsIndexerHealth"
        private const val PROBE_INTERVAL_MS = 60_000L
        private const val FAILURES_TO_GO_DOWN = 3
        private const val PROBE_PASSES_TO_RECOVER = 2
        /** Failures further apart than this aren't a run. */
        private const val FAILURE_WINDOW_MS = 120_000L
        /** A network handoff fails every request in flight at once: that's one failure, not three. */
        private const val MIN_FAILURE_SPACING_MS = 5_000L

        fun meansDown(statusCode: Int): Boolean = statusCode == 429 || statusCode >= 500

        /** Retrofit reads the body as it decodes, so a body that won't decode surfaces here. */
        fun isUnreadableBody(error: Throwable): Boolean =
            error is JsonParseException || error is MalformedJsonException || error is EOFException
    }
}

/**
 * The last first page of each read, on disk, for when the indexer can't answer. Keyed by the
 * whole request, so each account has its own (its key is in the query). Only reads that show
 * content: a saved list of scheduled posts could offer to cancel one that already went out.
 */
object KaPostsPageCache {
    private val CACHED_PATHS = setOf(
        "get-posts-watching", "get-contents-following", "get-posts", "get-replies",
        "get-post", "get-thread", "get-notifications", "get-user-details",
    )
    private const val LIMIT = 200
    private val queue = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kaposts-pagecache").apply { isDaemon = true }
    }
    private val queueDispatcher = queue.asCoroutineDispatcher()

    private fun directory(context: Context) = File(context.cacheDir, "KaPostsPages")

    /** Null for what isn't kept: a later page (its cursor is only good with the indexer) or a
     *  read not in the list. */
    fun key(base: String, path: String, query: Map<String, String>): String? {
        if (path !in CACHED_PATHS || query.containsKey("before")) return null
        val canonical = base + "|" + path + "|" +
            query.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** [encode] runs on the cache's own queue: a page's JSON is too much work for Main. */
    fun save(context: Context, key: String, encode: () -> ByteArray) {
        val directory = directory(context)
        queue.execute {
            try {
                val data = encode()
                directory.mkdirs()
                val temp = File(directory, "$key.tmp")
                temp.writeBytes(data)
                if (!temp.renameTo(File(directory, key))) temp.delete()
                prune(directory)
            } catch (e: Exception) {
                Log.w("KaPostsPageCache", "Could not save a page", e)
            }
        }
    }

    suspend fun load(context: Context, key: String): ByteArray? {
        val file = File(directory(context), key)
        return withContext(queueDispatcher) {
            try { if (file.isFile) file.readBytes() else null } catch (_: Exception) { null }
        }
    }

    /** Delete Account: nothing an account read stays behind. */
    fun removeAll(context: Context) {
        val directory = directory(context)
        queue.execute { directory.deleteRecursively() }
    }

    private fun prune(directory: File) {
        val files = directory.listFiles() ?: return
        if (files.size <= LIMIT) return
        files.sortedBy { it.lastModified() }.take(files.size - LIMIT).forEach { it.delete() }
    }
}

/**
 * Posts read straight from chain data. A payload can name anyone as its author, so one counts
 * only if its signature verifies against the key it names, the same check the indexer makes.
 */
@Singleton
class KaPostsChainPosts @Inject constructor(
    private val chainReader: KaPostChainReader,
) {
    /** A verified post and the signature that proved it: a signed payload can be put on chain
     *  again by anyone, as a new transaction with a new id, so repeats are found by this. */
    data class VerifiedPost(val post: KPost, val signature: String) {
        /** Pubkey and signature: the same for every copy of one signed payload. */
        val repeatKey: String get() = xOnly(post.userPublicKey) + ":" + signature.lowercase()
    }

    /** A verified `delete:` or `edit:` record. It counts only on a post by the key that signed
     *  it: the indexer applies these the same way, and without them a deleted post comes back for
     *  as long as it's down. */
    sealed class Amendment {
        data class Delete(val postId: String) : Amendment()
        data class Edit(val postId: String, val b64: String, val mentions: List<String>, val timestampMs: Long) : Amendment()
    }

    /** A delete or edit and the key that signed it ([authorPubkey]). */
    data class SignedAmendment(val authorPubkey: String, val amendment: Amendment)

    private data class LatestEdit(val b64: String, val mentions: List<String>, val timestampMs: Long)

    /**
     * One person's posts and replies from Kaspa REST, newest first (a node keeps no history by
     * address). Null when REST didn't answer. Off Main: a Schnorr verify for each of up to 200
     * transactions, for up to 20 people at a time.
     */
    suspend fun posts(ofPubkey: String): List<KPost>? = withContext(Dispatchers.Default) { postsOnChain(ofPubkey) }

    private suspend fun postsOnChain(ofPubkey: String): List<KPost>? {
        val address = KaPostsService.kaspaAddressFromPubkey(ofPubkey) ?: return null
        val found = mutableListOf<VerifiedPost>()
        val deleted = mutableSetOf<String>()
        val latestEdits = mutableMapOf<String, LatestEdit>()
        for (page in 0 until PAGES_PER_PERSON) {
            val list = chainReader.addressPayloads(address, PAGE_SIZE, page * PAGE_SIZE)
                ?: return if (page == 0) null else applying(deleted, latestEdits, found)
            for (tx in list) {
                if (tx.isAccepted == false) continue
                val hex = tx.payload ?: continue
                val payload = hexToUtf8(hex) ?: continue
                val timestampMs = tx.blockTime ?: 0L
                when (val amendment = verifiedAmendment(payload, timestampMs, ofPubkey)) {
                    is Amendment.Delete -> deleted += amendment.postId
                    is Amendment.Edit -> {
                        if (amendment.timestampMs >= (latestEdits[amendment.postId]?.timestampMs ?: Long.MIN_VALUE)) {
                            latestEdits[amendment.postId] = LatestEdit(amendment.b64, amendment.mentions, amendment.timestampMs)
                        }
                    }
                    null -> {
                        val verified = verifiedPost(payload, tx.transactionId, timestampMs) ?: continue
                        if (xOnly(verified.post.userPublicKey) != xOnly(ofPubkey)) continue
                        found += verified
                    }
                }
            }
            if (list.size < PAGE_SIZE) break
        }
        return applying(deleted, latestEdits, found)
    }

    /** What's left of a person's posts: the first copy of each signed payload, deleted ones gone,
     *  edits made within the indexer's window in place. Newest first. */
    private fun applying(
        deleted: Set<String>,
        latestEdits: Map<String, LatestEdit>,
        found: List<VerifiedPost>,
    ): List<KPost> {
        val seen = HashSet<String>()
        return found.sortedBy { it.post.timestamp }
            .filter { seen.add(it.repeatKey) && it.post.id !in deleted }
            .map { verified ->
                val post = verified.post
                val edit = latestEdits[post.id]
                if (edit == null || !editApplies(post, edit.timestampMs)) {
                    post
                } else {
                    edited(post, edit.b64, edit.mentions, edit.timestampMs)
                }
            }
            .sortedByDescending { it.timestamp }
    }

    private fun verifiedAmendment(payload: String, timestampMs: Long, ofPubkey: String): Amendment? =
        signedAmendment(payload, timestampMs, onlyBy = ofPubkey)?.amendment

    companion object {
        private const val PAGES_PER_PERSON = 2
        private const val PAGE_SIZE = 100
        private val COMPRESSED_PUBKEY = Regex("^[0-9a-fA-F]{66}$")
        private val HEX = Regex("^[0-9a-fA-F]*$")

        /** The lowercased x-only part of a 02/03 key. Addresses only give x, and a real post
         *  carries true parity, so keys are compared on this. */
        fun xOnly(pubkey: String): String = pubkey.takeLast(64).lowercase()

        /**
         * A delete or edit and the key that signed it, whoever that is: the block stream doesn't
         * know a post's author until it is matched ([KaPostsLiveChainFeed]). [onlyBy] skips the
         * signature check for anyone else's.
         */
        fun signedAmendment(payload: String, timestampMs: Long, onlyBy: String? = null): SignedAmendment? {
            if (!payload.startsWith(KaPostsProtocol.PREFIX)) return null
            val fields = payload.removePrefix(KaPostsProtocol.PREFIX).split(":")
            if (fields.size < 4) return null
            if (onlyBy != null && xOnly(fields[1]) != xOnly(onlyBy)) return null
            return when (fields[0]) {
                "delete" -> {
                    if (!signatureIsValid(fields[2], KaPostsProtocol.deleteSigningString(fields[3]), fields[1])) return null
                    SignedAmendment(fields[1], Amendment.Delete(fields[3]))
                }
                "edit" -> {
                    if (fields.size < 6) return null
                    val mentions = decodedMentions(fields[5]) ?: return null
                    val text = decodedText(fields[4]) ?: return null
                    if (!KaPostsProtocol.isKaChatContent(text) || KaPostsProtocol.stripMarker(text).isBlank()) return null
                    if (!signatureIsValid(fields[2], KaPostsProtocol.editSigningString(fields[3], fields[4], fields[5]), fields[1])) return null
                    SignedAmendment(fields[1], Amendment.Edit(fields[3], fields[4], mentions, timestampMs))
                }
                else -> null
            }
        }

        /** Whether an edit made at [editedAtMs] counts on [post]: within the indexer's edit
         *  window, and never on a poll (section 5.9: its options are what people voted on). */
        fun editApplies(post: KPost, editedAtMs: Long): Boolean =
            post.contentType != "poll" && post.poll == null &&
                editedAtMs >= post.timestamp &&
                editedAtMs - post.timestamp <= KaPostsService.EDIT_WINDOW_MS

        /** [post] as a later edit left it. */
        fun edited(post: KPost, b64: String, mentions: List<String>, atMs: Long): KPost =
            post.copy(postContent = b64, mentionedPubkeys = mentions, editedAt = atMs)

        /**
         * A post, reply, quote or poll from a decoded payload, or null for anything else: another
         * action, another app's post (no KaChat marker), a repost with no text, or a bad signature.
         */
        fun verifiedPost(payload: String, txId: String, timestampMs: Long): VerifiedPost? {
            if (!payload.startsWith(KaPostsProtocol.PREFIX)) return null
            val fields = payload.removePrefix(KaPostsProtocol.PREFIX).split(":")
            if (fields.size < 5) return null
            val action = fields[0]
            val pubkey = fields[1]
            val signature = fields[2]
            val b64: String
            val signingString: String
            var parent: String? = null
            var mentionsJson = "[]"
            var poll: KPoll? = null
            when (action) {
                "post" -> {
                    b64 = fields[3]
                    mentionsJson = fields[4]
                    signingString = KaPostsProtocol.postSigningString(b64, mentionsJson)
                }
                "poll" -> {
                    // A poll is a post whose text is the question (KAPOSTS_INDEXER.md section 5.9).
                    // The votes are counted by the indexer only, so from the chain it shows its
                    // options and closing time with nothing counted yet; a vote cast meanwhile
                    // still goes on chain (iOS 369794e).
                    if (fields.size < 7) return null
                    val closesAtMs = fields[5].toLongOrNull() ?: return null
                    val options = fields[4].split(",").filter { it.isNotEmpty() }
                    if (options.size !in 2..4 || decodedMentions(fields[6]) == null) return null
                    b64 = fields[3]
                    mentionsJson = fields[6]
                    signingString = KaPostsProtocol.pollSigningString(b64, fields[4], closesAtMs, mentionsJson)
                    poll = KPoll(options = options, counts = null, total = null, closesAt = closesAtMs, myVote = null)
                }
                "reply" -> {
                    if (fields.size < 6) return null
                    b64 = fields[4]
                    parent = fields[3]
                    mentionsJson = fields[5]
                    if (decodedMentions(mentionsJson) == null) return null
                    signingString = KaPostsProtocol.replySigningString(fields[3], b64, mentionsJson)
                }
                "quote" -> {
                    if (fields.size < 6 || !fields[5].matches(COMPRESSED_PUBKEY)) return null
                    b64 = fields[4]
                    signingString = KaPostsProtocol.quoteSigningString(fields[3], b64, fields[5])
                }
                else -> return null
            }
            val text = decodedText(b64) ?: return null
            if (!KaPostsProtocol.isKaChatContent(text) || KaPostsProtocol.stripMarker(text).isBlank()) return null
            if (!signatureIsValid(signature, signingString, pubkey)) return null
            val post = KPost(
                id = txId,
                userPublicKey = pubkey,
                postContent = b64,
                timestamp = timestampMs,
                repliesCount = null,
                quotesCount = null,
                upVotesCount = null,
                downVotesCount = null,
                tipsCount = null,
                parentPostId = parent,
                mentionedPubkeys = decodedMentions(mentionsJson) ?: emptyList(),
                isUpvoted = null,
                isDownvoted = null,
                blockedUser = null,
                contentType = action,
                isQuote = action == "quote",
                quote = null,
                editedAt = null,
                poll = poll,
            )
            return VerifiedPost(post, signature)
        }

        /** The real check: BIP-340 Schnorr over the keyed Blake2b of what the action signs. Also
         *  what a post opened by link is held to ([KaPostsProtocol.parseChainPayload]). */
        fun signatureIsValid(signature: String, signingString: String, pubkey: String): Boolean {
            if (pubkey.length != 66) return false
            val key = decodeHex(pubkey.substring(2)) ?: return false
            if (key.size != 32) return false
            val signatureBytes = decodeHex(signature) ?: return false
            if (signatureBytes.size != 64) return false
            return KaspaMessageSigner.verifyPersonalMessage(signingString, signatureBytes, key)
        }

        private fun decodedMentions(json: String): List<String>? {
            val element = try { JsonParser.parseString(json) } catch (_: Exception) { return null }
            if (!element.isJsonArray) return null
            val mentions = mutableListOf<String>()
            for (item in element.asJsonArray) {
                if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) return null
                mentions += item.asString
            }
            return mentions
        }

        /** Base64 to text; null for bytes that aren't UTF-8. */
        private fun decodedText(b64: String): String? = try {
            strictUtf8(Base64.getDecoder().decode(b64))
        } catch (_: IllegalArgumentException) {
            null
        }

        fun hexToUtf8(hex: String): String? = decodeHex(hex.trim())?.let { strictUtf8(it) }

        private fun strictUtf8(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

        private fun decodeHex(hex: String): ByteArray? {
            if (hex.length % 2 != 0 || !hex.matches(HEX)) return null
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

/**
 * New posts off the node's block stream while the indexer is down: what the feed can still get
 * live. Only while it's down, since streaming every block costs data the indexer otherwise saves,
 * and only while the app is in front (iOS suspends the app instead). Its own owner on the same
 * connection [BroadcastScanningService] streams from, so the two never stop each other's blocks.
 */
@Singleton
class KaPostsLiveChainFeed @Inject constructor(
    private val nodePoolManager: NodePoolManager,
    private val notificationHelper: NotificationHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + com.kachat.app.util.StartupSafety.logOnlyHandler("KaPostsLiveChainFeed"))

    private val _posts = MutableStateFlow<List<KPost>>(emptyList())
    /** Newest first. */
    val posts: StateFlow<List<KPost>> = _posts.asStateFlow()

    /** A delete ([edit] null) or the latest edit of one post by one key (iOS 369794e). */
    data class LiveAmendment(val edit: LiveEdit?, val seenAtMs: Long)

    data class LiveEdit(val b64: String, val mentions: List<String>, val timestampMs: Long)

    /** What one block held for this feed. */
    private class Scan {
        val posts = mutableListOf<KaPostsChainPosts.VerifiedPost>()
        val amendments = mutableListOf<Pair<String, LiveAmendment>>()
    }

    private val _amendments = MutableStateFlow<Map<String, LiveAmendment>>(emptyMap())
    /** Deletes and edits seen on the stream during the outage, keyed by [amendmentKey] (post id
     *  and the x-only key that signed it): one counts only on a post by that key, as on the
     *  indexer, so a stranger's signed "delete" of someone else's post is never matched. */
    val amendments: StateFlow<Map<String, LiveAmendment>> = _amendments.asStateFlow()

    private var active = false
    /** Every copy of a signed payload after the first is a replay ([KaPostsChainPosts.VerifiedPost.repeatKey]).
     *  Capped, oldest out first ([seenOrder]): during a long outage it grew by every post streamed
     *  while [posts] stayed at [KEEP]. The cap is far past [KEEP], so a replay is only missed for
     *  a post long gone from the stream. */
    private val seenPayloads = HashSet<String>()
    private val seenOrder = ArrayDeque<String>()
    private var streamJob: Job? = null

    init {
        scope.launch { notificationHelper.appForegroundFlow.collect { reevaluate() } }
    }

    @Synchronized
    fun setActive(isActive: Boolean) {
        if (isActive == active) return
        active = isActive
        if (!isActive) {
            // The indexer has these now.
            _posts.value = emptyList()
            _amendments.value = emptyMap()
            seenPayloads.clear()
            seenOrder.clear()
        }
        reevaluate()
    }

    @Synchronized
    private fun reevaluate() {
        if (active && notificationHelper.isAppInForeground) start() else stop()
    }

    private fun start() {
        if (streamJob?.isActive == true) return
        // A stopped job may still be sending its NOTIFY_STOP: this one waits, so that stop can't
        // land after this START and silence it.
        val previous = streamJob
        streamJob = scope.launch {
            previous?.join()
            while (isActive) {
                var conn: KaspadConnection? = null
                try {
                    conn = nodePoolManager.getBroadcastConnection()
                    // Captured BEFORE subscribing, as BroadcastScanningService does: a death or
                    // reconnect racing the NOTIFY_START must read as "generation moved" below.
                    val subscribedGeneration = conn.connectionGeneration.value
                    // `conn` is set before subscribing: a stop mid-subscribe still takes the owner
                    // off, in the finally below.
                    val blocks = conn.subscribeToBlockAdded(owner = CLIENT)
                    coroutineScope {
                        val collector = launch {
                            blocks.collect { block ->
                                val scan = extract(block)
                                if (scan.posts.isNotEmpty() || scan.amendments.isNotEmpty()) add(scan)
                            }
                        }
                        conn.connectionGeneration.first { it != subscribedGeneration }
                        collector.cancel()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Block stream interrupted, retrying", e)
                } finally {
                    // The job's own unsubscribe, so it runs before a restart's subscribe (which
                    // joins this job). After a reconnect it's a no-op: that stream is gone.
                    val subscribed = conn
                    if (subscribed != null) {
                        withContext(NonCancellable) {
                            try {
                                withTimeoutOrNull(UNSUBSCRIBE_TIMEOUT_MS) { subscribed.unsubscribeFromBlockAdded(owner = CLIENT) }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send NOTIFY_STOP", e)
                            }
                        }
                    }
                }
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun stop() {
        // Kept, not nulled: the next start joins it, and its finally sends the NOTIFY_STOP.
        streamJob?.cancel()
    }

    /**
     * [post] as the stream last saw it: null once its author deleted it, their latest edit in
     * place. Reads served while down (a saved page, Kaspa REST) go through this too, so a post
     * deleted during the outage doesn't come back on the next refresh (iOS 369794e).
     */
    fun amended(post: KPost): KPost? {
        val amendment = _amendments.value[amendmentKey(post.id, post.userPublicKey)] ?: return post
        val edit = amendment.edit ?: return null
        if (!KaPostsChainPosts.editApplies(post, edit.timestampMs) ||
            edit.timestampMs <= (post.editedAt ?: Long.MIN_VALUE)
        ) return post
        return KaPostsChainPosts.edited(post, edit.b64, edit.mentions, edit.timestampMs)
    }

    fun amended(posts: List<KPost>): List<KPost> =
        if (_amendments.value.isEmpty()) posts else posts.mapNotNull { amended(it) }

    private fun extract(block: Rpc.RpcBlock): Scan {
        val blockTime = if (block.hasHeader()) block.header.timestamp else System.currentTimeMillis()
        val scan = Scan()
        for (tx in block.transactionsList) {
            if (!tx.hasVerboseData()) continue
            val txId = tx.verboseData.transactionId
            if (txId.isBlank()) continue
            if (PREFIXES.none { tx.payload.startsWith(it, ignoreCase = true) }) continue
            val payload = KaPostsChainPosts.hexToUtf8(tx.payload) ?: continue
            val verified = KaPostsChainPosts.verifiedPost(payload, txId, blockTime)
            if (verified != null) {
                scan.posts += verified
                continue
            }
            val signed = KaPostsChainPosts.signedAmendment(payload, blockTime) ?: continue
            when (val amendment = signed.amendment) {
                is KaPostsChainPosts.Amendment.Delete -> scan.amendments +=
                    amendmentKey(amendment.postId, signed.authorPubkey) to LiveAmendment(edit = null, seenAtMs = blockTime)
                is KaPostsChainPosts.Amendment.Edit -> scan.amendments +=
                    amendmentKey(amendment.postId, signed.authorPubkey) to LiveAmendment(
                        edit = LiveEdit(amendment.b64, amendment.mentions, amendment.timestampMs),
                        seenAtMs = blockTime,
                    )
            }
        }
        return scan
    }

    @Synchronized
    private fun add(scan: Scan) {
        if (!active) return
        if (scan.amendments.isNotEmpty()) {
            val updated = _amendments.value.toMutableMap()
            for ((key, amendment) in scan.amendments) {
                // A delete stands; a later edit replaces an earlier one.
                val existing = updated[key]
                if (existing != null) {
                    val existingEdit = existing.edit ?: continue
                    val edit = amendment.edit
                    if (edit != null && edit.timestampMs < existingEdit.timestampMs) continue
                }
                updated[key] = amendment
            }
            if (updated.size > KEEP_AMENDMENTS) {
                updated.entries.sortedBy { it.value.seenAtMs }
                    .take(updated.size - KEEP_AMENDMENTS)
                    .map { it.key }
                    .forEach { updated.remove(it) }
            }
            _amendments.value = updated
        }
        val known = _posts.value.mapTo(HashSet()) { it.id }
        val fresh = scan.posts.filter { it.post.id !in known && firstSighting(it.repeatKey) }.map { it.post }
        val next = amended((fresh + _posts.value).sortedByDescending { it.timestamp }.take(KEEP))
        if (fresh.isEmpty() && scan.amendments.isEmpty()) return
        _posts.value = next
    }

    /** False for a replay of a payload already streamed. */
    private fun firstSighting(repeatKey: String): Boolean {
        if (!seenPayloads.add(repeatKey)) return false
        seenOrder.addLast(repeatKey)
        while (seenOrder.size > KEEP_SEEN) {
            seenPayloads.remove(seenOrder.removeFirst())
        }
        return true
    }

    companion object {
        private const val TAG = "KaPostsLiveChainFeed"
        private const val CLIENT = "kaposts-chain-feed"
        private const val KEEP = 300
        private const val KEEP_SEEN = 3000
        private const val KEEP_AMENDMENTS = 1000

        fun amendmentKey(postId: String, authorPubkey: String): String =
            postId + ":" + KaPostsChainPosts.xOnly(authorPubkey)
        private const val RETRY_DELAY_MS = 5_000L
        /** A stop that hangs mustn't hold up the next start, which waits for it. */
        private const val UNSUBSCRIBE_TIMEOUT_MS = 5_000L
        private val PREFIXES: List<String> = listOf("post:", "reply:", "quote:", "poll:", "delete:", "edit:").map { action ->
            (KaPostsProtocol.PREFIX + action).toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        }
    }
}
