package com.kachat.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

/**
 * A bot challenge from KaPlay, carried in a post (iOS 9e22075 `KaPlayBotChallenge`):
 *
 * `kachatchess://bot/<level>?name=<bot name>&moves=<n>&by=<address>&game=<game id>`
 *
 * `level` is 1 to 5 and is what makes it a challenge; a link without a valid one stays plain text.
 * Everything else is optional and attacker-controlled like any link in a post, so each value is
 * checked and a bad one dropped, and [url] is rebuilt from what passed: the card's button is the
 * one place KaPost opens this scheme, and it only ever opens this exact shape.
 */
data class KaPlayBotChallenge(
    val level: Int,
    val botName: String?,
    val moves: Int?,
    val url: String,
) {
    /** "Beat Coderofstuff (Level 3) in 14 moves", or "Challenge: beat Coderofstuff (Level 3)"
     *  without a move count. */
    val title: String
        get() {
            val bot = botName?.let { "$it (Level $level)" } ?: "the Level $level bot"
            val count = moves ?: return "Challenge: beat $bot"
            return "Beat $bot in $count ${if (count == 1) "move" else "moves"}"
        }

    /** The first valid challenge in a post's text, and the exact substring it was found as. */
    data class Match(val challenge: KaPlayBotChallenge, val matchedText: String)

    companion object {
        const val SCHEME = "kachatchess"
        private const val NAME_LIMIT = 40
        private val LINK = Regex("""kachatchess://bot/[^\s<>"']+""", RegexOption.IGNORE_CASE)
        /** Trailing prose punctuation is not part of the link ("... kachatchess://bot/3."). */
        private const val TRAILING_TRIM = ".,;:!?)]}'\""

        fun parse(source: Uri): KaPlayBotChallenge? {
            if (!source.scheme.equals(SCHEME, ignoreCase = true) || !source.host.equals("bot", ignoreCase = true)) return null
            // A hierarchical link only: getQueryParameter throws on anything else.
            if (!source.isHierarchical) return null
            val parts = source.pathSegments
            // Digits only: toIntOrNull alone also takes "+3".
            val level = parts.singleOrNull()?.takeIf { part -> part.isNotEmpty() && part.all { it in '0'..'9' } }?.toIntOrNull()
                ?: return null
            if (level !in 1..5) return null

            val botName = source.getQueryParameter("name")?.let(::cleanName)?.takeIf { it.isNotEmpty() }
            val moves = source.getQueryParameter("moves")
                ?.takeIf { raw -> raw.isNotEmpty() && raw.all { it in '0'..'9' } }
                ?.toIntOrNull()
                ?.takeIf { it in 1..9_999 }
            val by = source.getQueryParameter("by")?.takeIf { com.kachat.app.util.KaspaAddress.isValid(it) }
            val game = source.getQueryParameter("game")?.takeIf { raw ->
                raw.isNotEmpty() && raw.length <= 128 &&
                    raw.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
            }

            val rebuilt = Uri.Builder().scheme(SCHEME).authority("bot").appendPath(level.toString())
            botName?.let { rebuilt.appendQueryParameter("name", it) }
            moves?.let { rebuilt.appendQueryParameter("moves", it.toString()) }
            by?.let { rebuilt.appendQueryParameter("by", it) }
            game?.let { rebuilt.appendQueryParameter("game", it) }
            return KaPlayBotChallenge(level, botName, moves, rebuilt.build().toString())
        }

        /** No control or direction marks, which could make the card read as something else. */
        private fun cleanName(raw: String): String {
            val kept = buildString {
                var index = 0
                while (index < raw.length) {
                    val point = raw.codePointAt(index)
                    val type = Character.getType(point).toByte()
                    if (type != Character.CONTROL && type != Character.FORMAT) appendCodePoint(point)
                    index += Character.charCount(point)
                }
            }
            return kept.trim().take(NAME_LIMIT)
        }

        fun match(text: String): Match? {
            if (!text.contains("kachatchess://", ignoreCase = true)) return null
            LINK.findAll(text).forEach { found ->
                val candidate = found.value.trimEnd { it in TRAILING_TRIM }
                val challenge = runCatching { parse(Uri.parse(candidate)) }.getOrNull()
                if (challenge != null) return Match(challenge, candidate)
            }
            return null
        }
    }
}

/** A challenge opens in KaPlay. Without KaPlay installed, the store opens to get it. */
object KaPlayHandoff {
    /** KaPlay isn't listed yet, so this is a Play Store search for it; swap in its page
     *  (`details?id=kaplay.kachat.app`) once it is (iOS `appStoreURL`). */
    private const val STORE_URL = "market://search?q=KaPlay&c=apps"
    private const val STORE_WEB_URL = "https://play.google.com/store/search?q=KaPlay&c=apps"

    fun open(context: Context, challenge: KaPlayBotChallenge) {
        fun view(url: String): Boolean = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
        if (view(challenge.url)) return
        if (!view(STORE_URL)) view(STORE_WEB_URL)
    }
}

/** iOS `crown.fill`: Material has no crown. */
private val KaPlayCrown: ImageVector by lazy {
    ImageVector.Builder("KaPlayCrown", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 7.5f)
            lineTo(7.5f, 11.5f)
            lineTo(12f, 4.5f)
            lineTo(16.5f, 11.5f)
            lineTo(21f, 7.5f)
            lineTo(19f, 19.5f)
            lineTo(5f, 19.5f)
            close()
        }
    }.build()
}

/** A bot challenge link in a KaPost, drawn as a card instead of the link's long text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KaPostBotChallengeCard(challenge: KaPlayBotChallenge, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = com.kachat.app.util.rememberHaptics()
    var showMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KaspaTeal.copy(alpha = 0.08f))
            .border(1.dp, KaspaTeal.copy(alpha = 0.35f), shape)
            .combinedClickable(
                role = Role.Button,
                onLongClick = { showMenu = true },
                onClick = {
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                    KaPlayHandoff.open(context, challenge)
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = "Opens KaPlay to play this bot." }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(KaspaTeal.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KaPlayCrown, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("KAPLAY CHALLENGE", color = KaspaTeal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    challenge.title,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(KaspaTeal)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Challenge", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showMenu) {
        ActionSheetContainer(title = "KaPlay Challenge", subtitle = null, onDismiss = { showMenu = false }) {
            ActionSheetRow(
                icon = Icons.Filled.ContentCopy,
                title = "Copy Link",
                subtitle = "Copies the address to your clipboard.",
            ) {
                clipboard.setText(AnnotatedString(challenge.url))
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                showMenu = false
            }
        }
    }
}
