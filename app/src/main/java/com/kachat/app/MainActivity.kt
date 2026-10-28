package com.kachat.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Close
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.kachat.app.services.CrashRecorder
import com.kachat.app.services.NotificationHelper
import com.kachat.app.ui.theme.KaChatTheme
import com.kachat.app.ui.KaChatApp
import com.kachat.app.ui.screens.BroadcastDeepLink
import com.kachat.app.ui.screens.KaChatLink
import com.kachat.app.ui.screens.KaPostsDeepLink
import com.kachat.app.viewmodels.WalletViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Single Activity — all navigation is handled in Compose via NavHost.
 *
 * Extends [AppCompatActivity] (rather than plain `FragmentActivity`, which it's a superset of)
 * because the per-app language API (`AppCompatDelegate.setApplicationLocales`, used by the
 * Language setting in Settings) relies on `AppCompatActivity`'s `attachBaseContext` override to
 * apply the locale override on API levels below 33.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var onboardingGate: com.kachat.app.services.OnboardingGate

    // The KaChat sheet that explains the battery exemption before Android's own dialog. Re-derived
    // on every resume (see refreshBatteryRationale), so it follows the notification answer.
    private var batteryRationaleDue by mutableStateOf(false)

    private var pendingChannelName by mutableStateOf<String?>(null)
    // "cold" / "spending" — which wallet screen an address-activity receipt tap opens.
    private var pendingWalletActivityKind by mutableStateOf<String?>(null)
    // The one own address a "Received" receipt named: its History opens in a sheet (iOS 13046ad).
    // Held here until the shell is up and takes it, which is what covers a cold start (iOS's
    // OwnAddressRoute.pending).
    private var pendingOwnAddress by mutableStateOf<String?>(null)

    // Android 13+ requires an explicit runtime grant before ANY notification (local or FCM push)
    // can be shown. Registered here (during construction, as required) and requested in onCreate.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The battery sheet waits for this answer: notifications off means there is nothing
            // for the exemption to deliver, so it is not worth asking.
            if (granted) refreshBatteryRationale()
        }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * The battery-optimization exemption - Android's "Let KaChat always run in background?"
     * dialog (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). Encrypted DM/group push is delivered
     * data-only so the app can decrypt it on the phone; an app frozen by aggressive OEM battery
     * management never wakes to receive it, and the notification never appears.
     *
     * It used to fire the system dialog straight from onCreate with no word of explanation - on
     * Android 13+ a launch AFTER notifications were granted, seemingly at random; below 13, the
     * first launch, over whatever was on screen. Now [BatteryExemptionRationale] says why first,
     * and Android's dialog only opens from its Allow. Asked once either way, as before.
     */
    private fun batteryExemptionDue(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val prefs = getSharedPreferences("kachat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return false
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun refreshBatteryRationale() {
        batteryRationaleDue = batteryExemptionDue()
    }

    private fun markBatteryExemptionAsked() {
        getSharedPreferences("kachat_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("battery_exemption_asked", true).apply()
        batteryRationaleDue = false
    }

    private fun acceptBatteryExemption() {
        markBatteryExemptionAsked()
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            // A few OEMs don't implement the direct dialog — fall back to the battery-optimization list.
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers returning from the notification-permission dialog, and from system Settings.
        guarded("battery rationale") { refreshBatteryRationale() }
    }

    /** A side step of opening the app: a failure is logged, never the end of it. */
    private fun guarded(what: String, block: () -> Unit) =
        com.kachat.app.util.StartupSafety.step("MainActivity", what, block)

    /** The launching (or new) Intent's notification target and links. */
    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        guarded("notification target") { consumeNotificationTarget(intent) }
        guarded("broadcast link") { handleBroadcastDeepLink(intent) }
        guarded("profile link") { handleProfileDeepLink(intent) }
        guarded("payment request link") { handlePaymentRequestDeepLink(intent) }
        guarded("clear intent payload") { clearConsumedIntentPayload(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones lock to portrait; tablets (sw>=600dp, see res/values-sw600dp/bools.xml) rotate
        // freely - mirroring iOS (iPhone portrait-only, iPad any orientation).
        requestedOrientation = if (resources.getBoolean(R.bool.lock_portrait)) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        // Balances, chats and the seed screen used to sit in the task switcher's thumbnail of the
        // app in plain view. This is Android's own switch for exactly that, and unlike FLAG_SECURE
        // it does not also block screenshots and screen recording, which people legitimately use
        // on a chat - and it cannot fight the FLAG_SECURE the seed and private-key screens set and
        // clear for themselves. Below API 33 there is no equivalent that leaves screenshots alone,
        // so those devices keep the thumbnail. iOS covers the same exposure with a blur (7e39943).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        enableEdgeToEdge()
        // Everything up to setContent is a side step - a permission prompt, a notification or
        // link to route. Each is guarded on its own: one that throws is
        // logged, and the app still opens (KaChatApplication's startup rule).
        guarded("notification permission") { maybeRequestNotificationPermission() }
        guarded("battery rationale") { refreshBatteryRationale() }
        handleLaunchIntent(intent)
        setContent {
            val walletViewModel: WalletViewModel = hiltViewModel()
            // Settings > Customization > Appearance: System follows the device, as on iOS.
            val appearance by walletViewModel.appearance.collectAsState()
            val isLoggedIn by walletViewModel.isLoggedIn.collectAsState()
            KaChatTheme(darkTheme = appearance.isDark(androidx.compose.foundation.isSystemInDarkTheme())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KaChatApp(
                        pendingChannelName = pendingChannelName,
                        onPendingChannelHandled = { pendingChannelName = null },
                        pendingWalletActivityKind = pendingWalletActivityKind,
                        onPendingWalletActivityHandled = { pendingWalletActivityKind = null },
                        pendingOwnAddress = pendingOwnAddress,
                        onPendingOwnAddressHandled = { pendingOwnAddress = null }
                    )
                    CrashNotice()
                    BatteryExemptionRationale(isLoggedIn = isLoggedIn)
                }
            }
        }
    }

    /**
     * Shown once on the launch after the app crashed: the previous process died before anyone
     * could see why, and its record is the one thing that turns "it will not open" into a fix.
     * Share sends the newest report to a share sheet (mail, Telegram, anything); Not now keeps
     * it for the diagnostics archive under Settings > Diagnostics.
     */
    /**
     * Says why before Android asks. Only inside the app proper - logged in, no onboarding wizard
     * running - and never on the same launch as the crash notice, so two sheets never stack.
     * Dismissing it counts as "Not now": asked once, as the system dialog always was.
     */
    @androidx.compose.runtime.Composable
    private fun BatteryExemptionRationale(isLoggedIn: Boolean) {
        val onboardingHeld by onboardingGate.held.collectAsState()
        val crashNoticePending = remember { CrashRecorder.unseenCrash(this) != null }
        if (!batteryRationaleDue || !isLoggedIn || onboardingHeld || crashNoticePending) return
        com.kachat.app.ui.screens.ActionSheetContainer(
            title = androidx.compose.ui.res.stringResource(R.string.battery_rationale_title),
            subtitle = androidx.compose.ui.res.stringResource(R.string.battery_rationale_body),
            onDismiss = { markBatteryExemptionAsked() },
        ) {
            com.kachat.app.ui.screens.ActionSheetRow(
                icon = androidx.compose.material.icons.Icons.Default.NotificationsActive,
                title = androidx.compose.ui.res.stringResource(R.string.battery_rationale_allow),
                subtitle = androidx.compose.ui.res.stringResource(R.string.battery_rationale_allow_detail),
                onClick = { acceptBatteryExemption() },
            )
            com.kachat.app.ui.screens.ActionSheetRow(
                icon = androidx.compose.material.icons.Icons.Default.Close,
                title = androidx.compose.ui.res.stringResource(R.string.not_now),
                subtitle = androidx.compose.ui.res.stringResource(R.string.battery_rationale_not_now_detail),
                onClick = { markBatteryExemptionAsked() },
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun CrashNotice() {
        val crashFile = remember { CrashRecorder.unseenCrash(this) }
        var show by remember { mutableStateOf(crashFile != null) }
        if (!show || crashFile == null) return
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { show = false; CrashRecorder.markSeen(this) },
            title = { Text("KaPost closed unexpectedly") },
            text = { Text("The app did not close normally the last time it ran. A report of what happened was saved. Sharing it with the developer is the quickest way to get it fixed - it contains no messages, keys or addresses.") },
            confirmButton = {
                TextButton(onClick = {
                    show = false
                    CrashRecorder.markSeen(this)
                    shareCrashReport(crashFile)
                }) { Text("Share report") }
            },
            dismissButton = {
                TextButton(onClick = { show = false; CrashRecorder.markSeen(this) }) { Text("Not now") }
            }
        )
    }

    private fun shareCrashReport(file: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "KaPost crash report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share crash report"))
        }.onFailure { android.util.Log.w("MainActivity", "Could not share crash report", it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }


    /**
     * Strips the link/share payload once the handlers above have read it.
     *
     * The Activity keeps its launching Intent, so a configuration change re-runs `onCreate`
     * against it. Settings > Language does exactly that (`AppCompatDelegate.setApplicationLocales`
     * recreates the Activity), and without this a language change re-opened the last post that
     * had been opened from a link, or re-staged the last thing that had been shared into the app.
     * The notification extras are stripped in [consumeNotificationTarget] for the same reason;
     * these two carry their payload in the action/data/stream instead of in our own extras.
     *
     * Safe to run immediately: every handler reads what it needs synchronously before any
     * suspending work.
     */
    private fun clearConsumedIntentPayload(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW ||
            intent.action == Intent.ACTION_SEND ||
            intent.action == Intent.ACTION_SEND_MULTIPLE
        ) {
            intent.action = null
            intent.data = null
            intent.type = null
            listOf(
                Intent.EXTRA_TEXT,
                Intent.EXTRA_STREAM,
                Intent.EXTRA_SHORTCUT_ID,
            ).forEach(intent::removeExtra)
        }
    }

    /**
     * Reads whichever notification target the launching Intent carries, then strips it.
     *
     * Two shapes arrive here:
     *  - The app's OWN notifications ([NotificationHelper]), which carry explicit extras.
     *  - A notification FCM drew itself. Any push carrying an FCM `notification` block is shown
     *    by the OS while the app is backgrounded or dead, and `onMessageReceived` never runs for
     *    it in that state: no [NotificationHelper] intent is ever built, and the tap instead
     *    launches this Activity with the push's DATA payload as plain string extras. Those keys
     *    are the server's (`type`, `channel`, `sender`, the post id), not ours, so without
     *    [applyFcmNotificationTarget] such a tap just restored the last screen the user was on.
     *    The two in-repo descriptions of the server's choice disagree (this app's
     *    `KaChatFirebaseMessagingService` header says broadcast/KaPosts ship a `notification`
     *    block; PUSH_ANDROID_SETUP.md says everything is data-only), so both shapes are handled
     *    here rather than betting on one.
     *
     * Stripping matters because a configuration change (Settings > Language recreates the
     * Activity) re-runs `onCreate` against the same Intent: without it, changing the language
     * jumped the user back into whatever conversation the last tapped notification was for.
     */
    private fun consumeNotificationTarget(intent: Intent) {
        pendingChannelName = intent.getStringExtra(NotificationHelper.EXTRA_CHANNEL_NAME)
        pendingWalletActivityKind = intent.getStringExtra(NotificationHelper.EXTRA_WALLET_ACTIVITY_KIND)
        // One address got the Kaspa: open that address's History rather than the wallet tab -
        // the kind is then only the fallback (iOS 13046ad).
        pendingOwnAddress = intent.getStringExtra(NotificationHelper.EXTRA_WALLET_ACTIVITY_ADDRESS)?.takeIf { it.isNotEmpty() }
        if (pendingOwnAddress != null) pendingWalletActivityKind = null
        // A .kachat name notification: open that name (iOS beeedd4).
        intent.getStringExtra(NotificationHelper.EXTRA_KACHAT_NAME)?.takeIf { it.isNotBlank() }?.let {
            com.kachat.app.ui.screens.KachatDeepLink.pendingName.value =
                com.kachat.app.services.kachatnames.KachatNames.Codec.normalize(it.removeSuffix(".kachat"))
        }
        val ownKaPostTarget = handleKaPostDeepLink(intent)
        if (!ownKaPostTarget && pendingChannelName == null && pendingWalletActivityKind == null &&
            pendingOwnAddress == null
        ) {
            applyFcmNotificationTarget(intent)
        }
        listOf(
            NotificationHelper.EXTRA_CONTACT_ID,
            NotificationHelper.EXTRA_CHANNEL_NAME,
            NotificationHelper.EXTRA_GROUP_ID,
            NotificationHelper.EXTRA_OPEN_GROUPS,
            NotificationHelper.EXTRA_WALLET_ACTIVITY_KIND,
            NotificationHelper.EXTRA_WALLET_ACTIVITY_ADDRESS,
            NotificationHelper.EXTRA_KACHAT_NAME,
            NotificationHelper.EXTRA_OPEN_KAPOSTS,
            NotificationHelper.EXTRA_KAPOST_TXID,
            NotificationHelper.EXTRA_KAPOST_FOCUS_TXID,
            FCM_KEY_TYPE,
        ).forEach(intent::removeExtra)
    }

    /**
     * Routes a tap on a notification FCM drew from its own `notification` block, using the data
     * payload the OS handed us as Intent extras (schema documented on
     * `KaChatFirebaseMessagingService`). Every push type is mapped, not just the public ones, so
     * a server-side change to which of them carry a `notification` block cannot silently break
     * their taps.
     */
    private fun applyFcmNotificationTarget(intent: Intent) {
        // The push's own `type` names it; a KaPosts push also carries thread_id "kaposts" (the
        // same thread the iOS payload uses), which is taken when `type` is missing - otherwise
        // such a tap reopened whatever screen was last up instead of the post.
        val type = intent.getStringExtra(FCM_KEY_TYPE)
            ?: intent.getStringExtra("thread_id")?.takeIf { it == "kaposts" }
        when (type) {
            "name_event" -> intent.getStringExtra("name")?.takeIf { it.isNotBlank() }?.let {
                com.kachat.app.ui.screens.KachatDeepLink.pendingName.value =
                    com.kachat.app.services.kachatnames.KachatNames.Codec.normalize(it.lowercase().removeSuffix(".kachat"))
            }
            "broadcast" -> pendingChannelName = intent.getStringExtra("channel")?.takeIf { it.isNotBlank() }
            "kaposts" -> {
                // The server's own key for "the content that was acted on" has been spelled
                // three ways across the iOS/Android/indexer contracts (see the audit note in
                // KaChatFirebaseMessagingService) — accept all of them rather than silently
                // deep-linking to nothing.
                val postId = FCM_KEYS_POST_ID.firstNotNullOfOrNull {
                    intent.getStringExtra(it)?.takeIf { value -> value.isNotBlank() }
                }
                // A reply targets the reply ITSELF - the push's tx_id is the reply's own - and the
                // landing rule opens its parent's thread with it spliced in and scrolled to, the
                // same as a tap on the foreground banner. Anything else opens the acted-on post.
                val actionTxId = intent.getStringExtra("tx_id")?.takeIf { it.isNotBlank() }
                val replyTxId = actionTxId?.takeIf {
                    postId != null && KaPostsDeepLink.isReplyPush(
                        intent.getStringExtra("kaposts_kind"),
                        intent.getStringExtra("body"),
                    )
                }
                KaPostsDeepLink.pendingFocusReplyTxId.value = null
                // No post id (a follow, or a payload that omitted it): the Notifications list,
                // never the action's own txid — that is a vote/follow transaction, not a post,
                // and opening it as one only produced a "post not found" toast.
                KaPostsDeepLink.pendingOpenNotifications.value = postId == null
                KaPostsDeepLink.pendingPostTxId.value = replyTxId ?: postId ?: ""
            }
            // Chats live in KaChat: a 1:1 or group push that reaches KaPosts opens nothing here.
        }
        FCM_KEYS_POST_ID.forEach(intent::removeExtra)
        // Stripped once read, so a configuration change cannot route the same tap twice.
        listOf("channel", "sender", "thread_id", "tx_id", "kaposts_kind", "body").forEach(intent::removeExtra)
    }

    /** kachat:// and KaPosts' own kaposts://, which carry the same targets (iOS KaPosts
     *  KaChatInternalLink.parse). */
    private fun isAppLinkScheme(scheme: String?): Boolean =
        scheme.equals("kachat", ignoreCase = true) || scheme.equals("kaposts", ignoreCase = true)

    /**
     * Broadcast room share links: kachat://broadcast/<channel> (the share-text form) and
     * https://kachat.app/broadcast/<channel> (universal-link form). The name is untrusted,
     * so [BroadcastDeepLink.request] runs it through normalizeChannelName/isValidChannelName (plus
     * the route-safety rules) and drops it outright if it doesn't pass — nothing is joined and no
     * navigation happens. MainShell picks up a request that does pass, enforces Child Mode, and
     * opens the room (joining it first when it isn't one of the curated ones).
     */
    /** The host a KaChat link arrives on: kachat.app, the one the app writes. */
    private fun isKaChatWebHost(host: String?): Boolean {
        val clean = host?.lowercase()?.removePrefix("www.") ?: return false
        return clean == KaChatLink.WEB_HOST
    }

    /** kachat://profile/<address> or https://kachat.app/u/<address> - someone's shared profile
     *  (iOS 863b0e9). The address is validated, checksum and all, before anything routes. */
    private fun handleProfileDeepLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val raw = when {
            isAppLinkScheme(uri.scheme) &&
                uri.host.equals("profile", ignoreCase = true) -> uri.lastPathSegment
            uri.scheme.equals("https", ignoreCase = true) &&
                isKaChatWebHost(uri.host) &&
                uri.pathSegments.firstOrNull() == "u" -> uri.pathSegments.getOrNull(1)
            else -> null
        } ?: return
        com.kachat.app.ui.screens.ProfileDeepLink.request(raw)
    }

    /** kachat://pay/<address>?... or https://kachat.app/pay/<address>?... - someone's payment
     *  request (iOS c6851d6). Everything in it is checked - the address on this network, the exact
     *  amount, the memo, a signature when it carries one - before it opens: in KaPay for someone
     *  else's ([com.kachat.app.ui.screens.KaPayHandoff]), Send Kaspa filled in for a chat payment,
     *  the request itself for your own; nothing is ever sent without slide to send. */
    private fun handlePaymentRequestDeepLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val isPayLink = when {
            isAppLinkScheme(uri.scheme) -> uri.host.equals("pay", ignoreCase = true)
            uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true) ->
                isKaChatWebHost(uri.host) && uri.pathSegments.firstOrNull() == "pay"
            else -> false
        }
        if (!isPayLink) return
        val ref = com.kachat.app.ui.screens.KaChatLink.parse(uri.toString()) as? com.kachat.app.ui.screens.KaChatLinkRef.PaymentRequest
            ?: return
        com.kachat.app.ui.screens.PaymentRequestRouter.open(ref.request)
    }

    private fun handleBroadcastDeepLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val rawChannel = when {
            isAppLinkScheme(uri.scheme) &&
                uri.host.equals("broadcast", ignoreCase = true) -> uri.lastPathSegment
            uri.scheme.equals("https", ignoreCase = true) &&
                isKaChatWebHost(uri.host) &&
                uri.pathSegments.firstOrNull() == "broadcast" -> uri.pathSegments.getOrNull(1)
            else -> null
        } ?: return
        BroadcastDeepLink.request(rawChannel)
    }


    /**
     * KaPosts share links land here: kachat://kapost/<txid> (the share-text form) and
     * https://kachat.app/post/<txid> (universal-link form). The txid is handed to
     * [KaPostsDeepLink]; KaChatApp navigates to the KaPosts tab and the screen opens the
     * post's thread.
     */
    private fun handleKaPostDeepLink(intent: Intent): Boolean {
        // A KaPosts notification tap: deep-open the exact post/comment the notification was
        // about (EXTRA_KAPOST_TXID); with no post named (a follow) open the Notifications list
        // instead of dropping the user on the feed with nothing explaining the ping.
        if (intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_KAPOSTS, false)) {
            // Set the focus (the reply's own txid, for scroll-to-comment) and the notifications
            // flag BEFORE the post id - the post id is what KaPostsScreen's LaunchedEffect keys on.
            KaPostsDeepLink.pendingFocusReplyTxId.value =
                intent.getStringExtra(NotificationHelper.EXTRA_KAPOST_FOCUS_TXID)
            val postTxId = intent.getStringExtra(NotificationHelper.EXTRA_KAPOST_TXID)
                ?.takeIf { it.isNotBlank() }
            KaPostsDeepLink.pendingOpenNotifications.value = postTxId == null
            KaPostsDeepLink.pendingPostTxId.value = postTxId ?: ""
            return true
        }
        if (intent.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        // Same forms iOS's KaChatInternalLink.parse accepts: the kachat:// scheme, and the
        // https/http universal link with or without www.
        val host = uri.host?.lowercase()?.removePrefix("www.")
        val txId = when {
            isAppLinkScheme(uri.scheme) &&
                uri.host.equals("kapost", ignoreCase = true) -> uri.lastPathSegment
            (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
                isKaChatWebHost(host) &&
                uri.pathSegments.firstOrNull() == "post" -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        // A post is a transaction id: 64 hex characters. Anything else in the link goes nowhere.
        if (txId != null && TX_ID_PATTERN.matches(txId)) {
            KaPostsDeepLink.pendingOpenNotifications.value = false
            KaPostsDeepLink.pendingPostTxId.value = txId.lowercase()
        }
        return false
    }

    companion object {
        /** FCM data-payload keys that reach us as Intent extras when the OS drew the
         *  notification itself — see [applyFcmNotificationTarget]. */
        private const val FCM_KEY_TYPE = "type"
        private val FCM_KEYS_POST_ID = listOf("post_id", "postId", "content_id")
        private val TX_ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    }
}
