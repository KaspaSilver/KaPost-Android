package com.kachat.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

/**
 * The KaPost link preview reads a shared post off its own transaction payload, because the K
 * indexer has no single-post lookup. These shapes come from live mainnet transactions, so a
 * change to the payload format that would blank those cards fails here first.
 *
 * Since iOS 369794e a payload only reads as a post when its BIP-340 signature verifies against
 * the key it names and its text carries the KaChat marker, so every payload here is signed for
 * real, the way [com.kachat.app.services.KaPostsService] signs a post (keyed-Blake2b personal
 * message, Schnorr), with a fixed test key.
 */
class KaPostsChainPayloadTest {

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun bytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    /** The compressed 02/03 key, as KaPostsService.requesterPubkey writes it. */
    private fun compressedPubkey(privateKey: ByteArray): String =
        hex(Secp256k1.G.multiply(BigInteger(1, privateKey)).normalize().getEncoded(true))

    private val privateKey = bytes("0c28fca386c7a227600b2fe50b7cae11ec86d3bf1fbe471be89827e19d72aa1d")
    private val otherPrivateKey = bytes("1e99423a4ed27608a15a2616a2b0e9e52ced330ac530edcc32c8ffc6a526aedd")

    private val pubkey = compressedPubkey(privateKey)
    private val postId = "1943b5083d67c98cac49c775e1809d8c1b317053a02b2489206fab7a5fca026f"

    private fun sign(signingString: String, key: ByteArray = privateKey): String =
        KaspaMessageSigner.sign(signingString, key, KaspaMessageSigner.SigningMode.KASPA_PERSONAL_MESSAGE)

    /** A KaPost message as the app writes it: marker first. */
    private fun marked(text: String): String = KaPostsProtocol.b64(KaPostsProtocol.KACHAT_MARKER + text)

    private fun signedPost(b64: String, mentions: String = "[]", root: String = "kchat:1:"): String =
        "${root}post:$pubkey:${sign(KaPostsProtocol.postSigningString(b64, mentions))}:$b64:$mentions"

    private fun signedReply(b64: String, mentions: String = "[]"): String =
        KaPostsProtocol.replyPayload(pubkey, sign(KaPostsProtocol.replySigningString(postId, b64, mentions)), postId, b64, mentions)

    private fun signedQuote(b64: String): String =
        KaPostsProtocol.quotePayload(pubkey, sign(KaPostsProtocol.quoteSigningString(postId, b64, pubkey)), postId, b64, pubkey)

    @Test
    fun `reads a post`() {
        val parsed = KaPostsProtocol.parseChainPayload(signedPost(marked("hello kaspa")))!!
        assertEquals("post", parsed.action)
        assertEquals(pubkey, parsed.authorPubkey)
        // A plain post points at nothing - field 2 there is the start of the message.
        assertNull(parsed.referencedId)
        // The exclusivity marker is stripped - it is invisible, but it would still lead the text.
        assertEquals("hello kaspa", parsed.message)
    }

    @Test
    fun `reads a reply, whose message sits one field later`() {
        val parsed = KaPostsProtocol.parseChainPayload(signedReply(marked("agreed")))!!
        assertEquals("reply", parsed.action)
        assertEquals("agreed", parsed.message)
        // The parent, so a reply opened off the chain lands inside its own thread.
        assertEquals(postId, parsed.referencedId)
    }

    @Test
    fun `reads a quote`() {
        val parsed = KaPostsProtocol.parseChainPayload(signedQuote(marked("worth reading")))!!
        assertEquals("quote", parsed.action)
        assertEquals("worth reading", parsed.message)
        // The quoted post, which is read in turn so the embedded card has its text.
        assertEquals(postId, parsed.referencedId)
    }

    @Test
    fun `a quote with no comment is a repost, not a parse failure`() {
        // A repost is the marker alone (KaPostsService.submitQuote with no text).
        val parsed = KaPostsProtocol.parseChainPayload(signedQuote(marked("")))!!
        assertEquals("", parsed.message)
    }

    @Test
    fun `reads the legacy k prefix, which older posts still carry`() {
        val payload = signedPost(marked("from before the migration"), root = "k:1:")
        assertEquals("from before the migration", KaPostsProtocol.parseChainPayload(payload)?.message)
    }

