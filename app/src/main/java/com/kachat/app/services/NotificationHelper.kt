package com.kachat.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kachat.app.MainActivity
import com.kachat.app.R
import com.kachat.app.models.ContactNotificationMode
import com.kachat.app.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local notifications for new messages/handshakes/payments — fired directly from
 * ChatRepository's existing poll loop. There's no server-side push infrastructure yet
 * (see the commented-out FCM service in AndroidManifest), so these only fire while the
 * app process is alive (foreground or recently backgrounded), not after the OS has
 * fully killed it. Settings > Notifications controls whether these fire at all, and
 * whether they carry sound/vibration.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsRepository
) {
    /** The localized words a 1:1 banner body uses (dmNotificationText), in the app's language. */
    fun dmNotificationLabels(): com.kachat.app.util.DmNotificationLabels =
        com.kachat.app.util.DmNotificationLabels.from(context)

    // Suppresses a notification for whichever contact's thread is currently on screen —
    // set by ChatViewModel as ChatThreadScreen opens/closes.
    private val activeContactId = MutableStateFlow<String?>(null)

    // Same idea for broadcast channels — set by BroadcastViewModel's startLiveViewing/stopLiveViewing.
    private val activeChannelName = MutableStateFlow<String?>(null)

    // Same idea for group chats — set by GroupChatViewModel as GroupChatThreadScreen opens/closes.
    private val activeGroupId = MutableStateFlow<String?>(null)

    // Whether any conversation thread (1:1 or group) is on screen right now — kept in lockstep
    // with the two flows above by their setters, so the existing screen open/close calls feed it
    // without any screen changes. Broadcast channels deliberately don't count: the Nextcloud
    // mirror carries 1:1 and group history, not channel posts.
    private val openChat = MutableStateFlow(false)

    // Whether the app is on screen right now — set by KaChatApplication's process lifecycle
    // observer. Foreground notification policy: banners DO fire while the user is elsewhere in
    // the app (chat list, another thread, Settings); only the conversation currently open on
    // screen is suppressed, via the activeContactId/activeChannelName/activeGroupId checks above.
    private val appForeground = MutableStateFlow(false)

    // Recently-notified transaction ids, so the same message reaching two delivery paths at once
    // (in-app poll/scan AND an FCM push, both live while the app is foregrounded) posts exactly
    // one banner. LRU-capped; access is synchronized because the claimants run on IO coroutines
    // and the FCM service thread.
    private val notifiedTxIds = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    // A KaPosts tip is also a chat payment, and its two pushes - "tipped your post" and the chat
    // payment - carry the same txid (IDX-028, KAPOSTS_INDEXER.md 5.11). The phone keeps one
    // notification, as iOS does through the shared apns-collapse-id (FCM's collapse_key only
    // collapses undelivered messages, never a posted banner): the KaPosts banner replaces the
    // payment banner when it arrives, and a payment banner for a tip whose KaPosts banner already
    // shows is not posted. Without a KaPosts banner the payment one stays.
    // chatBannerTxIds: which tx each chat banner was last posted for (notification id -> txid).
    // kaPostsBannerTxIds: the txids a KaPosts banner was posted for, LRU-capped.
    private val chatBannerTxIds = HashMap<Int, String>()
    private val kaPostsBannerTxIds = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 200
    }

    @Synchronized
    private fun hasKaPostsBanner(txId: String): Boolean = kaPostsBannerTxIds.containsKey(txId)

    @Synchronized
    private fun recordChatBanner(notificationId: Int, txId: String?) {
        if (txId.isNullOrBlank()) chatBannerTxIds.remove(notificationId) else chatBannerTxIds[notificationId] = txId
    }

    /** Records [txId]'s KaPosts banner and returns the chat banners still showing that same tx
     *  (a tip's payment banner), which it replaces. */
    @Synchronized
    private fun takeChatBannersReplacedByKaPosts(txId: String): List<Int> {
        kaPostsBannerTxIds[txId] = true
        val ids = chatBannerTxIds.filterValues { it == txId }.keys.toList()
        ids.forEach { chatBannerTxIds.remove(it) }
        return ids
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A channel's sound/vibration is fixed by the OS once created — the app can't
            // flip it later, only the user can (in system notification settings). The only
            // way an in-app Sound/Vibration toggle can actually do anything on API 26+ is
            // to pre-create one channel per combination and post through whichever one
            // matches the current settings.
            val manager = context.getSystemService(NotificationManager::class.java)
            CHANNELS.forEach { (channelId, soundOn, vibrationOn) ->
                // KaPost's wording, one name per combination: four entries all called "Messages"
                // couldn't be told apart in Android's settings.
                val name = when {
                    soundOn && vibrationOn -> "KaPost - sound and vibration"
                    soundOn -> "KaPost - sound only"
                    vibrationOn -> "KaPost - vibration only"
                    else -> "KaPost - silent"
                }
                val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Likes, replies, follows and Public Chats rooms you get notified for"
                    enableVibration(vibrationOn)
                    if (!soundOn) setSound(null, null)
                }
                manager?.createNotificationChannel(channel)
            }
            // The pre-4.0 background-sync foreground service ("KaChat - Checking for new
            // messages") is gone — remote push is the closed-app delivery path now. Deleting its
            // channel removes the stale "Background sync" entry from the system's notification
            // settings on devices that upgraded, and guarantees nothing can post through it.
            manager?.deleteNotificationChannel("kachat_sync_service")
        }
    }

    /** See [appForeground] — driven by KaChatApplication's ProcessLifecycleOwner observer. */
    fun setAppForeground(foreground: Boolean) {
        appForeground.value = foreground
    }

    /** Whether the app is on screen. No longer a banner gate: while push is active the remote
     *  push is the only banner source, foreground included (see [PushState]); the txId ledger
     *  in [claimTxId] stays so the push's own two paths (FCM handler and a push-triggered sync)
     *  cannot double-post. */
    val isAppInForeground: Boolean get() = appForeground.value

    /** Reactive form of [isAppInForeground] — drives lifecycle-scoped loops that must start on
     *  foreground and stop on background (e.g. NextcloudSyncService's remote change watcher). */
    val appForegroundFlow: StateFlow<Boolean> = appForeground.asStateFlow()

    /** Reactive form of "a conversation thread is open on screen" — mirrors [appForegroundFlow].
     *  Drives NextcloudSyncService's adaptive mirror cadence: fast tier while the user is inside
     *  a chat (where a mirrored message is visible the moment it lands), relaxed tier elsewhere. */
    val openChatFlow: StateFlow<Boolean> = openChat.asStateFlow()

    /** Snapshot form of [openChatFlow] for one-shot reads (e.g. picking a debounce tier). */
    val isChatOpen: Boolean get() = openChat.value

    /**
     * Claims [txId] for notification purposes. Returns true when this caller is the first to
     * claim it (post the banner); false when another path already did (skip — it's a duplicate).
     * A null/blank id always claims successfully (nothing to dedupe on).
     */
    @Synchronized
    private fun claimTxId(txId: String?): Boolean {
        if (txId.isNullOrBlank()) return true
        return notifiedTxIds.put(txId, true) == null
    }

    /**
     * Records [txId] as notified WITHOUT posting a banner. Called by a local delivery path that
     * decrypted the tx and deliberately chose silence (mentions-only group without a mention,
     * muted member, reaction to someone else's message in a mentions-only group): the silence is
     * a final decision, so claiming here guarantees a racing FCM push for the same tx can never
     * post the banner the local path just suppressed.
     */
    @Synchronized
    fun claimWithoutNotifying(txId: String?) {
        if (!txId.isNullOrBlank()) notifiedTxIds[txId] = true
    }

    /** Claims [txId] for a banner another path posts itself: false when one was already shown
     *  for it. A plain payment to the chatting address can banner from the payment sync and from
     *  [AddressActivityNotifier] (iOS 56b75c8) - whichever comes first is the only one. */
    fun claimForBanner(txId: String): Boolean = claimTxId(txId)

    /** Screens showing a 1:1 conversation, newest last. The chat thread and the chess board over
     *  it both hold the contact, and Compose Navigation disposes the thread only after the board
     *  has entered - a plain "set null on dispose" then cleared the board's hold, which stopped
     *  the open-chat fast poll while a game was on screen (iOS c1ee033). */
    private val contactHolds = mutableListOf<String>()

    fun enterContact(contactId: String) {
        synchronized(contactHolds) {
            contactHolds.add(contactId)
            activeContactId.value = contactId
        }
        refreshOpenChat()
    }

    fun leaveContact(contactId: String) {
        synchronized(contactHolds) {
            val index = contactHolds.lastIndexOf(contactId)
            if (index >= 0) contactHolds.removeAt(index)
            activeContactId.value = contactHolds.lastOrNull()
        }
        refreshOpenChat()
    }

    fun setActiveChannel(channelName: String?) {
        activeChannelName.value = channelName
    }

    /** Clears the room on screen only if it is still [channelName]: moving from room A to room B,
     *  A's screen goes away after B's has set itself, and a plain clear would drop B's. */
    fun clearActiveChannel(channelName: String) {
        activeChannelName.compareAndSet(channelName, null)
    }

    fun setActiveGroup(groupId: String?) {
        activeGroupId.value = groupId
        refreshOpenChat()
    }

    private fun refreshOpenChat() {
        openChat.value = activeContactId.value != null || activeGroupId.value != null
    }

    /** Lets `GroupRepository` check this without needing its own copy of the state - used to keep
     *  a group marked read in real time while its thread is on screen, not just once on open. */
    fun isViewingGroup(groupId: String): Boolean = activeGroupId.value == groupId

    /** The group whose thread is on screen, if any - the live group poll reads it every few
     *  seconds (see GroupScanningService). */
    val viewingGroupId: String? get() = activeGroupId.value

    /** Same for 1:1 threads: a message that lands WHILE its conversation is on screen inserts as
     *  already-read, so leaving the chat doesn't show an unread badge for messages you watched arrive. */
    fun isViewingContact(contactId: String): Boolean = activeContactId.value == contactId

    /** The room on screen, if any - the foreground sweep skips it (it has its own poll). */
    fun isViewingChannel(channelName: String): Boolean = activeChannelName.value == channelName

    /** The contact whose 1:1 thread is on screen right now (null when none) — lets
     *  ChatRepository's poll loop keep just the open conversation on the fast tick while the
     *  full contact sweep is throttled. */
    val currentContactId: String? get() = activeContactId.value

    /** The one notification a new Message Request gets: "New message request - <address> wants
     *  to chat with you." (NO_HANDSHAKE_MESSAGING.md §4). The caller decides it is the first. */
    suspend fun showMessageRequest(contactId: String, dedupeTxId: String? = null) {
        show(
            contactId = contactId,
            title = context.getString(com.kachat.app.R.string.new_message_request),
            text = context.getString(
                com.kachat.app.R.string.wants_to_chat_with_you,
                com.kachat.app.util.KaspaAddress.shortDisplay(contactId)
            ),
            dedupeTxId = dedupeTxId,
        )
    }

    /**
     * [tipTxId]: this payment is a KaPosts tip (IDX-030). Its banner is posted under tag = the
     * tip's txid, id 0 - the tag the indexer puts on the OS-drawn "tipped your post" push while
     * the app is in the background - so whichever arrives second replaces the other and the tip
     * keeps one notification, as iOS does with its collapse id.
     */
    suspend fun show(contactId: String, title: String, text: String, notificationOverride: ContactNotificationMode? = null, dedupeTxId: String? = null, tipTxId: String? = null) {
        if (activeContactId.value == contactId) return // already looking at this conversation
        if (!settings.notificationsEnabled.first()) return
        if (notificationOverride == ContactNotificationMode.OFF) return
        if (!claimTxId(dedupeTxId)) return // the other delivery path (poll vs push) got here first
        // A tip's payment: its KaPosts "tipped your post" banner already shows, and that is the
        // tip's one notification (IDX-028).
        if (!dedupeTxId.isNullOrBlank() && hasKaPostsBanner(dedupeTxId)) return
        // ...or the OS drew it from the push (tagged with the txid): it stays the one notification.
        if (!tipTxId.isNullOrBlank() && isTagShowing(tipTxId)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
            putExtra(EXTRA_CONTACT_ID, contactId)
        }
        // Stable per-contact request code so a burst of updates for the same
        // conversation replaces the pending intent instead of leaking a new one each time.
        // Namespaced ("chat:"), like every other family below: the request code doubles as the
        // PendingIntent key, so two families sharing one raw hash would have made a tap on one
        // notification open the other one's destination.
        val notificationId = "chat:$contactId".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Off/No Sound/Sound override wins over the global sound setting; vibration always
        // follows the global setting regardless — the override is specifically about sound.
        val soundEnabled = when (notificationOverride) {
            ContactNotificationMode.SOUND -> true
            ContactNotificationMode.NO_SOUND -> false
            else -> settings.notificationSoundEnabled.first()
        }
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val channelId = channelFor(soundEnabled, vibrationEnabled)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Below API 26 there are no channels — these are what actually apply there.
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()

        try {
            if (!tipTxId.isNullOrBlank()) {
                NotificationManagerCompat.from(context).notify(tipTxId, TIP_TAG_ID, notification)
            } else {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
                recordChatBanner(notificationId, dedupeTxId)
            }
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — skip rather than crash.
        }
    }

    /** Per-channel opt-in notification for a new broadcast message — see [EXTRA_CHANNEL_NAME]/BroadcastScanningService. */
    /** KaPosts social ping ("alice liked your post") - a tap deep-opens the exact
     *  post/comment when [postTxId] is known, else just the KaPosts tab. [focusTxId] is the
     *  acting content's txid (a reply's own transaction) - the opened thread scrolls to it. */
    suspend fun showKaPosts(text: String, actionTxId: String, postTxId: String? = null, focusTxId: String? = null) {
        if (!settings.notificationsEnabled.first()) return
        if (!claimTxId("kaposts_$actionTxId")) return // poller and FCM push can both see the same action
        // Child Mode removes KaPosts entirely - no notification pings for it either. Guarding
        // here covers every source at once: the foreground poller AND the FCM receive handler
        // (push registration already drops kaposts_pubkey, but a push can race re-registration).
        if (settings.childModeEnabled.first()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
            putExtra(EXTRA_OPEN_KAPOSTS, true)
            if (!postTxId.isNullOrBlank()) putExtra(EXTRA_KAPOST_TXID, postTxId)
            if (!focusTxId.isNullOrBlank()) putExtra(EXTRA_KAPOST_FOCUS_TXID, focusTxId)
        }
        val notificationId = "kaposts_$actionTxId".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val notification = NotificationCompat.Builder(context, channelFor(soundEnabled, vibrationEnabled))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("KaPost")
            .setContentText(text)
            // iOS threadIdentifier "kaposts": what cancelKaPostsNotifications recognizes it by.
            .setGroup(KAPOSTS_GROUP)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            synchronized(shownKaPostsIds) { shownKaPostsIds.add(notificationId) }
            // A tip: its payment banner, if one shows, gives way to this one (IDX-028).
            val replaced = takeChatBannersReplacedByKaPosts(actionTxId)
            val manager = NotificationManagerCompat.from(context)
            replaced.forEach { manager.cancel(it) }
            // ...and so does a tip's payment banner posted under its txid tag (IDX-030).
            if (actionTxId.isNotBlank()) manager.cancel(actionTxId, TIP_TAG_ID)
        } catch (_: SecurityException) {}
    }

    /** Whether a notification tagged [tag] is showing - the OS draws a push's notification under
     *  the tag the server sets (android.notification.tag), id 0. */
    private fun isTagShowing(tag: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .activeNotifications.any { it.tag == tag }
            }.getOrDefault(false)
        } else false

    /** Ids of the KaPosts banners posted this process - the fallback below API 23, where the
     *  shade's notifications can't be listed. */
    private val shownKaPostsIds = mutableSetOf<Int>()

    /**
     * Takes down every KaPosts notification in the shade once the reader has seen the stream
     * (iOS markSeen clears the "kaposts" thread), not only the ones this process drew.
     *
     * Ours carry the [KAPOSTS_GROUP] group. The ones FCM drew by itself while the app was in the
     * background carry neither our group nor a KaPosts channel (the default channel, tag = the
     * txid, as a room's push), and their data payload sits in the tap's intent, which can't be
     * read back. Their title tells them apart: the push service sends KaPosts pushes titled
     * "KaPosts" and room pushes "#room" (indexer push.rs).
     */
    fun cancelKaPostsNotifications() {
        val ids = synchronized(shownKaPostsIds) { shownKaPostsIds.toList().also { shownKaPostsIds.clear() } }
        val manager = NotificationManagerCompat.from(context)
        for (id in ids) manager.cancel(id)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val system = context.getSystemService(NotificationManager::class.java) ?: return
            for (sbn in system.activeNotifications) {
                val title = sbn.notification.extras
                    ?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                val isKaPosts = sbn.notification.group == KAPOSTS_GROUP ||
                    title == "KaPosts" || title == "KaPost"
                if (isKaPosts) system.cancel(sbn.tag, sbn.id)
            }
        }
    }

    /** Wallet address-activity notification ("Received X KAS" on a spending/cold address) — see
     *  [AddressActivityNotifier]. Gated only by the global notifications toggle here; the
     *  Address Activity setting itself is checked by the notifier before calling.
     *
     *  [kind] is [KIND_COLD] or [KIND_SPENDING] (iOS sends the same string in the notification's
     *  `userInfo["kind"]`): the tap opens Storage or Manage Addresses respectively, so the
     *  receipt lands on a screen that actually shows the address it was about. Without it the
     *  tap only re-opened whatever screen the app was last on.
     *
     *  [address] names the one address the Kaspa landed on (iOS 13046ad, `userInfo["address"]`):
     *  the tap then opens that address's own History, in a sheet over the current tab, and
     *  [kind] is only the fallback for a receipt that spans several addresses. */
    suspend fun showAddressActivity(
        title: String,
        text: String,
        dedupeKey: String,
        kind: String = KIND_SPENDING,
        address: String? = null,
    ) {
        if (!settings.notificationsEnabled.first()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
            putExtra(EXTRA_WALLET_ACTIVITY_KIND, kind)
            if (!address.isNullOrEmpty()) putExtra(EXTRA_WALLET_ACTIVITY_ADDRESS, address)
        }
        val notificationId = "addr_activity_$dedupeKey".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val notification = NotificationCompat.Builder(context, channelFor(soundEnabled, vibrationEnabled))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    /** [pushSender] / [pushPreview]: a room push's sender (its `subtitle`) and the indexer's own
     *  preview (its `body`), for [RoomVoicePushBursts]; null from the block scan. */
    suspend fun showBroadcast(
        channelName: String,
        title: String,
        text: String,
        dedupeTxId: String? = null,
        pushSender: String? = null,
        pushPreview: String? = null,
    ) {
        // Every broadcast notification funnels through here - the live block scan
        // (BroadcastScanningService) AND the remote push (KaChatFirebaseMessagingService, whose
        // `body` is whatever the indexer relayed, i.e. the raw on-chain content). Humanizing at
        // this single point is what stops a reply/photo/chess envelope's raw JSON reaching the
        // shade, and keeps both delivery paths' wording identical.
        val bodyText = broadcastNotificationText(text)
        // Already looking at this channel - only while the app is in front (iOS KaPostsApp
        // willPresent): a room left open behind the home button notifies as usual.
        if (activeChannelName.value == channelName && appForeground.value) return
        // A long voice note's later parts, while the app is open: listed, no banner or sound
        // (iOS 1b15521 KaPostsApp willPresent).
        val quietBurst = appForeground.value && pushSender != null && pushPreview != null &&
            RoomVoicePushBursts.isLaterPart(channelName, pushSender, pushPreview)
        if (!settings.notificationsEnabled.first()) return
        // Child Mode removes Broadcasts entirely - no local banners for them either. Covers the
        // scan-driven path (BroadcastScanningService) AND the FCM receive handler in one place
        // (registration already drops watched_broadcast_channels, but a push can race it).
        if (settings.childModeEnabled.first()) return
        if (!claimTxId(dedupeTxId)) return // the other delivery path (scan vs push) got here first

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
            putExtra(EXTRA_CHANNEL_NAME, channelName)
        }
        // Stable per-channel request code, same reasoning as the per-contact one above.
        val notificationId = "bcast:$channelName".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (quietBurst) {
            // Listed only, in the silent channel and under its own id per (room, sender): the
            // next later part replaces it, so the quiet one before it goes, and the first part's
            // banner stays (iOS 1b15521 `RoomVoicePushBursts`).
            val quiet = NotificationCompat.Builder(context, channelFor(soundEnabled = false, vibrationEnabled = false))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(bodyText)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(RoomVoicePushBursts.quietNotificationId(channelName, pushSender.orEmpty()), quiet)
            } catch (_: SecurityException) {}
            return
        }

        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val channelId = channelFor(soundEnabled, vibrationEnabled)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — skip rather than crash.
        }
    }

    /**
     * Per-group opt-in notification for a new `gcomm` message or a "you were added" `gctl_root`
     * join — see [EXTRA_GROUP_ID]/GroupRepository.
     *
     * [groupId] is the notification's identity (dedupe + open-thread suppression + request code).
     * [targetGroupId] is what the TAP opens, and defaults to the same thing. A remote push whose
     * only group handle is a blinded id or a tx id (see KaChatFirebaseMessagingService's
     * `group_control` / un-ingested `group_message` fallbacks) must pass null instead: those
     * strings are not local group ids, and routing `group_chat/<blinded id>` landed the user on a
     * permanently empty thread with no title and a composer that could not send. Null routes the
     * tap to the Group Chats list, which is the closest destination that actually exists.
     */
    suspend fun showGroup(
        groupId: String,
        title: String,
        text: String,
        dedupeTxId: String? = null,
        targetGroupId: String? = groupId,
    ) {
        if (activeGroupId.value == groupId) return // already looking at this group's thread
        if (!settings.notificationsEnabled.first()) return
        if (!claimTxId(dedupeTxId)) return // the other delivery path (scan/sync vs push) got here first

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERNAL_TOKEN, internalToken(context))
            if (targetGroupId != null) {
                putExtra(EXTRA_GROUP_ID, targetGroupId)
            } else {
                putExtra(EXTRA_OPEN_GROUPS, true)
            }
        }
        // Stable per-group request code, same reasoning as the per-contact/per-channel ones above.
        val notificationId = "group:$groupId".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundEnabled = settings.notificationSoundEnabled.first()
        val vibrationEnabled = settings.notificationVibrationEnabled.first()
        val channelId = channelFor(soundEnabled, vibrationEnabled)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!soundEnabled && !vibrationEnabled)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 250, 250, 250) else longArrayOf(0))
            .apply { if (!soundEnabled) setSound(null) }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — skip rather than crash.
        }
    }

    companion object {
        /** The id a tagged tip banner uses: the OS posts an FCM notification under its tag with
         *  id 0, so ours must match it to replace it (IDX-030). */
        const val TIP_TAG_ID = 0
        /**
         * MainActivity is exported (it takes kachat://, kaposts:// and kachat.app links), so any
         * app could start it with our notification extras. Every intent built here carries this
         * per-install random token, and MainActivity honours the extras only when it matches
         * ([isInternalIntent]). Generated once, kept in app-private prefs.
         */
        const val EXTRA_INTERNAL_TOKEN = "kapost_internal_token"
        private const val TOKEN_PREFS = "kapost_notification_token"
        private const val TOKEN_KEY = "token"
        @Volatile private var cachedToken: String? = null

        @Synchronized
        fun internalToken(context: Context): String {
            cachedToken?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
            val token = prefs.getString(TOKEN_KEY, null) ?: run {
                val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                bytes.joinToString("") { "%02x".format(it) }.also { prefs.edit().putString(TOKEN_KEY, it).apply() }
            }
            cachedToken = token
            return token
        }

        /** Whether [intent] was built by this class (see [EXTRA_INTERNAL_TOKEN]). */
        fun isInternalIntent(context: Context, intent: Intent): Boolean {
            val presented = runCatching { intent.getStringExtra(EXTRA_INTERNAL_TOKEN) }.getOrNull() ?: return false
            return java.security.MessageDigest.isEqual(
                presented.toByteArray(),
                internalToken(context).toByteArray(),
            )
        }

        /** The group every KaPosts banner is posted in (iOS threadIdentifier "kaposts"). */
        const val KAPOSTS_GROUP = "kaposts"
        /**
         * Friendly one-line preview for a broadcast room's notification, mirroring the mapping
         * 1:1 chats use (`ChatRepository`'s `notificationText`, and the identical one in
         * `KaChatFirebaseMessagingService.notificationPreview`).
         *
         * Broadcast content is the raw on-chain `bcast` body, which for anything but plain text
         * is a JSON envelope: a reply ([MessageReply]), a voice note, a photo, a chess move, or a
         * reaction. Without this the shade showed the envelope verbatim — the reported bug was a
         * reply's `{"type":"reply",...}` blob.
         *
         * A reply shows its own text (unwrapping one level, then mapping that in turn, so a voice
         * reply reads as a voice note rather than a JSON blob); every other envelope becomes its
         * placeholder. Wording matches what the chat list and the scan path already print for the
         * same envelopes, so the two delivery paths for one message never disagree. Idempotent:
         * an already-humanized string isn't JSON, so it passes straight through.
         */
        fun broadcastNotificationText(content: String): String {
            com.kachat.app.util.MessageReaction.parseOrNull(content)?.let { return "Reacted ${it.emoji}" }
            if (com.kachat.app.util.MessageEdit.parseOrNull(content) != null) return "Edited a message"
            val unwrapped = com.kachat.app.util.MessageReply.parseOrNull(content)?.text ?: content
            if (com.kachat.app.util.VoiceMessage.parseOrNull(unwrapped) != null) return "🎤 Audio message"
            if (com.kachat.app.util.ImageMessage.parseOrNull(unwrapped) != null) return "📷 Photo"
            if (com.kachat.app.util.ChessMessage.parseOrNull(unwrapped) != null) return "♟️ Chess game"
            com.kachat.app.util.VoiceMessage.parseAnyFileOrNull(unwrapped)?.let {
                return if (it.mimeType.startsWith("video/")) "🎬 Video" else "📎 File"
            }
            return unwrapped
        }

        const val CHANNEL_ID = "kachat_messages_sound_vibrate"
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_OPEN_KAPOSTS = "open_kaposts"
        const val EXTRA_KAPOST_TXID = "kapost_txid"
        const val EXTRA_KAPOST_FOCUS_TXID = "kapost_focus_txid"

        /** Set instead of [EXTRA_GROUP_ID] when the notification has no resolvable local group —
         *  the tap opens the Group Chats list rather than a `group_chat/<id>` route that would
         *  resolve to nothing. See [showGroup]'s `targetGroupId`. */
        const val EXTRA_OPEN_GROUPS = "open_groups"

        /** [KIND_COLD] / [KIND_SPENDING] — which wallet screen an address-activity tap opens. */
        const val EXTRA_WALLET_ACTIVITY_KIND = "wallet_activity_kind"
        const val KIND_COLD = "cold"
        const val KIND_SPENDING = "spending"
        /** The chatting address (iOS 56b75c8): its receipt always names the address, so the tap
         *  opens its History; as a fallback it lands where a spending receipt does. */
        const val KIND_CHATTING = "chatting"
        /** The single own address an address-activity notification is about; its tap opens
         *  that address's History (iOS 13046ad). Absent for a receipt across several addresses. */
        const val EXTRA_WALLET_ACTIVITY_ADDRESS = "wallet_activity_address"

        // (channelId, soundEnabled, vibrationEnabled)
        private val CHANNELS = listOf(
            Triple("kachat_messages_sound_vibrate", true, true),
            Triple("kachat_messages_sound_only", true, false),
            Triple("kachat_messages_vibrate_only", false, true),
            Triple("kachat_messages_silent", false, false)
        )

        internal fun channelFor(soundEnabled: Boolean, vibrationEnabled: Boolean) =
            CHANNELS.first { it.second == soundEnabled && it.third == vibrationEnabled }.first
    }
}

