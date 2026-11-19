package com.kachat.app.services

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.kachat.app.util.WebMOpusWriter
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A voice note recorded the way iOS records one (iOS 48f0dce): 16-bit PCM at 48 kHz mono from
 * [AudioRecord], Opus at 6 kbps (CBR where the encoder offers it, 20 ms frames - the platform
 * Opus encoder's fixed frame) from [MediaCodec], wrapped by [WebMOpusWriter] - iOS's own WebM
 * layout, a second of packets per Cluster. ~1.06 KB a second, so a 1:1 note really reaches 25 s
 * inside the 31 KB cap. MediaRecorder's WebM costs ~1.6 KB a second and stopped at ~19 s.
 *
 * The recording ends at [maxDurationMs] of captured audio, or before the packet that would carry
 * the file past [maxBytes] ([limitReached] then turns true for the caller to stop); [finish]
 * drains the encoder and writes the file through the same trim rule iOS applies.
 */
internal class OpusWebMRecorder private constructor(
    private val audioRecord: AudioRecord,
    private val codec: MediaCodec,
    private val maxDurationMs: Long,
    private val maxBytes: Int?
) {
    private val trackUid = WebMOpusWriter.randomTrackUid()
    private val packets = ArrayList<ByteArray>()
    private var preSkip = WebMOpusWriter.DEFAULT_PRE_SKIP
    private var sizer = WebMOpusWriter.Sizer(WebMOpusWriter.opusHead(preSkip), trackUid)
    /** Written by the capture thread; read for the recording bar's clock ([capturedMs]). */
    @Volatile private var pcmSamples = 0L
    private var packetsFull = false
    private val bytesSoFar = AtomicInteger(sizer.totalBytes)

    @Volatile private var stopRequested = false
    @Volatile var limitReached = false
        private set
    @Volatile private var failure: Throwable? = null
    private val thread = Thread({ run() }, "OpusWebMRecorder")

    /** The file length the packets kept so far make. */
    val currentBytes: Int get() = bytesSoFar.get()

    /** Audio captured so far, in ms: the recorder's own clock (iOS b78fdb0 `currentTime`). */
    val capturedMs: Long get() = pcmSamples * 1000L / WebMOpusWriter.SAMPLE_RATE_HZ

    /** Stops capture where it got to and keeps what was captured, for [finish] or [cancel]
     *  (a call or another app took the microphone, iOS b78fdb0 `handleInterruption`). */
    fun stopCapture() {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
    }

    private fun start() {
        thread.start()
    }

    /** Stops capture, drains the encoder and writes the note to [file]; false when nothing usable
     *  was captured. */
    fun finish(file: File): Boolean {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
        failure?.let { Log.w(TAG, "Opus recording ended with an error", it) }
        if (thread.isAlive || packets.isEmpty()) return false
        val webm = WebMOpusWriter.encode(packets, pcmSamples, preSkip, maxBytes, trackUid)
        file.writeBytes(webm)
        return true
    }

    /** Stops and keeps nothing. */
    fun cancel() {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
    }

    private fun run() {
        val maxSamples = maxDurationMs * WebMOpusWriter.SAMPLE_RATE_HZ / 1000
        val frameBytes = WebMOpusWriter.FRAME_SAMPLES * 2
        val frame = ByteArray(frameBytes)
        val info = MediaCodec.BufferInfo()
        try {
            audioRecord.startRecording()
            while (!stopRequested && !packetsFull && pcmSamples < maxSamples) {
                var filled = 0
                while (filled < frameBytes && !stopRequested) {
                    val read = audioRecord.read(frame, filled, frameBytes - filled)
                    if (read < 0) throw IllegalStateException("AudioRecord.read failed ($read)")
                    filled += read
                }
                if (filled < frameBytes) break // stopped mid-frame: only whole frames are kept
                queueInput(frame, frameBytes, endOfStream = false)
                pcmSamples += WebMOpusWriter.FRAME_SAMPLES
                drain(info, untilEndOfStream = false)
            }
            if (pcmSamples >= maxSamples || packetsFull) limitReached = true
            runCatching { audioRecord.stop() }
            queueInput(frame, 0, endOfStream = true)
            drain(info, untilEndOfStream = true)
        } catch (t: Throwable) {
            failure = t
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { audioRecord.release() }
        }
    }

    private fun queueInput(source: ByteArray, length: Int, endOfStream: Boolean) {
        var index = -1
        val deadline = System.nanoTime() + INPUT_WAIT_NS
        while (index < 0) {
            index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                drain(MediaCodec.BufferInfo(), untilEndOfStream = false)
                if (System.nanoTime() > deadline) throw IllegalStateException("Opus encoder took no input")
            }
        }
        val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("No encoder input buffer")
        buffer.clear()
        buffer.put(source, 0, length)
        val presentationUs = pcmSamples * 1_000_000L / WebMOpusWriter.SAMPLE_RATE_HZ
        codec.queueInputBuffer(index, 0, length, presentationUs, if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
    }

    private fun drain(info: MediaCodec.BufferInfo, untilEndOfStream: Boolean) {
        val deadline = System.nanoTime() + DRAIN_WAIT_NS
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (untilEndOfStream) DEQUEUE_TIMEOUT_US else 0L)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!untilEndOfStream || System.nanoTime() > deadline) return
                continue
            }
            if (index < 0) continue // format / buffers changed
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                val bytes = ByteArray(info.size)
                buffer.position(info.offset)
                buffer.get(bytes, 0, info.size)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    onCodecConfig(bytes)
                } else {
                    onPacket(bytes)
                }
            }
            codec.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        }
    }

    /** The encoder's header (a bare OpusHead, or Android's "AOPUSHDR" wrapping of one): only its
     *  pre-skip is taken - the OpusHead written is iOS's. */
    private fun onCodecConfig(bytes: ByteArray) {
        val at = indexOf(bytes, OPUS_HEAD_MAGIC)
        if (at < 0 || at + 12 > bytes.size) return
        val skip = (bytes[at + 10].toInt() and 0xFF) or ((bytes[at + 11].toInt() and 0xFF) shl 8)
        if (skip != preSkip && packets.isEmpty()) {
            preSkip = skip
            sizer = WebMOpusWriter.Sizer(WebMOpusWriter.opusHead(preSkip), trackUid)
            bytesSoFar.set(sizer.totalBytes)
        }
    }

    private fun onPacket(packet: ByteArray) {
        if (packetsFull) return
        if (maxBytes != null && packets.isNotEmpty() && sizer.totalWith(packet.size) > maxBytes) {
            // The next packet would carry the note past its ceiling: it, and anything after, stays out.
            packetsFull = true
            limitReached = true
            return
        }
        packets.add(packet)
        sizer.add(packet.size)
        bytesSoFar.set(sizer.totalBytes)
    }

    companion object {
        private const val TAG = "OpusWebMRecorder"
        private const val MIME = MediaFormat.MIMETYPE_AUDIO_OPUS
        private const val PREFERRED_ENCODER = "c2.android.opus.encoder"
        private const val BIT_RATE_BPS = 6_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val INPUT_WAIT_NS = 2_000_000_000L
        private const val DRAIN_WAIT_NS = 2_000_000_000L
        private const val JOIN_TIMEOUT_MS = 4_000L
        internal val OPUS_HEAD_MAGIC = "OpusHead".toByteArray(Charsets.US_ASCII)

        /** The platform Opus encoder (API 29+), preferring AOSP's Codec2 one; null if none. */
        private val encoderName: String? by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@lazy null
            runCatching {
                val names = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) } }
                    .map { it.name }
                names.firstOrNull { it == PREFERRED_ENCODER } ?: names.firstOrNull()
            }.getOrNull()
        }

        val isAvailable: Boolean get() = encoderName != null

        /** The platform Opus encoder, configured and started: 48 kHz mono 16-bit PCM in, CBR at
         *  [bitRateBps] where offered. Throws when there is none or it won't configure. Shared with
         *  [OpusWebMPartsRecorder], which makes one per part (iOS 1b15521). */
        internal fun createEncoder(bitRateBps: Int): MediaCodec {
            val name = encoderName ?: throw IllegalStateException("No Opus encoder on this device")
            val codec = MediaCodec.createByCodecName(name)
            try {
                val format = MediaFormat.createAudioFormat(MIME, WebMOpusWriter.SAMPLE_RATE_HZ, 1).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRateBps)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, WebMOpusWriter.FRAME_SAMPLES * 2)
                    // iOS sets VBR off (opus_encoder_set_vbr(0)); ask for the same where offered.
                    val cbr = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                    val caps = runCatching { codec.codecInfo.getCapabilitiesForType(MIME).encoderCapabilities }.getOrNull()
                    if (caps?.isBitrateModeSupported(cbr) == true) setInteger(MediaFormat.KEY_BITRATE_MODE, cbr)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
            } catch (e: Exception) {
                runCatching { codec.release() }
                throw e
            }
            return codec
        }

        /** 48 kHz mono 16-bit capture from the mic, or a throw when it won't initialize. */
        @SuppressLint("MissingPermission") // the mic button asks for RECORD_AUDIO before recording
        internal fun createAudioRecord(): AudioRecord {
            val minBuffer = AudioRecord.getMinBufferSize(
                WebMOpusWriter.SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) throw IllegalStateException("48 kHz mono PCM capture unsupported ($minBuffer)")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WebMOpusWriter.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, WebMOpusWriter.FRAME_SAMPLES * 2 * 10)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { record.release() }
                throw IllegalStateException("AudioRecord did not initialize")
            }
            return record
        }

        /** Starts a recording, or returns null when this device can't record this way (the caller
         *  then falls back to MediaRecorder). */
        fun start(maxDurationMs: Long, maxBytes: Int?): OpusWebMRecorder? {
            if (encoderName == null) return null
            var codec: MediaCodec? = null
            var record: AudioRecord? = null
            return try {
                codec = createEncoder(BIT_RATE_BPS)
                record = createAudioRecord()
                OpusWebMRecorder(record, codec, maxDurationMs, maxBytes).also { it.start() }
            } catch (e: Exception) {
                Log.w(TAG, "Opus encoder unavailable for this recording; falling back to MediaRecorder", e)
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { record?.release() }
                null
            }
        }

        internal fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            outer@ for (i in 0..haystack.size - needle.size) {
                for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }
    }
}

