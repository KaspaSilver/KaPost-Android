package com.kachat.app.models

import androidx.room.Entity

/** One side of a swap pair — ChangeNOW addresses currencies by (ticker, network) since some tickers exist on more than one chain. */
data class SwapCoin(val ticker: String, val network: String, val displayName: String)

/** Kaspa itself — always one side of every swap this app can do. Verified against ChangeNOW's live /v2/exchange/currencies list: ticker "kas", network "kas". */
val KAS_SWAP_COIN = SwapCoin("kas", "kas", "Kaspa")

/**
 * ChangeNOW's network code for Polygon is "matic" (not "polygon") — confirmed against the live
 * /v2/exchange/currencies list.
 */
val USDC_POLYGON_SWAP_COIN = SwapCoin("usdc", "matic", "USDC Coin (Polygon)")

// Single-network coins. All ticker/network pairs below were verified against ChangeNOW's live
// /v2/exchange/currencies list before adding - getting one of these wrong would send a swap's
// payout to the wrong chain, so none of this is guessed. Pi Network and DASH were explicitly
// requested but do not exist anywhere in ChangeNOW's currency list (checked twice), so they are
// not included.
val BTC_SWAP_COIN = SwapCoin("btc", "btc", "Bitcoin")
val ETH_SWAP_COIN = SwapCoin("eth", "eth", "Ethereum")
val SOL_SWAP_COIN = SwapCoin("sol", "sol", "Solana")
val XRP_SWAP_COIN = SwapCoin("xrp", "xrp", "XRP")
val BNB_SWAP_COIN = SwapCoin("bnb", "bsc", "BNB (BNB Smart Chain)")
val TRX_SWAP_COIN = SwapCoin("trx", "trx", "TRON")
val HYPE_SWAP_COIN = SwapCoin("hype", "hyperevm", "Hyperliquid")
val DOGE_SWAP_COIN = SwapCoin("doge", "doge", "Dogecoin")
val LTC_SWAP_COIN = SwapCoin("ltc", "ltc", "Litecoin")
val ZEC_SWAP_COIN = SwapCoin("zec", "zec", "Zcash")
val XMR_SWAP_COIN = SwapCoin("xmr", "xmr", "Monero")
val ADA_SWAP_COIN = SwapCoin("ada", "ada", "Cardano")
val BCH_SWAP_COIN = SwapCoin("bch", "bch", "Bitcoin Cash")
val ETC_SWAP_COIN = SwapCoin("etc", "etc", "Ethereum Classic")

// Tether (USDT), every network ChangeNOW lists.
val USDT_ETH_SWAP_COIN = SwapCoin("usdt", "eth", "Tether (ERC20)")
val USDT_TRX_SWAP_COIN = SwapCoin("usdt", "trx", "Tether (TRC20)")
val USDT_BSC_SWAP_COIN = SwapCoin("usdt", "bsc", "Tether (BNB Smart Chain)")
val USDT_SOL_SWAP_COIN = SwapCoin("usdt", "sol", "Tether (Solana)")
val USDT_MATIC_SWAP_COIN = SwapCoin("usdt", "matic", "Tether (Polygon)")
val USDT_ARBITRUM_SWAP_COIN = SwapCoin("usdt", "arbitrum", "Tether (Arbitrum)")
val USDT_OP_SWAP_COIN = SwapCoin("usdt", "op", "Tether (Optimism)")

// USDC, every other network ChangeNOW lists (Polygon is USDC_POLYGON_SWAP_COIN above).
val USDC_ETH_SWAP_COIN = SwapCoin("usdc", "eth", "USDC Coin (Ethereum)")
val USDC_SOL_SWAP_COIN = SwapCoin("usdc", "sol", "USDC Coin (Solana)")
val USDC_BSC_SWAP_COIN = SwapCoin("usdc", "bsc", "USDC Coin (BNB Smart Chain)")
val USDC_ALGO_SWAP_COIN = SwapCoin("usdc", "algo", "USDC Coin (Algorand)")
val USDC_OP_SWAP_COIN = SwapCoin("usdc", "op", "USDC Coin (Optimism)")
val USDC_ARBITRUM_SWAP_COIN = SwapCoin("usdc", "arbitrum", "USDC Coin (Arbitrum)")
val USDC_BASE_SWAP_COIN = SwapCoin("usdc", "base", "USDC Coin (Base)")
val USDC_SUI_SWAP_COIN = SwapCoin("usdc", "sui", "USDC Coin (Sui)")

val CURATED_SWAP_COINS = listOf(
    BTC_SWAP_COIN, ETH_SWAP_COIN, SOL_SWAP_COIN, XRP_SWAP_COIN, BNB_SWAP_COIN, TRX_SWAP_COIN,
    HYPE_SWAP_COIN, DOGE_SWAP_COIN, LTC_SWAP_COIN, ZEC_SWAP_COIN, XMR_SWAP_COIN, ADA_SWAP_COIN,
    BCH_SWAP_COIN, ETC_SWAP_COIN,
    USDT_ETH_SWAP_COIN, USDT_TRX_SWAP_COIN, USDT_BSC_SWAP_COIN, USDT_SOL_SWAP_COIN,
    USDT_MATIC_SWAP_COIN, USDT_ARBITRUM_SWAP_COIN, USDT_OP_SWAP_COIN,
    USDC_POLYGON_SWAP_COIN, USDC_ETH_SWAP_COIN, USDC_SOL_SWAP_COIN, USDC_BSC_SWAP_COIN,
    USDC_ALGO_SWAP_COIN, USDC_OP_SWAP_COIN, USDC_ARBITRUM_SWAP_COIN,
    USDC_BASE_SWAP_COIN, USDC_SUI_SWAP_COIN
)

/**
 * Local record of a swap this device initiated, kept for the "Swap History" list — ChangeNOW is
 * the source of truth for the exchange itself, this just remembers it happened and caches the
 * last status we saw so the list has something to show without a network round trip on open.
 */
@Entity(tableName = "swap_transactions", primaryKeys = ["id"])
data class SwapTransactionEntity(
    val id: String, // ChangeNOW exchange id — also the primary key
    val fromTicker: String,
    val fromNetwork: String,
    val toTicker: String,
    val toNetwork: String,
    val fromAmount: String,
    val toAmount: String,
    val payinAddress: String,
    val payoutAddress: String,
    val status: String,
    val createdAtMillis: Long,
    val kasSendTxId: String? = null, // set once this device auto-sent KAS to payinAddress, when KAS was the "from" side
    val addedToPortfolio: Boolean = false // set once the KAS leg of this swap has been recorded as a portfolio transaction
)