/**
 * KaChat's burst rule for parts of a long voice note or a multi-part photo, for room pushes (iOS
 * 1b15521 `RoomVoicePushBursts`, KaChat CLAUDE.md "Parts arrive as bare txIds"). The indexer pushes
 * every part of a long voice note as its own "Voice message", and the push carries no part tag.
 * So a voice push from the same sender in the same room within 60 seconds of the last one is a
 * later part: it is listed without a banner, sound or vibration, and the quiet entry before it is
 * removed (replaced: [quietNotificationId]), leaving the first banner and one quiet entry per
 * note. Two separate voice messages from one sender within a minute ring once; the room still
 * shows both.
 *
 * This only runs while KaPost is open (FCM hands a push with a notification block to the app only
 * then). With the app in the background or closed, the system shows every part's push as it
 * arrives.
 */
object RoomVoicePushBursts {
    /** Parts go out one after another, a few seconds apart. */
    private const val WINDOW_MS = 60_000L

    /** The indexer's previews for a media envelope: "Voice message" for any file or audio today
     *  (a photo too), "Photo" once it tells photos apart (kachat-indexer
     *  docs/ROOM_VOICE_PART_PUSHES.md). Multi-part photos (`mediaPart`) burst the same way. */
    private val MEDIA_PREVIEWS = setOf("Voice message", "Photo")

    /** The last push of each (room, sender) burst. */
    private val bursts = HashMap<String, Long>()

    /** True when this room push is a later part of a voice burst that already rang. Records the
     *  push either way. */
    @Synchronized
    fun isLaterPart(channelName: String, sender: String, body: String): Boolean {
        if (body.trim() !in MEDIA_PREVIEWS) return false
        val now = System.currentTimeMillis()
        bursts.entries.removeAll { now - it.value >= WINDOW_MS }
        val owner = owner(channelName, sender)
        val earlier = bursts[owner]
        bursts[owner] = now
        return earlier != null
    }

    /** The one quiet entry a (room, sender) burst keeps: each later part posts under this id, so
     *  it takes the place of the one before. */
    fun quietNotificationId(channelName: String, sender: String): Int =
        "bcast-burst:${owner(channelName, sender)}".hashCode()

    private fun owner(channelName: String, sender: String): String = "broadcast:$channelName|${sender.lowercase()}"
}