/**
 * A room voice note, recorded as iOS records one (iOS 1b15521 `PublicChatAudioRecorder`,
 * `WebMOpusEncoder.encodeParts`): 48 kHz mono PCM is kept while recording, up to
 * [VoiceNotePolicy.Path.maxSeconds], and [finishParts] encodes it as the parts it goes as - at
 * most [VoiceNotePolicy.Path.partSeconds] each (one part when it is that short), every part a
 * complete WebM-Opus file at 16 kbps CBR in 60 ms frames ([VoiceNotePolicy]).
 *
 * The platform Opus encoder only makes 20 ms frames (padded to the CBR size, so code 3), so each
 * run of three is joined into one 60 ms Opus packet (RFC 6716 code 3, three frames of one config,
 * the padding dropped) - the packet iOS's libopus writes at 60 ms. A run whose frames can't be
 * joined (the encoder changed its mode or bandwidth inside it) keeps its three 20 ms packets, and
 * every SimpleBlock is timed by its own packet's length, so the file stays valid.
 *
 * Each part's encoder is also fed the codec's look-ahead (the OpusHead pre-skip, 312 samples) from
 * the next part, and its Info Duration says the part's real length: a reader that cuts each
 * part's pre-skip and trims it to that duration joins the parts with no gap
 * ([com.kachat.app.util.WebMOpusPartsDecoder]).
 */
