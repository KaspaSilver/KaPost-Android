package com.kachat.app.util

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The parts of a long voice note (KaChat MESSAGING.md, "Long voice notes"), in the order given,
 * as ONE mono WAV file that `MediaPlayer` plays (iOS 1b15521 `WebMOpusDecoder.decodePartsToPCMFile`):
 * each part is decoded on its own (the platform Opus decoder), its pre-skip removed and cut to
 * its Info Duration (the encoder feeds each part a little of the next one to cover the codec's
 * look-ahead), so the joins are gapless and one player seeks across the whole note.
 *
 * Written at 24 kHz, as iOS decodes: the parts are narrowband-to-wideband SILK, so nothing
 * audible is lost, and 2:50 stays ~8 MB. A part that doesn't decode is left out; with none
 * decoding this returns null.
 *
 * The WebM reading is iOS's `parseWebM`: EBML containers nest at most 16 deep, SimpleBlock and
 * BlockGroup > Block both carry packets, a laced block is skipped, and a part never decodes past
 * [VoicePartTag.MAX_PART_SECONDS].
 */
object WebMOpusPartsDecoder {
    private const val OUTPUT_RATE = 24_000
    private const val OPUS_RATE = 48_000
    private const val TIMEOUT_US = 10_000L
    private const val MAX_CONTAINER_DEPTH = 16

    class DecodedParts(
        val file: File,
        val durationMs: Long,
        /** How many of the given parts decoded. */
        val decodedParts: Int,
    )

    private class ParsedWebM(val opusHead: ByteArray, val packets: List<ByteArray>, val durationMs: Double?)

    /**
     * The note's length without decoding it: each part's Info Duration (what [decodePartsToWav]
     * cuts it to), else what its packets hold. For the bubble's length before Play, as iOS's
     * preload shows the decoded total.
     */
    fun durationMs(parts: List<ByteArray>): Long {
        var total = 0.0
        for (part in parts.take(VoicePartTag.MAX_PARTS)) {
            val parsed = runCatching { parseWebM(part) }.getOrNull() ?: continue
            if (parsed.packets.isEmpty()) continue
            val packetMs = parsed.packets.sumOf { packetSamples(it) } * 1000.0 / OPUS_RATE
            val head = parseOpusHead(parsed.opusHead) ?: continue
            val playable = (packetMs - head.preSkip * 1000.0 / OPUS_RATE).coerceAtLeast(0.0)
            val wanted = parsed.durationMs?.takeIf { it > 0 && it < playable } ?: playable
            total += minOf(wanted, (VoicePartTag.MAX_PART_SECONDS + 1) * 1000)
        }
        return total.toLong()
    }

    fun decodePartsToWav(parts: List<ByteArray>, outFile: File): DecodedParts? {
        if (parts.isEmpty()) return null
        val maxPartFrames = (OUTPUT_RATE * (VoicePartTag.MAX_PART_SECONDS + 1)).toInt()
        var totalFrames = 0L
        var decodedParts = 0
        RandomAccessFile(outFile, "rw").use { out ->
            out.setLength(0)
            out.write(ByteArray(44)) // the header, written once the length is known
            for (part in parts.take(VoicePartTag.MAX_PARTS)) {
                val parsed = runCatching { parseWebM(part) }.getOrNull() ?: continue
                if (parsed.packets.isEmpty()) continue
                val head = parseOpusHead(parsed.opusHead) ?: continue
                val mono48 = runCatching { decode(parsed, head) }.getOrNull() ?: continue
                if (mono48.isEmpty()) continue
                // 48 kHz to 24 kHz, two samples to one.
                var frames = mono48.size / 2
                val mono = ShortArray(frames) { i -> ((mono48[2 * i].toInt() + mono48[2 * i + 1].toInt()) / 2).toShort() }
                frames = minOf(frames, maxPartFrames)
                parsed.durationMs?.let { durationMs ->
                    val wanted = Math.round(durationMs / 1000.0 * OUTPUT_RATE).toInt()
                    if (wanted in 1 until frames) frames = wanted
                }
                val bytes = ByteArray(frames * 2)
                for (i in 0 until frames) {
                    val sample = mono[i].toInt()
                    bytes[2 * i] = (sample and 0xFF).toByte()
                    bytes[2 * i + 1] = ((sample shr 8) and 0xFF).toByte()
                }
                out.write(bytes)
                totalFrames += frames
                decodedParts++
            }
            if (decodedParts == 0 || totalFrames == 0L) return@use
            out.seek(0)
            out.write(wavHeader(totalFrames))
        }
        if (decodedParts == 0 || totalFrames == 0L) {
            outFile.delete()
            return null
        }
        return DecodedParts(outFile, totalFrames * 1000 / OUTPUT_RATE, decodedParts)
    }

