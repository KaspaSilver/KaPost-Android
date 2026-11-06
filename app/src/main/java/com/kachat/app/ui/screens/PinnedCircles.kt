package com.kachat.app.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import org.json.JSONArray

/** Rooms pinned up out of the list into the circles row above it, newest pin first, per account
 *  (iOS 9653c04 `roomPins`). */
object KaPostRoomPins {
    private const val PREFS = "kapost_room_pins"

    fun load(context: Context, walletAddress: String): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(walletAddress, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, walletAddress: String, pins: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(walletAddress, JSONArray(pins).toString())
            .apply()
    }
}

/** A row of [PinnedCircle]s above the room list: swipe sideways for all of them. */
@Composable
internal fun PinnedCirclesRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * One circle in the row above the room list (iOS 9653c04 `PinnedCircleButton`): the [avatar], the
 * whole name under it, a red unread count and, when [showsPin], a pin. Tap opens; hold opens its
 * half sheet. Dimmed and inert while the list is in Select mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PinnedCircle(
    name: String,
    unreadCount: Int,
    showsPin: Boolean,
    isSelecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(68.dp)
            .alpha(if (isSelecting) 0.55f else 1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isSelecting,
                onClick = onTap,
                onLongClick = {
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_MEDIUM)
                    onLongPress()
                },
            )
            .semantics { contentDescription = "$name. Hold for options" },
    ) {
        Box(modifier = Modifier.size(60.dp)) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) { avatar() }
            if (unreadCount > 0 && !isSelecting) {
                Surface(
                    color = colors.danger,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                ) {
                    Text(
                        if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (showsPin && !isSelecting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-2).dp, y = 2.dp)
                        .background(KaspaTeal, CircleShape)
                        .padding(4.dp),
                ) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        // The whole name: a long one carries on underneath (up to three lines) rather than being
        // cut off. Every name reserves the three lines, so every circle is the same height.
        Text(
            name,
            color = colors.textPrimary,
            fontSize = 11.sp,
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(68.dp),
        )
    }
}
