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
 *  - 1:1 and group chat pushes are KaChat's, and `.kachat` name events (`name_event`) KaPay's
 *    and KaChat's; KaPosts ignores them.
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
                    // .kachat name events (offers, sales, renewal reminders) are KaPay's and
                    // KaChat's: KaPost shows no notifications about domains. They come because
                    // the push service routes them by primary_address, which KaPost registers.
                    "name_event" -> {
                        data["tx_id"]?.takeIf { it.isNotBlank() }?.let { notificationHelper.claimWithoutNotifying(it) }
                        Log.i(TAG, "Ignoring a .kachat name push: KaPost shows no domain notifications")
                    }

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
                            // A reply or a quote targets the action ITSELF, as the foreground
                            // poller does: the landing rule then opens its parent's thread with
                            // it spliced in and scrolled to - even before the indexer's reply
                            // page has it (iOS 093fe4d). Anything else opens the post that was
                            // acted on.
                            postTxId = txId.takeIf {
                                com.kachat.app.ui.screens.KaPostsDeepLink.isTxId(it) &&
                                    com.kachat.app.ui.screens.KaPostsDeepLink.pushOpensAction(data["kaposts_kind"], body)
                            }?.lowercase() ?: postTxId,
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
