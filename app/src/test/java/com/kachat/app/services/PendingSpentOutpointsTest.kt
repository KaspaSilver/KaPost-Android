package com.kachat.app.services

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cold Storage sends record the coins they spent (audit AND-015). */
class PendingSpentOutpointsTest {

    private val address = "kaspa:qpcold0000000000000000000000000000000000000000000000000000"
    private var clock = 1_000_000L
    // The default TTL: 10 minutes since iOS 369794e (was 120 s).
    private val pending = PendingSpentOutpoints(now = { clock })

    private fun utxo(tx: String, index: Int = 0, amount: Long = 100_000_000L) = UtxoEntry(
        address = address,
        outpoint = Outpoint(transactionId = tx, index = index),
        utxoEntry = UtxoData(amount = amount, scriptPublicKey = ScriptPublicKey("aa"), blockDaaScore = 1, isCoinbase = false)
    )

    private val a = utxo("a")
    private val b = utxo("b")
    private val c = utxo("c", 1)

    @Test
    fun `nothing recorded leaves the fetch untouched`() {
        assertEquals(listOf(a, b), pending.filter(address, listOf(a, b)))
    }

    @Test
    fun `a broadcast's inputs are kept out of the next fetch, even a stale one`() {
        pending.record(address, listOf(a.outpoint, c.outpoint))
        assertEquals(listOf(b), pending.filter(address, listOf(a, b, c)))
        // A fetch that already dropped the coin does not end the guard: a lagging source after it
        // may still list the coin.
        assertEquals(listOf(b), pending.filter(address, listOf(b)))
        assertEquals(listOf(b), pending.filter(address, listOf(a, b, c)))
    }

    @Test
    fun `the outpoint index matters, not just the transaction`() {
        pending.record(address, listOf(Outpoint("c", 0)))
        assertEquals(listOf(c), pending.filter(address, listOf(c)))
    }

    @Test
    fun `the TTL is ten minutes, as iOS reservedOutpointTTL`() {
        assertEquals(600_000L, PendingSpentOutpoints.TTL_MS)
    }

    @Test
    fun `entries expire after the TTL`() {
        pending.record(address, listOf(a.outpoint))
        // Still held past the old 120 s: a transaction can wait longer than that in the mempool.
        clock += 121_000L
        assertEquals(listOf(b), pending.filter(address, listOf(a, b)))
        clock += 478_000L
        assertEquals(listOf(b), pending.filter(address, listOf(a, b)))
        clock += 2_000L
        assertEquals(listOf(a, b), pending.filter(address, listOf(a, b)))
        assertEquals(0, pending.pendingCount(address))
    }

    @Test
    fun `recorded per address, whatever the case`() {
        pending.record(address.uppercase(), listOf(a.outpoint))
        assertEquals(listOf(b), pending.filter(address, listOf(a, b)))
        // Another address is not affected.
        assertEquals(listOf(a, b), pending.filter("kaspa:qpother", listOf(a, b)))
    }

    @Test
    fun `a submit's coins are held while it is out, and given back if nothing went out`() {
        val added = pending.holdForSubmit(address, listOf(a.outpoint, c.outpoint))
        assertEquals(2, added.size)
        assertEquals(listOf(b), pending.filter(address, listOf(a, b, c)))
        pending.release(address, added)
        assertEquals(listOf(a, b, c), pending.filter(address, listOf(a, b, c)))
    }

    @Test
    fun `a hold never takes over a coin already recorded, so its release leaves that one held`() {
        pending.record(address, listOf(a.outpoint))
        val added = pending.holdForSubmit(address, listOf(a.outpoint, b.outpoint))
        assertEquals(1, added.size)
        pending.release(address, added)
        assertEquals(listOf(b), pending.filter(address, listOf(a, b)))
    }
}
