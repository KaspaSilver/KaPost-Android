package com.kachat.app.services.grpc

import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time health record for one known node address.
 *
 * Deliberately simpler than the EWMA/quarantine-curve scheme sketched in the
 * project's POOLS_v2.md reference doc — a plain consecutive-failure counter is
 * enough for a status *display* (this isn't a routing decision under load that
 * needs a graceful backoff curve).
 */
data class NodeRecord(
    val address: String,
    val type: String, // "Seed" | "Discovered" | "DNS" | "Manual" | "Trusted"
    val lastProbe: NodeProbeResult?,
    val consecutiveFailures: Int = 0,
    val lastSuccessAt: Long? = null
)

class NodeRegistry {
    private val records = ConcurrentHashMap<String, NodeRecord>()

    fun update(address: String, type: String, result: NodeProbeResult) {
        val prior = records[address]
        // A failed GetBlockDagInfo leaves the network name it reported before: null would make an
        // unknown node of a known one (iOS 369794e `NodeProfiler.probe`).
        val kept = if (result.reachable && result.networkName.isNullOrBlank()) {
            result.copy(networkName = prior?.lastProbe?.networkName?.takeIf { it.isNotBlank() })
        } else {
            result
        }
        records[address] = NodeRecord(
            address = address,
            type = type,
            lastProbe = kept,
            consecutiveFailures = if (result.reachable) 0 else (prior?.consecutiveFailures ?: 0) + 1,
            lastSuccessAt = if (result.reachable) System.currentTimeMillis() else prior?.lastSuccessAt
        )
    }

    fun snapshot(): List<NodeRecord> = records.values.toList()

    fun remove(address: String) {
        records.remove(address)
    }

    /** Resets the registry back to just the given addresses (used by "Clear Connection Pool"). */
    fun resetTo(addresses: List<String>, type: String) {
        records.clear()
        addresses.forEach { records[it] = NodeRecord(address = it, type = type, lastProbe = null) }
    }

    fun containsAddress(address: String): Boolean = records.containsKey(address)

    /**
     * Zeroes every record's consecutive-failure count while keeping the addresses and last
     * probe results — called when the device's network path changes (WiFi <-> cellular, VPN
     * up/down; see NodePoolManager's ConnectivityManager callback). Unreachable/Quarantined
     * verdicts are facts about the OLD network only: a node unreachable on WiFi may be
     * perfectly fine on the VPN, so every node gets a fresh 3-strike allowance on the new
     * path instead of carrying strikes across networks. Records whose last probe failed drop
     * from "Quarantined" back to "Suspect" (statusOf's failure branch) until re-probed, which
     * also protects Discovered/DNS entries from NodePoolManager's Quarantine-based pruning
     * right after a path change.
     */
    fun forgiveFailures() {
        records.replaceAll { _, r -> r.copy(consecutiveFailures = 0) }
    }

    /** Most recent successful probe across every known node — drives the "Last Sync" field. */
    fun lastSuccessAt(): Long? = records.values.mapNotNull { it.lastSuccessAt }.maxOrNull()

    fun statusOf(r: NodeRecord): String {
        // A pinned trusted node is always used regardless of health (see
        // NodePoolManager.getBroadcastConnection, which bypasses this function entirely for
        // it) - the Suspect/Quarantined classification below exists to filter and rank a large
        // auto-discovered pool, and is meaningless (and misleading) for the one node the user
        // explicitly chose to always connect to.
        if (r.type == "Trusted") return "Active"
        return when {
            r.lastProbe?.reachable != true -> if (r.consecutiveFailures >= 3) "Quarantined" else "Suspect"
            r.lastProbe.isSynced == false -> "Suspect" // reachable but not synced — don't trust its data
            // A node on another network, or lagging the others, serves no balances (iOS 369794e
            // `NodeRegistry.isActiveEligible`); a node the user added is kept as it is.
            r.type != "Manual" && !isActiveEligible(r, daaReference(System.currentTimeMillis()), System.currentTimeMillis()) -> "Suspect"
            else -> "Active"
        }
    }

    /** Reachable, synced, and a reported network name that is the running network's (iOS
     *  `NodeRecord.isActiveEligible` + `NodeRegistry.belongs`). */
    private fun fitsNetwork(r: NodeRecord): Boolean {
        val probe = r.lastProbe ?: return false
        if (!probe.reachable || probe.isSynced == false) return false
        val name = probe.networkName
        return !name.isNullOrBlank() && belongsToRunningNetwork(name)
    }

    /**
     * Active eligibility in full (iOS 369794e `NodeRegistry.isActiveEligible`): [fitsNetwork], and a
     * DAA score no more than [MAX_ACTIVE_DAA_LAG] behind [reference] (an unknown score isn't
     * counted as lag).
     */
    private fun isActiveEligible(r: NodeRecord, reference: Long?, nowMs: Long): Boolean {
        if (!fitsNetwork(r)) return false
        val daa = projectedDaaScore(r, nowMs) ?: return true
        if (reference == null || daa >= reference) return true
        return reference - daa <= MAX_ACTIVE_DAA_LAG
    }

    /** The median projected DAA score of the nodes otherwise fit to be active: at least half of
     *  them are never behind it, so the lag rule can't leave the pool empty (iOS 369794e
     *  `NodeRegistry.daaReference`). */
    private fun daaReference(nowMs: Long): Long? {
        val scores = records.values
            .filter { it.type != "Trusted" && fitsNetwork(it) }
            .mapNotNull { projectedDaaScore(it, nowMs) }
            .sorted()
        return if (scores.isEmpty()) null else scores[scores.size / 2]
    }

    /** The node's reported DAA score carried forward to [nowMs] (10 a second), so nodes probed
     *  apart compare fairly. Null when the report is missing or older than [PROFILE_TTL_MS] (iOS
     *  `NodeRecord.projectedDaaScore`). */
    private fun projectedDaaScore(r: NodeRecord, nowMs: Long): Long? {
        val daa = r.lastProbe?.virtualDaaScore ?: return null
        val probedAt = r.lastSuccessAt ?: return null
        val ageMs = nowMs - probedAt
        if (ageMs < 0 || ageMs > PROFILE_TTL_MS) return null
        val projected = daa + ageMs / 100
        return if (projected < daa) Long.MAX_VALUE else projected
    }

    companion object {
        /** How far (in DAA score) an active node may trail the pool's median: about a minute at 10
         *  blocks a second (iOS 369794e `NodeRecord.maxActiveDaaLag`). */
        const val MAX_ACTIVE_DAA_LAG = 600L

        /** How long a probe's DAA score is good for (iOS `NodeProfile.profileTTL`, 10 min). */
        const val PROFILE_TTL_MS = 600_000L
    }
}