internal class OpusWebMPartsRecorder private constructor(
    private val audioRecord: AudioRecord,
    private val policy: com.kachat.app.util.VoiceNotePolicy.Path,
) {
    private val chunks = ArrayList<ShortArray>()
    /** Samples captured so far; written by the capture thread, read after it is joined. */
    @Volatile private var sampleCount = 0
    private val maxSamples = (policy.maxSeconds * WebMOpusWriter.SAMPLE_RATE_HZ).toInt()

    @Volatile private var stopRequested = false
    @Volatile var limitReached = false
        private set
    @Volatile private var failure: Throwable? = null
    private val thread = Thread({ run() }, "OpusWebMPartsRecorder")

    private fun start() {
        thread.start()
    }

    /** Stops capture and keeps nothing. */
    fun cancel() {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
        chunks.clear()
    }

    /** Audio captured so far, in ms: the recorder's own clock, which the recording bar and its
     *  2:50 cut-off go by (iOS b78fdb0 `currentTime`). */
    val capturedMs: Long get() = sampleCount.toLong() * 1000L / WebMOpusWriter.SAMPLE_RATE_HZ

    /** Stops capture where it got to and keeps what was captured, for [finishParts] or [cancel]
     *  (a call or another app took the microphone, iOS b78fdb0 `handleInterruption`). */
    fun stopCapture() {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
    }

    /**
     * Stops capture and encodes the note into [directory], in order. Audio past
     * [com.kachat.app.util.VoicePartTag.MAX_PARTS] parts is dropped, and so is a last scrap under
     * 0.2 s after a full part (the tap that stopped the recording), which would otherwise cost a
     * whole transaction. Empty when nothing usable was captured. Slow (a few seconds for 2:50):
     * off the main thread.
     */
    fun finishParts(directory: File): List<File> {
        stopRequested = true
        thread.join(JOIN_TIMEOUT_MS)
        failure?.let { Log.w(TAG, "Voice note capture ended with an error", it) }
        if (thread.isAlive) return emptyList()
        val captured = sampleCount
        if (captured <= 0) return emptyList()
        val sampleRate = WebMOpusWriter.SAMPLE_RATE_HZ
        val partSamples = maxOf(1, (policy.partSeconds * sampleRate).toInt())
        var total = minOf(captured, partSamples * maxOf(1, com.kachat.app.util.VoicePartTag.MAX_PARTS))
        val remainder = total % partSamples
        if (total > partSamples && remainder > 0 && remainder < (0.2 * sampleRate).toInt()) {
            total -= remainder
        }
        val token = System.currentTimeMillis()
        val files = ArrayList<File>()
        try {
            var partStart = 0
            while (partStart < total) {
                val partEnd = minOf(partStart + partSamples, total)
                val data = encodeRange(partStart, partEnd, captured)
                val name = if (files.isEmpty()) "voice_$token.webm" else "voice_$token-${files.size + 1}.webm"
                val file = File(directory, name)
                file.writeBytes(data)
                files.add(file)
                partStart = partEnd
            }
        } catch (e: Exception) {
            files.forEach { it.delete() }
            throw e
        } finally {
            chunks.clear()
        }
        return files
    }

    private fun run() {
        val buffer = ShortArray(WebMOpusWriter.FRAME_SAMPLES)
        try {
            audioRecord.startRecording()
            while (!stopRequested && sampleCount < maxSamples) {
                val wanted = minOf(buffer.size, maxSamples - sampleCount)
                val read = audioRecord.read(buffer, 0, wanted)
                if (read < 0) throw IllegalStateException("AudioRecord.read failed ($read)")
                append(buffer, read)
            }
            if (sampleCount >= maxSamples) limitReached = true
            runCatching { audioRecord.stop() }
        } catch (t: Throwable) {
            failure = t
        } finally {
            runCatching { audioRecord.release() }
        }
    }

    private fun append(source: ShortArray, count: Int) {
        var copied = 0
        var at = sampleCount
        while (copied < count) {
            val chunkIndex = at / CHUNK_SAMPLES
            while (chunks.size <= chunkIndex) chunks.add(ShortArray(CHUNK_SAMPLES))
            val within = at % CHUNK_SAMPLES
            val n = minOf(count - copied, CHUNK_SAMPLES - within)
            System.arraycopy(source, copied, chunks[chunkIndex], within, n)
            copied += n
            at += n
        }
        sampleCount = at
    }

    /** Little-endian 16-bit PCM of samples [from, from + count) into [dest]; past [availableEnd]
     *  it is silence. */
    private fun fillFrame(dest: ByteArray, from: Int, count: Int, availableEnd: Int) {
        java.util.Arrays.fill(dest, 0)
        for (i in 0 until count) {
            val index = from + i
            if (index >= availableEnd) break
            val sample = chunks[index / CHUNK_SAMPLES][index % CHUNK_SAMPLES].toInt()
            dest[i * 2] = (sample and 0xFF).toByte()
            dest[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Samples [start, end) as one WebM-Opus file (iOS `encodeRange` with `coverLookahead`): the
     * encoder also gets the look-ahead past the range (the next samples, else silence), so the
     * decoded output, after pre-skip, holds the whole range; the Info Duration is the range's
     * length, and never a file over the part's byte ceiling.
     */
    private fun encodeRange(start: Int, end: Int, captured: Int): ByteArray {
        val codec = OpusWebMRecorder.createEncoder(com.kachat.app.util.VoiceNotePolicy.BITRATE)
        val frameSamples = WebMOpusWriter.FRAME_SAMPLES
        val frameBytes = frameSamples * 2
        val frame = ByteArray(frameBytes)
        val packets = ArrayList<ByteArray>()
        var preSkip = WebMOpusWriter.DEFAULT_PRE_SKIP
        val contentCount = end - start
        val sixtyMsSamples = WebMOpusWriter.SAMPLE_RATE_HZ * com.kachat.app.util.VoiceNotePolicy.FRAME_MS / 1000
        // Whole 60 ms frames covering the range and the look-ahead, as iOS's 60 ms encoder writes.
        fun target(): Int = (contentCount + preSkip + sixtyMsSamples - 1) / sixtyMsSamples * sixtyMsSamples
        val info = MediaCodec.BufferInfo()

        fun drain(untilEndOfStream: Boolean) {
            val deadline = System.nanoTime() + DRAIN_WAIT_NS
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (untilEndOfStream) DEQUEUE_TIMEOUT_US else 0L)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!untilEndOfStream || System.nanoTime() > deadline) return
                    continue
                }
                if (index < 0) continue
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val bytes = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(bytes, 0, info.size)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        val at = OpusWebMRecorder.indexOf(bytes, OpusWebMRecorder.OPUS_HEAD_MAGIC)
                        if (at >= 0 && at + 12 <= bytes.size && packets.isEmpty()) {
                            preSkip = (bytes[at + 10].toInt() and 0xFF) or ((bytes[at + 11].toInt() and 0xFF) shl 8)
                        }
                    } else {
                        packets.add(bytes)
                    }
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }

        fun queue(length: Int, presentationUs: Long, endOfStream: Boolean) {
            var index = -1
            val deadline = System.nanoTime() + INPUT_WAIT_NS
            while (index < 0) {
                index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (index < 0) {
                    drain(untilEndOfStream = false)
                    if (System.nanoTime() > deadline) throw IllegalStateException("Opus encoder took no input")
                }
            }
            val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("No encoder input buffer")
            buffer.clear()
            buffer.put(frame, 0, length)
            codec.queueInputBuffer(index, 0, length, presentationUs, if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
        }

        try {
            var fed = 0
            // The look-ahead comes from the recording itself where there is more of it.
            while (fed < target()) {
                val availableEnd = minOf(captured, end + preSkip)
                fillFrame(frame, start + fed, frameSamples, availableEnd)
                queue(frameBytes, fed.toLong() * 1_000_000L / WebMOpusWriter.SAMPLE_RATE_HZ, endOfStream = false)
                fed += frameSamples
                drain(untilEndOfStream = false)
            }
            queue(0, fed.toLong() * 1_000_000L / WebMOpusWriter.SAMPLE_RATE_HZ, endOfStream = true)
            drain(untilEndOfStream = true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
        if (packets.isEmpty()) throw IllegalStateException("The Opus encoder wrote nothing")

        // Only the frames that were fed: the encoder may flush one more at the end of the stream.
        val fedPackets = fed20(target())
        val twenty = if (packets.size > fedPackets) packets.subList(0, fedPackets) else packets
        val outPackets: MutableList<ByteArray> = ArrayList(joinIntoSixtyMs(twenty))
        // Each packet's own length: a run that couldn't be joined is three 20 ms packets.
        val outSamples: MutableList<Int> = outPackets.mapTo(ArrayList<Int>(outPackets.size)) { OpusPacket.samples(it) }

        val head = WebMOpusWriter.opusHead(preSkip)
        val trackUid = WebMOpusWriter.randomTrackUid()
        val sampleRate = WebMOpusWriter.SAMPLE_RATE_HZ.toDouble()
        val durationSeconds = contentCount.toDouble() / sampleRate
        // The Info duration is what the packets actually hold - if the byte ceiling below ever
        // trims the tail, the header no longer claims the full part.
        fun keptDuration(): Double = minOf(durationSeconds, outSamples.sumOf { it.toLong() }.toDouble() / sampleRate)
        var webm = WebMOpusWriter.buildTimed(outPackets, outSamples, head, sampleRate, keptDuration(), trackUid)
        // The hard guard: never a file over the ceiling, whatever the codec did. Only when it
        // really is over: the 60 ms packets keep a full part well inside it.
        while (webm.size > policy.maxBytes && outPackets.size > 1) {
            outPackets.removeAt(outPackets.size - 1)
            outSamples.removeAt(outSamples.size - 1)
            webm = WebMOpusWriter.buildTimed(outPackets, outSamples, head, sampleRate, keptDuration(), trackUid)
        }
        return webm
    }

    private fun fed20(targetSamples: Int): Int = targetSamples / WebMOpusWriter.FRAME_SAMPLES

    /**
     * Every three 20 ms packets as one 60 ms packet ([OpusPacket.joinThree]). A run that can't be
     * joined (a packet that isn't one 20 ms frame, or the encoder changed its mode or bandwidth
     * inside it), and any packets past the last whole run, stay 20 ms packets, their padding
     * dropped - only that run, not the whole part.
     */
    private fun joinIntoSixtyMs(packets: List<ByteArray>): List<ByteArray> {
        val out = ArrayList<ByteArray>(packets.size / 3 + 2)
        var i = 0
        while (i + 3 <= packets.size) {
            val joined = OpusPacket.joinThree(packets[i], packets[i + 1], packets[i + 2])
            if (joined != null) {
                out.add(joined)
            } else {
                for (k in i until i + 3) out.add(OpusPacket.withoutPadding(packets[k]))
            }
            i += 3
        }
        while (i < packets.size) out.add(OpusPacket.withoutPadding(packets[i++]))
        return out
    }

    companion object {
        private const val TAG = "OpusWebMPartsRecorder"
        private const val CHUNK_SAMPLES = WebMOpusWriter.SAMPLE_RATE_HZ
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val INPUT_WAIT_NS = 2_000_000_000L
        private const val DRAIN_WAIT_NS = 2_000_000_000L
        private const val JOIN_TIMEOUT_MS = 4_000L

        /** Starts capturing a room voice note, or null when this device can't (no platform Opus
         *  encoder, or the mic won't open): the caller then records one part with MediaRecorder. */
        fun start(policy: com.kachat.app.util.VoiceNotePolicy.Path): OpusWebMPartsRecorder? {
            if (!OpusWebMRecorder.isAvailable) return null
            return try {
                OpusWebMPartsRecorder(OpusWebMRecorder.createAudioRecord(), policy).also { it.start() }
            } catch (e: Exception) {
                Log.w(TAG, "Voice note capture unavailable; falling back to MediaRecorder", e)
                null
            }
        }
    }
}

/** Opus packet framing (RFC 6716 section 3), for joining the platform encoder's 20 ms packets into
 *  the 60 ms ones iOS writes (iOS 1b15521, `VoiceNotePolicy.frameMs`). */
internal object OpusPacket {
    /** Samples per frame at 48 kHz for a TOC byte's config. */
    private fun frameSamples(toc: Int): Int {
        val config = (toc shr 3) and 0x1F
        return when {
            config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3]
            config < 16 -> intArrayOf(480, 960)[config and 1]
            else -> intArrayOf(120, 240, 480, 960)[config and 3]
        }
    }

    /** The packet's length in 48 kHz samples, or 0 for one too short to read. */
    fun samples(packet: ByteArray): Int {
        if (packet.isEmpty()) return 0
        val toc = packet[0].toInt() and 0xFF
        val frames = when (toc and 3) {
            0 -> 1
            1, 2 -> 2
            else -> if (packet.size < 2) return 0 else packet[1].toInt() and 0x3F
        }
        return frames * frameSamples(toc)
    }

    /** RFC 6716 section 3.2.1: the most bytes one frame may hold. */
    private const val MAX_FRAME_BYTES = 1275

    /** RFC 6716 section 3.2.5: a packet holds at most 120 ms of audio. */
    private const val MAX_PACKET_SAMPLES = 5760

    /** A packet's TOC byte and its frames, any padding dropped. */
    class Frames(val toc: Int, val frames: List<ByteArray>)

    /**
     * A packet's frames, for any frame-count code (RFC 6716 section 3.2: code 0 one frame, 1 two
     * of one size, 2 two sized by a length, 3 up to 48 frames, CBR or VBR, with or without
     * padding - the platform encoder pads every CBR packet it makes shorter than the bitrate,
     * which makes it code 3). Null for a packet RFC 6716 calls malformed.
     */
    fun parse(packet: ByteArray): Frames? {
        if (packet.isEmpty()) return null
        val toc = packet[0].toInt() and 0xFF
        val n = packet.size
        val frames = ArrayList<ByteArray>(3)
        when (toc and 3) {
            0 -> {
                if (n - 1 > MAX_FRAME_BYTES) return null
                frames.add(packet.copyOfRange(1, n))
            }
            1 -> {
                val body = n - 1
                if (body % 2 != 0 || body / 2 > MAX_FRAME_BYTES) return null
                val half = body / 2
                frames.add(packet.copyOfRange(1, 1 + half))
                frames.add(packet.copyOfRange(1 + half, n))
            }
            2 -> {
                val (first, used) = readLength(packet, 1, n) ?: return null
                val start = 1 + used
                if (start + first > n) return null
                if (n - start - first > MAX_FRAME_BYTES) return null
                frames.add(packet.copyOfRange(start, start + first))
                frames.add(packet.copyOfRange(start + first, n))
            }
            else -> {
                if (n < 2) return null
                val countByte = packet[1].toInt() and 0xFF
                val vbr = (countByte and 0x80) != 0
                val padded = (countByte and 0x40) != 0
                val count = countByte and 0x3F
                if (count == 0 || count * frameSamples(toc) > MAX_PACKET_SAMPLES) return null
                var at = 2
                // The padding length: each 255 means 254 bytes and another length byte.
                var padding = 0
                if (padded) {
                    while (true) {
                        if (at >= n) return null
                        val value = packet[at++].toInt() and 0xFF
                        padding += if (value == 255) 254 else value
                        if (value != 255) break
                    }
                }
                // The padding bytes themselves sit at the end of the packet.
                val end = n - padding
                if (end < at) return null
                if (vbr) {
                    val lengths = IntArray(count)
                    var sum = 0
                    for (i in 0 until count - 1) {
                        val (length, used) = readLength(packet, at, end) ?: return null
                        at += used
                        lengths[i] = length
                        sum += length
                    }
                    val last = end - at - sum
                    if (last < 0 || last > MAX_FRAME_BYTES) return null
                    lengths[count - 1] = last
                    for (length in lengths) {
                        frames.add(packet.copyOfRange(at, at + length))
                        at += length
                    }
                } else {
                    val body = end - at
                    if (body % count != 0 || body / count > MAX_FRAME_BYTES) return null
                    val size = body / count
                    repeat(count) {
                        frames.add(packet.copyOfRange(at, at + size))
                        at += size
                    }
                }
            }
        }
        return Frames(toc, frames)
    }

    /** RFC 6716 section 3.2.1's one- or two-byte frame length at [at], before [end]: the length
     *  and the bytes it took, or null when it runs past [end]. */
    private fun readLength(packet: ByteArray, at: Int, end: Int): Pair<Int, Int>? {
        if (at >= end) return null
        val first = packet[at].toInt() and 0xFF
        if (first < 252) return first to 1
        if (at + 1 >= end) return null
        return (4 * (packet[at + 1].toInt() and 0xFF) + first) to 2
    }

    /**
     * Three 20 ms packets as one 60 ms code 3 packet (iOS's libopus at 60 ms writes one packet of
     * three frames): each must hold exactly one 20 ms frame, all of the same config and stereo
     * flag. The padding is dropped; CBR when the frames are one size, else VBR with the first two
     * lengths. Null when they can't be joined.
     */
    fun joinThree(a: ByteArray, b: ByteArray, c: ByteArray): ByteArray? {
        val parsed = listOf(a, b, c).map { parse(it) ?: return null }
        if (parsed.any { it.frames.size != 1 }) return null
        // The config (bits 7-3) and the stereo flag (bit 2): everything but the frame-count code.
        val toc = parsed[0].toc and 0xFC
        if (parsed.any { (it.toc and 0xFC) != toc }) return null
        if (frameSamples(toc) != 960) return null
        return pack(toc, parsed.map { it.frames[0] })
    }

    /** The same audio without its padding (a code 3 packet as code 0 when it holds one frame), or
     *  [packet] itself when it has none to drop or can't be read. */
    fun withoutPadding(packet: ByteArray): ByteArray {
        if (packet.isEmpty() || (packet[0].toInt() and 3) != 3) return packet
        val parsed = parse(packet) ?: return packet
        return pack(parsed.toc and 0xFC, parsed.frames)
    }

    /** [frames] of one config as one packet without padding: code 0 for one frame, else code 3
     *  (CBR when they are one size, else VBR). */
    private fun pack(tocConfig: Int, frames: List<ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream(2 + frames.sumOf { it.size } + 2 * frames.size)
        if (frames.size == 1) {
            out.write(tocConfig and 0xFC) // code 0
            out.write(frames[0])
            return out.toByteArray()
        }
        out.write((tocConfig and 0xFC) or 3)
        if (frames.all { it.size == frames[0].size }) {
            out.write(frames.size) // v = 0 (CBR), p = 0, M
        } else {
            out.write(0x80 or frames.size) // v = 1 (VBR), p = 0, M
            for (frame in frames.subList(0, frames.size - 1)) writeLength(out, frame.size)
        }
        frames.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun writeLength(out: java.io.ByteArrayOutputStream, length: Int) {
        if (length < 252) {
            out.write(length)
        } else {
            val first = 252 + (length and 3)
            out.write(first)
            out.write((length - first) / 4)
        }
    }
}
