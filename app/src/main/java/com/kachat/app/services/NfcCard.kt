package com.kachat.app.services

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.kachat.app.models.WalletSourceFamily
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Client for a KaChat-NFC card (iOS 9653c04 `NFCCard`): an optional backup of a seed phrase the user
 * already has, saved and restored by tapping a Java Card. The card never generates or uses a key. It
 * holds bytes this file has already encrypted, behind a PIN. The commands and backup format are
 * public, in docs/PROTOCOL.md of https://github.com/KaspaSilver/KaChat-NFC, so a card works across
 * apps.
 */
object NfcCard {
    val APPLET_AID = byteArrayOf(0xF0.toByte(), 0x4B, 0x41, 0x43, 0x48, 0x41, 0x54, 0x4E, 0x46, 0x43, 0x01)

    const val PIN_LENGTH = 6
    const val TAP_PROMPT = "Hold your card flat against the back of your phone."

    /** How long a scan waits for a card, as iOS's reader session does. */
    private const val SCAN_TIMEOUT_MS = 60_000L

    /** Every build, the Play one too (iOS e5a9b11); a phone without NFC has no card screens. */
    fun isAvailable(context: Context): Boolean =
        NfcAdapter.getDefaultAdapter(context) != null

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * Runs [work] against one card, for the length of one tap. A tap lasts as long as [work] does,
     * so slow work such as key derivation belongs outside it. Android has no scan sheet of its own:
     * the caller shows one while this runs ([com.kachat.app.ui.screens.NfcScanSheet]).
     */
    suspend fun <T> run(activity: Activity, work: suspend (NfcCardConnection) -> T): T {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: throw NfcCardError.NfcUnavailable()
        if (!adapter.isEnabled) throw NfcCardError.NfcOff()
        try {
            val isoDep = withTimeoutOrNull(SCAN_TIMEOUT_MS) { awaitCard(adapter, activity) } ?: throw NfcCardError.TimedOut()
            return withContext(Dispatchers.IO) {
                try {
                    isoDep.connect()
                    // Long enough for the card's slower commands; a lost card fails sooner.
                    isoDep.timeout = 10_000
                    work(NfcCardConnection(isoDep))
                } catch (e: TagLostException) {
                    throw NfcCardError.CardRemoved()
                } catch (e: IOException) {
                    throw NfcCardError.CardRemoved()
                } catch (e: SecurityException) {
                    // "Tag is out of date": the card left and came back.
                    throw NfcCardError.CardRemoved()
                } finally {
                    runCatching { isoDep.close() }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    private suspend fun awaitCard(adapter: NfcAdapter, activity: Activity): IsoDep = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val answered = AtomicBoolean(false)
            adapter.enableReaderMode(
                activity,
                { tag ->
                    if (answered.compareAndSet(false, true)) {
                        val isoDep = IsoDep.get(tag)
                        // Not an ISO 7816 smart card at all, so the applet can't be on it.
                        if (isoDep == null) continuation.resumeWithException(NfcCardError.NotLoaded())
                        else continuation.resume(isoDep)
                    }
                },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 2_000) },
            )
        }
    }

    /** The card's words for a thing that isn't the user's fault to retry forever. */
    fun messageFor(error: Throwable): String? = when (error) {
        is CancellationException -> null
        is NfcCardError -> error.message
        else -> error.message ?: "Something went wrong."
    }
}

sealed class NfcCardError(message: String) : Exception(message) {
    class NfcUnavailable : NfcCardError("This phone can't read NFC cards.")
    class NfcOff : NfcCardError("NFC is off. Turn it on in your phone's settings and try again.")
    class TimedOut : NfcCardError("No card was found in time. Try again, and keep the card flat against the back of your phone.")
    class CardRemoved : NfcCardError("The card moved away too soon. Hold it still and try again. If a save was interrupted, your previous backup on the card is still there.")
    class NotLoaded : NfcCardError("This card doesn't have the KaChat-NFC software yet. Load it from the NFC Card page in Kaspa Quick Start.")
    class WrongPin(val triesRemaining: Int) : NfcCardError(
        if (triesRemaining == 1) "Wrong PIN. 1 try left before this card's PIN is blocked for good."
        else "Wrong PIN. $triesRemaining tries left."
    )
    class PinBlocked : NfcCardError("This card's PIN is blocked after too many wrong tries, so any backup on it can no longer be read. Use your 12 or 24 words instead.")
    class NoBackup : NfcCardError("There's no backup on this card.")
    class NoPin : NfcCardError("This card has no PIN yet, so it has no backup on it.")
    class CardChanged : NfcCardError("This isn't the card you scanned first. Start again with the card you want to use.")
    class BackupTooLarge : NfcCardError("This account's backup is too large for the card.")
    class WrongPassphrase : NfcCardError("That passphrase doesn't match this backup. It has to be exact, including capitals and spaces.")
    class NoPassphrase : NfcCardError("This account has no passphrase, so it can't be backed up to a card.")
    class UnreadableBackup : NfcCardError("This backup can't be read. It may have been made by a newer version of KaPost.")
    class SaveNotConfirmed : NfcCardError("The save couldn't be confirmed, so don't rely on this card. Try again.")
    class SeedUnavailable : NfcCardError("This account's 12 or 24 words couldn't be read on this device.")
    class CardFailed(detail: String) : NfcCardError("The card reported a problem ($detail). Try again.")
}

