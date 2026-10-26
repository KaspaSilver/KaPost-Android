package com.kachat.app.util

import com.google.gson.Gson

/**
 * A reaction (tapback) to an earlier message — embedded as JSON directly in the same plaintext
 * content used for plain text (no separate wire type), matching how [MessageReply] already
 * carries its target reference. [targetTxId] is the reacted-to message's Kaspa transaction id
 * (the only identifier both parties/platforms agree on - a local row id isn't shared). [action]
 * is "add" or "remove": picking a new emoji on a message you've already reacted to replaces your
 * previous one, and tapping your currently-active reaction again removes it.
 */
data class MessageReactionContent(
    val type: String = "reaction",
    val targetTxId: String,
    val emoji: String,
    val action: String
)

object MessageReaction {
    private val gson = Gson()

    fun encode(targetTxId: String, emoji: String, action: String): String {
        return gson.toJson(MessageReactionContent(targetTxId = targetTxId, emoji = emoji, action = action))
    }

    /**
     * Parses [text] as a reaction if it looks like one, else returns null - same {-prefix +
     * size-guard + explicit type check as [MessageReply.parseOrNull], for the same reason (avoid
     * a wasted Gson parse attempt on every other JSON-shaped message envelope during scrolling).
     */
    fun parseOrNull(text: String?): MessageReactionContent? {
        if (text.isNullOrBlank() || text.length > 100_000 || text.trimStart().firstOrNull() != '{') return null
        return try {
            val parsed = gson.fromJson(text, MessageReactionContent::class.java) ?: return null
            if (parsed.type == "reaction") parsed else null
        } catch (e: Exception) {
            null
        }
    }
}
