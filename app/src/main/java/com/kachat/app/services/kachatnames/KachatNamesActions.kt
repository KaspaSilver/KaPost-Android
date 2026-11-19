package com.kachat.app.services.kachatnames

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kachat.app.services.WalletManager
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `.kachat` actions KaPost still takes: saving the address profile (`kchat:1:profile:`) and
 * following that transaction until it shows, plus reading which names and offers this wallet
 * holds. Claiming, renewing, selling, transferring, buying and offers are KaPay's (owner's call,
 * 2026-11-18), so nothing here signs a registry transaction: no claim resumes, no offer is
 * returned or withdrawn by itself (iOS b78fdb0 `KachatNamesActions`).
 */
@Singleton
class KachatNamesActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: KachatNamesService,
    private val registry: KachatNamesRegistry,
    private val walletManager: WalletManager,
    /** Whether a spending address was ever used ([ownNameKeys], iOS d1026de: ChatService's
     *  `spendingAddressUsedState`). Lazy: only read when the registry is asked about them. */
    private val walletService: dagger.Lazy<com.kachat.app.services.WalletService>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.kachat.app.util.StartupSafety.logOnlyHandler("KachatNamesActions"))
    private val gson = Gson()
    private val lock = Any()

    /** Action errors; English like iOS's service errors (localized by the screens, `kachatErrorText`). */
    sealed class ActionError(message: String) : Exception(message) {
        class NoWallet : ActionError("No wallet is open.")
        class KeyMismatch : ActionError("This wallet's key does not match its address.")
    }

    class Signer(val address: String, val privateKey: ByteArray, val me: ByteArray)

    /**
     * The current wallet's chatting address and key on the network the app runs on - the profile
     * record's signer (iOS d36fc42 `profileSigner()`). It doesn't need a live registry: profiles
     * work on every network ([KachatNamesService.profilesEnabled]).
     */
    fun profileSigner(): Signer {
        if (!KachatNamesService.profilesEnabled) throw KachatNamesService.ServiceError.TestnetOnly()
        val address = walletManager.getActiveAccount()?.address?.lowercase() ?: throw ActionError.NoWallet()
        val key = try { walletManager.getPrivateKeyBytes() } catch (_: Exception) { throw ActionError.NoWallet() }
        if (!KaspaNetwork.isOnActiveNetwork(address)) throw KachatNamesService.ServiceError.WrongAddressNetwork()
        val me = KachatNamesService.xonlyKey(key)
        // the address must be this key's Schnorr (version 0) address, on either network
        val (version, payload) = runCatching { KaspaAddress.decode(address) }.getOrNull() ?: throw ActionError.KeyMismatch()
        if (version.toInt() != 0 || !payload.contentEquals(me)) throw ActionError.KeyMismatch()
        return Signer(address, key, me)
    }

    // Addresses an earlier build bought with (iOS eb9b027)

    private val marketPrefs: SharedPreferences
        get() = context.getSharedPreferences(MARKET_PREFS_NAME, Context.MODE_PRIVATE)

    private val marketAddressesKey: String? get() = myAddress?.let { "kachat_market_addresses.$it" }

    /** Spending addresses an earlier build bought with (the retired Buy From picker), per wallet:
     *  Your Domains still lists the names they hold next to the chatting address's. Nothing adds
     *  to it any more. */
    val marketAddresses: List<String>
        get() {
            val key = marketAddressesKey ?: return emptyList()
            val raw = marketPrefs.getString(key, null) ?: return emptyList()
            return runCatching { gson.fromJson<List<String>>(raw, STRING_LIST_TYPE) }.getOrNull().orEmpty()
        }

    // This wallet's keys (iOS d1026de, IOS-085 / XP-024)

    /** "wallet|highest revealed spending index|addresses Buy From was once set to": when this changes,
     *  the wallet's keys are worked out again. */
    private fun ownKeysCacheKey(wallet: String): String {
        val account = walletManager.getActiveAccount()
        val max = maxOf(0, account?.let { WalletManager.legacyMaxSpendingIndex(it) } ?: 0)
        return "$wallet|$max|${marketAddresses.size}"
    }

    /** Every revealed spending address, index order (iOS `spendingAddresses(inRange:)`); empty
     *  when they can't be derived. Derives on a cache miss, so off the main thread. */
    private fun revealedSpendingAddresses(): List<String> {
        val account = walletManager.getActiveAccount() ?: return emptyList()
        val max = maxOf(0, WalletManager.legacyMaxSpendingIndex(account))
        return runCatching { walletManager.deriveSpendingAddresses(0..max) }.getOrNull().orEmpty()
            .entries.sortedBy { it.key }.map { it.value }
    }

    @Volatile private var ownKeyCache: Pair<String, List<ByteArray>>? = null

    /**
     * The chatting key and every revealed spending address's key: a name or an offer on any of
     * them is this wallet's, on every device restored from the seed - not only on the one that
     * bought it. Worked out once per account and address count (iOS d1026de `allOwnKeys`, which
     * is a property there; here it may derive addresses, so it runs on [Dispatchers.IO]).
     */
    suspend fun allOwnKeys(): List<ByteArray> = withContext(Dispatchers.IO) {
        val wallet = myAddress ?: return@withContext emptyList()
        val cacheKey = ownKeysCacheKey(wallet)
        ownKeyCache?.takeIf { it.first == cacheKey }?.let { return@withContext it.second }
        val keys = ArrayList<ByteArray>()
        fun add(key: ByteArray?) {
            if (key != null && keys.none { it.contentEquals(key) }) keys.add(key)
        }
        add(myKey)
        val spending = revealedSpendingAddresses()
        for (address in spending) add(KachatNamesRegistry.keyOf(address))
        for (address in marketAddresses) add(KachatNamesRegistry.keyOf(address))
        // a failed derivation isn't remembered: the next call tries again
        if (spending.isNotEmpty()) ownKeyCache = cacheKey to keys
        keys
    }

    @Volatile private var nameKeysCache: Triple<String, Long, List<ByteArray>>? = null

    /**
     * The keys worth asking the registry about (one lookup each): the chatting key, then every
     * revealed spending address that has ever been used - a name or an offer needs KAS on its
     * address - and the addresses Buy From was set to. The used/unused answers are cached (used
     * is for good), so after the first time this costs no requests; the list itself is kept ten
     * minutes. Probed eight at a time (iOS d1026de `ownNameKeys`).
     */
    suspend fun ownNameKeys(): List<ByteArray> = withContext(Dispatchers.IO) {
        val wallet = myAddress ?: return@withContext emptyList()
        val me = myKey ?: return@withContext emptyList()
        val cacheKey = ownKeysCacheKey(wallet)
        nameKeysCache?.let { (key, at, keys) ->
            if (key == cacheKey && System.currentTimeMillis() - at < 600_000L) return@withContext keys
        }
        val spending = revealedSpendingAddresses()
        val hints = marketAddresses.toSet()
        val wallets = walletService.get()
        val used = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val toProbe = ArrayList<String>()
        for (address in spending) {
            if (address.lowercase() in hints) { used[address] = true; continue }
            val known = wallets.cachedSpendingAddressUsedState(address)
            if (known != null) used[address] = known else toProbe += address
        }
        val gate = Semaphore(8)
        coroutineScope {
            toProbe.map { address ->
                async { gate.withPermit { used[address] = wallets.spendingAddressUsedState(address) ?: false } }
            }.awaitAll()
        }
        val keys = mutableListOf(me)
        fun add(key: ByteArray?) {
            if (key != null && keys.none { it.contentEquals(key) }) keys.add(key)
        }
        for (address in spending) if (used[address] == true) add(KachatNamesRegistry.keyOf(address))
        for (address in marketAddresses) add(KachatNamesRegistry.keyOf(address))
        // a different wallet signed in meanwhile: this answer isn't its
        if (myAddress != wallet) return@withContext emptyList()
        nameKeysCache = Triple(cacheKey, System.currentTimeMillis(), keys)
        keys
    }

    /**
     * Every name ([heldOnly] false: inactive ones too) or held name, and every open offer, on
     * this wallet's keys ([ownNameKeys]), asked four keys at a time (iOS d1026de
     * `ownNamesAndOffers`). Throws when a lookup fails, as iOS.
     */
    suspend fun ownNamesAndOffers(heldOnly: Boolean): Pair<List<NameInfo>, List<OfferInfo>> {
        val keys = ownNameKeys()
        val gate = Semaphore(4)
        val answers = coroutineScope {
            keys.map { key ->
                async {
                    gate.withPermit {
                        val n = if (heldOnly) registry.heldNames(key) else registry.names(key, includeInactive = true)
                        n to registry.myOffers(key)
                    }
                }
            }.awaitAll()
        }
        return answers.flatMap { it.first } to answers.flatMap { it.second }
    }

    /** The current wallet's x-only key, without touching the private key. */
    val myKey: ByteArray? get() = walletManager.getActiveAccount()?.address?.let { KachatNamesRegistry.keyOf(it) }

    val myAddress: String? get() = walletManager.getActiveAccount()?.address?.lowercase()

    /**
     * Brings this wallet's own profile up to date with the chain (every network): a profile saved
     * on another device (iPhone, Desktop) shows here too (iOS 5d4ce87). Called when the app comes
     * to the foreground (KaChatApplication, iOS `handleScenePhaseChange`). Nothing else: no claim
     * is resumed (iOS b78fdb0).
     */
    fun resume() {
        val address = myAddress ?: return
        scope.launch { registry.syncOwnProfile(address) }
    }

    // Following a sent transaction, node only (iOS e426432)

    /** Where a sent profile transaction is: sent, waiting in a node's mempool, in a block, shown
     *  (the registry has it), or dropped (no node has it). */
    enum class TxStage { SENT, IN_MEMPOOL, ACCEPTED, SHOWN, DROPPED }

    private val _txStages = MutableStateFlow<Map<String, TxStage>>(emptyMap())
    /** The stage of every transaction sent from this app this session, by txid. */
    val txStages: StateFlow<Map<String, TxStage>> = _txStages.asStateFlow()

    private fun setStage(txId: String, stage: TxStage) {
        synchronized(lock) { _txStages.value = _txStages.value + (txId to stage) }
    }

    /**
     * Follows [txId] (a profile save, a plain self-transfer) until it is in a block, then refreshes
     * the registry so the change shows. Only the mempool, then the REST API, can tell (iOS
     * b78fdb0 `follow(txId:)`).
     */
    fun follow(txId: String) {
        setStage(txId, TxStage.SENT)
        scope.launch {
            val started = System.currentTimeMillis()
            fun elapsed() = System.currentTimeMillis() - started
            while (elapsed() < 300_000) {
                if (service.isInMempool(txId)) {
                    if (_txStages.value[txId] == TxStage.SENT) setStage(txId, TxStage.IN_MEMPOOL)
                } else {
                    // Out of the mempool: in a block, or dropped. The REST API settles it.
                    if (registry.isAccepted(txId)) {
                        setStage(txId, TxStage.ACCEPTED)
                        registry.refreshUntilIncludes(txId, service.currentVirtualDaaScore())
                        setStage(txId, TxStage.SHOWN)
                        return@launch
                    }
                    if (elapsed() > 60_000) {
                        setStage(txId, TxStage.DROPPED)
                        return@launch
                    }
                }
                delay(1_000)
            }
            setStage(txId, TxStage.DROPPED)
        }
    }

    // Profile record

    /**
     * What saving [profile] will cost: the profile record is a self-transfer from the chatting
     * address, so the network fee is all it spends. Built (and signed) the way [saveProfile]
     * builds it, never sent: spent inputs minus outputs (iOS `profileFee`, 7e238e5).
     */
    suspend fun profileFee(profile: Profile): Long = withContext(Dispatchers.IO) {
        val s = profileSigner()
        val json = profile.sanitized().recordJSON()
        service.profileRecordFee(s.address, s.privateKey, json)
    }

    /** Writes the address profile (`kchat:1:profile:`): a self-transfer, network fee only. */
    suspend fun saveProfile(profile: Profile): String = withContext(Dispatchers.IO) {
        val s = profileSigner()
        val clean = profile.sanitized()
        val json = clean.recordJSON()
        val txId = service.submitProfileRecord(s.address, s.privateKey, json)
        registry.noteOwnProfile(clean, s.address, txId)
        registry.refreshAfter(txId)
        txId
    }

    companion object {
        /** iOS keeps these in UserDefaults.standard (`kachat_market_addresses.<wallet>`). */
        private const val MARKET_PREFS_NAME = "kachat_market_addresses"
        private val STRING_LIST_TYPE = object : TypeToken<List<String>>() {}.type
    }
}
