package com.kachat.app.models

/**
 * Identity derivation-path family of the wallet a seed phrase was imported from — found by
 * import's auto-detect (iOS 1ab014f `findImportAccounts`, [com.kachat.app.viewmodels.WalletViewModel.findImportAccounts]),
 * which replaced the "which wallet is this seed from?" list. Different Kaspa wallets put the same
 * seed's funds and KNS domains on different BIP32 branches; opening the right family at import is
 * what lets KaChat find them. Ported from iOS's
 * `WalletSourceFamily` (`Services/WalletManager.swift`), whose rules were replicated from
 * KasWare's ADDRESS_TYPES/RESTORE_WALLETS constants and its `hd-keyring.ts` `_pubkeyFromIndex`
 * derivation switch.
 *
 * - [KASPA_STANDARD]: `m/44'/111111'/0'/0/{i}` (receive chain, normal final index). KaChat's own
 *   family, and also what KasWare, Kaspium, Kastle, Core Golang CLI, OKX and Ledger seed imports
 *   land on. (Kastle derives `m/44'/111111'/{account}'/0/{index}`; account 0 is byte-identical to
 *   this, and its secondary variant `m/44'/111111'/0'/0/{accountIndex}` falls inside the same
 *   scan range, so it needs no derivation code of its own.)
 * - [KASPA_LEGACY_972]: `m/44'/972/0'/0'/{i'}` — KDX and the Kaspanet Web Wallet. NOTE: 972 is
 *   deliberately NOT hardened (KasWare's hdPath string "m/44'/972/0'" has no apostrophe on 972
 *   and their keyring derives it normally), while the change level AND the final index ARE
 *   hardened.
 * - [ONE_KEY]: the standard `m/44'/111111'/0'/0/{i}` key, then a BIP340 taproot-style tweak —
 *   negate the private key when its compressed pubkey has an odd Y (0x03 prefix), then add
 *   taggedHash("TapTweak", xOnlyPubkey) mod n. The address derives from the tweaked key.
 *
 * Persisted by [name] on [com.kachat.app.services.WalletManager.Account.sourceFamily] — do not
 * rename the entries. A null/unknown stored value means [KASPA_STANDARD], which is exactly what
 * every account created before this feature existed used.
 */
enum class WalletSourceFamily {
    KASPA_STANDARD,
    KASPA_LEGACY_972,
    /** Chainge: m/44'/111111'/0'/0', one hardened leaf - exactly one address per seed, so only
     *  index 0 exists (iOS `chainge`). Placed where iOS lists it, since import walks [entries]. */
    CHAINGE,
    ONE_KEY;

    /**
     * How import names this wallet type (iOS 1ab014f `displayName`). Standard covers KaChat,
     * KasWare, Kaspium, Kastle, Core Golang CLI, OKX and Ledger, which all use the same addresses.
     * Standard's name is translated: the screens show `R.string.import_standard_kaspa_wallet`.
     */
    val displayName: String
        get() = when (this) {
            KASPA_STANDARD -> "Standard Kaspa Wallet"
            KASPA_LEGACY_972 -> "KDX / Kaspanet Web"
            CHAINGE -> "Chainge"
            ONE_KEY -> "OneKey"
        }

    /**
     * How many addresses import's auto-detect checks (iOS 1ab014f `importSearchDepth`). The
     * standard chain keeps the depth of one page of the chatting address picker; the others only
     * have to be recognised.
     */
    val importSearchDepth: IntRange
        get() = when (this) {
            KASPA_STANDARD -> 0 until 50
            KASPA_LEGACY_972, ONE_KEY -> 0 until 20
            CHAINGE -> 0 until 1
        }

    /** True when the family has a whole receive chain to scan; false for Chainge, where only
     *  index 0 exists (iOS `supportsIndexScan`). */
    val supportsIndexScan: Boolean
        get() = this != CHAINGE

    companion object {
        fun fromRaw(raw: String?): WalletSourceFamily =
            entries.firstOrNull { it.name == raw } ?: KASPA_STANDARD
    }
}

/** Which wallet type and address an import should open (iOS 1ab014f `ImportAccountChoice`). */
data class ImportAccountChoice(
    val family: WalletSourceFamily = WalletSourceFamily.KASPA_STANDARD,
    val index: Int = 0,
)

/**
 * An address import's auto-detect looked at (iOS 1ab014f `ImportAccountCandidate`,
 * [com.kachat.app.viewmodels.WalletViewModel.findImportAccounts]).
 */
data class ImportAccountCandidate(
    val family: WalletSourceFamily,
    val index: Int,
    val address: String,
    val balanceSompi: Long = 0L,
    val hasHistory: Boolean = false,
) {
    val choice: ImportAccountChoice get() = ImportAccountChoice(family, index)
    val id: String get() = "${family.name}-$index"
}
