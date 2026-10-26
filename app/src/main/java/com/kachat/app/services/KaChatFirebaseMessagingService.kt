package com.kachat.app.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Receives FCM messages and turns them into notifications via [NotificationHelper].
 *
 * Two shapes arrive from the server (`kasia-indexer` `push.rs`):
 *  - **Public content** (broadcast / KaPosts) is sent WITH a `notification` block, so the OS shows
 *    it even when the app is dead; `onMessageReceived` also runs when the app is alive.
 *  - 1:1 and group chat pushes are KaChat's; KaPosts ignores them.
 *
 * Data schema:
 *   broadcast : type, channel, title, subtitle, body, thread_id, tx_id
 *   kaposts   : type, title, subtitle, body, thread_id, tx_id, [post_id | postId | content_id]
 *               (the target content's txid, absent for a follow — see [POST_ID_KEYS])
 *   chat/DM   : type(contextual|payment|handshake|group_message|group_control), sender, title,
 *               body, tx_id, timestamp, daa_score, [amount], [enc_payload], [blinded_group_id]
 */
@AndroidEntryPoint
class KaChatFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager
    @Inject lateinit var walletManager: WalletManager
    @Inject lateinit var settingsRepository: com.kachat.app.repository.AppSettingsRepository

    override fun onNewToken(token: String) {
        // FCM rotated the token — re-register it with the indexer (signed with the wallet key).
        Log.i(TAG, "FCM token rotated, re-registering with the push service")
        pushRegistrationManager.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            Log.d(TAG, "push received with empty data payload, ignoring")
            return
        }

        val type = data["type"].orEmpty()
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        Log.i(TAG, "push received: type=$type txId=${data["tx_id"].orEmpty().take(16)}")

        // NotificationHelper.show* are suspend and quick; block so the work completes before the
        // service is torn down.
        runBlocking {
            try {
                // Any push about the other network - the service this device left when it moved
                // between mainnet and testnet still pushing it - stays silent, whatever its type:
                // DMs, payments and handshakes as before, and now group, public chat, KaPosts and
                // .kachat name pushes too (iOS d946a65 rule, audit AND-006). Not filed either: the
                // same key would decrypt it into the wrong account (iOS df52425).
                if (com.kachat.app.util.PushNetworkFilter.isOtherNetwork(data, com.kachat.app.util.KaspaNetwork.launch)) {
                    data["tx_id"]?.takeIf { it.isNotBlank() }?.let { notificationHelper.claimWithoutNotifying(it) }
                    Log.i(TAG, "Silenced a $type push from the other network")
                    return@runBlocking
                }
                when (type) {
                    // .kachat name events (KACHAT_NAMES_INDEXER.md Part E): offers, sales,
                    // renewal reminders, written in the device's language (iOS beeedd4).
                    "name_event" -> handleNameEvent(data)

                    "broadcast" -> {
                        val channel = data["channel"] ?: return@runBlocking
                        // An edit changes an earlier message in place - nothing to announce. The
                        // push service is asked not to send these at all (PUBLIC_CHATS_INDEXER.md);
                        // this is the backstop for one that arrives anyway.
                        if (com.kachat.app.util.BroadcastPushPreview.isEditEnvelope(body)) {
                            Log.i(TAG, "Edit push dropped: an edit is never announced")
                            data["tx_id"]?.takeIf { it.isNotBlank() }?.let { notificationHelper.claimWithoutNotifying(it) }
                            return@runBlocking
                        }
                        notificationHelper.showBroadcast(
                            channelName = channel,
                            title = title.ifEmpty { "#$channel" },
                            // The server has sent room messages on raw - a reply's JSON, the same
                            // cut off mid-way, or still base64 - and each read as gibberish.
                            text = com.kachat.app.util.BroadcastPushPreview.clean(body),
                            // Collapses with the live block scan's banner when the app is
                            // foregrounded and both paths see the same message.
                            dedupeTxId = data["tx_id"]?.takeIf { it.isNotBlank() },
                        )
                    }

                    "kaposts" -> {
                        val txId = data["tx_id"].orEmpty()
                        // The id of the content that was acted ON. The published contract calls
                        // it `postId` (the iOS repo's PUSH_EXTENSIONS.md section 3), this file's own schema
                        // comment said `post_id`, and the code read `content_id` — accept all
                        // three rather than let one server-side spelling silently disable the
                        // deep link. It is deliberately absent for a follow.
                        //
                        // No fallback to the ACTION's txid: that is a vote/follow/reply
                        // transaction, not a post, so opening it as one just produced a "post
                        // not found" toast. Null lands the tap on the Notifications list.
                        val postTxId = POST_ID_KEYS.firstNotNullOfOrNull { data[it]?.takeIf { v -> v.isNotBlank() } }
                        // The five KaPosts switches governed only the local poller; a push
                        // arrived whatever they said. See shouldNotifyKaPostsPush.
                        if (!settingsRepository.shouldNotifyKaPostsPush(data["kaposts_kind"], body.ifEmpty { title })) {
                            Log.i(TAG, "KaPosts push suppressed by notification settings")
                            return@runBlocking
                        }
                        notificationHelper.showKaPosts(
                            text = body.ifEmpty { title },
                            actionTxId = txId,
                            // A reply targets the reply ITSELF, as the foreground poller does:
                            // the landing rule then opens its parent's thread with the reply
                            // spliced in and scrolled to - even before the indexer's reply page
                            // has it. Anything else opens the post that was acted on.
                            postTxId = txId.takeIf {
                                it.isNotBlank() && postTxId != null &&
                                    com.kachat.app.ui.screens.KaPostsDeepLink.isReplyPush(data["kaposts_kind"], body)
                            } ?: postTxId,
                        )
                    }

                    // 1:1 messages, payments, handshakes, groups and calls live in KaChat:
                    // KaPosts shows only its own pushes and public chat rooms (iOS KaPosts
                    // AppDelegate).
                    else -> Log.d(TAG, "Ignoring push of unknown type: $type")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle push (type=$type): ${e.message}")
            }
        }
    }

    /**
     * A `.kachat` name event, written in the device's language (iOS NotificationService
     * handleNameEvent). Payload (indexer Part E): `event` (name_offer, name_sold,
     * name_offer_accepted, name_renewal_open, name_expiring, name_grace; name_lapsed,
     * name_offer_declined and name_offer_refunded for when the indexer sends them, iOS 86471dd),
     * `name` (without
     * .kachat), optional `amount` (sompi) and `days`. An event this build doesn't know keeps the
     * server's own text. Tapping it opens the name.
     */
    private suspend fun handleNameEvent(data: Map<String, String>) {
        val rawName = data["name"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return
        val bare = rawName.removeSuffix(".kachat")
        val name = "$bare.kachat"
        val amount = data["amount"]?.trim()?.toLongOrNull()?.let { sompi ->
            kasText(sompi) + " " + com.kachat.app.util.KaspaUnit.symbol
        }
        val days = data["days"]?.trim()?.toIntOrNull()
        val (title, body) = when (data["event"]) {
            "name_offer" -> getString(com.kachat.app.R.string.kn_push_new_offer, name) to
                (amount?.let { getString(com.kachat.app.R.string.kn_push_offered, it) } ?: getString(com.kachat.app.R.string.kn_push_offer_no_amount))
            "name_sold" -> getString(com.kachat.app.R.string.kn_push_sold, name) to
                (amount?.let { getString(com.kachat.app.R.string.kn_push_paid, it) } ?: getString(com.kachat.app.R.string.kn_push_listing_bought))
            "name_offer_accepted" -> getString(com.kachat.app.R.string.kn_push_offer_accepted) to getString(com.kachat.app.R.string.kn_push_yours_now, name)
            "name_renewal_open" -> getString(com.kachat.app.R.string.kn_push_renew, name) to getString(com.kachat.app.R.string.kn_push_renewal_open)
            // days on mainnet's yearly clock; testnet's periods are minutes long (days = 0)
            "name_expiring" -> getString(com.kachat.app.R.string.kn_push_expires_soon, name) to when {
                days != null && days > 1 -> getString(com.kachat.app.R.string.kn_push_days_left, days)
                days == 1 -> getString(com.kachat.app.R.string.kn_push_one_day_left)
                else -> getString(com.kachat.app.R.string.kn_push_renew_soon)
            }
            "name_grace" -> getString(com.kachat.app.R.string.kn_push_expired, name) to getString(com.kachat.app.R.string.kn_push_grace_soon)
            "name_lapsed" -> getString(com.kachat.app.R.string.kn_bell_no_longer_yours_title, name) to getString(com.kachat.app.R.string.kn_bell_lapsed_available_body)
            "name_offer_declined" -> getString(com.kachat.app.R.string.kn_bell_declined_title, name) to
                com.kachat.app.util.KaspaUnit.label(getString(com.kachat.app.R.string.kn_bell_declined_body))
            "name_offer_refunded" -> getString(com.kachat.app.R.string.kn_bell_offer_expired_title, name) to
                com.kachat.app.util.KaspaUnit.label(getString(com.kachat.app.R.string.kn_bell_offer_expired_body))
            else -> data["title"].orEmpty().ifEmpty { name } to data["body"].orEmpty()
        }
        val dedupeKey = data["tx_id"]?.takeIf { it.isNotBlank() } ?: "${data["event"]}:$bare:${data["amount"]}:${data["days"]}"
        notificationHelper.showKachatName(title = title, text = body, name = bare, dedupeKey = dedupeKey)
    }

    /** Sompi as KAS text: up to 8 decimals, trailing zeros dropped (iOS kasText). */
    private fun kasText(sompi: Long): String =
        String.format(java.util.Locale.US, "%.8f", sompi / 100_000_000.0).trimEnd('0').trimEnd('.')

    companion object {
        // Same tag as PushRegistrationManager: `adb logcat -s KaChatPush` shows registrations,
        // token rotations, and every received push in one stream.
        private const val TAG = PushRegistrationManager.TAG

        /** Every spelling the KaPosts push has used for "the content that was acted on".
         *  Kept in sync with MainActivity.FCM_KEYS_POST_ID, which reads the same payload when
         *  FCM drew the notification itself. */
        private val POST_ID_KEYS = listOf("post_id", "postId", "content_id")
    }
}
