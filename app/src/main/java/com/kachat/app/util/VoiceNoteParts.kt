package com.kachat.app.util

import com.kachat.app.models.BroadcastMessageEntity

/**
 * The media-file JSON a voice note goes on chain as, written by hand so its bytes are iOS's
 * (iOS 1b15521 `MediaFileEnvelope.json`): `type`, `name`, `size`, `mimeType`, then `voicePart` (one
 * part of a long voice note) where KaChat writes it, so head-only sniffs ([VoicePartTag.sniff])
 * find it, then `content`. A single voice note still goes through [VoiceMessage.encode].
 */
object MediaFileEnvelope {
    fun json(name: String, size: Int, mimeType: String, dataUrlContent: String, voicePart: VoicePartTag? = null): String {
        val part = voicePart?.let { ",\"voicePart\":${it.json}" } ?: ""
        return "{\"type\":\"file\",\"name\":\"${escape(name)}\",\"size\":$size,\"mimeType\":\"${escape(mimeType)}\"$part,\"content\":\"${escape(dataUrlContent)}\"}"
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

/**
 * One part of a long on-chain voice note, KaChat's format (KaChat MESSAGING.md, "Long voice
 * notes (multi-part)", iOS 1b15521 `VoicePartTag`): `"voicePart":{"id":"<32 hex>","index":<1-based>,
 * "count":<2...12>}` in the media-file JSON, between `mimeType` and `content`. Every part is a
 * complete WebM-Opus file, so a client that ignores the tag shows the parts as separate voice
 * notes, in order.
 *
 * Read defensively: anything outside the rules in [of] is no tag, and the message is then a plain
 * single voice note.
 */
data class VoicePartTag(val id: String, val index: Int, val count: Int) {
    /** The JSON object as it goes on the wire, fixed key order. */
    val json: String get() = "{\"id\":\"$id\",\"index\":$index,\"count\":$count}"

    /** The part file's name, e.g. "voice-2of12.webm". */
    val fileName: String get() = "voice-${index}of$count.webm"

    companion object {
        /** The most parts one note may have - a strict bound for readers too. The longest room
         *  note ([VoiceNotePolicy.PUBLIC_CHAT]) needs exactly 12. */
        const val MAX_PARTS = 12

        /** The longest one part may run when read (a crafted part can't decode past this). Real
         *  parts are 14.5 s at most. */
        const val MAX_PART_SECONDS = 25.0

        /** A tag that passes the rules, else null: a 16-64 character hex id (lowercased), a count
         *  of 2...[MAX_PARTS] and an index inside it. */
        fun of(id: String, index: Int, count: Int): VoicePartTag? {
            val lowered = id.lowercase()
            if (lowered.length !in 16..64) return null
            if (!lowered.all { it in '0'..'9' || it in 'a'..'f' }) return null
            if (count !in 2..MAX_PARTS || index !in 1..count) return null
            return VoicePartTag(lowered, index, count)
        }

        /** A tag from a decoded JSON value: null unless it is an object with a string `id` and
         *  integer `index` / `count` that pass the rules. */
        fun fromJsonObject(value: Any?): VoicePartTag? {
            val obj = value as? org.json.JSONObject ?: return null
            val id = obj.opt("id") as? String ?: return null
            val index = integer(obj.opt("index")) ?: return null
            val count = integer(obj.opt("count")) ?: return null
            return of(id, index, count)
        }

        private fun integer(value: Any?): Int? {
            if (value !is Number) return null // a Boolean is not a Number here
            val double = value.toDouble()
            if (!double.isFinite() || double != Math.rint(double) || kotlin.math.abs(double) >= 1_000) return null
            return double.toInt()
        }

        /** A fresh note id: 16 random bytes as lowercase hex. */
        fun newGroupId(): String {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }

        /** The tag of a voice message's JSON, read from its head only ([MediaFileEnvelope.json]
         *  writes it before the file). Null for everything else, replies and photos included. */
        fun sniff(text: String): VoicePartTag? {
            if (text.firstOrNull() != '{') return null
            if (text.length <= 64 && text.toByteArray(Charsets.UTF_8).size <= 64) return null
            val head = text.take(2048)
            val mime = InlineMediaSniff.mimeType(head) ?: return null
            if (!mime.lowercase().startsWith("audio/")) return null
            val keyAt = head.indexOf("\"voicePart\"")
            if (keyAt < 0) return null
            val rest = head.substring(keyAt + "\"voicePart\"".length)
            val open = rest.indexOf('{')
            if (open < 0) return null
            if (!rest.substring(0, open).all { it == ':' || it.isWhitespace() }) return null
            val close = rest.indexOf('}', open)
            if (close < 0) return null
            val parsed = try {
                org.json.JSONObject(rest.substring(open, close + 1))
            } catch (e: Exception) {
                return null
            }
            return fromJsonObject(parsed)
        }
    }
}

/**
 * On-chain voice notes in public chat rooms (iOS 1b15521 `VoiceNotePolicy`): KaChat's 16 kbps CBR
 * Opus in 60 ms frames, parts of at most 14.5 s, and a note only as long as 1 KAS of fees allows.
 *
 * Part layout at 16 kbps ([VoiceNoteLayout]): 120-byte packets, 126 bytes each as a SimpleBlock,
 * 10 bytes per Cluster of 16, 155 of headers. A part of T seconds is
 * N = ceil((T x 48000 + 312 look-ahead) / 2880) packets and 155 + 126 N + 10 ceil(N / 16) bytes.
 *
 * A room message is plain text: `kchat:1:bcast:<room>:` and the media-file JSON, the file
 * base64'd in a data URL. A part's payload is 15 + room name (36 at most) + ~190 bytes of JSON
 * and tag + 4 ceil(R / 3) for R bytes of WebM. Its fee is 2 grams per transaction byte
 * (payload + 272 of input and output), 100 sompi a gram.
 *
 * - Part cap 31,000 bytes: N <= 243 (244 is 31,059), so T <= 14.57 s -> **14.5 s**, 242
 *   packets, 30,807 bytes. With a 36-character room that is a 41,318-byte payload, mass
 *   83,180, 0.0832 KAS.
 * - Note length: 12 full parts (174 s) are 0.998 KAS and a 13th would pass 1 KAS, so the note
 *   is **170 s** (2:50, rounded down to 5 s): 12 parts, 0.976 KAS. 175 s would be 13 parts,
 *   1.005 KAS.
 * - The same numbers as KaChat's 1:1 notes: base64 in a room costs what ECIES + base64 does there.
 */
object VoiceNotePolicy {
    const val BITRATE = 16_000
    const val FRAME_MS = 60

    data class Path(
        /** The longest one part runs; a note this short is one untagged message. */
        val partSeconds: Double,
        /** The longest note: its parts' fees add up to 1 KAS at most. */
        val maxSeconds: Double,
        /** Hard ceiling of one part's WebM file. */
        val maxBytes: Int,
    )

    val PUBLIC_CHAT = Path(partSeconds = 14.5, maxSeconds = 170.0, maxBytes = 31_000)
}

/** The WebM-Opus layout [com.kachat.app.services.OpusWebMPartsRecorder] writes, for working out a
 *  recording's part sizes before it is encoded (the live fee). CBR makes it exact to a few bytes
 *  (iOS 1b15521 `VoiceNoteLayout`). */
object VoiceNoteLayout {
    /** The size of each part a recording of [duration] seconds makes: parts of [partSeconds], at
     *  most [maxParts], each capped at [maxBytes] as the encoder caps it. */
    fun partSizes(
        duration: Double,
        partSeconds: Double,
        maxParts: Int = VoicePartTag.MAX_PARTS,
        bitrate: Int,
        frameMs: Int,
        maxBytes: Int,
        sampleRate: Double = 48_000.0,
    ): List<Int> {
        var remaining = minOf(maxOf(duration, frameMs.toDouble() / 1000), partSeconds * maxParts)
        val frameSamples = maxOf(1, sampleRate.toInt() * frameMs / 1000)
        val frameRate12 = maxOf(1, 12 * sampleRate.toInt() / frameSamples)
        val packetBytes = (12 * bitrate / 8 + frameRate12 / 2) / frameRate12
        val packetsPerCluster = maxOf(1, 1000 / maxOf(1, frameMs))
        val lookahead = sampleRate.toInt() * 312 / 48_000
        val sizes = ArrayList<Int>()
        while (remaining > 0.0005 && sizes.size < maxParts) {
            val part = minOf(remaining, partSeconds)
            val samples = (part * sampleRate).toInt() + lookahead
            val packets = (samples + frameSamples - 1) / frameSamples
            val clusters = (packets + packetsPerCluster - 1) / packetsPerCluster
            sizes.add(minOf(155 + packets * (packetBytes + 6) + clusters * 10, maxBytes))
            remaining -= part
        }
        return sizes
    }
}

/** Parts of one long voice note in a room: same room, sender, tag id and count (iOS 1b15521
 *  `PublicChatVoiceNote`). */
data class BroadcastVoiceNote(
    val key: String,
    val count: Int,
    /** The parts present, one per index, in index order. */
    val parts: List<BroadcastMessageEntity>,
    /** The earliest part in the room: where the one bubble sits, and whose time it shows. */
    val anchor: BroadcastMessageEntity,
) {
    val isComplete: Boolean get() = parts.size == count

    /** The note's status: any failed part fails it, then any part still sending keeps it
     *  sending, then any part not confirmed shows that. */
    val deliveryStatus: String
        get() {
            val statuses = parts.map { it.deliveryStatus }
            return when {
                "failed" in statuses -> "failed"
                "pending" in statuses -> "pending"
                "unconfirmed" in statuses -> "unconfirmed"
                else -> "sent"
            }
        }

    /** The parts to resend: failed, in index order. */
    val failedParts: List<BroadcastMessageEntity> get() = parts.filter { it.deliveryStatus == "failed" }

    companion object {
        fun key(channel: String, sender: String, tag: VoicePartTag): String =
            "${channel.lowercase()}|${sender.lowercase()}|${tag.id}|${tag.count}"
    }
}

/** iOS 1b15521 `PublicChatVoiceParts`. */
object BroadcastVoiceParts {
    data class Result(
        /** Notes by their anchor's id. */
        val notesByAnchorId: Map<String, BroadcastVoiceNote> = emptyMap(),
        /** Parts that aren't their note's anchor: not drawn. */
        val hiddenIds: Set<String> = emptySet(),
    )

    /** Groups the voice-note parts of a room's [messages], which must be in room order. A note
     *  with one part present is still a note (its bubble says it is receiving the rest). */
    fun group(messages: List<BroadcastMessageEntity>): Result {
        val tagged = LinkedHashMap<String, MutableList<Pair<BroadcastMessageEntity, VoicePartTag>>>()
        for (message in messages) {
            if (message.content.firstOrNull() != '{') continue
            val tag = VoicePartTag.sniff(message.content) ?: continue
            val key = BroadcastVoiceNote.key(message.channelName, message.senderAddress, tag)
            tagged.getOrPut(key) { ArrayList() }.add(message to tag)
        }
        if (tagged.isEmpty()) return Result()
        val notes = HashMap<String, BroadcastVoiceNote>()
        val hidden = HashSet<String>()
        for ((key, members) in tagged) {
            val anchor = members.firstOrNull()?.first ?: continue
            val byIndex = java.util.TreeMap<Int, BroadcastMessageEntity>()
            for ((message, tag) in members) {
                val existing = byIndex[tag.index]
                // Two messages for one index (a resend that landed twice): the delivered one.
                if (existing == null || priority(message.deliveryStatus) > priority(existing.deliveryStatus)) {
                    byIndex[tag.index] = message
                }
            }
            notes[anchor.id] = BroadcastVoiceNote(
                key = key,
                count = members[0].second.count,
                parts = byIndex.values.toList(),
                anchor = anchor,
            )
            for ((message, _) in members) if (message.id != anchor.id) hidden.add(message.id)
        }
        return Result(notes, hidden)
    }

    /** The note key of a message that is a part, else null. */
    fun noteKey(message: BroadcastMessageEntity): String? {
        if (message.content.firstOrNull() != '{') return null
        val tag = VoicePartTag.sniff(message.content) ?: return null
        return BroadcastVoiceNote.key(message.channelName, message.senderAddress, tag)
    }

    private fun priority(status: String): Int = when (status) {
        "sent" -> 3
        "unconfirmed" -> 2
        "pending" -> 1
        else -> 0
    }
}