    /**
     * One part through the platform Opus decoder, as mono 48 kHz samples after its pre-skip. The
     * decoder is told the pre-skip (csd-1) and drops it; should it not, it is dropped here - the
     * packets' own length says which.
     */
    private fun decode(parsed: ParsedWebM, head: OpusHead): ShortArray {
        val channels = head.channels.coerceIn(1, 2)
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val maxSamples = OPUS_RATE * (VoicePartTag.MAX_PART_SECONDS + 1).toInt()
        val out = ShortArray(maxSamples)
        var count = 0
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, OPUS_RATE, channels).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(parsed.opusHead))
                setByteBuffer("csd-1", nanos(head.preSkip.toLong() * 1_000_000_000L / OPUS_RATE))
                setByteBuffer("csd-2", nanos(80_000_000L))
            }
            codec.configure(format, null, null, 0)
            codec.start()
            var outputChannels = channels
            var nextPacket = 0
            var inputDone = false
            var presentationUs = 0L
            val info = MediaCodec.BufferInfo()
            var idleRounds = 0
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        buffer.clear()
                        if (nextPacket < parsed.packets.size) {
                            val packet = parsed.packets[nextPacket++]
                            buffer.put(packet)
                            codec.queueInputBuffer(index, 0, packet.size, presentationUs, 0)
                            presentationUs += packetSamples(packet) * 1_000_000L / OPUS_RATE
                        } else {
                            codec.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputChannels = runCatching { codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(channels).coerceAtLeast(1)
                    continue
                }
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // A decoder that stops answering after the end of input: what came out stands.
                    if (inputDone && ++idleRounds > 200) break
                    continue
                }
                if (index < 0) continue
                idleRounds = 0
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val frames = shorts.remaining() / outputChannels
                    for (frame in 0 until frames) {
                        if (count >= maxSamples) break
                        var sum = 0
                        for (channel in 0 until outputChannels) sum += shorts.get(frame * outputChannels + channel).toInt()
                        out[count++] = (sum / outputChannels).toShort()
                    }
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
        // What the packets hold before any pre-skip: if the decoder kept the pre-skip, drop it here.
        val raw = parsed.packets.sumOf { packetSamples(it).toLong() }
        val droppedByDecoder = (raw - count).coerceAtLeast(0L)
        val stillToSkip = (head.preSkip - droppedByDecoder).coerceIn(0L, count.toLong()).toInt()
        return out.copyOfRange(stillToSkip, count)
    }

    private fun nanos(value: Long): ByteBuffer =
        ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).apply { flip() }

    private fun wavHeader(frames: Long): ByteArray {
        val dataBytes = frames * 2
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((36 + dataBytes).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(OUTPUT_RATE)
        buffer.putInt(OUTPUT_RATE * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes.toInt())
        return buffer.array()
    }

    /** A packet's length in 48 kHz samples (RFC 6716 TOC), 0 for one too short to read. */
    private fun packetSamples(packet: ByteArray): Int {
        if (packet.isEmpty()) return 0
        val toc = packet[0].toInt() and 0xFF
        val config = (toc shr 3) and 0x1F
        val frameSamples = when {
            config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3]
            config < 16 -> intArrayOf(480, 960)[config and 1]
            else -> intArrayOf(120, 240, 480, 960)[config and 3]
        }
        val frames = when (toc and 3) {
            0 -> 1
            1, 2 -> 2
            else -> if (packet.size < 2) return 0 else packet[1].toInt() and 0x3F
        }
        return frames * frameSamples
    }

    private class OpusHead(val channels: Int, val preSkip: Int)

    private fun parseOpusHead(data: ByteArray): OpusHead? {
        if (data.size < 19 || !data.copyOfRange(0, 8).contentEquals("OpusHead".toByteArray(Charsets.US_ASCII))) return null
        val channels = data[9].toInt() and 0xFF
        val preSkip = (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8)
        return OpusHead(maxOf(1, channels), preSkip)
    }

    // ---- iOS `parseWebM` --------------------------------------------------------------------

    private class InvalidWebM(message: String) : Exception(message)

    private val CONTAINER_IDS = setOf(
        0x18538067L, // Segment
        0x1549A966L, // Info
        0x1654AE6BL, // Tracks
        0xAEL,       // TrackEntry
        0x1F43B675L, // Cluster
        0xA0L,       // BlockGroup - other muxers wrap an Opus frame in BlockGroup > Block
    )

    private fun parseWebM(data: ByteArray): ParsedWebM {
        var opusHead: ByteArray? = null
        val packets = ArrayList<ByteArray>()
        var durationMs: Double? = null

        fun parseElements(from: Int, until: Int, depth: Int) {
            if (depth > MAX_CONTAINER_DEPTH) throw InvalidWebM("Nested too deeply.")
            var offset = from
            while (offset < until) {
                val (id, idLen, _) = readVInt(data, offset, forSize = false)
                val sizeOffset = offset + idLen
                if (sizeOffset >= until) break
                val sizeIsAllOnes = id == 0x18538067L && sizeOffset + 8 <= until &&
                    (0 until 8).all { data[sizeOffset + it] == 0xFF.toByte() }
                val (size, sizeLen, isUnknown) = if (sizeIsAllOnes) Triple(0L, 8, true) else readVInt(data, sizeOffset, forSize = true)
                val payloadStart = sizeOffset + sizeLen
                if (payloadStart > until) break
                val payloadEnd = if (isUnknown) {
                    until
                } else {
                    val end = payloadStart.toLong() + size
                    if (end < payloadStart || size < 0) break
                    minOf(end, until.toLong()).toInt()
                }
                when {
                    id == 0x63A2L -> opusHead = data.copyOfRange(payloadStart, payloadEnd)
                    id == 0x4489L -> durationMs = readFloat(data, payloadStart, payloadEnd)
                    // SimpleBlock (0xA3) or Block (0xA1, inside a BlockGroup) share the
                    // track-number/timecode/flags header layout.
                    id == 0xA3L || id == 0xA1L -> parseSimpleBlock(data.copyOfRange(payloadStart, payloadEnd))?.let { packets.add(it) }
                    id in CONTAINER_IDS -> parseElements(payloadStart, payloadEnd, depth + 1)
                }
                if (payloadEnd <= offset) break
                offset = payloadEnd
            }
        }

        parseElements(0, data.size, 0)
        val head = opusHead ?: throw InvalidWebM("Missing OpusHead.")
        return ParsedWebM(head, packets, durationMs)
    }

    /** An EBML float: 4 or 8 bytes, big-endian. Null for any other length or a non-finite value. */
    private fun readFloat(data: ByteArray, from: Int, until: Int): Double? {
        val length = until - from
        var bits = 0L
        for (i in from until until) bits = (bits shl 8) or (data[i].toLong() and 0xFF)
        val value = when (length) {
            8 -> java.lang.Double.longBitsToDouble(bits)
            4 -> java.lang.Float.intBitsToFloat(bits.toInt()).toDouble()
            else -> return null
        }
        return if (value.isFinite() && value > 0) value else null
    }

    private fun parseSimpleBlock(block: ByteArray): ByteArray? {
        var offset = 0
        // Any track number: these files hold one audio track, numbered as the muxer likes.
        val (_, trackLen, _) = readVInt(block, offset, forSize = true)
        offset += trackLen
        if (offset + 3 > block.size) throw InvalidWebM("Short SimpleBlock.")
        offset += 2 // relative timecode
        val flags = block[offset].toInt() and 0xFF
        offset += 1
        // A laced block is skipped rather than losing every other packet.
        if ((flags shr 1) and 0x03 != 0) return null
        return block.copyOfRange(offset, block.size)
    }

    private fun readVInt(data: ByteArray, offset: Int, forSize: Boolean): Triple<Long, Int, Boolean> {
        if (offset >= data.size) throw InvalidWebM("Unexpected end of data.")
        val first = data[offset].toInt() and 0xFF
        var mask = 0x80
        var length = 1
        while (length <= 8 && (first and mask) == 0) {
            mask = mask shr 1
            length++
        }
        if (length > 8) throw InvalidWebM("Invalid VINT length.")
        if (offset + length > data.size) throw InvalidWebM("Truncated VINT.")
        var value: Long = if (forSize) (first and (mask - 1)).toLong() else first.toLong()
        for (index in 1 until length) value = (value shl 8) or (data[offset + index].toLong() and 0xFF)
        if (forSize) {
            val maxValue = if (length == 8) (1L shl 56) - 1 else (1L shl (7 * length)) - 1
            return Triple(value, length, value == maxValue)
        }
        return Triple(value, length, false)
    }
}
