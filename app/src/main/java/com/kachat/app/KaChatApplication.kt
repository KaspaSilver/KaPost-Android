package com.kachat.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import androidx.work.WorkManager
import com.kachat.app.services.BroadcastScanningService
import com.kachat.app.services.CrashRecorder
import com.kachat.app.services.KaPostsNotificationPoller
import com.kachat.app.services.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class — required by Hilt for dependency injection.
 * All singleton services are initialized here via Hilt modules in the `di` package.
 *
 * Startup rule: nothing optional may stop the app from opening. A Play build 5.2 would not open
 * on a GrapheneOS phone (no crash log): 5.2 built more services eagerly, and on a hardened ROM
 * the Keystore behind EncryptedSharedPreferences can throw. So every service below is injected
 * as [dagger.Lazy] - Hilt's field injection (inside super.onCreate) creates none of them - and
 * onCreate then builds each one as its own [startupStep], in the same order as before: one that
 * throws is logged and the app opens without it. (The portfolio is built just after, off the main
 * thread.) The lifecycle callbacks below are guarded the same way. [hiltWorkerFactory] is the one
 * plain field (WorkManager's configuration needs it, and it is built from the graph alone).
 */
@HiltAndroidApp
class KaChatApplication : Application(), Configuration.Provider {

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Before anything else - Hilt builds the whole object graph in onCreate, and a crash in
        // there is exactly the "closes on the splash screen" a user cannot describe. The record
        // is bundled into the diagnostics archive and offered for sharing on the next launch.
        CrashRecorder.install(this)
        // The network this launch runs on (Settings > Connection > Testnet), known before Hilt
        // starts any service - the node pool, wallet and indexers are all set up for it.
        // [base], not `this`: inside attachBaseContext the Application's own applicationContext
        // is still null, and reading prefs through it crashed every launch.
        com.kachat.app.util.KaspaNetwork.init(base)
        // The rooms the indexer tracks beyond the curated ones (iOS 21c480d): read by the
        // scanner's prune and metered gate as soon as they start, so known before Hilt does.
        com.kachat.app.services.IndexerTrackedRooms.init(base)
    }

    // @Singleton instances are otherwise only created lazily the first time something actually
    // requests them — field-injecting this here forces it to exist from app startup, so its
    // self-observing "start/stop scanning based on the setting" logic (see its init block) runs
    // for the app's whole lifetime rather than only after the user happens to open a broadcast
    // screen.
    @Inject
    lateinit var broadcastScanningService: dagger.Lazy<BroadcastScanningService>

    // Same lazy-singleton reasoning again: PushRegistrationManager's init block observes the
    // active account / contact set / broadcast bells / hidden senders / notifications setting and
    // re-registers (or unregisters) the FCM token with the push service whenever any of it
    // changes. Field-injecting it here guarantees those observers run for the app's whole
    // lifetime, not just after the first screen that happens to request it.
    @Inject
    lateinit var pushRegistrationManager: dagger.Lazy<com.kachat.app.services.PushRegistrationManager>

    // A scheduled KaPost holds the coins its signed transaction spends. Those coins must be off
    // limits from the moment the app starts - not from whenever KaPosts happens to be opened -
    // or an ordinary send could spend one underneath it and the post would be invalid when its
    // time came. Field-injected (and loaded in onCreate) for exactly that reason.
    @Inject
    lateinit var kaPostsScheduledStore: dagger.Lazy<com.kachat.app.services.KaPostsScheduledStore>

    @Inject
    lateinit var nodePoolManager: dagger.Lazy<com.kachat.app.services.NodePoolManager>

    // Foreground-only KaPosts pings (started/stopped by the process lifecycle observer below) —
    // while the app is closed, the push service is the only KaPosts notification source.
    @Inject
    lateinit var kaPostsNotificationPoller: dagger.Lazy<KaPostsNotificationPoller>

    // Address-activity notifications (external receipts on spending/cold addresses): per-wallet
    // baseline + diff engine, foreground poll + on-foreground catch-up. See its class doc.
    @Inject
    lateinit var addressActivityNotifier: dagger.Lazy<com.kachat.app.services.AddressActivityNotifier>

    // For mirroring the persisted "Verbose API Logging" toggle into ApiLogging.verbose (a plain
    // volatile flag the OkHttp logging interceptor reads per request) — see onCreate below.
    @Inject
    lateinit var appSettingsRepository: dagger.Lazy<com.kachat.app.repository.AppSettingsRepository>

    // Foreground-policy flag: the local poll/scan notification paths post banners while the app
    // is on screen (only the open conversation is suppressed, inside NotificationHelper itself),
    // and defer to remote push only while backgrounded. The lifecycle observer below keeps the
    // flag current.
    @Inject
    lateinit var notificationHelper: dagger.Lazy<com.kachat.app.services.NotificationHelper>

    // Built at startup purely so its companion accessor is live: KaPost link cards render from
    // several screens with different view models, and none of them owns this.
    @Inject
    lateinit var kaPostLinkPreviewService: dagger.Lazy<com.kachat.app.services.KaPostLinkPreviewService>

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    // .kachat registrations in flight (testnet only) resume on every foreground, like iOS's
    // app-active (KaChat 5df42b4). Lazy, so a mainnet launch never even builds the names stack.
    @Inject
    lateinit var kachatNamesActions: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesActions>

    // Identity is .kachat everywhere (iOS e52357d): every contact name and avatar reads the
    // registry's identity cache and the social image cache through their companions, so both are
    // built at startup - on mainnet too since iOS d36fc42, for address profiles (profilesEnabled).
    @Inject
    lateinit var kachatNamesRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>

    @Inject
    lateinit var kachatSocialImages: dagger.Lazy<com.kachat.app.services.kachatnames.KachatSocialImageResolver>

    /** The contacts the .kachat sweep below asks about (iOS 3214a2b). */
    @Inject
    lateinit var chatRepository: dagger.Lazy<com.kachat.app.repository.ChatRepository>

    // The Address Book (iOS 00767a4): built at startup because every display name reads it
    // through its companion (addressDisplayName), and it follows the active wallet on its own.
    @Inject
    lateinit var addressBookManager: dagger.Lazy<com.kachat.app.services.AddressBookManager>

    // Payment requests (iOS c6851d6): built at startup because a request card or a pasted link
    // asks whether it is your own through its companion (PaymentRequest.isOwn).
    @Inject
    lateinit var paymentRequestWallet: dagger.Lazy<com.kachat.app.services.PaymentRequestWallet>

    /** Sends the payment notices an empty chatting address could not, once it has KAS
     *  (iOS 710fdc0, IOS-086). */
    @Inject
    lateinit var paymentNoticeOutbox: dagger.Lazy<com.kachat.app.services.PaymentNoticeOutbox>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory()).build()

    /**
     * WorkManager initializes itself the first time anything asks for it - and a singleton built
     * early (NextcloudSyncService's init cancels or schedules its work on a background thread)
     * could ask before the [hiltWorkerFactory] field below had been injected,
     * which crashed with an uninitialized lateinit. The Hilt component already exists by then, so
     * the factory is read straight from it in that window.
     */
    private fun workerFactory(): HiltWorkerFactory =
        if (::hiltWorkerFactory.isInitialized) hiltWorkerFactory
        else dagger.hilt.android.EntryPointAccessors
            .fromApplication(this, WorkerFactoryEntryPoint::class.java)
            .hiltWorkerFactory()

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun hiltWorkerFactory(): HiltWorkerFactory
    }

    /** One non-essential startup step: a failure is logged and the app opens without it. */
    private fun startupStep(what: String, block: () -> Unit) =
        com.kachat.app.util.StartupSafety.step(TAG, what, block)

    /** For the coroutines launched below: a failure is logged, never the end of the process. */
    private val logOnly = com.kachat.app.util.StartupSafety.logOnlyHandler(TAG)

    override fun onCreate() {
        super.onCreate()

        // How did the previous process end? A native crash or an OS kill never reaches the
        // exception handler installed in attachBaseContext; the system's own exit history does.
        startupStep("process exit history") { CrashRecorder.noteProcessExits(this) }

        // The always-on services, built now exactly as field injection used to build them (same
        // order), each on its own so one failing cannot take the others - or the app - down.
        // Every one of them is reached again through its Lazy below, and from screens.
        startupStep("broadcast scanning") { broadcastScanningService.get() }
        startupStep("push registration") { pushRegistrationManager.get() }
        startupStep("scheduled KaPosts") { kaPostsScheduledStore.get() }
        startupStep("node pool") { nodePoolManager.get() }
        startupStep("KaPosts notifications") { kaPostsNotificationPoller.get() }
        startupStep("address activity") { addressActivityNotifier.get() }
        startupStep("settings") { appSettingsRepository.get() }
        startupStep("notifications") { notificationHelper.get() }
        startupStep("KaPost link previews") { kaPostLinkPreviewService.get() }
        // The Address Book's companion is what every display name reads, and its own init
        // follows each wallet load - so it is still built here, at launch, only guarded.
        startupStep("Address Book") { addressBookManager.get() }
        startupStep("payment requests") { paymentRequestWallet.get() }
        startupStep("payment notices") { paymentNoticeOutbox.get() }

        // Reserve the coins every still-waiting scheduled post is going to spend, before any
        // send can pick them (see the field above).
        startupStep("scheduled KaPosts reload") { kaPostsScheduledStore.get().reloadIfNeeded() }

        // The .kachat identity caches the display rules read (see the fields above). Every
        // network since iOS d36fc42 (`profilesEnabled`): mainnet has no registry yet, but address
        // profiles need none, so its identities are profile-only (avatars, banners, bios).
        startupStep(".kachat identity caches") {
            if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                startupStep(".kachat registry") { kachatNamesRegistry.get() }
                startupStep(".kachat social images") { kachatSocialImages.get() }
                // People shown as their address are asked again for a .kachat name, once a
                // minute while the app is open (iOS 3214a2b).
                startupStep(".kachat unnamed sweep") {
                    kachatNamesRegistry.get().startUnnamedIdentitySweep(
                        isActive = {
                            ProcessLifecycleOwner.get().lifecycle.currentState
                                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                        },
                        candidates = { unnamedContactAddresses() },
                    )
                }
            }
        }

        // KaChat's group catch-up worker, Nextcloud sync and portfolio backfill are not started:
        // KaPosts has no groups, backups or portfolio. The group worker an older install may
        // have enqueued under this package is cancelled.
        startupStep("group sync worker") {
            WorkManager.getInstance(this).cancelUniqueWork(SyncWorker.WORK_NAME)
        }
        // KaPost shows no notifications about domains: any .kachat renewal reminder an earlier
        // build scheduled is cancelled.
        startupStep(".kachat reminders: cancel") {
            com.kachat.app.services.kachatnames.KachatNameReminders.cancelAll(this)
        }

        // Keep ApiLogging.verbose (read by the OkHttp interceptors in AppModule on every request)
        // in sync with the persisted Verbose API Logging toggle for the process's whole lifetime.
        // A volatile flag instead of collecting the Flow inside the interceptor because that code
        // runs on OkHttp threads for every request of the 2s sync loop.
        ProcessLifecycleOwner.get().lifecycleScope.launch(logOnly) {
            appSettingsRepository.get().verboseApiLogging.collect { com.kachat.app.util.ApiLogging.verbose = it }
        }

        // Foreground/background transitions. There is deliberately NO mechanism here keeping the
        // process alive while backgrounded: FCM push (see PushRegistrationManager) is the only
        // delivery path for 1:1 DMs, broadcasts, and KaPosts once the app leaves the foreground,
        // so a push-delivery problem surfaces as missing notifications instead of being silently
        // masked by a background poller. The in-process pollers normally just freeze with the
        // process and resume on foreground; on battery-exempted devices where the process stays
        // alive, ChatRepository's poll loop and NodePoolManager's probe loop additionally pause
        // themselves while backgrounded with push active (see their gates) so an exempted
        // process doesn't poll forever. The block-stream scanners gate themselves too: the
        // group scanner runs only with at least one group, foregrounded, on an unmetered
        // network (indexer catch-up + the 15-min SyncWorker cover the gaps), and the broadcast
        // scanner drops to indexer-covered delivery on metered networks (see each service's
        // class doc) — a full block stream is the app's single biggest data cost.
        val foregroundObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Backgrounded: remote push takes over as the notification source for the
                // push-covered surfaces — the local paths stop posting their own banners.
                startupStep("notifications: background") { notificationHelper.get().setAppForeground(false) }
                // In-app KaPosts pings are a foreground concern only — the push service covers
                // KaPosts while backgrounded/closed.
                startupStep("KaPosts notifications: stop") { kaPostsNotificationPoller.get().stop() }
                startupStep("address activity: background") { addressActivityNotifier.get().onAppBackground() }
            }

            override fun onStart(owner: LifecycleOwner) {
                // Foregrounded: local poll/scan banners fire again (only the conversation open
                // on screen is suppressed); a racing push for the same tx collapses via the
                // txId dedupe inside NotificationHelper.
                startupStep("notifications: foreground") { notificationHelper.get().setAppForeground(true) }
                // KaPosts pings while the app is actually open (60s poll) — iOS parity.
                startupStep("KaPosts notifications: start") { kaPostsNotificationPoller.get().start() }
                // Immediate catch-up diff for address activity (external receipts while the app
                // was closed, first-run silent baseline seeding), then its foreground poll loop.
                startupStep("address activity: foreground") { addressActivityNotifier.get().onAppForeground() }
                // A batch of gRPC connections can die silently while backgrounded/asleep (the OS
                // tears down sockets, and each KaspadConnection's own self-reconnect can be
                // suspended along with the rest of the app) - reconnect any that are dead right
                // now instead of waiting for the next 5-30s probe cycle to notice and replace them.
                startupStep("node pool: reconnect") { nodePoolManager.get().reconnectStaleConnections() }
                // This wallet's own profile follows the chain on every network (a profile saved
                // on another device shows here; iOS 5d4ce87), and .kachat registrations in flight
                // (where names are live) resume after a relaunch: commit -> wait ->
                // register continues by itself.
                startupStep(".kachat resume") {
                    if (com.kachat.app.services.kachatnames.KachatNamesService.profilesEnabled) {
                        kachatNamesActions.get().resume()
                    }
                }
                if (runCatching { com.kachat.app.services.kachatnames.KachatNamesService.isLaunched }.getOrDefault(false)) {
                    // The .kachat registry, fresh for the name screens. Where names are live
                    // (testnet-10, and mainnet since iOS ef6b21e). KaPost raises no notifications
                    // from it (no KachatNamesNotifier).
                    owner.lifecycleScope.launch(Dispatchers.IO + logOnly) {
                        try {
                            kachatNamesRegistry.get().refreshIfStale()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("KaChatApplication", ".kachat registry refresh failed", e)
                        }
                    }
                }
            }
        }
        startupStep("lifecycle observer") { ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver) }
    }

    private companion object {
        const val TAG = "KaChatApplication"
    }

    /**
     * The contacts the app shows as an address unless they have a .kachat name: no name you gave
     * them in the chat or the Address Book, not your own address - the people you talk to most
     * recently first (iOS KachatNamesRegistry.refreshUnnamedIdentities).
     */
    private suspend fun unnamedContactAddresses(): List<String> {
        val repo = chatRepository.get()
        val contacts = repo.getContacts().first()
        val latest = repo.getLatestMessages().first().associate { it.contactId to it.blockTimestamp }
        return contacts
            .filter { contact ->
                contact.alias.isNullOrBlank() &&
                    com.kachat.app.services.AddressBookManager.nameFor(contact.id) == null &&
                    // the self-chat: a contact row of the account's own address
                    !contact.id.equals(contact.walletAddress, ignoreCase = true)
            }
            .sortedByDescending { latest[it.id] ?: 0L }
            .map { it.id }
    }
}
