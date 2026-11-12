package com.kachat.app.repository

import com.kachat.app.models.BroadcastChannelEntity
import com.kachat.app.models.BroadcastMessageEntity
import com.kachat.app.models.BroadcastRetention
import com.kachat.app.models.HiddenBroadcastSenderEntity
import com.kachat.app.models.FeaturedBroadcastChannels
import com.kachat.app.models.ReactionEntity
import com.kachat.app.services.NetworkService
import com.kachat.app.services.WalletManager
import com.kachat.app.services.WalletService
import com.kachat.app.services.database.KaChatDatabase
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.kachat.app.util.MessageProtocol
import com.kachat.app.util.MessageReaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcast rooms — public, unencrypted, one-to-many channels identified by a plaintext channel
 * name (see MessageProtocol's bcast payload functions). Kept separate from [ChatRepository]:
 * channels/messages here have no contact concept, no per-account message scoping (the message
 * cache is a raw capture of public chain data, shared across whichever account is active), and no
 * encryption, so folding this into ChatRepository would mostly add conditionals rather than reuse.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BroadcastRepository @Inject constructor(
    private val database: KaChatDatabase,
    private val walletManager: WalletManager,
    private val walletService: WalletService,
    private val networkService: NetworkService,
    private val settings: com.kachat.app.repository.AppSettingsRepository,
    // Public-chat authorship (audit XP-012): the indexer's senderAddress is only its guess, so
    // every row it serves is verified (input 0's address == output 0's) before it is stored.
    private val senderVerifier: com.kachat.app.services.BroadcastSenderVerifier,
    // Whether the phone has a connection at all: offline says nothing about an indexer.
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) {
    /**
     * Where a room's sends run - messages, edits, reactions, retries and voice notes - so they
     * finish after the room is closed, as iOS runs them on the shared PublicChatService. A room's
     * view model dies on Back, and a send cancelled with it would be marked failed even when the
     * transaction already went out, and a Retry would then post and pay twice.
     */
    val sendScope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate +
            com.kachat.app.util.StartupSafety.logOnlyHandler("BroadcastRepository")
    )

    /** The active account's address - the scanner refreshes the indexer's tracked rooms on a switch. */
    val activeAddressFlow: kotlinx.coroutines.flow.StateFlow<String?> get() = walletManager.activeAddressFlow

    /** Channels joined by whichever account is currently active — re-emits automatically on account switch. */
    fun getJoinedChannels(): Flow<List<BroadcastChannelEntity>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else database.broadcastDao().getJoinedChannels(address)
        }
    }

    /** "Joining" and "creating" a channel are the same action — there's no ownership/membership protocol. */
    suspend fun joinChannel(rawName: String) {
        val name = MessageProtocol.normalizeChannelName(rawName)
        require(MessageProtocol.isValidChannelName(name)) { "Invalid channel name" }
        database.broadcastDao().joinChannel(
            BroadcastChannelEntity(channelName = name, walletAddress = walletManager.getAddress())
        )
    }

    /** Removes the channel from the joined list AND permanently deletes every cached message for it — the UI must confirm this with the user first, since it's destructive and can't be undone (rejoining later starts with no history). Featured rooms can't be left (matches iOS). */
    suspend fun leaveChannel(channelName: String) {
        // Curated rooms (Popular and the language rooms alike) are permanent fixtures of the
        // list screen, so no UI offers Leave; guard against stray paths. A room the indexer
        // tracks that you added yourself stays one you can leave (iOS 21c480d).
        if (channelName in FeaturedBroadcastChannels.CURATED_NAMES) return
        database.broadcastDao().leaveChannel(channelName, walletManager.getAddress())
        // The message table is shared by every account on this device (iOS keeps one store per
        // account): the history goes only once no other account still has the room.
        if (database.broadcastDao().countJoinedAccounts(channelName) == 0) {
            database.broadcastDao().deleteMessagesForChannel(channelName)
        }
    }

    /**
     * Every default room is in Public Chats from the start (iOS KaPosts 865c9ab
     * `ensureDefaultChannelsJoined`): #kaspa, #kachat-bugs and the language rooms, auto-joined
     * with the FIXED retention their indexer backfill serves - except any in [hidden], switched
     * off with the customize button. They join with notifications off, which is what keeps the
     * language rooms cheap: the background scan and the push registration only cover rooms whose
     * bell is on. Idempotent - joinChannel inserts with IGNORE, so an already-joined row (and its
     * bell/listen toggles) is left completely untouched.
     */
    suspend fun ensureDefaultChannelsJoined(hidden: Set<String>) {
        val address = try { walletManager.getAddress() } catch (_: Exception) { return }
        for (name in FeaturedBroadcastChannels.CURATED_NAMES) {
            if (name in hidden) continue
            database.broadcastDao().joinChannel(
                BroadcastChannelEntity(
                    channelName = name,
                    walletAddress = address,
                    retentionMillis = BroadcastRetention.MAX_MILLIS,
                )
            )
        }
    }

    /**
     * Per-channel opt-in to background scanning — the user chooses exactly which channels stay
     * live while the app is backgrounded, rather than one all-or-nothing setting. Turning this
     * off also turns notifications off for the channel (a channel that isn't being scanned has
     * no way to know about new messages to notify about).
     */
    suspend fun setAlwaysListen(channelName: String, alwaysListen: Boolean) {
        if (alwaysListen) {
            database.broadcastDao().setAlwaysListen(channelName, walletManager.getAddress(), true)
        } else {
            database.broadcastDao().disableAlwaysListenAndNotify(channelName, walletManager.getAddress())
        }
    }

    /** Per-channel opt-in to a notification for new messages — enabling this also turns always-listen on for the channel, since notifications depend on it actually being scanned. */
    suspend fun setNotifyEnabled(channelName: String, notifyEnabled: Boolean) {
        val address = walletManager.getAddress()
        if (notifyEnabled) {
            database.broadcastDao().enableNotifyAndAlwaysListen(channelName, address)
        } else {
            database.broadcastDao().disableNotify(channelName, address)
        }
    }

    /** Per-channel override of local message retention, set via the settings icon next to a channel — clamped to [1 second, BroadcastRetention.MAX_MILLIS] so the UI's 3-day cap can't be bypassed by a bad input. */
    suspend fun setRetentionMillis(channelName: String, retentionMillis: Long) {
        // Rooms the indexer tracks have a FIXED 30-day retention (the indexer serves 30 days of
        // history and the prune keeps all of it) - no per-room override.
        if (FeaturedBroadcastChannels.isIndexerTracked(channelName)) return
        val clamped = retentionMillis.coerceIn(1_000L, BroadcastRetention.MAX_MILLIS)
        database.broadcastDao().setRetentionMillis(channelName, walletManager.getAddress(), clamped)
    }

    /** The active account's always-listen channel names — non-empty drives whether background scanning runs at all, and membership decides which channels' messages actually get cached while it's running. */
    fun getAlwaysListenChannelNames(): Flow<Set<String>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptySet()) else database.broadcastDao().getAlwaysListenChannelNames(address).map { it.toSet() }
        }
    }

    /** The active account's notify-enabled channel names — drives which channels' new messages fire a system notification. */
    fun getNotifyEnabledChannelNames(): Flow<Set<String>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptySet()) else database.broadcastDao().getNotifyEnabledChannelNames(address).map { it.toSet() }
        }
    }

    /**
     * What the Public Chats list shows for one room: its newest message and how many messages
     * from other people are newer than the read marker. [lastMessage] is null for a room with
     * nothing in it yet. Counting stops at the bounded window it looks at, which is far more
     * than a badge ever needs to say.
     */
    data class RoomSummary(
        val lastMessage: BroadcastMessageEntity?,
        val unreadCount: Int,
    )

    /**
     * Newest message and unread count for [channelName], respecting the senders hidden in this
     * room and never counting reactions - they are not messages anyone reads. [lastReadMs] null
     * means the room has no marker yet, so nothing in it counts as unread.
     */
    fun roomSummary(channelName: String, myAddress: String?, lastReadMs: Long?): Flow<RoomSummary> {
        return combine(
            database.broadcastDao().getLatestMessagesForChannel(channelName, SUMMARY_WINDOW),
            getHiddenSenders(),
        ) { newestFirst, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val readable = newestFirst.filterNot {
                it.senderAddress in hiddenHere || MessageReaction.parseOrNull(it.content) != null ||
                    com.kachat.app.util.MessageEdit.parseOrNull(it.content) != null
            }
            // A long voice note's parts count once (iOS 1b15521 `unreadCount`).
            val seenNotes = HashSet<String>()
            val unread = if (lastReadMs == null) 0 else readable.count {
                it.blockTimestamp > lastReadMs && it.senderAddress != myAddress &&
                    (com.kachat.app.util.BroadcastVoiceParts.noteKey(it)?.let { note -> seenNotes.add(note) } ?: true)
            }
            RoomSummary(lastMessage = readable.firstOrNull(), unreadCount = unread)
        }.flowOn(Dispatchers.Default)
    }

    /** A room's newest [ROOM_WINDOW_SIZE] rows (widened by "Load earlier messages"), and whether
     *  the read filled the window — which is the only way to know there is more behind it. */
    data class RoomWindow(
        val messages: List<BroadcastMessageEntity>,
        val hasMore: Boolean,
        /** Long voice notes in the window by their first part's id (iOS 1b15521
         *  `voiceNotesByChannel`): one bubble each, worked out with the window so the room doesn't
         *  sniff every row on every pass. Their other parts are left out of [messages]. */
        val voiceNotes: Map<String, com.kachat.app.util.BroadcastVoiceNote> = emptyMap(),
    )

    /** Never includes messages from a sender hidden IN THIS ROOM (or via a legacy every-room hide) — including ones already cached from before the hide (see BroadcastScanningService for the future-side enforcement). Reaction messages (see [getReactions]) never render as a message row, so they're filtered out here too. */
    fun getMessages(channelName: String): Flow<List<BroadcastMessageEntity>> =
        getMessageWindow(channelName, limit = null).map { it.messages }

    /**
     * The room read as a window. A curated room keeps thirty days of history, and the screen used
     * to load every row of it on open and again on every change (iOS 02a4f58). [limit] null reads
     * the whole room, which is what the summary paths and the chess arena's reducer need.
     *
     * `hasMore` is measured on the RAW rows, before reactions and edits are filtered out: those
     * are rows in the same table, so a window of N raw rows renders fewer than N bubbles, and
     * counting the rendered ones would call a full window exhausted.
     */
    fun getMessageWindow(channelName: String, limit: Int?): Flow<RoomWindow> {
        val rows = if (limit == null) {
            database.broadcastDao().getMessagesForChannel(channelName)
        } else {
            // DESC + LIMIT takes the NEWEST n off the (channelName, blockTimestamp) index; the
            // screen wants them oldest-first, as the unwindowed query already returns them.
            database.broadcastDao().getLatestMessagesForChannel(channelName, limit).map { it.asReversed() }
        }
        return combine(rows, getHiddenSenders()) { messages, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            // And never two rows with one id: the room's LazyColumn is keyed by it, and a
            // duplicate key crashes Compose (iOS d0b819f - there it left a blank gap). The table's
            // primary key is the id, so this is a guard, not a cleanup.
            val seen = HashSet<String>(messages.size)
            val fresh = messages.filter {
                seen.add(it.id) &&
                    it.senderAddress !in hiddenHere && MessageReaction.parseOrNull(it.content) == null &&
                    com.kachat.app.util.MessageEdit.parseOrNull(it.content) == null
            }
            // A long voice note is one bubble at its first part; its other parts aren't drawn.
            val voiceNotes = com.kachat.app.util.BroadcastVoiceParts.group(fresh)
            RoomWindow(
                messages = if (voiceNotes.hiddenIds.isEmpty()) fresh else fresh.filter { it.id !in voiceNotes.hiddenIds },
                hasMore = limit != null && messages.size >= limit,
                voiceNotes = voiceNotes.notesByAnchorId,
            )
        // The reaction/edit sniff runs over every row on every change - through a room's first
        // backfill that is once per page. Collected from the screen it ran on the main thread
        // (iOS 9c9e3e0, 5463ccc).
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Reactions in [channelName], derived from the cached broadcast message rows themselves —
     * a reaction is just a broadcast whose content is the [MessageReaction] JSON (same wire
     * format as 1:1/group chats), so the rows the block scanner / indexer backfill already
     * persist ARE the reaction storage: they survive restarts, load with the channel's history,
     * and dedupe by txId, which keeps re-processing the indexer's repeated 200-row pages
     * harmless (unlike replaying add/remove events into a separate table would be).
     *
     * Aggregation matches group chat's semantics exactly: reactor = sender address, one reaction
     * per (target, reactor) with the newest blockTime winning, and a "remove" deleting the chip —
     * except a FAILED remove, which is restored marked failed (so it isn't silently lost and the
     * Retry under the message can re-attempt it), mirroring GroupRepository.sendGroupReaction's
     * failure handling. Emitted as in-memory [ReactionEntity] values (never persisted to the
     * `reactions` table — walletAddress is left blank) purely so the existing ReactionPill UI is
     * reused as-is; `reactionTxId` carries the reaction message row's own id for [retryReactionMessage].
     */
    fun getReactions(channelName: String): Flow<List<ReactionEntity>> {
        // Only rows carrying the reaction marker are read (iOS `loadReactions` reads its own
        // store on change): the whole room, parsed, on every insert into any room was the cost.
        val rows = database.broadcastDao().getReactionRowsForChannel(channelName).distinctUntilChanged()
        return combine(rows, getHiddenSenders()) { messages, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val newestPerReactor = LinkedHashMap<Pair<String, String>, Pair<BroadcastMessageEntity, com.kachat.app.util.MessageReactionContent>>()
            for (row in messages) {
                if (row.senderAddress in hiddenHere) continue
                val parsed = MessageReaction.parseOrNull(row.content) ?: continue
                val key = parsed.targetTxId to row.senderAddress
                val existing = newestPerReactor[key]
                // >= so a tie is broken by row order (DAO orders by blockTimestamp ASC).
                if (existing == null || row.blockTimestamp >= existing.first.blockTimestamp) {
                    newestPerReactor[key] = row to parsed
                }
            }
            newestPerReactor.values.mapNotNull { (row, parsed) ->
                if (parsed.action != "add" && row.deliveryStatus != "failed") return@mapNotNull null
                ReactionEntity(
                    targetTxId = parsed.targetTxId,
                    walletAddress = "", // in-memory value object only, never inserted into the reactions table
                    reactorAddress = row.senderAddress,
                    emoji = parsed.emoji,
                    reactionTxId = row.id,
                    blockTimestamp = row.blockTimestamp,
                    deliveryStatus = row.deliveryStatus,
                    failedAction = if (row.deliveryStatus == "failed") parsed.action else null
                )
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    /**
     * The newest edit per message in [channelName], derived from the cached broadcast rows the
     * same way [getReactions] derives reactions: an edit is just a broadcast whose content is the
     * [com.kachat.app.util.MessageEdit] JSON, so the stored rows ARE the storage — they survive
     * restarts, load with the room's history and dedupe by txId.
     *
     * An edit counts only if its sender sent the message it names and that message is text; the
     * newest by block time wins. Keyed by the target's txId, ready for the screen to apply as it
     * draws each bubble.
     */
    fun getEdits(channelName: String): Flow<Map<String, BroadcastEdit>> {
        // Only rows carrying the edit marker are read (iOS `loadEdits`), then just the messages
        // they name. Room re-runs the query on any change to the table, so a target arriving
        // after its edit still lands - which is why these rows are not deduplicated (the result
        // is, below).
        val rows = database.broadcastDao().getEditRowsForChannel(channelName)
        return combine(rows, getHiddenSenders()) { editRows, hidden ->
            val hiddenHere = hiddenAddressesIn(channelName, hidden)
            val edits = editRows.mapNotNull { row ->
                if (row.senderAddress in hiddenHere) return@mapNotNull null
                // A failed edit never reached the chain (only an older build left one, iOS ee5ec98).
                if (row.deliveryStatus == "failed") return@mapNotNull null
                com.kachat.app.util.MessageEdit.parseOrNull(row.content)?.let { row to it }
            }
            val senders = HashMap<String, BroadcastMessageEntity>(edits.size)
            for (chunk in edits.map { it.second.targetTxId }.distinct().chunked(500)) {
                for (row in database.broadcastDao().getMessagesByIds(chunk)) {
                    if (row.channelName == channelName) senders[row.id] = row
                }
            }
            val newestPerTarget = LinkedHashMap<String, BroadcastEdit>()
            for ((row, parsed) in edits) {
                val target = senders[parsed.targetTxId] ?: continue
                if (target.senderAddress != row.senderAddress) continue
                if (!com.kachat.app.util.MessageEdit.isEditable(target.content)) continue
                val existing = newestPerTarget[parsed.targetTxId]
                // >= so a tie is broken by row order (DAO orders by blockTimestamp ASC).
                if (existing == null || row.blockTimestamp >= existing.blockTimestamp) {
                    newestPerTarget[parsed.targetTxId] = BroadcastEdit(
                        targetTxId = parsed.targetTxId,
                        text = parsed.text,
                        editMessageId = row.id,
                        blockTimestamp = row.blockTimestamp,
                        deliveryStatus = row.deliveryStatus
                    )
                }
            }
            newestPerTarget
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    /** One message's newest edit in a room — see [getEdits]. */
    data class BroadcastEdit(
        val targetTxId: String,
        val text: String,
        val editMessageId: String,
        val blockTimestamp: Long,
        val deliveryStatus: String
    )

    /** Re-attempts a reaction message whose send previously failed — [reactionMessageId] is the reaction's own broadcast message row id (see [getReactions]'s `reactionTxId`). */
    suspend fun retryReactionMessage(reactionMessageId: String?) {
        val row = reactionMessageId?.let { database.broadcastDao().getMessage(it) } ?: return
        retryBroadcast(row)
    }

    /** The rooms' own hides, for a room's Hidden Users list (per-room since 4.0; channelName "" =
     *  every room). Blocked accounts are left out: they are undone on the Blocked screen. */
    fun getRoomHiddenSenders(): Flow<List<HiddenBroadcastSenderEntity>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else database.broadcastDao().getHiddenSenders(address)
        }
    }

    /**
     * Everyone whose messages and pushes are left out: the rooms' hides, plus accounts blocked in
     * KaPost, as every-room rows (iOS 9e22075 `PublicChatStore.blockedSenders`). Not stored rows:
     * the block list owns them, per account. Re-emits on account switch.
     */
    fun getHiddenSenders(): Flow<List<HiddenBroadcastSenderEntity>> {
        return walletManager.activeAddressFlow.flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else combine(
                database.broadcastDao().getHiddenSenders(address),
                settings.kapostsBlocked(address),
            ) { rows, blocked ->
                rows + blocked.map { HiddenBroadcastSenderEntity(it, address, "") }
            }
        }
    }

    /** Blocks an account from a room's sender sheet: the same list as a post's Block, which also
     *  supersedes a mute. */
    suspend fun blockSender(senderAddress: String) {
        val wallet = walletManager.activeAddressFlow.value ?: return
        settings.setKapostsBlocked(wallet, settings.kapostsBlocked(wallet).first() + senderAddress)
        settings.setKapostsMuted(wallet, settings.kapostsMuted(wallet).first() - senderAddress)
    }

    companion object {
        /** How far back the Public Chats list looks in each room. A badge past this reads the
         *  same to anyone, and it keeps a busy curated room cheap to summarise. */
        const val SUMMARY_WINDOW = 200

        /** How much of a room the thread screen reads at once, and how much each "Load earlier
         *  messages" adds. iOS's `roomWindowSize`. */
        const val ROOM_WINDOW_SIZE = 400

        /** Failures in a row that confirm an indexer is down, and how close they must follow one
         *  another (iOS ee5ec98 `noteIndexerResult`: three inside two minutes). */
        private const val INDEXER_DOWN_FAILURES = 3
        private const val INDEXER_FAILURE_WINDOW_MS = 120_000L

        /** A handoff fails every open room's poll at the same moment, and that is one failure. */
        private const val INDEXER_FAILURE_COALESCE_MS = 5_000L

        /** How far before an outage a room's re-read starts (iOS ee5ec98: ten minutes). */
        private const val OUTAGE_REWIND_MS = 600_000L

        /** What a long voice note's balance check leaves over, iOS's `dustThreshold` (0.2 KAS,
         *  iOS 1b15521 `ensureSufficientBalanceForVoiceParts`). */
        private const val VOICE_NOTE_DUST_THRESHOLD_SOMPI = 20_000_000L

        /** The indexer [channelName] reads from: its own override, or the app-wide one. Blank when
         *  neither is set (iOS `indexerBaseURL(forChannel:)`). An http:// override an older build
         *  saved is ignored, as [roomIndexerOverride] ignores it. */
        fun indexerBaseUrl(channelName: String, appWide: String, overrides: Map<String, String>): String {
            val own = overrides[channelName.trim().lowercase()]
                ?.takeUnless { isCleartextHttp(it) }?.trim().orEmpty()
            return own.ifEmpty { appWide.trim() }
        }

        /** What a room indexer typed as http:// is refused with (iOS SettingsView's words). */
        const val HTTPS_REQUIRED = "Use https. Unencrypted connections are not supported."

        /** True for an http:// address, which no room indexer is ever asked over. */
        fun isCleartextHttp(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

        /**
         * A room indexer as typed, by the app's rule for every server address
         * (`ConnectionViewModel.httpsUrlOrNull`): trimmed, without a trailing slash; null for
         * http://, and a bare host gets https://. Blank stays blank.
         */
        fun httpsUrlOrNull(value: String): String? {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isEmpty()) return trimmed
            if (isCleartextHttp(trimmed)) return null
            return if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
        }

        /** How often the app-wide indexer is asked which rooms it tracks (iOS: hourly). */
        private const val TRACKED_ROOMS_CHECK_INTERVAL_MS = 60L * 60 * 1000

        /** Deep backfill re-runs while some rows' senders are still unverified (XP-012). */
        private const val DEEP_UNVERIFIED_RETRIES = 3

        /** Launches [reverifyCachedSenders] may wait for the REST API before giving up (XP-012). */
        const val REVERIFY_MAX_ATTEMPTS = 3

        /** Cached rows checked per pass step - one REST search request. */
        private const val REVERIFY_CHUNK = 100

        /** How far before a leftover pending row its delivered copy may sit: the row carries this
         *  phone's clock, the copy the chain's (iOS e5a9b11, 300_000). */
        private const val SETTLE_DELIVERED_WINDOW_MS = 5L * 60 * 1000

        /** Which senders are hidden in [channelName]: room-scoped rows plus legacy every-room ("") rows. */
        fun hiddenAddressesIn(channelName: String, rows: List<HiddenBroadcastSenderEntity>): Set<String> =
            rows.filter { it.channelName.isEmpty() || it.channelName == channelName }
                .map { it.senderAddress }
                .toSet()
    }

    /** Hides a sender in ONE room - their messages and notifications from that room disappear; other rooms are unaffected. */
    suspend fun hideSender(senderAddress: String, channelName: String) {
        database.broadcastDao().hideSender(
            HiddenBroadcastSenderEntity(senderAddress, walletManager.getAddress(), channelName)
        )
    }

    suspend fun unhideSender(senderAddress: String, channelName: String) {
        database.broadcastDao().unhideSender(senderAddress, walletManager.getAddress(), channelName)
    }

    // MARK: - Indexer backfill (4.0): the featured rooms are backed by the KaChat broadcast
    // indexer, so history sent while the app was closed appears on room open. Merge is
    // dedupe-by-txId via the DAO's REPLACE insert; hidden-sender filtering happens at read.

    /** Channels whose full 30-day history was already paged in this process — the deep backfill
     *  runs once per room per launch; the 8s poll then only needs the newest page. */
    private val deepBackfilledChannels: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Where an interrupted deep backfill picks up: the `before` cursor of the next page to ask
     *  for, and how many pages of the safety valve are left. Session-only, like the completed
     *  set above. Without this a single thrown page - one timeout on page 30 of a busy room -
     *  restarted the whole pager from page 1 on the next 8s tick, and kept doing so until every
     *  page happened to succeed in one go (iOS deepBackfillResume). */
    private val deepBackfillResume = mutableMapOf<String, Pair<Long, Int>>()

    /** Oldest a room's next backfill needs to reach after an outage (ms): the indexer gets the
     *  last word on that stretch, without paging its whole 30 days again (iOS ee5ec98). */
    private val deepBackfillFloor = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The newest block time the indexer has shown for each room this session. A steady poll
     *  whose whole page is newer than this has a gap behind it (iOS ee5ec98). */
    private val indexerNewestSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Indexers confirmed down (three failures in a row inside two minutes), and since when, by
     *  base URL. Their rooms go back on the block stream on cellular (iOS ee5ec98). */
    private val _unreachableIndexers = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Long>>(emptyMap())
    val unreachableIndexers: kotlinx.coroutines.flow.StateFlow<Map<String, Long>> get() = _unreachableIndexers

    private class IndexerFailureRun(var count: Int, var lastAt: Long)

    /** Guards [indexerFailureRuns] and the writes to [_unreachableIndexers]: the open room's poll
     *  and the closed-room sweep report from different threads. */
    private val indexerHealthLock = Any()
    private val indexerFailureRuns = HashMap<String, IndexerFailureRun>()

    /** The app-wide indexer and the rooms' own, for the scanner's cellular gate. */
    fun roomIndexerBases(): Flow<Pair<String, Map<String, String>>> =
        combine(settings.broadcastIndexerUrl, settings.broadcastIndexerOverrides) { appWide, overrides ->
            appWide to overrides
        }

    private suspend fun indexerBaseUrl(channelName: String): String =
        indexerBaseUrl(channelName, settings.broadcastIndexerUrl.first(), settings.broadcastIndexerOverrides.first())

    /**
     * Every answer or failure from a room's indexer (iOS ee5ec98 `noteIndexerResult`). Null, or a
     * 4xx, means it answered: the count resets, and an indexer that was down has its rooms read
     * the outage again. Only a failure that says the indexer is at fault counts towards three.
     */
    private suspend fun noteIndexerResult(base: String, failed: Exception?) {
        if (failed == null || isClientRejection(failed)) {
            val since = synchronized(indexerHealthLock) {
                indexerFailureRuns.remove(base)
                val downSince = _unreachableIndexers.value[base] ?: return
                _unreachableIndexers.value = _unreachableIndexers.value - base
                downSince
            }
            android.util.Log.i("BroadcastRepository", "Room indexer is answering again")
            rewindRoomsAfterOutage(base, since)
            return
        }
        if (!meansDown(failed) && !isServerFailure(failed)) return
        synchronized(indexerHealthLock) {
            val now = System.currentTimeMillis()
            val run = indexerFailureRuns.getOrPut(base) { IndexerFailureRun(count = 0, lastAt = 0L) }
            if (now - run.lastAt < INDEXER_FAILURE_COALESCE_MS) return
            if (now - run.lastAt > INDEXER_FAILURE_WINDOW_MS) run.count = 0
            run.count += 1
            run.lastAt = now
            if (run.count < INDEXER_DOWN_FAILURES || base in _unreachableIndexers.value) return
            _unreachableIndexers.value = _unreachableIndexers.value + (base to now)
        }
        android.util.Log.w("BroadcastRepository", "Room indexer isn't answering: rooms stay on the block stream")
    }

    /** A 429 or a 5xx is the indexer's trouble (iOS `KaPostsIndexerHealth.meansDown(statusCode:)`). */
    private fun statusMeansDown(code: Int): Boolean = code == 429 || code >= 500

    /** A 4xx is the indexer answering, whatever it thinks of this request. */
    private fun isClientRejection(error: Exception): Boolean =
        (error as? retrofit2.HttpException)?.code()?.let { it in 400..499 && !statusMeansDown(it) } == true

    private fun isServerFailure(error: Exception): Boolean =
        (error as? retrofit2.HttpException)?.code()?.let { statusMeansDown(it) } == true

    /** A transport failure while the phone has a connection. A phone that's offline says nothing
     *  about the indexer, and neither does a cancelled request or a body that won't decode. */
    private fun meansDown(error: Exception): Boolean {
        if (error !is java.io.IOException) return false
        if (error is java.io.EOFException || error is com.google.gson.stream.MalformedJsonException) return false
        return !phoneIsOffline()
    }

    private fun phoneIsOffline(): Boolean {
        val manager = appContext.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Rooms this indexer serves read that stretch from it again, from ten minutes before it went
     *  away: what the block stream missed comes in, and the indexer has the final word (iOS
     *  ee5ec98). The next step of the poll that noticed it does the paging. */
    private suspend fun rewindRoomsAfterOutage(base: String, since: Long) {
        val floor = since - OUTAGE_REWIND_MS
        val appWide = settings.broadcastIndexerUrl.first()
        val overrides = settings.broadcastIndexerOverrides.first()
        for (channel in deepBackfilledChannels.toList()) {
            if (indexerBaseUrl(channel, appWide, overrides) == base) {
                deepBackfilledChannels.remove(channel)
                deepBackfillFloor[channel] = floor
            }
        }
    }

    /** Room edits whose send failed, by room then target txId: the text, for Retry. The message
     *  itself goes back to what it read before, since that text never reached the chain (iOS
     *  ee5ec98 `failedEditsByChannel`). In memory only, as there. */
    private val _failedEdits = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val failedEdits: kotlinx.coroutines.flow.StateFlow<Map<String, Map<String, String>>> get() = _failedEdits

    /** The text of this message's failed edit, when there is one to retry. */
    fun failedEditText(channelName: String, targetTxId: String): String? =
        _failedEdits.value[channelName]?.get(targetTxId)

    private fun setFailedEdit(channelName: String, targetTxId: String, text: String?) {
        val room = _failedEdits.value[channelName].orEmpty()
        val updated = if (text == null) room - targetTxId else room + (targetTxId to text)
        _failedEdits.value = if (updated.isEmpty()) _failedEdits.value - channelName
        else _failedEdits.value + (channelName to updated)
    }

    /**
     * Edits one of this wallet's own messages (iOS ee5ec98 `sendPublicChatEdit`). Never sent: its
     * row goes, which puts the message back to what it read, and the text waits for Retry. An
     * unknown outcome keeps its row, "unconfirmed", until the delivered copy turns it into a
     * normal edit, with no Retry, which could pay twice.
     */
    suspend fun sendEdit(channelName: String, targetTxId: String, text: String) {
        setFailedEdit(channelName, targetTxId, null)
        try {
            sendBroadcast(channelName, com.kachat.app.util.MessageEdit.encode(targetTxId, text), dropRowOnFailure = true)
        } catch (e: com.kachat.app.services.SendOutcomeUnknownException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            setFailedEdit(channelName, targetTxId, text)
            throw e
        }
    }

    /** How many times a room's deep backfill was re-run because some rows' senders could not be
     *  verified yet (audit XP-012). Session-only. */
    private val deepUnverifiedRetries = mutableMapOf<String, Int>()

    /** Sends started in this run, by pending id. A row still pending that isn't one of these was
     *  left by a run that ended mid-send, and nothing is working on it (iOS e5a9b11). */
    private val pendingSendsThisRun: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /** Rooms already checked for those rows, once per account per run (iOS e5a9b11). */
    private val settledPendingChannels = mutableSetOf<String>()

    /** The account [settledPendingChannels] was filled for. */
    private var settledForWallet: String? = null

    /**
     * Called with the active account whenever it lands or changes. Another account's rows have
     * not been checked yet, so a real switch starts the set again (iOS e5a9b11 setCurrentWallet).
     * Every room screen's view model reports the same account, so only a change clears it.
     */
    fun onActiveWalletChanged(address: String?) {
        val normalized = address?.lowercase()
        synchronized(settledPendingChannels) {
            if (normalized == settledForWallet) return
            settledForWallet = normalized
            settledPendingChannels.clear()
        }
        // iOS ee5ec98 setCurrentWallet. The backfilled set stays: Android's room messages are one
        // table shared by every account, so what was fetched still holds for the next one.
        _failedEdits.value = emptyMap()
        deepBackfillFloor.clear()
        indexerNewestSeen.clear()
    }

    /**
     * Once the room's full history is in (after the deep backfill, or straight away for a room
     * with no indexer), so a send that did land has its delivered copy to match (iOS e5a9b11
     * `settlePendingSendsFromBeforeThisRun`). A pending row no send in this run is working on, or
     * an unconfirmed one whose id never became a transaction's, goes if its delivered copy is here
     * (same sender and content, sent, chain time no more than five minutes before the row's own).
     * The rest may have landed while the app was closed, so they show "Not confirmed" rather than
     * a Retry that could pay twice (iOS 33095ac `settlePendingMessages`).
     */
    suspend fun settlePendingSendsFromBeforeThisRun(channelName: String) {
        synchronized(settledPendingChannels) {
            if (!settledPendingChannels.add(channelName)) return
        }
        val dao = database.broadcastDao()
        for (row in dao.getUnsettledSends(channelName)) {
            if (row.id in pendingSendsThisRun) continue
            val delivered = dao.countDeliveredCopies(
                channelName = channelName,
                senderAddress = row.senderAddress,
                content = row.content,
                sinceMillis = row.blockTimestamp - SETTLE_DELIVERED_WINDOW_MS,
            )
            if (delivered > 0) {
                dao.deleteMessage(row.id)
            } else if (row.deliveryStatus != "unconfirmed") {
                dao.updateMessageStatus(row.id, "unconfirmed")
            }
        }
    }

    /**
     * A send whose outcome is unknown (iOS 33095ac `markMessageUnconfirmed`): the row takes the
     * transaction's id, so its delivered copy settles it, and shows "Not confirmed" with no Retry.
     * A copy already here means it landed: the row just goes.
     */
    private suspend fun markMessageUnconfirmed(pendingId: String, txId: String) {
        pendingSendsThisRun.remove(pendingId)
        val dao = database.broadcastDao()
        val row = dao.getMessage(pendingId) ?: return
        // No id to match a copy by: the row stays as it is, "Not confirmed", for the settle.
        if (txId.isBlank()) {
            dao.updateMessageStatus(pendingId, "unconfirmed")
            return
        }
        dao.deleteMessage(pendingId)
        if (dao.getMessage(txId) != null) return
        // Signed here, so its sender is known when it comes back from the chain (XP-012).
        senderVerifier.rememberOwnBroadcast(txId, row.senderAddress)
        dao.insertMessage(row.copy(id = txId, deliveryStatus = "unconfirmed"))
    }

    /** Fetches history for [channelName] and merges it into the local cache. The FIRST call per
     *  channel per launch pages backwards (`before` = oldest blockTime seen) through the
     *  indexer's whole 30-day window — a single newest page (200 rows) meant busy rooms never
     *  loaded anywhere near what the indexer holds. Later calls fetch just the newest page.
     *  Returns the number of rows fetched, or -1 when the indexer is unreachable (callers treat
     *  that as "no backfill", nothing user-facing breaks). */
    /** This wallet's own address, or null before there is one. */
    fun myAddress(): String? = runCatching { walletManager.getAddress() }.getOrNull()

    /**
     * One small newest-page fetch for a room nobody is looking at, merged like any other indexer
     * page. Returns the rows that were NEW to the store, so the caller can banner what just
     * arrived (iOS 3c14b46). Reactions never render as rows, so they are left out.
     */
    suspend fun fetchNewestFromIndexer(channelName: String, limit: Int = 40): List<BroadcastMessageEntity> {
        val override = roomIndexerOverride(channelName)
        val api = (override?.let { networkService.broadcastIndexerApiFor(it) }
            ?: networkService.broadcastIndexerApi.value) ?: return emptyList()
        val hidden = hiddenAddressesIn(channelName, getHiddenSenders().first())
        val base = indexerBaseUrl(channelName)
        val page = try {
            api.getBroadcasts(channel = channelName, limit = limit)
        } catch (e: Exception) {
            noteIndexerResult(base, e)
            android.util.Log.w("BroadcastRepository", "Sweep fetch failed for $channelName", e)
            return emptyList()
        }
        noteIndexerResult(base, null)
        // An indexer only stores the rooms it tracks: rows back mean it keeps this room's history.
        if (!page.messages.isNullOrEmpty()) com.kachat.app.services.IndexerTrackedRooms.markServed(channelName)
        // Neither a reaction nor an edit is a message anyone reads; the room's own backfill is
        // what persists them.
        val wanted = page.messages.orEmpty().filter { message ->
            val content = message.content ?: return@filter false
            MessageReaction.parseOrNull(content) == null && com.kachat.app.util.MessageEdit.parseOrNull(content) == null
        }
        // Hidden is judged on the VERIFIED sender, never on the indexer's guess.
        return admitIndexerRows(channelName, wanted, skipSender = { it in hidden }).inserted
    }

    /** What [admitIndexerRows] stored, and how many rows it could not verify yet. */
    private data class Admitted(val inserted: List<BroadcastMessageEntity>, val unknown: Int)

    /**
     * Merges indexer rows into the store with their sender VERIFIED (audit XP-012): the
     * indexer's `senderAddress` is ignored; a new row is stored only when input 0 spends from the
     * address output 0 pays, under that address. Forged rows are dropped; rows the REST API
     * can't answer for yet are skipped and asked again on a later poll.
     *
     * A row already in the store was stored by a verified path (this one, the live scan, or this
     * phone's own send; older cached rows are re-checked once by [reverifyCachedSenders]), so it
     * keeps its sender and only takes the indexer's chain time: a row this phone sent carries its
     * own clock until the chain's time reaches it, the block scan may never have seen the block,
     * and the chess arena orders seats and runs its clocks by the indexer's time (iOS 29bf054).
     */
    private suspend fun admitIndexerRows(
        channelName: String,
        rows: List<com.kachat.app.services.BroadcastHistoryRow>,
        skipSender: (String) -> Boolean = { false },
    ): Admitted {
        val toVerify = ArrayList<com.kachat.app.services.BroadcastHistoryRow>()
        for (row in rows) {
            val txId = row.txId?.takeIf { it.isNotBlank() } ?: continue
            if (row.content == null) continue
            val existing = database.broadcastDao().getMessage(txId)
            if (existing != null) {
                val chainTime = row.blockTime
                // A send whose outcome was unknown: its delivered copy makes it sent (iOS 33095ac).
                if (existing.deliveryStatus == "unconfirmed") {
                    database.broadcastDao().settleUnconfirmed(txId, chainTime ?: existing.blockTimestamp)
                    continue
                }
                if (chainTime != null && (chainTime != existing.blockTimestamp || existing.deliveryStatus != "sent")) {
                    database.broadcastDao().insertMessage(existing.copy(blockTimestamp = chainTime, deliveryStatus = "sent"))
                }
                continue
            }
            toVerify += row
        }
        if (toVerify.isEmpty()) return Admitted(emptyList(), 0)
        val verdicts = senderVerifier.verifyRows(toVerify.mapNotNull { it.txId }, channelName)
        val inserted = mutableListOf<BroadcastMessageEntity>()
        var unknown = 0
        for (row in toVerify) {
            val txId = row.txId ?: continue
            val judged = verdicts[txId]
            when (judged.verdict) {
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.VERIFIED -> {
                    val sender = judged.senderAddress ?: continue
                    if (skipSender(sender)) continue
                    val entity = BroadcastMessageEntity(
                        id = txId,
                        channelName = channelName,
                        senderAddress = sender,
                        content = row.content ?: continue,
                        blockTimestamp = row.blockTime ?: System.currentTimeMillis(),
                        deliveryStatus = "sent"
                    )
                    // Only a missing id (iOS): the block scan may have stored it meanwhile.
                    if (database.broadcastDao().insertMessageIfAbsent(entity) != -1L) inserted += entity
                }
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.FORGED -> Unit // logged by the verifier
                com.kachat.app.services.BroadcastSenderVerifier.Verdict.UNKNOWN -> unknown += 1
            }
        }
        return Admitted(inserted, unknown)
    }

    /**
     * One pass over the cached public-chat rows stored before sender verification existed
     * (audit XP-012): those took output 0's address (live scan) or the indexer's guess as the
     * sender. Each is checked against the chain once: verified rows keep (or get corrected to)
     * the real sender, forged rows are deleted. Rows the REST API answers for but doesn't know are
     * deleted too (retry, then drop). If the REST API can't be reached the pass is tried again on
     * a later launch, up to [REVERIFY_MAX_ATTEMPTS] times, after which unverifiable rows go.
     * Returns true once the pass is complete.
     */
    suspend fun reverifyCachedSenders(attempt: Int): Boolean {
        val rows = database.broadcastDao().getAllSentMessages()
        if (rows.isEmpty()) return true
        // The last allowed attempt settles every row, reachable REST API or not.
        val giveUp = attempt >= REVERIFY_MAX_ATTEMPTS - 1
        var complete = true
        for (chunk in rows.chunked(REVERIFY_CHUNK)) {
            val verdicts = senderVerifier.verifyRows(chunk.map { it.id }, channel = "cached")
            if (verdicts.lookupFailed && !giveUp) {
                complete = false
                continue
            }
            for (row in chunk) {
                val judged = verdicts[row.id]
                when (judged.verdict) {
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.VERIFIED -> {
                        val sender = judged.senderAddress ?: continue
                        if (sender != row.senderAddress) {
                            database.broadcastDao().insertMessage(row.copy(senderAddress = sender))
                        }
                    }
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.FORGED -> database.broadcastDao().deleteMessage(row.id)
                    com.kachat.app.services.BroadcastSenderVerifier.Verdict.UNKNOWN -> {
                        if (!verdicts.wasAnswered(row.id) && !giveUp) complete = false
                        else database.broadcastDao().deleteMessage(row.id)
                    }
                }
            }
        }
        return complete
    }

    /** When the app-wide indexer was last asked which rooms it tracks: (base URL, millis). */
    @Volatile
    private var trackedRoomsCheckedAt: Pair<String, Long>? = null

    /**
     * Asks the app-wide public chat indexer which rooms it tracks (`/broadcast-channels`), at
     * most once an hour per indexer - on app open and wallet switch. An indexer without the
     * endpoint answers nothing and the set keeps what it learned from served rows
     * (iOS `PublicChatService.refreshIndexerTrackedRooms`, 21c480d).
     */
    suspend fun refreshIndexerTrackedRooms() {
        val base = settings.broadcastIndexerUrl.first().trim()
        if (base.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            val last = trackedRoomsCheckedAt
            if (last != null && last.first == base && now - last.second < TRACKED_ROOMS_CHECK_INTERVAL_MS) return
            trackedRoomsCheckedAt = base to now
        }
        val api = networkService.broadcastIndexerApiFor(base) ?: return
        val channels: List<String>? = try {
            api.getTrackedChannels().channels?.filterNotNull()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Tracked rooms not listed by $base: ${e.message}")
            null
        }
        if (channels == null) return
        com.kachat.app.services.IndexerTrackedRooms.setReported(channels)
    }

    /**
     * GET /chess/leaderboard on [channelName]'s indexer - its own where it has one, the app-wide
     * one otherwise (iOS ChessTournamentService.refreshIndexerLeaderboards). Null on any failure:
     * the phone's own boards stay in place.
     */
    suspend fun fetchChessLeaderboard(
        channelName: String,
        period: String,
    ): com.kachat.app.services.ChessIndexerLeaderboardResponse? {
        val override = roomIndexerOverride(channelName)
        val api = (override?.let { networkService.broadcastIndexerApiFor(it) }
            ?: networkService.broadcastIndexerApi.value) ?: return null
        return try {
            api.getChessLeaderboard(period = period, limit = 1000)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Chess leaderboard ($period) not served: ${e.message}")
            null
        }
    }

    /** The newest chain time stored for [channelName], or null for an empty room. */
    suspend fun newestBlockTimestamp(channelName: String): Long? =
        database.broadcastDao().newestBlockTimestamp(channelName)

    /**
     * Room Info's check after a Save: asks [baseUrl] for one row of [channelName] and returns how
     * many came back (iOS `PublicChatRoomInfoView.saveIndexer`, limit 1). Throws when it can't be
     * reached. A URL Retrofit can't take is refused here: [NetworkService.broadcastIndexerApiFor]
     * would quietly fall back to the app-wide indexer and test that one instead.
     */
    suspend fun probeIndexer(baseUrl: String, channelName: String): Int {
        val trimmed = baseUrl.trim()
        // Never asked over http (the app's https-only rule for every server address).
        if (isCleartextHttp(trimmed)) throw IllegalArgumentException(HTTPS_REQUIRED)
        val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        if (withSlash.toHttpUrlOrNull() == null) throw IllegalArgumentException("unsupported URL")
        val api = networkService.broadcastIndexerApiFor(trimmed) ?: throw IllegalArgumentException("unsupported URL")
        return api.getBroadcasts(channel = channelName, limit = 1).messages.orEmpty().size
    }

    /**
     * [channelName]'s own indexer, or null when it follows the app-wide one. An http:// override
     * an older build saved is ignored - the room reads from the app-wide indexer instead - since
     * Room Info no longer takes one.
     */
    private suspend fun roomIndexerOverride(channelName: String): String? =
        settings.broadcastIndexerOverrides.first()[channelName.trim().lowercase()]
            ?.takeUnless { isCleartextHttp(it) }

    suspend fun backfillFromIndexer(channelName: String): Int {
        // This room's own indexer where it has one, the app-wide client otherwise. A broadcast is
        // on-chain, so any indexer watching the same network serves the same room.
        val override = roomIndexerOverride(channelName)
        val api = (override?.let { networkService.broadcastIndexerApiFor(it) }
            ?: networkService.broadcastIndexerApi.value) ?: return -1
        val base = indexerBaseUrl(channelName)
        var fetched = 0
        var unverified = 0

        /** Merges one page: rows back mean the indexer keeps this room's history (iOS 21c480d),
         *  and each is stored only with a verified sender (audit XP-012). */
        suspend fun merge(messages: List<com.kachat.app.services.BroadcastHistoryRow>) {
            if (messages.isNotEmpty()) com.kachat.app.services.IndexerTrackedRooms.markServed(channelName)
            unverified += admitIndexerRows(channelName, messages).unknown
            fetched += messages.size
        }

        return try {
            // The one-time deep backfill pages at 200; the recurring 8s poll only needs the
            // newest sliver (dedupe-by-txId makes overlap harmless), so its page is small —
            // 200 rows every 8s was the single biggest steady-state indexer cost per open room.
            val response = api.getBroadcasts(
                channel = channelName,
                limit = if (channelName in deepBackfilledChannels) 30 else 200,
            )
            noteIndexerResult(base, null)
            val newest = response.messages.orEmpty()
            // The newest page goes in FIRST, before any history is paged (iOS fetchFromIndexerAndMerge).
            merge(newest)
            val newestTimes = newest.mapNotNull { it.blockTime }
            // More arrived since the last poll than the page holds (a busy room, or the app away
            // for a while): page back to the rows already seen, through the backfill below and
            // its limit, resume and floor. It used to stay missing until the next launch (iOS ee5ec98).
            val seen = indexerNewestSeen[channelName]
            val oldestNewest = newestTimes.minOrNull()
            if (channelName in deepBackfilledChannels && response.hasMore == true &&
                seen != null && oldestNewest != null && oldestNewest > seen) {
                deepBackfilledChannels.remove(channelName)
                deepBackfillFloor[channelName] = seen
            }
            newestTimes.maxOrNull()?.let { newestTime ->
                indexerNewestSeen[channelName] = maxOf(newestTime, seen ?: newestTime)
            }
            var interrupted = false
            if (channelName !in deepBackfilledChannels) {
                val cutoff = System.currentTimeMillis() - BroadcastRetention.INDEXER_MILLIS
                // Picking up an interrupted pager: everything newer than the recorded cursor is
                // already in the store from the attempt that recorded it.
                val resume = deepBackfillResume[channelName]
                var hasMore = resume != null || response.hasMore == true
                var oldest: Long? = resume?.first ?: oldestNewest
                var pagesLeft = resume?.second ?: 50 // 50 × 200 = 10k rows, far beyond any real room
                val floor = maxOf(cutoff, deepBackfillFloor[channelName] ?: cutoff)
                while (hasMore && pagesLeft > 0) {
                    val before = oldest ?: break
                    if (before <= floor) break // older pages are pruned anyway, or already right
                    pagesLeft -= 1
                    val page = try {
                        api.getBroadcasts(channel = channelName, limit = 200, before = before)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep what this attempt did get: the cursor recorded after each landed
                        // page is where the next 8s tick resumes, not page 1.
                        android.util.Log.w("BroadcastRepository", "Indexer backfill paused for $channelName at before=$before", e)
                        interrupted = true
                        break
                    }
                    val messages = page.messages.orEmpty()
                    if (messages.isEmpty()) break
                    merge(messages)
                    hasMore = page.hasMore == true
                    oldest = messages.mapNotNull { it.blockTime }.minOrNull()
                    val next = oldest
                    if (hasMore && next != null) deepBackfillResume[channelName] = next to pagesLeft
                }
                // Marked done only once the pager actually finishes; a paused one keeps its
                // resume cursor and the next 8s poll continues from there.
                if (!interrupted) {
                    deepBackfillResume.remove(channelName)
                    // Rows whose sender could not be verified yet (the REST API had no answer) were
                    // left out: page the history again on a later poll - rows already stored cost no
                    // lookup - a few times before settling for what verified.
                    val retries = deepUnverifiedRetries[channelName] ?: 0
                    if (unverified > 0 && retries < DEEP_UNVERIFIED_RETRIES) {
                        deepUnverifiedRetries[channelName] = retries + 1
                    } else {
                        deepBackfilledChannels.add(channelName)
                        deepBackfillFloor.remove(channelName)
                        deepUnverifiedRetries.remove(channelName)
                        // The whole history is in, so a send that did land has its copy to match
                        // (iOS e5a9b11).
                        settlePendingSendsFromBeforeThisRun(channelName)
                    }
                }
            }
            if (interrupted) -1 else fetched
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort on top of live scanning - the loop just tries again next tick.
            noteIndexerResult(base, e)
            android.util.Log.w("BroadcastRepository", "Indexer fetch failed for $channelName", e)
            -1
        }
    }

    /**
     * Inserts an optimistic "pending" placeholder immediately (before the network call) so the
     * message shows up in the room right away and stays visible even if the send fails — matches
     * ChatViewModel.sendMessage's exact pattern for 1:1 chats. On success the placeholder is
     * swapped for the real message (real txId, "sent"); on failure it flips to "failed" in place
     * with a Retry option, rather than disappearing silently. [dropRowOnFailure]: an edit, whose
     * row goes instead - see [sendEdit].
     */
    suspend fun sendBroadcast(
        channelName: String,
        content: String,
        feeRateOverride: Long? = null,
        dropRowOnFailure: Boolean = false,
    ): String {
        val myAddress = walletManager.getAddress()
        val pendingId = "pending_${java.util.UUID.randomUUID()}"
        // This run is working on it, so the leftover-pending settle leaves it alone (iOS e5a9b11).
        pendingSendsThisRun.add(pendingId)
        database.broadcastDao().insertMessage(
            BroadcastMessageEntity(
                id = pendingId,
                channelName = channelName,
                senderAddress = myAddress,
                content = content,
                blockTimestamp = System.currentTimeMillis(),
                deliveryStatus = "pending"
            )
        )
        try {
            val txId = walletService.sendBroadcast(channelName, content, feeRateOverride = feeRateOverride).txId
            // Signed here, so its sender is known when it comes back from the chain (XP-012).
            senderVerifier.rememberOwnBroadcast(txId, myAddress)
            database.broadcastDao().deleteMessage(pendingId)
            database.broadcastDao().insertMessage(
                BroadcastMessageEntity(
                    id = txId,
                    channelName = channelName,
                    senderAddress = myAddress,
                    content = content,
                    blockTimestamp = System.currentTimeMillis(),
                    deliveryStatus = "sent"
                )
            )
            return txId
        } catch (e: com.kachat.app.services.SendOutcomeUnknownException) {
            // It may have landed, so no Retry, which would post and pay twice. "Not confirmed"
            // until its delivered copy shows up (iOS 33095ac). A reaction or an edit is a row
            // here too, so it is marked the same way.
            markMessageUnconfirmed(pendingId, e.txId)
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Never "failed": it may have gone out, and a Retry could post and pay twice. Left
            // pending for the settle, like an unknown outcome.
            throw e
        } catch (e: Exception) {
            if (dropRowOnFailure) {
                pendingSendsThisRun.remove(pendingId)
                database.broadcastDao().deleteMessage(pendingId)
            } else {
                database.broadcastDao().updateMessageStatus(pendingId, "failed")
            }
            throw e
        }
    }

    /** Re-attempts a failed broadcast, reusing its same "pending_<uuid>" id/content — the same placeholder resurrected, not a new message, matching ChatViewModel.retrySendMessage. */
    suspend fun retryBroadcast(message: BroadcastMessageEntity) {
        // Only a failed send of this account's, and only once: a second tap before the list
        // re-emits would send it twice (iOS `retryPublicChatMessage` guards the same).
        if (message.senderAddress != walletManager.getAddress()) return
        // Pending again and worked on by this run, so the settle must not touch it (iOS e5a9b11).
        pendingSendsThisRun.add(message.id)
        if (database.broadcastDao().claimFailedForRetry(message.id) == 0) return
        try {
            val txId = walletService.sendBroadcast(message.channelName, message.content).txId
            senderVerifier.rememberOwnBroadcast(txId, message.senderAddress)
            database.broadcastDao().deleteMessage(message.id)
            database.broadcastDao().insertMessage(
                BroadcastMessageEntity(
                    id = txId,
                    channelName = message.channelName,
                    senderAddress = message.senderAddress,
                    content = message.content,
                    blockTimestamp = System.currentTimeMillis(),
                    deliveryStatus = "sent"
                )
            )
        } catch (e: com.kachat.app.services.SendOutcomeUnknownException) {
            // May have landed: "Not confirmed" with no Retry (iOS 33095ac). Passed on so a room
            // message can say so; a reaction's retry says nothing, as iOS's `try?` does.
            markMessageUnconfirmed(message.id, e.txId)
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Left pending, never "failed" (see [sendBroadcast]).
            throw e
        } catch (e: Exception) {
            database.broadcastDao().updateMessageStatus(message.id, "failed")
        }
    }

    /**
     * A long voice note (iOS 1b15521 `sendPublicChatVoiceParts`): each part a complete WebM-Opus
     * file sent as its own ordinary voice message, one transaction each, tagged `voicePart` with
     * one random id (KaChat's format, KaChat MESSAGING.md "Long voice notes (multi-part)"). Parts
     * belong together by (room, sender, id, count).
     *
     * The balance for every part is checked first. Every part then shows at once, Sending, a
     * millisecond apart so they keep their order, and they go out in index order, one after
     * another, so each spends the change of the one before. When one fails the ones after it are
     * marked Failed too, for one Retry to send them in order. Parts never carry a reply: the reply
     * envelope would push a part past what one transaction holds. [onQueued] runs once the parts
     * show (the caller drops its reply then). A note of one part goes as an ordinary voice
     * message ([sendBroadcast]) instead.
     */
    suspend fun sendVoiceParts(channelName: String, parts: List<ByteArray>, onQueued: () -> Unit = {}) {
        if (parts.size < 2 || parts.size > com.kachat.app.util.VoicePartTag.MAX_PARTS || parts.any { it.isEmpty() }) {
            throw IllegalArgumentException("Audio file is empty")
        }
        val myAddress = walletManager.getAddress()
        val mimeType = "audio/webm"
        val noteId = com.kachat.app.util.VoicePartTag.newGroupId()
        val contents = parts.mapIndexed { offset, data ->
            val tag = com.kachat.app.util.VoicePartTag.of(noteId, offset + 1, parts.size)
                ?: throw IllegalArgumentException("Audio file is empty")
            com.kachat.app.util.MediaFileEnvelope.json(
                name = tag.fileName,
                size = data.size,
                mimeType = mimeType,
                dataUrlContent = "data:$mimeType;base64," + android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP),
                voicePart = tag,
            )
        }

        // The whole note first, before a single part goes out.
        ensureSufficientBalanceForVoiceParts(channelName, contents, myAddress)

        val dao = database.broadcastDao()
        val start = System.currentTimeMillis()
        val pending = contents.mapIndexed { offset, content ->
            BroadcastMessageEntity(
                id = "pending_${java.util.UUID.randomUUID()}",
                channelName = channelName,
                senderAddress = myAddress,
                content = content,
                blockTimestamp = start + offset,
                deliveryStatus = "pending",
            )
        }
        for (row in pending) {
            pendingSendsThisRun.add(row.id)
            dao.insertMessage(row)
        }
        onQueued()

        for ((offset, row) in pending.withIndex()) {
            try {
                sendPendingVoicePart(row)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failRemainingParts(pending.drop(offset + 1))
                throw e
            }
        }
    }

    /**
     * Retry on a long voice note's bubble (iOS 1b15521 `retryPublicChatVoiceParts`): only its
     * failed parts, one after another in index order, so each spends the change of the one
     * before. All of them show Sending at once; when one fails again, the ones after it go back to
     * Failed for the next Retry. Says nothing on a failure, as [retryBroadcast].
     */
    suspend fun retryVoiceParts(failedParts: List<BroadcastMessageEntity>) {
        val myAddress = walletManager.getAddress()
        val dao = database.broadcastDao()
        val claimed = ArrayList<BroadcastMessageEntity>()
        for (part in failedParts) {
            if (part.deliveryStatus != "failed" || part.senderAddress != myAddress) continue
            pendingSendsThisRun.add(part.id)
            // Only once: a second tap before the list re-emits would send it twice.
            if (dao.claimFailedForRetry(part.id) > 0) claimed.add(part.copy(deliveryStatus = "pending"))
        }
        for ((offset, part) in claimed.withIndex()) {
            try {
                sendPendingVoicePart(part)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("BroadcastRepository", "Voice note part retry failed", e)
                failRemainingParts(claimed.drop(offset + 1))
                return
            }
        }
    }

    /** Sends a part whose row is already pending, under its own id; the sent row keeps the
     *  pending row's time, so the note keeps its order. Throws when it failed (the row is then
     *  Failed, or Not confirmed for an unknown outcome). */
    private suspend fun sendPendingVoicePart(row: BroadcastMessageEntity) {
        val dao = database.broadcastDao()
        try {
            val txId = walletService.sendBroadcast(row.channelName, row.content).txId
            senderVerifier.rememberOwnBroadcast(txId, row.senderAddress)
            dao.deleteMessage(row.id)
            dao.insertMessage(row.copy(id = txId, deliveryStatus = "sent"))
        } catch (e: com.kachat.app.services.SendOutcomeUnknownException) {
            markMessageUnconfirmed(row.id, e.txId)
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Left pending, never "failed" (see [sendBroadcast]).
            throw e
        } catch (e: Exception) {
            dao.updateMessageStatus(row.id, "failed")
            throw e
        }
    }

    /** The parts after one that failed never went: Failed too, so one Retry sends them in order. */
    private suspend fun failRemainingParts(parts: List<BroadcastMessageEntity>) {
        val dao = database.broadcastDao()
        for (part in parts) {
            pendingSendsThisRun.remove(part.id)
            dao.updateMessageStatus(part.id, "failed")
        }
    }

    /**
     * What all of a note's parts cost together, against the coins there are, leaving the dust
     * threshold (0.2 KAS) over so the last part still has a change output to stand on (iOS
     * 1b15521 `ensureSufficientBalanceForVoiceParts`). A check that can't be made (the node
     * unreachable) lets the send go on and fail on its own.
     */
    private suspend fun ensureSufficientBalanceForVoiceParts(channelName: String, contents: List<String>, walletAddress: String) {
        var totalFee = 0L
        for (content in contents) {
            val payload = MessageProtocol.buildBcastPayload(channelName, content)
            val mass = com.kachat.app.util.KaspaMass.calculateMass(numInputs = 1, outputScriptLens = listOf(34), payloadSize = payload.size)
            totalFee += com.kachat.app.util.KaspaMass.calculateFee(mass, null)
        }
        val utxos = try {
            walletService.fetchMessageUtxosOrThrow(walletAddress)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BroadcastRepository", "Voice note balance precheck unavailable, continuing send", e)
            return
        }
        val available = utxos.sumOf { it.utxoEntry.amount }
        if (available <= 0L) throw com.kachat.app.services.NoCoinFreeYetException()
        val required = totalFee + VOICE_NOTE_DUST_THRESHOLD_SOMPI
        if (available < required) {
            throw IllegalStateException(
                String.format(
                    java.util.Locale.US,
                    com.kachat.app.util.SendFailureHint.INSUFFICIENT_BALANCE_TEMPLATE,
                    kasAmount(required),
                    kasAmount(available),
                )
            )
        }
    }

    private fun kasAmount(sompi: Long): String =
        java.math.BigDecimal.valueOf(sompi).movePointLeft(8).stripTrailingZeros().toPlainString()
}