/** The applet's answer to SELECT. */
data class NfcCardStatus(
    val pinSet: Boolean,
    val hasBackup: Boolean,
    val pinBlocked: Boolean,
    val triesRemaining: Int,
    val maxBackup: Int,
)

/** One card, for the length of one tap. */
class NfcCardConnection internal constructor(private val isoDep: IsoDep) {
    private fun send(apdu: ByteArray): Pair<ByteArray, Int> {
        val reply = isoDep.transceive(apdu)
        if (reply.size < 2) throw NfcCardError.CardFailed("short reply")
        val status = (reply[reply.size - 2].toInt() and 0xFF shl 8) or (reply[reply.size - 1].toInt() and 0xFF)
        return reply.copyOf(reply.size - 2) to status
    }

    /** [responseLength] 256 is sent as Le 00; null sends none. */
    private fun apdu(cla: Int, ins: Int, p1p2: Int, data: ByteArray, responseLength: Int?): ByteArray {
        var out = byteArrayOf(cla.toByte(), ins.toByte(), (p1p2 shr 8).toByte(), p1p2.toByte())
        if (data.isNotEmpty()) out += byteArrayOf(data.size.toByte()) + data
        if (responseLength != null) out += byteArrayOf(responseLength.toByte())
        return out
    }

    private fun command(ins: Int, p1p2: Int = 0, data: ByteArray = ByteArray(0), responseLength: Int? = null): ByteArray {
        val (body, status) = send(apdu(0x80, ins, p1p2, data, responseLength))
        return when (status) {
            0x9000 -> body
            in 0x63C0..0x63CF -> throw NfcCardError.WrongPin(status and 0x000F)
            0x6983 -> throw NfcCardError.PinBlocked()
            0x6A88 -> throw NfcCardError.NoBackup()
            else -> throw NfcCardError.CardFailed("%04X".format(status))
        }
    }

    fun select(): NfcCardStatus {
        val (body, status) = send(apdu(0x00, 0xA4, 0x0400, NfcCard.APPLET_AID, 256))
        if (status == 0x6A82) throw NfcCardError.NotLoaded()
        if (status != 0x9000) throw NfcCardError.CardFailed("%04X".format(status))
        if (body.size != 7 || body[0].toInt() != 1) throw NfcCardError.UnreadableBackup()
        val flags = body[3].toInt()
        return NfcCardStatus(
            pinSet = flags and 0x01 != 0,
            hasBackup = flags and 0x02 != 0,
            pinBlocked = flags and 0x04 != 0,
            triesRemaining = body[4].toInt() and 0xFF,
            maxBackup = (body[5].toInt() and 0xFF shl 8) or (body[6].toInt() and 0xFF),
        )
    }

    fun setPin(pin: String) {
        command(0x21, data = pin.toByteArray(Charsets.UTF_8))
    }

    fun verifyPin(pin: String) {
        command(0x20, data = pin.toByteArray(Charsets.UTF_8))
    }

    /** The card checks the hash before the new copy replaces the old one, so an interrupted save
     *  leaves the previous backup in place. */
    fun writeBackup(backup: ByteArray) {
        command(0xD0, p1p2 = backup.size)
        val chunkSize = 200
        var offset = 0
        while (offset < backup.size) {
            val end = minOf(offset + chunkSize, backup.size)
            command(0xD1, p1p2 = offset, data = backup.copyOfRange(offset, end))
            offset = end
        }
        command(0xD2, data = MessageDigest.getInstance("SHA-256").digest(backup))
    }

    fun readBackup(): ByteArray {
        var backup = ByteArray(0)
        while (true) {
            val chunk = command(0xB0, p1p2 = backup.size, responseLength = 256)
            if (chunk.isEmpty()) return backup
            backup += chunk
        }
    }