    @Test
    fun `a message containing colons survives, since base64 has none`() {
        val text = "ratio 3:1, see https://example.com:8443/x"
        assertEquals(text, KaPostsProtocol.parseChainPayload(signedPost(marked(text)))?.message)
    }

    @Test
    fun `a signed poll reads as its question`() {
        val question = marked("Which one?")
        val csv = KaPostsProtocol.pollOptionsCsv(listOf("A", "B"))
        val closesAt = 1_790_300_000_000L
        val signature = sign(KaPostsProtocol.pollSigningString(question, csv, closesAt, "[]"))
        val parsed = KaPostsProtocol.parseChainPayload(
            KaPostsProtocol.pollPayload(pubkey, signature, question, csv, closesAt, "[]")
        )!!
        assertEquals("poll", parsed.action)
        assertEquals("Which one?", parsed.message)
        assertNull(parsed.referencedId)
    }

    @Test
    fun `actions that carry no text are not posts`() {
        val signature = sign("anything")
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:vote:$pubkey:$signature:$postId:upvote:$pubkey"))
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:follow:$pubkey:$signature:follow:$pubkey"))
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:unquote:$pubkey:$signature:$postId"))
    }

    @Test
    fun `another app's payload on the same chain is ignored`() {
        assertNull(KaPostsProtocol.parseChainPayload("ciph_msg:1:comm:deadbeef"))
        assertNull(KaPostsProtocol.parseChainPayload(""))
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey"))
    }

    @Test
    fun `a message that is not base64 is not a post`() {
        val signature = sign(KaPostsProtocol.postSigningString("not base64!", "[]"))
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$signature:not base64!:[]"))
    }

    // MARK: - Forged and foreign payloads (iOS 369794e)

    @Test
    fun `a dummy signature is rejected`() {
        val b64 = marked("hello kaspa")
        val dummy = "9f5eab321e9c2355d27b6deb6d252b44549ef0d" + "a".repeat(89)
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$dummy:$b64:[]"))
    }

    @Test
    fun `a post signed by another key but naming this one is rejected`() {
        val b64 = marked("I am someone else")
        val forged = sign(KaPostsProtocol.postSigningString(b64, "[]"), key = otherPrivateKey)
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$forged:$b64:[]"))
        // The same signature is good under the key that really made it.
        val otherPubkey = compressedPubkey(otherPrivateKey)
        assertNotNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$otherPubkey:$forged:$b64:[]"))
    }

    @Test
    fun `a tampered message is rejected`() {
        val signature = sign(KaPostsProtocol.postSigningString(marked("send 1 KAS"), "[]"))
        val tampered = marked("send 1000 KAS")
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$signature:$tampered:[]"))
    }

    @Test
    fun `a reply moved to another parent is rejected`() {
        val b64 = marked("agreed")
        val signature = sign(KaPostsProtocol.replySigningString(postId, b64, "[]"))
        val otherParent = "a".repeat(64)
        assertNull(KaPostsProtocol.parseChainPayload(KaPostsProtocol.replyPayload(pubkey, signature, otherParent, b64, "[]")))
    }

    @Test
    fun `a flipped signature bit is rejected`() {
        val b64 = marked("hello kaspa")
        val signature = sign(KaPostsProtocol.postSigningString(b64, "[]"))
        val last = signature.last()
        val flipped = signature.dropLast(1) + (if (last == '0') '1' else '0')
        assertNotNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$signature:$b64:[]"))
        assertNull(KaPostsProtocol.parseChainPayload("kchat:1:post:$pubkey:$flipped:$b64:[]"))
    }

    @Test
    fun `a validly signed message without the KaChat marker is another app's post`() {
        val unmarked = KaPostsProtocol.b64("hello kaspa")
        assertNull(KaPostsProtocol.parseChainPayload(signedPost(unmarked)))
        assertNull(KaPostsProtocol.parseChainPayload(signedReply(unmarked)))
        assertNull(KaPostsProtocol.parseChainPayload(signedQuote(KaPostsProtocol.b64(""))))
    }
}
