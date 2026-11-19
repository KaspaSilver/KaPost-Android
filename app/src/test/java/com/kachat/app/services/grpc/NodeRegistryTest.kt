package com.kachat.app.services.grpc

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRegistryTest {

    // Unit tests run on mainnet (KaspaNetwork.launch), so an eligible node reports "kaspa-mainnet"
    // (iOS 369794e `NodeRegistry.isActiveEligible`).
    private fun reachable(
        address: String,
        synced: Boolean = true,
        networkName: String? = "kaspa-mainnet",
        daa: Long? = 123L,
    ) = NodeProbeResult(
        address = address, reachable = true, latencyMs = 42, isSynced = synced,
        isUtxoIndexed = true, serverVersion = "1.0.0", networkName = networkName, virtualDaaScore = daa
    )

    private fun unreachable(address: String) = NodeProbeResult(
        address = address, reachable = false, latencyMs = null, isSynced = null,
        isUtxoIndexed = null, serverVersion = null, networkName = null, virtualDaaScore = null, error = "timeout"
    )

    @Test
    fun `a freshly successful node is Active`() {
        val registry = NodeRegistry()
        registry.update("1.2.3.4:16110", "Seed", reachable("1.2.3.4:16110"))

        val record = registry.snapshot().single()
        assertEquals("Active", registry.statusOf(record))
        assertEquals(0, record.consecutiveFailures)
    }

    @Test
    fun `reachable but not synced is Suspect, not Active`() {
        val registry = NodeRegistry()
        registry.update("1.2.3.4:16110", "Seed", reachable("1.2.3.4:16110", synced = false))

        val record = registry.snapshot().single()
        assertEquals("Suspect", registry.statusOf(record))
    }

    @Test
    fun `one or two failures is Suspect, not yet Quarantined`() {
        val registry = NodeRegistry()
        val address = "1.2.3.4:16110"
        registry.update(address, "Seed", unreachable(address))
        assertEquals("Suspect", registry.statusOf(registry.snapshot().single()))

        registry.update(address, "Seed", unreachable(address))
        val record = registry.snapshot().single()
        assertEquals(2, record.consecutiveFailures)
        assertEquals("Suspect", registry.statusOf(record))
    }

    @Test
    fun `three consecutive failures becomes Quarantined`() {
        val registry = NodeRegistry()
        val address = "1.2.3.4:16110"
        repeat(3) { registry.update(address, "Seed", unreachable(address)) }

        val record = registry.snapshot().single()
        assertEquals(3, record.consecutiveFailures)
        assertEquals("Quarantined", registry.statusOf(record))
    }

    @Test
    fun `a success resets the failure counter and recovers to Active`() {
        val registry = NodeRegistry()
        val address = "1.2.3.4:16110"
        repeat(3) { registry.update(address, "Seed", unreachable(address)) }
        registry.update(address, "Seed", reachable(address))

        val record = registry.snapshot().single()
        assertEquals(0, record.consecutiveFailures)
        assertEquals("Active", registry.statusOf(record))
    }

    @Test
    fun `resetTo replaces the entire registry contents`() {
        val registry = NodeRegistry()
        registry.update("old:16110", "Discovered", reachable("old:16110"))

        registry.resetTo(listOf("seed1:16110", "seed2:16110"), "Seed")

        val snapshot = registry.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals(setOf("seed1:16110", "seed2:16110"), snapshot.map { it.address }.toSet())
    }

    @Test
    fun `lastSuccessAt reflects the most recent successful probe across all nodes`() {
        val registry = NodeRegistry()
        registry.update("a:16110", "Seed", unreachable("a:16110"))
        assertEquals(null, registry.lastSuccessAt())

        registry.update("b:16110", "Seed", reachable("b:16110"))
        assertEquals(true, registry.lastSuccessAt()!! > 0)
    }

    // MARK: - Active eligibility (iOS 369794e)

    @Test
    fun `a node on another network is Suspect, unless the user added it`() {
        val registry = NodeRegistry()
        registry.update("t:16110", "Seed", reachable("t:16110", networkName = "kaspa-testnet-10"))
        assertEquals("Suspect", registry.statusOf(registry.snapshot().single()))

        val manual = NodeRegistry()
        manual.update("m:16110", "Manual", reachable("m:16110", networkName = "kaspa-testnet-10"))
        assertEquals("Active", manual.statusOf(manual.snapshot().single()))
    }

    @Test
    fun `a node that never named its network is Suspect`() {
        val registry = NodeRegistry()
        registry.update("n:16110", "Seed", reachable("n:16110", networkName = null))
        assertEquals("Suspect", registry.statusOf(registry.snapshot().single()))
    }

    @Test
    fun `a failed network read keeps the name reported before`() {
        val registry = NodeRegistry()
        registry.update("k:16110", "Seed", reachable("k:16110"))
        registry.update("k:16110", "Seed", reachable("k:16110", networkName = null))
        val record = registry.snapshot().single()
        assertEquals("kaspa-mainnet", record.lastProbe?.networkName)
        assertEquals("Active", registry.statusOf(record))
    }

    @Test
    fun `a node more than 600 DAA behind the pool's median is Suspect`() {
        val registry = NodeRegistry()
        registry.update("a:16110", "Seed", reachable("a:16110", daa = 1_000_000L))
        registry.update("b:16110", "Seed", reachable("b:16110", daa = 1_000_000L))
        registry.update("near:16110", "Seed", reachable("near:16110", daa = 999_500L))
        registry.update("far:16110", "Seed", reachable("far:16110", daa = 998_000L))
        val byAddress = registry.snapshot().associateBy { it.address }
        assertEquals(600L, NodeRegistry.MAX_ACTIVE_DAA_LAG)
        assertEquals("Active", registry.statusOf(byAddress.getValue("a:16110")))
        assertEquals("Active", registry.statusOf(byAddress.getValue("near:16110")))
        assertEquals("Suspect", registry.statusOf(byAddress.getValue("far:16110")))
    }
}