    fun wipe() {
        command(0xE0)
    }
}

/**
 * What's encrypted on the card: everything an account needs beyond the words, because some accounts
 * can't be restored correctly from the words alone. The BIP39 passphrase is deliberately not here:
 * it's what protects the account if someone gets past the PIN. [chattingAddress] lets a restore catch
 * a mistyped passphrase, which would otherwise open a different, empty account. [family] is iOS's
 * name for it, so a card works across apps.
 */
data class NfcCardBackupPayload(
    val words: List<String>,
    val chattingAddress: String,
    val family: String,
    val chattingAddressIndex: Int,
    val alias: String,
    val version: Int = 2,
) {
    /** Null for a family this app doesn't know. */
    val walletFamily: WalletSourceFamily?
        get() = NfcCardBackupFormat.familyFromCard(family)
}

/**
 * The backup format in docs/PROTOCOL.md: magic, PBKDF2 iterations, salt, nonce, then AES-256-GCM. The
 * key comes from the card's PIN, so nothing extra to remember. A six-digit PIN can be guessed offline
 * by anyone who extracts the chip, which is why the passphrase stays off the card. Key derivation
 * takes a moment, so call these away from the main thread.
 */
object NfcCardBackupFormat {
    private val MAGIC = "KNB1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 600_000
    private const val MAXIMUM_ITERATIONS = 10_000_000L
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 16
    private const val HEADER_LENGTH = 4 + 4 + SALT_LENGTH + NONCE_LENGTH

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun familyToCard(family: WalletSourceFamily): String = when (family) {
        WalletSourceFamily.KASPA_STANDARD -> "kaspaStandard"
        WalletSourceFamily.KASPA_LEGACY_972 -> "kaspaLegacy972"
        WalletSourceFamily.CHAINGE -> "chainge"
        WalletSourceFamily.ONE_KEY -> "oneKey"
    }

    fun familyFromCard(raw: String): WalletSourceFamily? = when (raw) {
        "kaspaStandard" -> WalletSourceFamily.KASPA_STANDARD
        "kaspaLegacy972" -> WalletSourceFamily.KASPA_LEGACY_972
        "chainge" -> WalletSourceFamily.CHAINGE
        "oneKey" -> WalletSourceFamily.ONE_KEY
        else -> null
    }

    fun seal(payload: NfcCardBackupPayload, pin: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val iterations = byteArrayOf(
            (ITERATIONS ushr 24).toByte(), (ITERATIONS ushr 16).toByte(), (ITERATIONS ushr 8).toByte(), ITERATIONS.toByte(),
        )
        val header = MAGIC + iterations + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt, ITERATIONS), GCMParameterSpec(TAG_LENGTH * 8, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gson.toJson(payload).toByteArray(Charsets.UTF_8))
    }

    fun open(backup: ByteArray, pin: String): NfcCardBackupPayload {
        if (backup.size <= HEADER_LENGTH + TAG_LENGTH || !backup.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw NfcCardError.UnreadableBackup()
        }
        val count = (4 until 8).fold(0L) { total, i -> total shl 8 or (backup[i].toLong() and 0xFF) }
        if (count < ITERATIONS || count > MAXIMUM_ITERATIONS) throw NfcCardError.UnreadableBackup()
        val salt = backup.copyOfRange(8, 8 + SALT_LENGTH)
        val nonce = backup.copyOfRange(8 + SALT_LENGTH, HEADER_LENGTH)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(pin, salt, count.toInt()), GCMParameterSpec(TAG_LENGTH * 8, nonce))
            cipher.updateAAD(backup, 0, HEADER_LENGTH)
            cipher.doFinal(backup, HEADER_LENGTH, backup.size - HEADER_LENGTH)
        } catch (e: java.security.GeneralSecurityException) {
            throw NfcCardError.UnreadableBackup()
        }
        return try {
            val json = JsonParser.parseString(String(plaintext, Charsets.UTF_8)).asJsonObject
            if (json.get("version").asInt != 2) throw NfcCardError.UnreadableBackup()
            NfcCardBackupPayload(
                words = json.getAsJsonArray("words").map { it.asString },
                chattingAddress = json.get("chattingAddress").asString,
                family = json.get("family").asString,
                chattingAddressIndex = json.get("chattingAddressIndex").asInt,
                alias = json.get("alias").asString,
            )
        } catch (e: NfcCardError) {
            throw e
        } catch (e: Exception) {
            throw NfcCardError.UnreadableBackup()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        // NFKD, so the same password typed on another device or keyboard gives the same key.
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKD)
        val spec = PBEKeySpec(normalized.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
