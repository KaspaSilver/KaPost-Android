package com.kachat.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import com.kachat.app.util.KaspaAddress

class NameServicesTest {
    /** A real mainnet address (unit tests run on mainnet): [NameResolution] keeps only an
     *  address valid on the running network (iOS 369794e). */
    private fun mainnetAddress(seed: Int): String =
        KaspaAddress.encode("kaspa", 0x00, ByteArray(32) { (it * 7 + seed).toByte() })

    private val kAddress = mainnetAddress(1)
    private val kaspaAddress = mainnetAddress(2)
    private val kachatAddress = mainnetAddress(3)

    @Test
    fun `dotk normalizes like its SDK`() {
        assertEquals("bob", NameNormalization.dotkCanonical("  Bob.K "))
        assertEquals("bob-1", NameNormalization.dotkCanonical("BOB-1"))
        assertEquals("béb", NameNormalization.dotkNormalize("béb")) // only A-Z is lowered
        assertNull(NameNormalization.dotkCanonical("béb"))
        assertNull(NameNormalization.dotkCanonical("-bob"))
        assertNull(NameNormalization.dotkCanonical(""))
        assertNull(NameNormalization.dotkCanonical("a".repeat(33)))
    }

    @Test
    fun `kaspa names normalizes like its SDK`() {
        assertEquals("bob", NameNormalization.kaspaNamesCanonical("Ｂob.kaspa")) // fullwidth B, NFKC
        assertEquals("bob", NameNormalization.kaspaNamesCanonical("BOB"))
        assertNull(NameNormalization.kaspaNamesCanonical("bob "))
        assertNull(NameNormalization.kaspaNamesCanonical("bob_1"))
        assertNull(NameNormalization.kaspaNamesCanonical("bob-"))
    }

    @Test
    fun `typed endings split longest first`() {
        assertEquals("bob" to NameServiceTLD.KASPA, NameServiceTLD.splitTypedName("bob.kaspa"))
        assertEquals("bob" to NameServiceTLD.KAS, NameServiceTLD.splitTypedName("bob.kas"))
        assertEquals("Bob" to NameServiceTLD.K, NameServiceTLD.splitTypedName("Bob.K")) // case kept, as on iOS
        assertEquals("bob" to null, NameServiceTLD.splitTypedName("bob"))
    }

    @Test
    fun `primary is the typed ending, and a bare name is a kachat name only`() {
        val results = listOf(
            NameResolution(NameServiceTLD.KAS, "bob.kas", null, false),
            NameResolution(NameServiceTLD.K, "bob.k", kAddress, false),
            NameResolution(NameServiceTLD.KASPA, "bob.kaspa", kaspaAddress, false),
        )
        // A bare "bob" means bob.kachat (Android ea932a0a = iOS 5a5122d): another service's bob
        // is never picked for it.
        assertNull(NameServicesClient.primary(results, "bob"))
        assertEquals(NameServiceTLD.KASPA, NameServicesClient.primary(results, "bob.kaspa")?.tld)
        assertNull(NameServicesClient.primary(results, "bob.kas"))
        assertTrue(NameServicesClient.looksLikeName("bob.k"))
        assertFalse(NameServicesClient.looksLikeName("kaspa:qqq"))

        val withKachat = results + NameResolution(NameServiceTLD.KACHAT, "bob.kachat", kachatAddress, false)
        assertEquals(NameServiceTLD.KACHAT, NameServicesClient.primary(withKachat, "bob")?.tld)
        assertEquals(NameServiceTLD.K, NameServicesClient.primary(withKachat, "bob.k")?.tld)
    }

    @Test
    fun `an answer that isn't an address of this network is no answer, and marked failed`() {
        assertTrue(KaspaAddress.isValidOnActiveNetwork(kAddress))
        val good = NameResolution(NameServiceTLD.K, "bob.k", kAddress, false)
        assertEquals(kAddress, good.address)
        assertFalse(good.failed)

        val testnet = KaspaAddress.encode("kaspatest", 0x00, ByteArray(32) { (it * 7 + 1).toByte() })
        for (bad in listOf("kaspa:k", "kaspa:kachat", testnet, "not an address")) {
            val resolution = NameResolution(NameServiceTLD.K, "bob.k", bad, false)
            assertNull(bad, resolution.address)
            assertTrue(bad, resolution.failed)
        }

        // Not registered (no address) stays a plain "not found", not a failure.
        val missing = NameResolution(NameServiceTLD.K, "bob.k", null, false)
        assertNull(missing.address)
        assertFalse(missing.failed)

        // A fake address is never picked as the answer.
        val results = listOf(NameResolution(NameServiceTLD.KACHAT, "bob.kachat", "kaspa:kachat", false))
        assertNull(NameServicesClient.primary(results, "bob"))
    }
}
