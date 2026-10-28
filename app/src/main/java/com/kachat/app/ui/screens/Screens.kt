package com.kachat.app.ui.screens

import androidx.compose.material.icons.filled.IosShare
import com.kachat.app.util.UserFacingError
import com.kachat.app.util.redactedForLog
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.Gravity
import android.view.WindowManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.ui.theme.iosShadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.PhoneEnabled
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.FlowRow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kachat.app.R
import com.kachat.app.models.avatarFallbackText
import com.kachat.app.models.displayName
import com.kachat.app.models.Conversation
import com.kachat.app.models.MessageEntity
import com.kachat.app.models.ReactionEntity
import com.kachat.app.repository.ChatRepository
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.services.KnsService
import com.kachat.app.services.VoiceRecorderService
import com.kachat.app.services.UtxoEntry
import com.kachat.app.ui.theme.KaspaBlue
import com.kachat.app.ui.theme.KaspaSubtext
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.ChatTimeFormat
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaMass
import com.kachat.app.util.rememberCameraCaptureLauncher
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.ImageMessage
import com.kachat.app.util.ImagePrep
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.MessageReply
import com.kachat.app.util.TextLinkify
import com.kachat.app.util.MessageProtocol
import com.kachat.app.util.copyPrivateKeyWithAutoWipe
import com.kachat.app.util.VoiceMessage
import com.kachat.app.util.VoiceMessageContent
import com.kachat.app.viewmodels.ChatViewModel
import com.kachat.app.viewmodels.ConnectionStatus as ConnStatus
import com.kachat.app.viewmodels.ConnectionViewModel
import com.kachat.app.viewmodels.NodeInfo
import com.kachat.app.viewmodels.SettingsViewModel
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.kachat.app.util.showAddressCopiedToast
import androidx.compose.material.icons.outlined.InsertDriveFile





/** See the truncation check inside [MessageBubble]'s plain-text branch for why this exists. Also used by BroadcastScreens.kt's room bubble. */
const val MESSAGE_TEXT_TRUNCATION_THRESHOLD = 2_000
const val MESSAGE_TEXT_PREVIEW_LENGTH = 500




/**
 * Full text of a message too long to render inline (see [MESSAGE_TEXT_TRUNCATION_THRESHOLD]) - a
 * full-screen scrollable, selectable text view, matching iMessage's "tap to see more" behavior.
 * Shared with BroadcastScreens.kt's room bubble, not just private-chat messages.
 */
@Composable
fun FullMessageTextDialog(text: String, onDismiss: () -> Unit, onCopy: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetBarButton(stringResource(R.string.done), onClick = onDismiss)
                Text(stringResource(R.string.message), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                SheetBarIconButton(Icons.Default.ContentCopy, stringResource(R.string.copy), onClick = onCopy)
            }
            HorizontalDivider(color = LocalAppColors.current.textTertiary.copy(alpha = 0.5f))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Text(text, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}








/**
 * Voice notes that kept playing after their bubble left the composition.
 *
 * A bubble owns its `MediaPlayer`, and a `LazyColumn` disposes a bubble the moment it scrolls
 * out of view - which used to release the player and cut the voice note off mid-play as soon as
 * the reader scrolled. Now a bubble that is disposed while PLAYING hands its player here; the
 * note plays on, a bubble for the same message scrolling back in adopts the player (and shows it
 * playing), and leaving the thread is what actually stops everything - the thread screens call
 * [stopAllAfterChildrenDispose] from their own dispose (iOS LazyAudioBubble.stopAllPlayback).
 * Main-thread only.
 */
object VoicePlayback {
    private class Detached(val player: android.media.MediaPlayer, val tempFile: java.io.File?)

    private val detached = HashMap<String, Detached>()

    /** Takes back a player detached under [key], if one is still playing. */
    fun adopt(key: String): Pair<android.media.MediaPlayer, java.io.File?>? =
        detached.remove(key)?.let { it.player to it.tempFile }

    /** Keeps [player] alive after its bubble is gone; released on completion or [stopAll]. */
    fun detach(key: String, player: android.media.MediaPlayer, tempFile: java.io.File?) {
        detached.remove(key)?.let { release(it) }
        val entry = Detached(player, tempFile)
        detached[key] = entry
        player.setOnCompletionListener {
            if (detached[key] === entry) {
                detached.remove(key)
                release(entry)
            }
        }
    }

    fun stopAll() {
        val entries = detached.values.toList()
        detached.clear()
        entries.forEach { release(it) }
    }

    /**
     * For a thread screen's dispose: its bubbles detach their players during the same
     * composition apply, in no guaranteed order relative to the screen's own dispose, so the
     * stop is posted to run once that apply has finished.
     */
    fun stopAllAfterChildrenDispose() {
        android.os.Handler(android.os.Looper.getMainLooper()).post { stopAll() }
    }

    private fun release(entry: Detached) {
        runCatching { if (entry.player.isPlaying) entry.player.stop() }
        runCatching { entry.player.release() }
        entry.tempFile?.delete()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioBubble(voiceContent: VoiceMessageContent, isSent: Boolean, onLongPress: () -> Unit, onDoubleClick: () -> Unit = {}) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var isReady by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(voiceContent.content) {
        // Stable per message: the same voice note scrolling back in finds the player it left
        // playing (see VoicePlayback).
        val playbackKey = "${voiceContent.content.length}:${voiceContent.content.hashCode()}"
        var tempFile: java.io.File? = null
        val adopted = VoicePlayback.adopt(playbackKey)
        if (adopted != null) {
            val (player, file) = adopted
            tempFile = file
            player.setOnCompletionListener { isPlaying = false }
            durationMs = runCatching { player.duration }.getOrDefault(0)
            isReady = true
            isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            mediaPlayer = player
        } else {
            try {
                val bytes = android.util.Base64.decode(VoiceMessage.base64Payload(voiceContent), android.util.Base64.DEFAULT)
                val file = java.io.File(context.cacheDir, "voice_playback_${System.nanoTime()}.webm")
                file.writeBytes(bytes)
                tempFile = file
                val player = android.media.MediaPlayer()
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener {
                    durationMs = it.duration
                    isReady = true
                }
                player.setOnCompletionListener { isPlaying = false }
                player.prepareAsync()
                mediaPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("AudioBubble", "Could not prepare voice message for playback", e)
            }
        }
        onDispose {
            val player = mediaPlayer
            mediaPlayer = null
            // A note still playing when the bubble scrolls out keeps playing; anything else is
            // released here as before.
            if (player != null && runCatching { player.isPlaying }.getOrDefault(false)) {
                VoicePlayback.detach(playbackKey, player, tempFile)
            } else {
                player?.release()
                tempFile?.delete()
            }
        }
    }

    Surface(
        color = if (isSent) LocalAppColors.current.outgoingBubble else LocalAppColors.current.incomingBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress, onDoubleClick = onDoubleClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(min = 150.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = isReady,
                onClick = {
                    val player = mediaPlayer ?: return@IconButton
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (player.currentPosition >= player.duration) player.seekTo(0)
                        player.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isReady) KaspaTeal else LocalAppColors.current.textSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isReady) VoiceMessage.formatDuration(durationMs) else "...",
                color = LocalAppColors.current.textPrimary,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * A voice note that lives in a local file - one sent through Nextcloud, downloaded from its share.
 * The same play button, waveform glyph and duration as [AudioBubble], and the same sent/received
 * colours, so an on-chain and a Nextcloud voice note look and play alike. The file belongs to the
 * preview cache and is not deleted here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioFileBubble(file: java.io.File, isSent: Boolean, onLongPress: () -> Unit, onDoubleClick: () -> Unit = {}) {
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0) }
    var isReady by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(file.absolutePath) {
        // Stable per file: the same note scrolling back in finds the player it left playing.
        val playbackKey = "file:${file.absolutePath}"
        val adopted = VoicePlayback.adopt(playbackKey)
        if (adopted != null) {
            val (player, _) = adopted
            player.setOnCompletionListener { isPlaying = false }
            durationMs = runCatching { player.duration }.getOrDefault(0)
            isReady = true
            isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            mediaPlayer = player
        } else {
            try {
                val player = android.media.MediaPlayer()
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener {
                    durationMs = it.duration
                    isReady = true
                }
                player.setOnCompletionListener { isPlaying = false }
                player.prepareAsync()
                mediaPlayer = player
            } catch (e: Exception) {
                android.util.Log.e("AudioFileBubble", "Could not prepare voice note for playback", e)
            }
        }
        onDispose {
            val player = mediaPlayer
            mediaPlayer = null
            if (player != null && runCatching { player.isPlaying }.getOrDefault(false)) {
                VoicePlayback.detach(playbackKey, player, null)
            } else {
                player?.release()
            }
        }
    }

    Surface(
        color = if (isSent) LocalAppColors.current.outgoingBubble else LocalAppColors.current.incomingBubble,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress, onDoubleClick = onDoubleClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(min = 150.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = isReady,
                onClick = {
                    val player = mediaPlayer ?: return@IconButton
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (player.currentPosition >= player.duration) player.seekTo(0)
                        player.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isReady) KaspaTeal else LocalAppColors.current.textSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = LocalAppColors.current.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isReady) VoiceMessage.formatDuration(durationMs) else "...",
                color = LocalAppColors.current.textPrimary,
                fontSize = 13.sp
            )
        }
    }
}






/** "0:07" — the composer's live recording timer. */
fun formatRecordingElapsed(elapsedMs: Long): String = VoiceMessage.formatDuration(elapsedMs.toInt())













/** The teal name card, for any name service: the name, and an optional corner badge ("Primary"
 *  on KNS, "Settling" on a `.kaspa` name still inside its settling window) - iOS DomainNameCardView. */
@Composable
fun DomainNameCard(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    /** A small line in the bottom-right corner (Your Domains > .kachat: when it expires, iOS 72b7dc7). */
    footnote: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(KaspaTeal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (footnote != null) {
            Text(
                footnote,
                color = Color.Black.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
            )
        }
    }
}









/**
 * Send Kaspa (iOS `WithdrawKaspaView`): Chats > + > Send Kaspa, Donate, the Address Book, an
 * address's Send, paying a payment request - and, in [isCompoundMode], Compound UTXOs for one
 * address. Every send but Compound has a From step ([SendFromStep]): Your Address (the default)
 * or Choose an Address. Fees, Max, coin control and the send follow the
 * chosen address: the chatting address signs with the identity key
 * ([WalletViewModel.onSendClicked]), a spending address with its own key
 * ([WalletViewModel.withdrawFromSpendingAddress]); a payment request to a chatting address is a
 * chat payment from the chosen address. Never the external-QR-signer round trip [ColdSendFlow]
 * uses for watch-only keys.
 *
 * It is the step-by-step send (iOS KaPosts da3e953 `WithdrawKaspaView`; the pieces in
 * SendKaspaComponents.kt): To, From (skipped when it is fixed), Amount on the app's own keypad, a
 * Review that ends in Swipe up to Send ([SwipeUpToSendScreen], which shows the outcome), and the
 * fee's Edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressSendFlow(
    fromAddress: String,
    balanceSompi: Long,
    title: String,
    viewModel: WalletViewModel,
    onDone: () -> Unit,
    // Compound UTXOs only: the spending-chain index of [fromAddress], or null for the chatting
    // address. Every other send picks its address under From.
    spendingIndex: Int? = null,
    // Pre-fills the recipient with fromAddress itself (a self-send) and auto-fills Max, for the
    // "Compound UTXOs" entry point - merges every UTXO at this address into one. Locks the
    // recipient field instead of just pre-filling it, matching ColdSendFlow's identical
    // isCompoundMode behavior, since editing it away from fromAddress would defeat the point of
    // a compound send.
    isCompoundMode: Boolean = false,
    /**
     * Shown inside a sheet ([IosFullSheet] - every send entry, as iOS presents them): the bar's
     * leading control is "Cancel", as iOS's SpendingAddressWithdrawView and WithdrawKaspaView have
     * it, and the bars take no system insets of their own - the sheet already sits clear of them.
     */
    presentedAsSheet: Boolean = false,
    /** Pre-fills the recipient (Address Book > Send KAS, iOS `WithdrawKaspaView(prefillAddress:)`);
     *  it stays editable. */
    prefillAddress: String? = null,
    /** Someone's payment request (a tapped or pasted link, iOS c6851d6 `WithdrawKaspaView
     *  (paymentRequest:)`): its address is locked in, its amount and memo filled in. A request
     *  pasted into the recipient field does the same (not in Compound). */
    paymentRequest: com.kachat.app.models.PaymentRequest? = null,
    /** Opened from one address's history (its Send button, iOS 7a8102b `lockedSource`): the Kaspa
     *  leaves from that address and the From section offers no other. */
    lockedSource: SendSource? = null,
    /** Pays a chatting-address request as a chat payment (the memo encrypted to them inside it);
     *  null takes the app's own (the activity's), as every iOS send screen has ChatService. */
    chatViewModel: com.kachat.app.viewmodels.ChatViewModel? = null,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val fiatPriceInCurrency by portfolioViewModel.currentPriceUsd.collectAsState()
    val fiatCurrencyCode by portfolioViewModel.currency.collectAsState()
    var recipientInput by remember(fromAddress) { mutableStateOf(prefillAddress.orEmpty()) }
    var amountInput by remember(fromAddress) { mutableStateOf("") }
    val fiatAmountState = com.kachat.app.util.rememberKaspaFiatAmountState(resetKey = fromAddress, onKasTextChange = { amountInput = it })
    var showScanner by remember { mutableStateOf(false) }
    var manualUtxos by remember { mutableStateOf<List<UtxoEntry>?>(null) }
    var showCoinControl by remember { mutableStateOf(false) }
    var isEstimatingMax by remember { mutableStateOf(false) }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customExtraFeeSompi by remember { mutableStateOf<Long?>(null) }
    // Custom fee, edited in place on the fee row (SendFeeControls), as on iOS.
    var isEditingFee by remember { mutableStateOf(false) }
    var customFeeText by remember { mutableStateOf("") }
    /// The completed send: the review's swipe-up screen shows it.
    var sentTransaction by remember { mutableStateOf<SentTransaction?>(null) }
    /** Why the last send failed, under the swipe-up screen's "Failed". */
    var sendError by remember { mutableStateOf<String?>(null) }
    /** The step on screen; null until the first move (then [initialStep]). */
    var step by remember { mutableStateOf<SendFlowStep?>(null) }
    /** The fee being typed on the fee step, in KAS. */
    var feeDraft by remember { mutableStateOf("") }
    /** The From step's Choose an Address is on (its list shows), before or after a pick. */
    var choosingFrom by remember { mutableStateOf(false) }
    // This presentation's own sends (iOS's per-sheet isSending/sentTransaction): one closed
    // mid-send leaves its send running in the view model, and its outcome is never shown here
    // or on the next send screen.
    val sendId = remember { java.util.UUID.randomUUID().toString() }
    DisposableEffect(sendId) {
        viewModel.sends.open(sendId)
        onDispose { viewModel.sends.close(sendId) }
    }
    val isSending = sendId in viewModel.sends.inFlight.collectAsState().value
    val sendResult = viewModel.sends.results.collectAsState().value[sendId]
    val coroutineScope = rememberCoroutineScope()

    // The chat side of paying a chatting-address request: the caller's, else the activity's - the
    // same instance MainShell holds, so nothing new is built for it.
    val requestChatViewModel: com.kachat.app.viewmodels.ChatViewModel? = chatViewModel ?: run {
        val activity = generateSequence(LocalContext.current) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is androidx.activity.ComponentActivity } as? androidx.activity.ComponentActivity
        activity?.let { hiltViewModel<com.kachat.app.viewmodels.ChatViewModel>(it) }
    }
    /** The payment request this send pays, if any ([paymentRequest], or one pasted in). */
    var activeRequest by remember { mutableStateOf<com.kachat.app.models.PaymentRequest?>(null) }
    /** Fills in a payment request: the address (locked), the exact amount and the memo. The
     *  amount stays editable; whatever it says is what is sent, through the same exact parser. */
    fun applyRequest(request: com.kachat.app.models.PaymentRequest) {
        activeRequest = request
        recipientInput = request.address
        // the exact amount is what's sent; the field shows it in whichever unit it is in
        fiatAmountState.setExactKas(request.amountText, fiatPriceInCurrency)
    }

    // Where the Kaspa leaves from (the From section, iOS 6333dd3): the chatting address by
    // default. Compound UTXOs has no From - it merges [fromAddress] ([spendingIndex]) itself.
    var source by remember { mutableStateOf<SendSource>(lockedSource ?: SendSource.Chatting) }
    /** The From address's balance, loaded below for the From step and the amount's Available. */
    var sourceBalanceSompi by remember { mutableStateOf<Long?>(null) }
    val chattingAddress by viewModel.address.collectAsState()
    val sourceIndex = if (isCompoundMode) spendingIndex else source.spendingIndex
    val sourceAddress = if (isCompoundMode) fromAddress else (source.address(chattingAddress) ?: fromAddress)
    // A quantum-safe (KIP-22) From prices every input with its redeem script too (iOS
    // signatureScriptSize: kip22SignatureScriptSize).
    val sourceSignatureScriptSize = com.kachat.app.services.WalletManager.signatureScriptSize(sourceIndex)

    var isResolvingKns by remember { mutableStateOf(false) }
    var knsResolvedAddress by remember { mutableStateOf<String?>(null) }
    var knsResolvedDomain by remember { mutableStateOf<String?>(null) }
    var knsError by remember { mutableStateOf<String?>(null) }
    // Every service's answer for a typed name and the one in use: the rest go under "Other
    // domains" (iOS 6ac48a7).
    var nameResolutions by remember { mutableStateOf<List<com.kachat.app.services.NameResolution>>(emptyList()) }
    var selectedTld by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    // Use one service's answer (the priority one, or a pick from "Other domains").
    val selectResolution: (com.kachat.app.services.NameResolution) -> Unit = { resolution ->
        resolution.address?.let {
            knsResolvedAddress = it
            knsResolvedDomain = resolution.display
            selectedTld = resolution.tld
            knsError = null
        }
    }

    // The fee, as iOS prices it (SendKaspaComponents / SpendingAddressWithdrawView) and as the chat
    // Send KAS sheet already pays it: a base fee at the live quoted rate on the real inputs, plus
    // Fast / Priority / a custom fee as a flat extra on top (SendFeeModel). The send goes out with
    // no rate override (the engine prices the base at the quote) and the extra paid exactly - so a
    // multi-input send no longer scales the chosen fee by its input count.
    //
    // The quote is fetched once when the screen opens; until then the base is priced at the
    // network minimum, the floor of any quote.
    var liveFeeRateSompiPerGram by remember { mutableStateOf<Long?>(null) }
    val baseFeeRateSompiPerGram = liveFeeRateSompiPerGram ?: KaspaMass.MINIMUM_FEE_RATE_SOMPI_PER_GRAM
    // What automatic selection spends and pays for the amount typed (coin control off) - see the
    // preview LaunchedEffect below. Null while there is no amount, while it is being worked out,
    // or when the coins cannot cover it.
    var feePreview by remember { mutableStateOf<com.kachat.app.util.SendFeeModel.Preview?>(null) }
    var isPreviewingFee by remember { mutableStateOf(false) }
    // The amount Max last wrote, and the fee choice, From and coins it left room for: changing
    // any of them while the field still holds that amount works Max out again (see setMaxAmount).
    var maxFill by remember { mutableStateOf<SendMaxFill?>(null) }
    // The one exact parser for typed KAS (iOS 16b64bc): comma or dot, at most 8 decimals.
    val previewAmountSompi = KaspaUnit.sompiFromUserText(amountInput)
    // Base fee on the real inputs: coin control's exact set, else the automatic-selection preview,
    // else (no amount yet) one input.
    // The recipient's real script length when it is known (a P2SH or ECDSA address is a byte
    // longer than the standard one), so the base priced here is the base the send is priced at.
    val feeRecipient = (knsResolvedAddress ?: recipientInput).trim()
    val recipientScriptLen = remember(feeRecipient) {
        if (KaspaAddress.isValid(feeRecipient)) {
            runCatching { KaspaAddress.getScriptPublicKey(feeRecipient).length / 2 }
                .getOrDefault(com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN)
        } else {
            com.kachat.app.util.SendFeeModel.STANDARD_SCRIPT_LEN
        }
    }
    val manualInputCount = manualUtxos?.size?.takeIf { it > 0 }
    val defaultFeeSompi = when {
        manualInputCount != null -> com.kachat.app.util.SendFeeModel.baseFee(manualInputCount, baseFeeRateSompiPerGram, recipientScriptLen = recipientScriptLen, signatureScriptSize = sourceSignatureScriptSize)
        feePreview != null -> feePreview!!.baseFeeSompi
        else -> com.kachat.app.util.SendFeeModel.baseFee(1, baseFeeRateSompiPerGram, signatureScriptSize = sourceSignatureScriptSize)
    }
    // The preview settled its extra together with the inputs it needs; passing exactly that extra
    // makes the send pick those same inputs and pay the total shown.
    val extraFeeSompi = if (manualInputCount == null && feePreview != null) {
        feePreview!!.extraFeeSompi
    } else {
        com.kachat.app.util.SendFeeModel.extraFee(defaultFeeSompi, feeTier.multiplier, customExtraFeeSompi)
    }
    val effectiveFeeSompi = defaultFeeSompi + extraFeeSompi

    // Debounced KNS domain resolution - lets typing "name.kas" here resolve the same way Create
    // Chat's own address field already does. Skipped entirely in compound mode, where the
    // recipient is always the locked self-address, never user-typed.
    val resolveContext = LocalContext.current
    LaunchedEffect(recipientInput) {
        knsResolvedAddress = null
        knsResolvedDomain = null
        knsError = null
        nameResolutions = emptyList()
        selectedTld = null
        if (isCompoundMode) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        val trimmed = recipientInput.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("kaspa:", ignoreCase = true) ||
            trimmed.startsWith("kaspatest:", ignoreCase = true) || !com.kachat.app.services.NameServicesClient.looksLikeName(trimmed)
        ) {
            isResolvingKns = false
            return@LaunchedEffect
        }
        isResolvingKns = true
        kotlinx.coroutines.delay(500)
        // Every name service, in priority: the ending typed, else .kachat only (the others wait
        // under Other domains, iOS 5a5122d). The resolved line names which one answered; the
        // others are offered under "Other domains" (iOS 6ac48a7).
        val results = viewModel.resolveEverywhere(trimmed)
        nameResolutions = results
        isResolvingKns = false
        val primary = com.kachat.app.services.NameServicesClient.primary(results, trimmed)
        if (primary?.address != null) {
            selectResolution(primary)
        } else {
            knsError = com.kachat.app.services.NameServicesClient.notFoundMessage(resolveContext, trimmed, results)
        }
    }

    // Automatic selection's preview (coin control off): which coins this amount needs and what
    // they cost, so the fee shown is priced on the real inputs rather than an assumed one -
    // mirrors ColdSendFlow's previewSelection. Debounced; Send waits for it, so it never goes out
    // with an extra worked out for a different amount.
    LaunchedEffect(previewAmountSompi, baseFeeRateSompiPerGram, feeTier, customExtraFeeSompi, manualUtxos, sourceAddress, recipientScriptLen, sourceSignatureScriptSize) {
        feePreview = null
        if (manualUtxos.isNullOrEmpty() && previewAmountSompi != null && previewAmountSompi > 0) {
            isPreviewingFee = true
            kotlinx.coroutines.delay(400)
            feePreview = try {
                viewModel.previewAutomaticSelection(
                    address = sourceAddress,
                    amountSompi = previewAmountSompi,
                    feeRateSompiPerGram = baseFeeRateSompiPerGram,
                    feeMultiplier = feeTier.multiplier,
                    customExtraFeeSompi = customExtraFeeSompi,
                    recipientScriptLen = recipientScriptLen,
                    signatureScriptSize = sourceSignatureScriptSize,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SendFlow", "Fee preview failed for ${sourceAddress.redactedForLog()}", e)
                null
            }
        }
        isPreviewingFee = false
    }

    LaunchedEffect(Unit) {
        if (isCompoundMode) {
            recipientInput = fromAddress
            isEstimatingMax = true
        } else if (paymentRequest != null && activeRequest == null) {
            applyRequest(paymentRequest)
        }
        // The live quote, once (see baseFeeRateSompiPerGram). Falls back to the network minimum
        // on any failure.
        val quotedRate = viewModel.fetchQuotedFeeRateSompiPerGram()
        liveFeeRateSompiPerGram = quotedRate
        if (isCompoundMode) {
            try {
                // Consolidation is bounded by Kaspa's per-transaction mass cap, so pin the send to
                // one transaction's worth of UTXOs (largest-first, ≤MAX_INPUTS_PER_TRANSACTION) and
                // estimate Max over exactly those. This is why Max works even when the address has
                // more UTXOs than a single tx can hold - it consolidates one batch; repeat to reduce.
                val chunk = viewModel.maxConsolidatableChunk(fromAddress, quotedRate, feeTier.multiplier, customExtraFeeSompi, signatureScriptSize = sourceSignatureScriptSize)
                if (chunk != null) {
                    manualUtxos = chunk.second
                    fiatAmountState.setMaxKas(chunk.first / 100_000_000.0, fiatPriceInCurrency)
                    maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(chunk.first / 100_000_000.0), feeTier, customExtraFeeSompi, source, chunk.second)
                }
            } catch (e: Exception) {
                // Leave the field untouched on failure - same as the Max button itself.
            } finally {
                isEstimatingMax = false
            }
        }
    }

    // How the send ended, for the review's swipe-up screen: the transaction it shows, or the error
    // under "Failed" (KaPay's confirmation is the swipe-up's own full screen).
    LaunchedEffect(sendResult) {
        val result = sendResult ?: return@LaunchedEffect
        if (result.isSuccess) {
            result.getOrNull()?.let { txId ->
                sentTransaction = SentTransaction(
                    txId = txId,
                    amountSompi = KaspaUnit.sompiFromUserText(amountInput)?.takeIf { it > 0 },
                    // A compound is a self-send: naming the address it just came from reads as a
                    // mistake, so it just says how much moved. Else the domain when one was
                    // resolved, otherwise the address itself.
                    recipient = if (isCompoundMode) null else (knsResolvedDomain ?: recipientInput.trim()),
                )
            }
        } else {
            sendError = result.exceptionOrNull()?.message ?: context.getString(R.string.withdrawal_failed)
        }
        viewModel.sends.consume(sendId)
    }

    // The recipient scanner and coin control are sheets over the send, as iOS's
    // SpendingAddressWithdrawView and WithdrawKaspaView present QRScannerView and CoinControlView.
    if (showScanner) {
        QrScannerSheet(
            onScanned = { scanned -> recipientInput = KaspaAddress.fromScanned(scanned) },
            onDismiss = { showScanner = false }
        )
    }

    if (showCoinControl) {
        CoinControlSheet(
            fromAddress = sourceAddress,
            fetchUtxos = { addr -> viewModel.fetchUtxosForCoinControl(addr) },
            initialSelection = manualUtxos,
            onDone = { selection -> manualUtxos = selection },
            onDismiss = { showCoinControl = false }
        )
    }

    val amountSompi = KaspaUnit.sompiFromUserText(amountInput)
    // Only an address of the network the app runs on: the other network's is the same key on
    // another chain, refused with its own reason under the field (iOS ce20e87).
    val isValidAddress = remember(recipientInput) { KaspaAddress.isValidOnActiveNetwork(recipientInput.trim()) }
    val effectiveAddress = knsResolvedAddress ?: recipientInput
    val hasValidRecipient = if (knsResolvedAddress != null) true else (isValidAddress && !isResolvingKns)

    // Max: fee-aware, from the funds this send actually spends. Compound pins one mass-safe
    // transaction's worth of UTXOs (largest-first, <=cap) as the input set - see the compound
    // LaunchedEffect above.
    val setMaxAmount: () -> Unit = {
        coroutineScope.launch {
            isEstimatingMax = true
            try {
                // Room for the base fee and the Fast / Priority / custom extra, the extra derived
                // from Max's own base - never from a fee estimated for whatever amount was typed
                // before, which is how Max used to leave too little room for the extra.
                val maxTier = feeTier
                val maxCustomExtra = customExtraFeeSompi
                if (isCompoundMode) {
                    val chunk = viewModel.maxConsolidatableChunk(fromAddress, liveFeeRateSompiPerGram, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize)
                    if (chunk != null) {
                        manualUtxos = chunk.second
                        fiatAmountState.setMaxKas(chunk.first / 100_000_000.0, fiatPriceInCurrency)
                        maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(chunk.first / 100_000_000.0), maxTier, maxCustomExtra, source, chunk.second)
                    } else {
                        // Same silence as the plain Max had: nothing to consolidate, or nothing
                        // known yet.
                        IosToasts.error("Nothing to consolidate here yet.")
                    }
                } else {
                    // null = the wallet cannot answer yet (the REST client is created a moment
                    // after launch); 0 = it answered, and the fee eats the balance. Neither may
                    // write "0" into the field, which is what made this read as a dead button.
                    when (val maxSompi = viewModel.estimateMaxSendableAmount(sourceAddress, liveFeeRateSompiPerGram, manualUtxos, maxTier.multiplier, maxCustomExtra, signatureScriptSize = sourceSignatureScriptSize)) {
                        null -> IosToasts.error("Still connecting. Try Max again in a moment.")
                        0L -> IosToasts.error("Not enough here to cover the network fee.")
                        else -> {
                            fiatAmountState.setMaxKas(maxSompi / 100_000_000.0, fiatPriceInCurrency)
                            maxFill = SendMaxFill(com.kachat.app.util.formatKasAmount(maxSompi / 100_000_000.0), maxTier, maxCustomExtra, source, manualUtxos)
                        }
                    }
                }
            } catch (e: Exception) {
                // Said out loud, not swallowed. A silent catch here is indistinguishable from a
                // button that does nothing, which is exactly how this was reported - and it left
                // no trace in the log to diagnose it from either.
                Log.w("SendFlow", "Max estimate failed for ${sourceAddress.redactedForLog()}", e)
                IosToasts.error(UserFacingError.message(e, "Could not work out the maximum."))
            } finally {
                isEstimatingMax = false
            }
        }
    }
    // The fee, the From or the coins changed after Max filled the field (a speed picked, a custom
    // fee set, another address, coin control): Max is worked out again, or the send would need
    // more than the coins hold (KaPay `usedMax`). Only while the field still holds what Max wrote -
    // an amount typed since is left alone.
    LaunchedEffect(feeTier, customExtraFeeSompi, source, manualUtxos) {
        val filled = maxFill ?: return@LaunchedEffect
        if (filled.amount == amountInput &&
            (filled.tier != feeTier || filled.customExtra != customExtraFeeSompi || filled.source != source || filled.utxos != manualUtxos)
        ) {
            setMaxAmount()
        }
    }
    // Not while the fee preview is being worked out: the send passes the preview's extra.
    val canSend = !isSending && hasValidRecipient && (amountSompi ?: 0) > 0 && !isPreviewingFee
    val send: () -> Unit = {
        sendError = null
        amountSompi?.takeIf { canSend }?.let {
            val request = activeRequest
            val recipient = effectiveAddress.trim()
            // No rate override: the engine prices the base at the live quote, and the speed or
            // custom fee goes on top as the flat extra shown (SendFeeModel).
            if (request != null && !isCompoundMode && request.toChattingAddress && request.address.equals(recipient, ignoreCase = true) &&
                requestChatViewModel != null
            ) {
                // A chatting address (iOS c6851d6): a chat payment from the From address, the memo
                // encrypted to them inside it - it lands in your chat with them like any payment
                // from the chat (iOS 6333dd3 sendPayment(sourceSpendingIndex:)). With no contact to
                // pay as (one of your own accounts) it goes as a plain send from the same address.
                viewModel.sends.start(sendId)
                val sompi = it
                val extra = extraFeeSompi
                val utxos = manualUtxos
                val fromIndex = sourceIndex
                coroutineScope.launch {
                    if (requestChatViewModel.ensurePaymentRequestContact(recipient)) {
                        requestChatViewModel.sendPayment(
                            contactId = recipient,
                            amount = KaspaUnit.plain(sompi),
                            note = request.memo,
                            sourceSpendingIndex = fromIndex,
                            extraFeeSompi = extra,
                            manualUtxos = utxos,
                        ) { ok, error, txId ->
                            viewModel.sends.finish(
                                sendId,
                                if (ok && txId != null) Result.success(txId)
                                else Result.failure(IllegalStateException(error ?: context.getString(R.string.withdrawal_failed)))
                            )
                        }
                    } else if (fromIndex != null) {
                        viewModel.withdrawFromSpendingAddress(sendId, fromIndex, recipient, sompi, feeRateOverride = null, manualUtxos = utxos, extraFeeSompi = extra)
                    } else {
                        viewModel.onSendClicked(sendId, recipient, sompi, feeRateOverride = null, manualUtxos = utxos, extraFeeSompi = extra)
                    }
                }
            } else if (sourceIndex != null) {
                viewModel.withdrawFromSpendingAddress(sendId, sourceIndex, recipient, it, feeRateOverride = null, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi)
            } else {
                viewModel.onSendClicked(sendId, recipient, it, feeRateOverride = null, manualUtxos = manualUtxos, extraFeeSompi = extraFeeSompi)
            }
        }
    }

    // The steps of a send (iOS KaPosts da3e953 `WithdrawKaspaView`): who it goes to, where from,
    // how much (on the app's own keypad, so no keyboard stays up), and a review where only the fee
    // and coin control can change before swiping up to send. Compound UTXOs opens on the review (its
    // recipient and amount are set), a payment request or a pre-filled recipient on From (or the
    // amount, when From is fixed).
    /** The step after To: From, unless the send's From is fixed (an address's own Send). */
    val afterRecipient = if (lockedSource == null) SendFlowStep.FROM else SendFlowStep.AMOUNT
    val initialStep = when {
        isCompoundMode -> SendFlowStep.REVIEW
        paymentRequest != null || prefillAddress != null -> afterRecipient
        else -> SendFlowStep.RECIPIENT
    }
    val currentStep = step ?: initialStep
    val insets = if (presentedAsSheet) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    /** Where Back goes from a step; null for the first one. A payment request opened from a link
     *  has no recipient step (its address is fixed). */
    fun backStep(from: SendFlowStep): SendFlowStep? = when (from) {
        SendFlowStep.RECIPIENT -> null
        SendFlowStep.FROM -> if (paymentRequest != null) null else SendFlowStep.RECIPIENT
        SendFlowStep.AMOUNT -> when {
            lockedSource == null -> SendFlowStep.FROM
            isCompoundMode || paymentRequest != null -> null
            else -> SendFlowStep.RECIPIENT
        }
        SendFlowStep.REVIEW -> if (isCompoundMode) null else SendFlowStep.AMOUNT
        SendFlowStep.FEE -> SendFlowStep.REVIEW
    }
    fun go(to: SendFlowStep) {
        focusManager.clearFocus()
        if (to == SendFlowStep.RECIPIENT && activeRequest != null && paymentRequest == null) {
            // back from a pasted request: start the recipient over
            activeRequest = null
            recipientInput = ""
            fiatAmountState.reset()
            maxFill = null
        }
        step = to
    }
    val screenTitle = when {
        isCompoundMode -> stringResource(R.string.compound_utxos)
        activeRequest != null -> stringResource(R.string.pr_payment_request)
        else -> title
    }
    val availableSompi = if (isCompoundMode) balanceSompi else sourceBalanceSompi
    /** The amount is more than the From address holds. */
    val exceedsBalance = amountSompi != null && availableSompi != null && amountSompi > availableSompi
    val canContinueFromAmount = hasValidRecipient && (amountSompi ?: 0) > 0 && !exceedsBalance && !isEstimatingMax
    /** The recipient's name: the domain it resolved from. */
    val recipientName = knsResolvedDomain
    val sourceTitle = if (isCompoundMode) stringResource(R.string.send_this_address) else sendSourceTitle(source)
    // the From address's balance: the amount's Available and its check
    LaunchedEffect(source, chattingAddress) {
        if (isCompoundMode) return@LaunchedEffect
        sourceBalanceSompi = null
        val address = source.address(chattingAddress) ?: return@LaunchedEffect
        sourceBalanceSompi = viewModel.addressBalanceSompi(address)
    }

    SendStepTransition(currentStep, Modifier.fillMaxSize()) { shown ->
        when (shown) {
            SendFlowStep.RECIPIENT -> SendStepScreen(
                title = screenTitle,
                onCancel = onDone,
                canContinue = hasValidRecipient,
                onContinue = { go(afterRecipient) },
                insets = insets,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SendStepQuestion(stringResource(R.string.send_who_to))
                    SendRecipientCard(
                        input = recipientInput,
                        onInputChange = { input ->
                            // A payment request pasted in fills in everything it carries (iOS
                            // c6851d6) and moves on to its amount, as KaPay's WithdrawKaspaView.
                            val pasted = (KaChatLink.findFirst(input.trim())?.ref as? KaChatLinkRef.PaymentRequest)?.request
                            if (pasted != null) {
                                applyRequest(pasted)
                                go(afterRecipient)
                            } else {
                                recipientInput = input
                            }
                        },
                        isResolving = isResolvingKns,
                        resolvedAddress = knsResolvedAddress,
                        resolvedName = knsResolvedDomain,
                        nameResolutions = nameResolutions,
                        selectedTld = selectedTld,
                        onSelectResolution = selectResolution,
                        lookupError = knsError,
                        isValidAddress = isValidAddress,
                        onScan = { showScanner = true },
                        focusOnAppear = true,
                    )
                }
            }

            SendFlowStep.FROM -> SendFromStep(
                title = screenTitle,
                source = source,
                onSourceChange = { picked ->
                    if (picked != source) {
                        source = picked
                        // coins picked on one address can't pay from another
                        manualUtxos = null
                    }
                },
                choosingFrom = choosingFrom,
                onChoosingFromChange = { choosingFrom = it },
                availableSompi = sourceBalanceSompi,
                walletViewModel = viewModel,
                onBack = backStep(SendFlowStep.FROM)?.let { back -> { go(back) } },
                onCancel = onDone,
                onContinue = { go(SendFlowStep.AMOUNT) },
                insets = insets,
            )

            SendFlowStep.AMOUNT -> SendStepScreen(
                title = screenTitle,
                onBack = backStep(SendFlowStep.AMOUNT)?.let { back -> { go(back) } },
                onCancel = onDone,
                continueTitle = stringResource(R.string.send_review),
                canContinue = canContinueFromAmount,
                onContinue = { go(SendFlowStep.REVIEW) },
                insets = insets,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SendStepChip(stringResource(R.string.to), recipientName ?: effectiveAddress.trim(), isAddress = recipientName == null)
                    SendStepChip(stringResource(R.string.from), sourceTitle)
                }
                val request = activeRequest
                if (request != null) {
                    // A request's amount is fixed: no keypad, the request itself instead.
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        SendReviewAmount(amountSompi, fiatPriceInCurrency, fiatCurrencyCode)
                        PaymentRequestSummary(request = request)
                        if (exceedsBalance) {
                            Text(
                                stringResource(R.string.send_exceeds_source),
                                color = LocalAppColors.current.danger,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    val available = availableSompi
                    KaspaKeypadAmountEntry(
                        fiatAmountState = fiatAmountState,
                        priceInCurrency = fiatPriceInCurrency,
                        currencyCode = fiatCurrencyCode,
                        isEstimatingMax = isEstimatingMax,
                        maxEnabled = hasValidRecipient,
                        onMax = setMaxAmount,
                        footnote = when {
                            exceedsBalance -> stringResource(R.string.send_exceeds_source)
                            available != null -> KaspaUnit.label(stringResource(R.string.send_available_kas, KaspaUnit.plain(available)))
                            else -> null
                        },
                        footnoteIsError = exceedsBalance,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SendFlowStep.REVIEW -> SwipeUpToSendScreen(
                screenTitle = if (isCompoundMode) stringResource(R.string.compound_utxos) else stringResource(R.string.send_review),
                onCancel = onDone,
                onBack = backStep(SendFlowStep.REVIEW)?.let { back -> { go(back) } },
                barTitle = stringResource(if (isCompoundMode) R.string.swipe_up_to_consolidate else R.string.swipe_up_to_send),
                isBusy = isSending,
                isEnabled = canSend,
                amountSompi = amountSompi,
                recipient = if (isCompoundMode) null else (knsResolvedDomain ?: effectiveAddress.trim()).takeIf { it.isNotEmpty() },
                sent = sentTransaction,
                failureMessage = sendError,
                explorerUrl = { kaspaExplorer.txUrl(it) },
                onDone = {
                    sentTransaction = null
                    onDone()
                },
                onSwipe = send,
                insets = insets,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SendReviewAmount(
                        amountSompi = amountSompi,
                        priceInCurrency = fiatPriceInCurrency,
                        currencyCode = fiatCurrencyCode,
                        label = stringResource(if (isCompoundMode) R.string.send_consolidating else R.string.send_youre_sending),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val fromAddressShown = if (isCompoundMode) fromAddress else source.address(chattingAddress)
                    SendReviewCard {
                        SendReviewRow(stringResource(R.string.to)) {
                            if (isCompoundMode) {
                                SendReviewValue(stringResource(R.string.send_this_address_consolidating))
                            } else {
                                SendReviewValue(recipientName, effectiveAddress.trim())
                            }
                        }
                        SendReviewDivider()
                        SendReviewRow(stringResource(R.string.from)) {
                            SendReviewValue(
                                sourceTitle,
                                fromAddressShown?.let { "${it.take(14)}...${it.takeLast(6)}" },
                                middleEllipsis = false,
                            )
                        }
                        SendReviewDivider()
                        SendReviewRow(stringResource(R.string.total)) {
                            val amount = amountSompi
                            when {
                                isPreviewingFee || isEstimatingMax -> com.kachat.app.ui.theme.IosActivityIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = LocalAppColors.current.textSecondary,
                                    strokeWidth = 2.dp,
                                )
                                amount != null -> SendReviewValue("${KaspaUnit.plain(amount + effectiveFeeSompi)} ${KaspaUnit.symbol}")
                                else -> Text("—", color = LocalAppColors.current.textSecondary, fontSize = 15.sp)
                            }
                        }
                    }

                    activeRequest?.let { PaymentRequestSummary(request = it) }

                    // The only things that can still change: the fee and the coins it spends.
                    SendFeeControls(
                        feeTier = feeTier,
                        onFeeTierChange = {
                            feeTier = it
                            customExtraFeeSompi = null
                            isEditingFee = false
                        },
                        isEditingFee = false,
                        customFeeText = customFeeText,
                        onCustomFeeTextChange = { customFeeText = it },
                        isEstimatingFee = isPreviewingFee,
                        feeText = "${ChatRepository.formatKas(effectiveFeeSompi)} ${KaspaUnit.symbol}",
                        onStartEditing = {},
                        onCommit = {},
                        coinControlSummary = coinControlSummary(manualUtxos),
                        onCoinControl = { if (!isSending) showCoinControl = true },
                        onEditFee = {
                            feeDraft = KaspaUnit.plain(effectiveFeeSompi)
                            go(SendFlowStep.FEE)
                        },
                    )

                    sendError?.let {
                        Text(it, color = LocalAppColors.current.danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            SendFlowStep.FEE -> SendFeeEditorScreen(
                draft = feeDraft,
                onDraftChange = { feeDraft = it },
                minimumSompi = defaultFeeSompi,
                onCancel = { go(SendFlowStep.REVIEW) },
                onConfirm = { typed ->
                    // never under the network's minimum (iOS commitCustomFee)
                    customExtraFeeSompi = (typed - defaultFeeSompi).coerceAtLeast(0L)
                    go(SendFlowStep.REVIEW)
                },
                insets = insets,
            )
        }
    }
}

/** The steps of [SpendingAddressSendFlow], in order (iOS `WithdrawKaspaView.Step`). */
private enum class SendFlowStep { RECIPIENT, FROM, AMOUNT, REVIEW, FEE }

/** What Max last wrote into the amount, and the fee, From and coins it left room for. */
private data class SendMaxFill(
    val amount: String,
    val tier: ColdFeeTier,
    val customExtra: Long?,
    val source: SendSource,
    val utxos: List<UtxoEntry>?,
)

/**
 * On-chain transaction history + UTXOs for the wallet's identity/chatting address, with direct
 * Send/Receive/Export - reached from the Profile screen's "Chatting Address" section's "Manage
 * Address" row. Field-for-field the same screen as [SpendingAddressTxHistoryScreen] (balance
 * header, Transaction History/UTXOs tabs, Compound UTXOs, Export, Explorer, Receive/Send), just
 * for the single fixed identity address instead of one spending-chain index - reuses the same
 * address-keyed StateFlows/functions on [WalletViewModel] (spendingAddressTxHistory/Utxos,
 * getSpendingUtxoLabels, etc. - all keyed by address string, nothing spending-chain-specific
 * about them) rather than duplicating a second copy of this state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IdentityAddressDetailScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel(),
    /** Message on a .kachat name's owner, from its detail. */
    onOpenChat: (String) -> Unit = {},
    /** Opened as the root of the "Received" History sheet (iOS 13046ad `OwnAddressHistorySheet`):
     *  Done closes it, top left, in place of the back arrow. */
    closesWithDone: Boolean = false,
    /** KaPosts' Wallet tab shows this screen as "Wallet" (iOS ChattingAddressManageView `title`). */
    title: String = "Chatting Address",
    /** False at the root of a dock tab, which has nothing to go back to. */
    showsBack: Boolean = true,
) {
    val address by viewModel.address.collectAsState()
    val balanceSompi by viewModel.balanceSompi.collectAsState()

    val txHistory by viewModel.spendingAddressTxHistory.collectAsState()
    val isLoadingTxHistory by viewModel.loadingSpendingAddressTxHistory.collectAsState()
    val txHistoryFailed by viewModel.spendingAddressTxHistoryFailed.collectAsState()
    val utxos by viewModel.spendingAddressUtxos.collectAsState()
    val isLoadingUtxos by viewModel.loadingSpendingAddressUtxos.collectAsState()
    val utxosLoadError by viewModel.spendingAddressUtxosError.collectAsState()
    val utxoListAddress: String? = (address as String?)?.takeIf { it.isNotEmpty() }
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    // The identity key is at least as sensitive as the seed phrase itself (it IS the wallet's
    // main spending key) - gated behind the same biometric flag Settings > View Seed Phrase
    // uses, not the lower-stakes biometricSpendingKeyEnabled a derived spending address uses.
    val biometricSeedPhraseEnabled by viewModel.biometricSeedPhraseEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    // The tapped transaction, while its action chooser is up.
    var transactionActionTarget by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    // The transaction being filed into a portfolio, if any - see [AddToPortfolioSheet].
    var portfolioCandidate by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    var addedPortfolioName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    val tabPager = androidx.compose.foundation.pager.rememberPagerState(initialPage = selectedTab, pageCount = { 3 })
    LaunchedEffect(tabPager) {
        androidx.compose.runtime.snapshotFlow { tabPager.settledPage }.collect { selectedTab = it }
    }
    LaunchedEffect(selectedTab) {
        if (tabPager.currentPage != selectedTab) tabPager.animateScrollToPage(selectedTab)
    }
    var showQr by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showCompoundFlow by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    // The "Address Actions" sheet, and the public-key screen it can open.
    var showAddressActions by remember { mutableStateOf(false) }
    var showPublicKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var labelingUtxoKey by remember { mutableStateOf<String?>(null) }
    var labelInput by remember { mutableStateOf("") }
    // The .kachat name opened from the Domains tab's .kachat screen (iOS 881ada6).
    var openedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    // The name service opened from the Domains tab (iOS fe52376).
    var openedDomainService by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }

    LaunchedEffect(address) {
        val addr = address
        if (!addr.isNullOrEmpty()) {
            viewModel.loadSpendingAddressTxHistory(addr)
            viewModel.loadSpendingAddressUtxos(addr)
            utxoLabels = viewModel.getSpendingUtxoLabels(addr)
        }
    }

    // A .kachat name opened from this address's .kachat tab: its live detail, which knows which of
    // your addresses holds it (iOS 881ada6).
    openedKachat?.let { info ->
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { openedKachat = null }, onOpenChat = onOpenChat) }
        return
    }
    // A name service opened from this address's Domains tab: its names (iOS fe52376).
    openedDomainService?.let { tld ->
        AboveDock {
            AddressDomainServiceScreen(
                address = address.orEmpty(),
                tld = tld,
                onBack = { openedDomainService = null },
                onOpenKachat = { openedKachat = it },
            )
        }
        return
    }

    val flowAddress = address
    // Send and Compound are sheets over this screen that only Cancel closes, as iOS presents
    // WithdrawKaspaView (.interactiveDismissDisabled()).
    if (showWithdraw && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showWithdraw = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                onDone = close,
                presentedAsSheet = true,
                lockedSource = SendSource.Chatting,
            )
        }
    }

    if (showCompoundFlow && flowAddress != null) {
        IosFullSheet(
            onDismissed = {
                showCompoundFlow = false
                viewModel.loadSpendingAddressTxHistory(flowAddress)
                viewModel.loadSpendingAddressUtxos(flowAddress)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = flowAddress,
                balanceSompi = balanceSompi,
                title = "Send Kaspa",
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                isCompoundMode = true,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its
        // safeAreaInset bar and tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // At the root of the "Received" History sheet: Done, as iOS's
                    // OwnAddressHistorySheet puts it (13046ad); a back arrow everywhere else.
                    if (closesWithDone) {
                        SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    } else if (showsBack) {
                        SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                    }
                },
                // Export and Explorer used to be two unlabelled glyphs here. A pair of icons has
                // room for no words at all, so neither said what it did, and there was nowhere to
                // put a third thing - they live in the Address Actions sheet below now.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
        bottomBar = {
            // Receive and Send belong to the history; the UTXOs and Domains tabs don't show them
            // (iOS KaPosts 0b9fd28).
            if (selectedTab == 0) {
                AddressReceiveSendBar(
                    receiveEnabled = !address.isNullOrEmpty(),
                    sendEnabled = balanceSompi > 0L,
                    onReceive = { showQr = true },
                    onSend = { showWithdraw = true },
                )
            }
        }
    ) { padding ->
        // What Receive / Send and the dock cover: the lists scroll clear of it. Without the bar
        // (UTXOs, Domains) it is the dock alone.
        val barInset = maxOf(padding.calculateBottomPadding(), LocalDockInset.current)
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(balanceSompi)
            Button(
                onClick = { showAddressActions = true },
                enabled = !address.isNullOrEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.Black),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.address_actions), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            // Right above what it switches, the way Chats has it: History, UTXOs, Domains
            // (iOS b96d727/718b88c).
            UnderlineTabBar(
                titles = listOf(stringResource(R.string.address_tab_history), stringResource(R.string.utxos), stringResource(R.string.address_tab_domains)),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
            // Swipe sideways between the tabs, like Connection Settings (iOS KaPosts 0b9fd28).
            androidx.compose.foundation.pager.HorizontalPager(
                state = tabPager,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) { page ->
            when (page) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { address?.let { viewModel.loadSpendingAddressTxHistory(it) } },
                    onTransactionClick = { transactionActionTarget = it },
                    bottomPadding = barInset,
                )
                // KaPost reads .kachat names only, so the Domains tab is the address's .kachat
                // names - no .kas, .k or .kaspa cards (iOS KaPosts 865c9ab) - to look at only:
                // they are managed in KaChat Wallet (iOS KaPosts 4c0d5a8).
                2 -> if (com.kachat.app.services.NameServicesClient.OTHER_SERVICES_ENABLED) {
                    AddressDomainsTab(address.orEmpty(), onOpenService = { openedDomainService = it }, bottomPadding = barInset)
                } else {
                    KaPostAddressDomainsTab(address.orEmpty(), bottomPadding = barInset)
                }
                else -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { utxoListAddress?.let { viewModel.loadSpendingAddressUtxos(it) } },
                    onCompound = { showCompoundFlow = true },
                    onRename = { key ->
                        labelingUtxoKey = key
                        labelInput = utxoLabels[key] ?: ""
                    },
                    bottomPadding = barInset,
                )
            }
            }
        }
    }

    val qrAddress = address
    if (showQr && !qrAddress.isNullOrEmpty()) {
        // iOS ChattingAddressManageView shows ChattingAddressQRView with its default caption -
        // the chatting caption Profile uses (since 492aa53: "for chatting and domains only").
        // In a sheet with Close leading, as iOS's ChattingAddressManageView presents it.
        QrPageSheet(onDismiss = { showQr = false }, white = true) {
            ChattingAddressQrPage(
                address = qrAddress,
                balanceSompi = balanceSompi,
                dismiss = QrPageDismiss.CLOSE,
                onDismiss = { showQr = false },
            )
        }
    }

    if (showAddressActions) {
        IdentityAddressActionsSheet(
            onPrivateKey = {
                showAddressActions = false
                if (biometricSeedPhraseEnabled) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to View Private Key",
                        onSuccess = { showPrivateKey = true }
                    )
                } else {
                    showPrivateKey = true
                }
            },
            onPublicKey = {
                showAddressActions = false
                showPublicKey = true
            },
            onExplorer = {
                showAddressActions = false
                address?.let { uriHandler.openUri(kaspaExplorer.addressUrl(it)) }
            },
            onDismiss = { showAddressActions = false },
        )
    }

    if (showPublicKey) {
        IdentityAddressPublicKeyOverlay(address = address.orEmpty(), onDismiss = { showPublicKey = false })
    }

    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getPrivateKeyHex(), onDismiss = { showPrivateKey = false })
    }

    labelingUtxoKey?.let { key ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { labelingUtxoKey = null },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_utxo), color = LocalAppColors.current.textPrimary) },
            text = {
                com.kachat.app.ui.theme.IosTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val addr = address ?: return@TextButton
                    viewModel.setSpendingUtxoLabel(addr, key, labelInput)
                    utxoLabels = viewModel.getSpendingUtxoLabels(addr)
                    labelingUtxoKey = null
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labelingUtxoKey = null }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    transactionActionTarget?.let { tapped ->
        TransactionActionsSheet(
            tx = tapped,
            onOpenExplorer = {
                transactionActionTarget = null
                uriHandler.openUri(kaspaExplorer.txUrl(tapped.txId))
            },
            onAddToPortfolio = {
                transactionActionTarget = null
                portfolioCandidate = tapped
            },
            onDismiss = { transactionActionTarget = null },
        )
    }

    portfolioCandidate?.let { candidate ->
        AddToPortfolioSheet(
            tx = candidate,
            address = address.orEmpty(),
            viewModel = portfolioViewModel,
            onDismiss = { portfolioCandidate = null },
            onAdded = { addedPortfolioName = it },
        )
    }
    addedPortfolioName?.let { name ->
        PortfolioAddedSnackbar(name) { addedPortfolioName = null }
    }
}

/**
 * The half sheet behind "Address Actions" on the Chatting Address screen. Same three-row shape as
 * Manage Addresses' and Cold Storage's sheets of the same name, so all three read as one object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityAddressActionsSheet(
    onPrivateKey: () -> Unit,
    onPublicKey: () -> Unit,
    onExplorer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            // Expanded, not half-height: a partially-expanded sheet cuts the last row off.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.address_actions),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    ActionSheetRow(
                        icon = Icons.Default.Key,
                        title = "View Private Key",
                        subtitle = "The key that spends this address. Never share it.",
                        onClick = onPrivateKey,
                    )
                    ActionSheetRow(
                        icon = Icons.Default.Numbers,
                        title = "View Public Key",
                        subtitle = "The public half of this address, for anyone who asks for it.",
                        onClick = onPublicKey,
                    )
                    ActionSheetRow(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.view_in_explorer),
                        subtitle = "Opens this address on your chosen block explorer.",
                        onClick = onExplorer,
                    )
                }
            }
        }
    }
}

/**
 * The chatting address's public key, from the Address Actions sheet.
 *
 * Nothing is derived or unlocked to show this: a Kaspa P2PK address IS its public key in bech32,
 * so this decodes the address the screen already has. No FLAG_SECURE, no reveal gate and no
 * clipboard expiry either - unlike the private key alongside it, this is a value you hand out on
 * purpose. Mirrors iOS's `ChattingAddressPublicKeyView`.
 */
@Composable
private fun IdentityAddressPublicKeyOverlay(address: String, onDismiss: () -> Unit) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // null only for an address shape that carries no key (script-hash), which the wallet's own
    // chatting address never is.
    val publicKeyHex = remember(address) {
        runCatching {
            com.kachat.app.util.KaspaAddress.decode(address).second.joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SheetBarBackButton(onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Public Key",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaspaTeal.copy(alpha = 0.1f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Safe to share", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    "This is the public half of your chatting address. It identifies you and cannot spend anything.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            if (publicKeyHex != null) {
                Text(
                    publicKeyHex,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(publicKeyHex))
                    keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    IosToasts.show("Public key copied")
                }) {
                    Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Public Key", color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "This address does not carry a public key.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Reveals a single spending address's own derived private key - not the wallet's seed phrase -
 * so a specific address's spending capability can be exported/backed up without exposing the
 * rest of the wallet. Mirrors SeedPhraseScreen's reveal flow at the same sensitivity level
 * (FLAG_SECURE screenshot/recording block, tap-to-reveal with a 7s auto-hide timer) rather than
 * inventing a lighter-weight pattern for equally sensitive key material. The caller already
 * gates presentation behind biometrics, same as View Seed Phrase's own entry point.
 */
@Composable
private fun SpendingAddressPrivateKeyOverlay(privateKeyHex: String, onDismiss: () -> Unit) {
    val keyCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(revealed) {
        if (revealed) {
            delay(7000)
            revealed = false
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                SheetBarButton(stringResource(R.string.done), onClick = onDismiss)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C1E1E))
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = LocalAppColors.current.warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.security_warning),
                        color = LocalAppColors.current.warning,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Anyone with this address's private key can spend its funds. Never share it with anyone.",
                        color = Color(0xFF948B8B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!revealed) {
                Column(
                    modifier = Modifier
                        .clickable { revealed = true }
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        null,
                        tint = LocalAppColors.current.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tap to reveal private key",
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    privateKeyHex,
                    color = LocalAppColors.current.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalAppColors.current.surface)
                        .padding(16.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            if (revealed) {
                TextButton(onClick = {
                    copyPrivateKeyWithAutoWipe(context, privateKeyHex)
                    keyCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                    IosToasts.show("Private key copied. Clipboard will clear in 30s.")
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SpendingAddressTxHistoryRow(
    tx: ColdStorageAddressDiscovery.AddressTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `direction(for:)` is nil - "Transaction", grey, no amount - when nothing left for
    // someone else (sent) or arrived here (received): a self-send or consolidation.
    val directionKnown = tx.amountSompi > 0
    val directionColor = when {
        !directionKnown -> colors.textSecondary
        tx.sent -> colors.danger
        else -> colors.success
    }
    // iOS `.formatted(date: .abbreviated, time: .shortened)`: "Sep 24, 2026 at 2:25 PM".
    val dateStr = tx.blockTimeMillis?.let {
        java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.US).format(java.util.Date(it))
    }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // "arrow.up.circle.fill" / "arrow.down.circle.fill" at title2: a 22 disc, the arrow cut
        // out of it in the card's colour.
        FilledCircleArrow(up = directionKnown && tx.sent, color = directionColor, cutout = Color(0xFF1C1C1E), size = 22.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    !directionKnown -> "Transaction"
                    tx.sent -> "Sent"
                    else -> "Received"
                },
                color = directionColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                tx.txId,
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            dateStr?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (directionKnown) {
                Text(
                    "${if (tx.sent) "-" else "+"}%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, tx.amountSompi / 100_000_000.0),
                    color = directionColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            // What this transaction paid the network (iOS 9839906).
            transactionFeeText(tx.feeSompi)?.let {
                Text(it, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1)
            }
            // iOS "arrow.up.right.square" at caption, accent.
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = KaspaTeal, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun SpendingAddressUtxoRow(
    utxo: ColdStorageAddressDiscovery.AddressUtxo,
    label: String? = null,
    onRenameClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    // iOS `utxoRow`: the coin glyph at title2, the label / amount / outpoint, Coinbase, the pencil.
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (utxo.isCoinbase) {
            // "cube.fill"
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ViewInAr, null, tint = KaspaTeal, modifier = Modifier.requiredSize(28.dp))
            }
        } else {
            // "circle.grid.2x2.fill": four dots in a square.
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                val r = size.minDimension * 0.2f
                for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                    drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, utxo.amountSompi / 100_000_000.0),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            MiddleEllipsisText(
                "${utxo.transactionId}:${utxo.index}",
                color = colors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (utxo.isCoinbase) {
            Text(
                stringResource(R.string.coinbase),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.size(32.dp).clickable(onClick = onRenameClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = KaspaTeal, modifier = Modifier.size(21.dp))
        }
    }
}

/** iOS 26 `.insetGrouped` corner radius and the gap above a list's first section. */
private val InsetGroupedRadius = 26.dp
private val InsetGroupedTopGap = 35.dp

/**
 * One row of an iOS 26 `.insetGrouped` section: the section is a single rounded card 16 in from
 * each edge, so only the first row rounds its top corners and the last its bottom ones, and every
 * row after the first draws the inset hairline separator above itself (from [separatorStart] to
 * 16 short of the trailing edge).
 */
@Composable
private fun Modifier.insetGroupedCell(index: Int, count: Int, separatorStart: Dp = 16.dp): Modifier {
    val colors = LocalAppColors.current
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) InsetGroupedRadius else 0.dp,
        topEnd = if (first) InsetGroupedRadius else 0.dp,
        bottomStart = if (last) InsetGroupedRadius else 0.dp,
        bottomEnd = if (last) InsetGroupedRadius else 0.dp,
    )
    val divider = colors.divider
    return this
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(shape)
        .background(colors.surface)
        .then(
            if (first) Modifier
            else Modifier.drawBehind {
                val stroke = 0.5.dp.toPx().coerceAtLeast(1f)
                drawLine(
                    color = divider,
                    start = Offset(separatorStart.toPx(), stroke / 2f),
                    end = Offset(size.width - 16.dp.toPx(), stroke / 2f),
                    strokeWidth = stroke,
                )
            }
        )
}

/** A one-row `.insetGrouped` list - the loading, empty and failed states iOS puts in the list. */
@Composable
private fun InsetGroupedSingleRowList(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .insetGroupedCell(0, 1)
                    .padding(horizontal = 16.dp, vertical = 15.dp)
            ) {
                content()
            }
        }
    }
}

/** "Balance" over the amount at the top of an address's page (iOS: caption, then title3 semibold). */
@Composable
private fun AddressPageBalanceHeader(balanceSompi: Long) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.balance),
            color = LocalAppColors.current.textSecondary,
            fontSize = 12.sp
        )
        Text(
            "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, balanceSompi / 100_000_000.0),
            color = LocalAppColors.current.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
    }
}

/**
 * An address page's History tab - iOS `transactionsList`: one `.insetGrouped` card of
 * transactions, or the spinner, "Could not load transactions." with Try Again, or "No
 * transactions yet." as the list's only row.
 */
@Composable
private fun AddressHistoryList(
    txHistory: List<ColdStorageAddressDiscovery.AddressTransaction>,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onTransactionClick: (ColdStorageAddressDiscovery.AddressTransaction) -> Unit,
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
) {
    val colors = LocalAppColors.current
    when {
        isLoading && txHistory.isEmpty() -> InsetGroupedSingleRowList {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
            }
        }
        // A failed fetch is not an empty history. Saying "No transactions yet." here was a
        // confident answer about someone's money the app had not got.
        txHistory.isEmpty() && failed -> InsetGroupedSingleRowList {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Could not load transactions.", color = colors.textSecondary, fontSize = 17.sp)
                Text(
                    stringResource(R.string.try_again),
                    color = KaspaTeal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
        }
        txHistory.isEmpty() -> InsetGroupedSingleRowList {
            Text(stringResource(R.string.no_transactions_yet), color = colors.textSecondary, fontSize = 17.sp)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
        ) {
            itemsIndexed(txHistory, key = { _, tx -> tx.txId }) { i, tx ->
                SpendingAddressTxHistoryRow(
                    tx = tx,
                    // Tapping a transaction asks what to do with it. It used to open the
                    // explorer outright, which left "Add to Portfolio" on a button most people
                    // never looked for.
                    onClick = { onTransactionClick(tx) },
                    // The separator starts under the text, past the 26 icon and its 12 gap.
                    modifier = Modifier.insetGroupedCell(i, txHistory.size, separatorStart = 54.dp),
                )
            }
        }
    }
}

/**
 * An address page's UTXOs tab - iOS `utxosList`: Compound UTXOs as its own section with its
 * footer when there is more than one coin, then one `.insetGrouped` card of coins, or the
 * spinner, the load-failure row or "No UTXOs." as that section's only row.
 */
@Composable
private fun AddressUtxoList(
    utxos: List<ColdStorageAddressDiscovery.AddressUtxo>,
    isLoading: Boolean,
    loadError: String?,
    labels: Map<String, String>,
    onRetry: () -> Unit,
    onCompound: () -> Unit,
    onRename: (String) -> Unit,
    /** Extra room at the list's end for what floats over the page's foot (Receive / Send, the dock). */
    bottomPadding: Dp = 0.dp,
) {
    val colors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = InsetGroupedTopGap, bottom = 16.dp + bottomPadding),
    ) {
        if (utxos.size > 1) {
            item(key = "compound") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .insetGroupedCell(0, 1)
                            .clickable(onClick = onCompound)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.CallMerge, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.compound_utxos),
                            color = KaspaTeal,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // The section footer.
                    Text(
                        stringResource(R.string.compound_utxos_description),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp, bottom = 24.dp)
                    )
                }
            }
        }
        when {
            isLoading && utxos.isEmpty() -> item(key = "loading") {
                Box(
                    modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                }
            }
            // The load-failure row when the node could not be asked, otherwise "No UTXOs.".
            utxos.isEmpty() -> item(key = "empty") {
                if (loadError != null) {
                    UtxoLoadFailureRow(detail = loadError, retry = onRetry, modifier = Modifier.insetGroupedCell(0, 1))
                } else {
                    Text(
                        stringResource(R.string.no_utxos),
                        color = colors.textSecondary,
                        fontSize = 17.sp,
                        modifier = Modifier.insetGroupedCell(0, 1).padding(horizontal = 16.dp, vertical = 15.dp),
                    )
                }
            }
            else -> itemsIndexed(utxos, key = { _, u -> "${u.transactionId}:${u.index}" }) { i, utxo ->
                val key = "${utxo.transactionId}:${utxo.index}"
                SpendingAddressUtxoRow(
                    utxo = utxo,
                    label = labels[key],
                    onRenameClick = { onRename(key) },
                    modifier = Modifier.insetGroupedCell(i, utxos.size, separatorStart = 54.dp),
                )
            }
        }
    }
}

/**
 * Receive and Send at the foot of an address's page (iOS `safeAreaInset(edge: .bottom)`): two
 * accent capsules 12 apart, 16 in and 16 off the bottom. Send is "arrow.up.circle.fill" and, with
 * nothing to send, disabled at half opacity as iOS draws it.
 */
@Composable
private fun AddressReceiveSendBar(
    receiveEnabled: Boolean,
    sendEnabled: Boolean,
    onReceive: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            // Above the dock the page runs under (0 where there is none).
            .padding(bottom = LocalDockInset.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AddressBarCapsule(Icons.Default.QrCode, stringResource(R.string.receive), FontWeight.SemiBold, receiveEnabled, onReceive, Modifier.weight(1f))
        AddressBarCapsule(null, stringResource(R.string.send), FontWeight.Bold, sendEnabled, onSend, Modifier.weight(1f))
    }
}

@Composable
private fun AddressBarCapsule(
    /** Null: iOS's "arrow.up.circle.fill" - a black disc with the arrow cut out in the accent. */
    icon: ImageVector?,
    title: String,
    weight: FontWeight,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp).alpha(if (enabled) 1f else 0.5f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            contentColor = Color.Black,
            disabledContainerColor = KaspaTeal,
            disabledContentColor = Color.Black,
        )
    ) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp))
        else FilledCircleArrow(up = true, color = Color.Black, cutout = KaspaTeal, size = 17.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = weight)
    }
}

/** SF Symbols' "arrow.up.circle.fill" / "arrow.down.circle.fill": a [color] disc with the arrow
 *  cut out of it, drawn in [cutout] (what is behind the symbol). */
@Composable
private fun FilledCircleArrow(up: Boolean, color: Color, cutout: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            null,
            tint = cutout,
            modifier = Modifier.size(size * 0.68f),
        )
    }
}







/** Default tapback-style set, not a full emoji keyboard - user-customizable via Settings > Chats
 *  > Quick Reactions (see [com.kachat.app.repository.AppSettingsRepository.quickReactionEmojis]),
 *  this is just the fallback [QuickReactionBar] uses until then. */
val QUICK_REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/**
 * Which message's quick-reaction bar is open, if any - iOS's screen-level
 * `activeQuickReactionMessageId`: one bar at a time, and a tap anywhere in the message list
 * ([closesQuickReactionBar]) closes it.
 */
object QuickReactionBarState {
    var activeId by mutableStateOf<String?>(null)
}

/**
 * On a message list: a tap anywhere in it closes the open quick-reaction bar, alongside whatever
 * else the tap does (iOS's `simultaneousGesture(TapGesture())` on the list). Scrolls don't count.
 */
fun Modifier.closesQuickReactionBar(): Modifier = composed {
    // Leaving the chat closes it too: iOS's bar is the screen's state and goes with it.
    DisposableEffect(Unit) { onDispose { QuickReactionBarState.activeId = null } }
    this
}.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        var moved = false
        while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) {
                if (!moved && QuickReactionBarState.activeId != null) QuickReactionBarState.activeId = null
                break
            }
        }
    }
}

/**
 * The bar a double-tapped message shows - iOS's `QuickReactionBarView`, laid out directly above
 * the bubble in the list rather than floating over it: "+" (the full picker, [onMore]) and the
 * quick emoji in a row, the reply arrow under them at the trailing edge, on the app's frosted
 * card (18 corners, 14 by 10 inside).
 */
@Composable
fun QuickReactionBar(
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onMore: () -> Unit,
    emojis: List<String> = QUICK_REACTION_EMOJIS,
    modifier: Modifier = Modifier,
) {
    val pickerContext = LocalContext.current
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .iosGlass(18.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            // Six emoji at 26sp plus the picker button run past a 320dp screen once the
            // reader's font scale is up - the row scrolls sideways rather than losing one.
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leads the row: the six quick emoji cover the common cases, and this is the way
            // to any of the others without going to Settings to change which six they are.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(LocalAppColors.current.textPrimary.copy(alpha = 0.08f))
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "More reactions",
                    tint = LocalAppColors.current.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
            emojis.forEach { emoji ->
                Text(
                    emoji,
                    fontSize = 26.sp,
                    modifier = Modifier.clickable {
                        EmojiRecents.record(pickerContext, emoji)
                        onReact(emoji)
                    }
                )
            }
        }
        Box(
            Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onReply),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = stringResource(R.string.reply), tint = KaspaTeal, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * [QuickReactionBar] for the message [messageId], when its bar is the open one: closes the bar
 * on any choice and keeps the full picker with the bubble (the bar goes away under it, as iOS's
 * screen owns its picker). Place it first in the bubble's column, aligned to the bubble's side.
 */
@Composable
fun InlineQuickReactionBar(
    messageId: String,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    /** The quick set; the Settings > Chats > Quick Reactions one when the caller has none. */
    emojis: List<String>? = null,
) {
    var showFullPicker by remember { mutableStateOf(false) }
    if (showFullPicker) {
        EmojiReactionPickerSheet(
            onDismiss = { showFullPicker = false },
            onPick = { onReact(it) },
        )
    }
    if (QuickReactionBarState.activeId == messageId) {
        val quickSet = emojis ?: run {
            val settingsViewModel: com.kachat.app.viewmodels.SettingsViewModel = hiltViewModel()
            settingsViewModel.quickReactionEmojis.collectAsState().value
        }
        QuickReactionBar(
            onReact = { emoji -> onReact(emoji); QuickReactionBarState.activeId = null },
            onReply = { onReply(); QuickReactionBarState.activeId = null },
            onMore = { QuickReactionBarState.activeId = null; showFullPicker = true },
            emojis = quickSet,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** Small rounded pill overlapping a bubble's bottom outer corner, showing the distinct emoji reacted with (and a count when more than one person used the same one). */
@Composable
fun ReactionPill(reactions: List<ReactionEntity>, modifier: Modifier = Modifier, myAddress: String? = null) {
    val counts = remember(reactions) { reactions.groupingBy { it.emoji }.eachCount() }
    // Status of the local user's own reaction: "pending" → no icon, "sent" → green checkmark once it
    // goes through, "failed" → red error icon + red outline (the tappable Retry is under the message).
    // Only the local user's reaction ever pends/fails; everyone else's is always "sent".
    val myReaction = remember(reactions, myAddress) { reactions.firstOrNull { it.reactorAddress == myAddress } }
    // The green "sent" checkmark is a recent confirmation, not a permanent badge - drop it once the
    // reaction is older than 10 minutes. Computed each recomposition (not remembered) so it clears
    // on the next refresh past the window. Pending/failed are always shown.
    val myStatus = myReaction?.let { r ->
        when (r.deliveryStatus) {
            "failed" -> "failed"
            "sent" -> if (System.currentTimeMillis() - r.blockTimestamp < 600_000L) "sent" else null
            else -> null
        }
    }
    Surface(
        color = LocalAppColors.current.surfaceVariant,
        shape = RoundedCornerShape(50),
        shadowElevation = 2.dp,
        border = if (myStatus == "failed") BorderStroke(1.dp, LocalAppColors.current.danger.copy(alpha = 0.6f)) else null,
        modifier = modifier
    ) {
        Row(
            // A message with many different reactions would otherwise grow a pill wider than the
            // screen; it scrolls instead, so every reaction stays reachable.
            modifier = Modifier
                .widthIn(max = 240.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (myStatus) {
                "failed" -> Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = stringResource(R.string.failed_to_send),
                    tint = LocalAppColors.current.danger,
                    modifier = Modifier.size(11.dp)
                )
                "sent" -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LocalAppColors.current.success,
                    modifier = Modifier.size(11.dp)
                )
            }
            counts.forEach { (emoji, count) ->
                Text(emoji, fontSize = 13.sp)
                if (count > 1) {
                    Text(count.toString(), fontSize = 11.sp, color = LocalAppColors.current.textSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * "Edit KaChat Profile": the live address profile editor ([KachatLiveProfileEditorScreen], iOS
 * 5df42b4) on both networks - it writes the `kchat:1:profile:` record, a self-send that needs no
 * registry, so it saves on mainnet too; only the primary name waits for mainnet's registry (iOS
 * d36fc42). The placeholder below (iOS 09e0403) is the switch-back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KachatProfileEditorScreen(
    onBack: () -> Unit,
    showSetupGuides: Boolean = true,
    onSetupGuide: () -> Unit = {},
) {
    // The live editor on every network since iOS 7227d69, and it saves there too since d36fc42.
    // The placeholder below is kept, unreachable, as the switch-back.
    if (com.kachat.app.services.kachatnames.KachatNamesService.isEnabled) {
        KachatLiveProfileEditorScreen(onBack = onBack)
        return
    }
    val colors = LocalAppColors.current
    @Composable
    fun placeholderRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(text, color = colors.textSecondary)
        }
    }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.edit_kachat_profile), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            // The setup guide lives here since 5.2 (iOS b000310) - .kachat is the name KaChat sets
            // up for you; .kas profiles are edited field by field in Your Domains.
            if (showSetupGuides) {
                SettingsSection(title = null) {
                    SettingsNavigationItem(stringResource(R.string.kg_setup_guide), null, showIcon = false, onClick = onSetupGuide)
                }
                SettingsFooter(stringResource(R.string.kg_names_setup_footer))
            }
            SettingsSection(title = null) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.kachat_profile_coming_body), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SettingsSection(title = stringResource(R.string.avatar)) { placeholderRow(stringResource(R.string.choose_avatar), Icons.Default.Photo) }
            SettingsSection(title = stringResource(R.string.banner)) { placeholderRow(stringResource(R.string.choose_banner), Icons.Default.Image) }
            SettingsSection(title = stringResource(R.string.profile)) {
                listOf(R.string.bio, R.string.x_handle, R.string.website, R.string.telegram, R.string.discord_user_id, R.string.email, R.string.github, R.string.redirect_url)
                    .forEachIndexed { index, res ->
                        if (index > 0) HorizontalDivider(color = colors.background)
                        placeholderRow(stringResource(res))
                    }
            }
            SettingsSection(title = stringResource(R.string.kachat_name_section)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.name), color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.none_yet), color = colors.textSecondary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}





@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeedPhraseScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val secretCopyHaptic = com.kachat.app.util.rememberHaptics()
    var revealed by remember { mutableStateOf(false) }
    val mnemonic = remember { viewModel.getActiveMnemonic() ?: "" }
    // A passphrase (the "25th word") changes every key: the words alone restore a different,
    // empty wallet, so the backup screen must say so and show it (iOS 3076ab7, IOS-017).
    val passphrase = remember { viewModel.getActivePassphrase() }
    val privateKey = remember { viewModel.getPrivateKeyHex() }
    val words = remember { mnemonic.split(" ") }
    val context = LocalContext.current

    // Blocks screenshots and screen recording of the seed phrase / private key for as long as
    // this screen is on-screen (window-level flag, the standard Android mechanism - unlike iOS,
    // which has no API to block a screenshot outright, only to detect active screen recording
    // after the fact via UIScreen.isCaptured). Cleared on leaving so it doesn't leak onto other
    // screens.
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Auto-hides the words again after a short window instead of leaving them on screen
    // indefinitely once revealed — someone glancing at the phone later shouldn't still see them.
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(7000)
            revealed = false
        }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.seed_phrase), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2C1E1E))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = LocalAppColors.current.warning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.security_warning),
                        color = LocalAppColors.current.warning,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.anyone_with_your_seed_phrase_can),
                        color = Color(0xFF948B8B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (passphrase != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = LocalAppColors.current.warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.seed_phrase_also_uses_passphrase),
                        color = LocalAppColors.current.warning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!revealed) {
                Column(
                    modifier = Modifier
                        .clickable { revealed = true }
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = LocalAppColors.current.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tap_to_reveal_seed_phrase),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    words.forEachIndexed { index, word ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(LocalAppColors.current.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = word,
                                color = LocalAppColors.current.textPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                // Under the words, with the same FLAG_SECURE window and 7-second auto-hide.
                if (passphrase != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LocalAppColors.current.surface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.seed_phrase_passphrase_label),
                            color = LocalAppColors.current.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = passphrase,
                            color = LocalAppColors.current.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (revealed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Both copies go through the sensitive path: marked secret for the system
                    // clipboard and wiped 30 seconds later (copyPrivateKeyWithAutoWipe). Copying
                    // the phrase is how it reaches a password manager, which is a safer home for
                    // it than a photograph of a notebook (iOS f9beba1).
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, mnemonic, label = "recovery phrase")
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show("Seed phrase copied. Clipboard will clear in 30s.")
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Seed Phrase", color = KaspaTeal)
                        }
                    }
                    TextButton(onClick = {
                        copyPrivateKeyWithAutoWipe(context, privateKey)
                        // iOS SettingsView: the haptic and the 30-second notice, as the phrase has.
                        secretCopyHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        IosToasts.show("Private key hex copied. Clipboard will clear in 30s.")
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tag, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_private_key_hex), color = KaspaTeal)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    // 4.0 (matches iOS): null renders the settings HUB (one row per category); a key renders
    // just that category's items as its own page.
    sectionKey: String? = null,
    walletViewModel: WalletViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val balance by walletViewModel.fullBalance.collectAsState()
    val dotColorHex by connectionViewModel.dotColorHex.collectAsState()
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()
    // The switch is only true when Android agrees: a stored "on" with the OS permission revoked
    // would show as enabled while nothing could ever arrive.
    val notificationSettingsContext = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    notificationSettingsContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
        // Only flip the setting on when Android actually agreed - otherwise the switch would
        // sit on with nothing behind it.
        if (granted) settingsViewModel.setNotificationsEnabled(true)
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationSettingsContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, notificationSettingsContext.packageName)
            )
        }
    }
    val showFeeEstimate by settingsViewModel.showFeeEstimate.collectAsState()
    val kaspaExplorer by chatViewModel.kaspaExplorer.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        walletViewModel.refreshBalance()
    }

    var showKaPostsSettings by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    // A tab tapped on the dock copy over KaPosts Settings closes it, as every KaPosts cover.
    LaunchedEffect(Unit) { KaPostsDock.closeCovers.collect { showKaPostsSettings = false } }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (sectionKey != null) {
                // A category page is pushed inside the sheet's navigation stack: iOS's inline bar
                // with the back chevron on the left and the page's title in the middle - no
                // connection dot, no balance.
                SettingsSubpageBar(
                    title = settingsSectionTitle(sectionKey),
                    onBack = { navController.popBackStack() },
                )
            } else {
            // iOS KaPosts' Settings tab toolbar: the balance in the middle and the connection dot
            // in glass top right (865c9ab) - no title, and no Done, since it is a tab rather than
            // a sheet.
            Row(
                modifier = Modifier
                    .background(LocalAppColors.current.background)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    // measured on the iPhone: the bar's centre 37 below the sheet's top
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The dot's width on the other side, so the balance sits in the middle.
                Spacer(Modifier.size(44.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { BalanceLabelRow(balance, copyOnTap = true) }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .barGlass()
                        .clip(CircleShape)
                        .clickable { ConnectionStatusOverlayState.open() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(12.dp).background(Color(dotColorHex), CircleShape))
                }
            }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            // The hub keeps its own measured spacing; a category page is an iOS Form, its
            // sections spaced by SettingsFormSection.
            verticalArrangement = if (sectionKey == null) Arrangement.spacedBy(24.dp) else Arrangement.Top
        ) {
            // A Form's first header sits 4 under the inline bar.
            if (sectionKey != null) Spacer(Modifier.height(4.dp))
            if (sectionKey == null) {
                // Flat hub list (matches iOS): no section titles - just the categories, the
                // seed-phrase action, and Danger Zone in one card - its top 122 below the
                // sheet's, as on the iPhone.
                Spacer(Modifier.height(24.dp))
                Surface(
                    color = LocalAppColors.current.surface,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                    SettingsNavigationItem(stringResource(R.string.customization), Icons.Outlined.Brush, onClick = { navController.navigate("settings_section/customization") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.security), com.kachat.app.ui.theme.IosSymbols.LockShield, onClick = { navController.navigate("settings_section/security") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.connection), Icons.Outlined.CellTower, onClick = { navController.navigate("settings_section/connection") })
                    SettingsDivider(inset = IosRowTextInset)
                    // KaPosts' own settings, where KaChat had Chats, Storage and Chat History (iOS
                    // KaPosts SettingsView).
                    SettingsNavigationItem("KaPost", Icons.AutoMirrored.Outlined.Article, onClick = { showKaPostsSettings = true })
                    SettingsDivider(inset = IosRowTextInset)
                    // The same page as the cog on the Notifications tab. Public Chats has no row
                    // here: its rooms are chosen with the customize button on the Public Chats tab
                    // itself (iOS KaPosts d37f64d).
                    SettingsNavigationItem(stringResource(R.string.notifications), Icons.Outlined.NotificationsActive, onClick = { navController.navigate("settings_section/notifications") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem("Cache", Icons.Outlined.Storage, onClick = { navController.navigate("cache_settings") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.diagnostics), Icons.Outlined.MonitorHeart, onClick = { navController.navigate("settings_section/diagnostics") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem("Open Source Licenses", Icons.Outlined.Description, onClick = { navController.navigate("open_source_licenses") })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsActionItem(stringResource(R.string.view_seed_phrase), Icons.Outlined.Key, LocalAppColors.current.danger, labelColor = LocalAppColors.current.danger) {
                        if (biometricSeedPhraseEnabled) {
                            context.authenticateWithDeviceCredential(
                                title = "Unlock to View Seed Phrase",
                                onSuccess = { navController.navigate("seed_phrase") }
                            )
                        } else {
                            navController.navigate("seed_phrase")
                        }
                    }
                    SettingsDivider(inset = IosRowTextInset)
                    // Log out, not delete: the account stays saved on this phone, and deleting it is
                    // done from Saved Accounts on the opening screen (iOS KaPosts 865c9ab).
                    SettingsActionItem(stringResource(R.string.log_out), Icons.AutoMirrored.Outlined.Logout, LocalAppColors.current.danger, labelColor = LocalAppColors.current.danger) {
                        showLogout = true
                    }
                    }
                }
            }

            if (sectionKey == "customization") {
                // iOS KaPosts customizationPage: one section - the three pickers and Show Fee
                // Estimate.
                SettingsFormSection(title = stringResource(R.string.customization)) {
                    // Appearance / Language / Currency are shared with the accounts screen's App
                    // Settings (iOS AppWideCustomizationPickers); the dock and the fee estimate
                    // belong to this account's Settings only.
                    AppWideCustomizationPickers(walletViewModel)
                    SettingsDivider()
                    // Moved here from Chats: it decides whether a number is drawn on the composer,
                    // which is a display preference, not a chat behaviour.
                    SettingsSwitchItem(stringResource(R.string.show_fee_estimate), showFeeEstimate) { enabled ->
                        settingsViewModel.setShowFeeEstimate(enabled)
                    }
                }
            }

            if (sectionKey == "security") {
                SettingsFormSection(title = stringResource(R.string.security)) {
                    // Shared with the accounts screen's App Settings (see AppSettingsScreen.kt) -
                    // every row is app-wide, so both entry points render the exact same items.
                    SecuritySettingsItems(walletViewModel = walletViewModel)
                }
            }

            if (sectionKey == "connection") {
                ConnectionHubSections(
                    explorerName = kaspaExplorer.displayName,
                    onOpenConnectionSettings = { navController.navigate("connection_settings") },
                    onOpenExplorer = { navController.navigate("kaspa_explorer_settings") },
                )
            }

            if (sectionKey == "notifications") {
                // The master switch, at the top of the page it governs (iOS NotificationsHubPage,
                // behind the cog on the Notifications tab and in Settings): push carries KaPosts
                // and the public chat rooms' bells, so it sits above the pages rather than inside
                // one of them. No header, as on iOS; the footer says what it does.
                SettingsFormSection(
                    title = null,
                    first = true,
                    footer = if (notificationsEnabled && notificationPermissionGranted) {
                        "Everything you have switched on below can reach you, even when the app is closed. Turning this off silences all of it."
                    } else if (!notificationPermissionGranted) {
                        // Android only: the OS permission is a separate switch here.
                        "Android has notifications switched off for KaPost. Turning this on will ask for permission."
                    } else {
                        "Turn this on to receive anything you have switched on below, even when the app is closed."
                    },
                ) {
                    SettingsSwitchItem(
                        stringResource(R.string.push_notifications),
                        notificationsEnabled && notificationPermissionGranted,
                    ) { enabled ->
                        if (enabled && !notificationPermissionGranted) {
                            requestNotificationPermission()
                        } else {
                            settingsViewModel.setNotificationsEnabled(enabled)
                        }
                    }
                }

                // KaPosts' notifications are about posts only - no wallet ones (iOS KaPosts
                // d37f64d). Public Chats rooms keep their own bells, in each room.
                SettingsFormSection(title = stringResource(R.string.notifications)) {
                    SettingsNavigationItem("KaPost", Icons.AutoMirrored.Outlined.Article, onClick = {
                        navController.navigate("kaposts_notification_settings")
                    })
                    SettingsDivider(inset = IosRowTextInset)
                    SettingsNavigationItem(stringResource(R.string.sound_vibration), Icons.AutoMirrored.Outlined.VolumeUp, onClick = {
                        navController.navigate("notification_settings")
                    })
                }
            }

            if (sectionKey == "diagnostics") {
                // Shared with the accounts screen's App Settings (see AppSettingsScreen.kt).
                DiagnosticsSettingsSections(chatViewModel = chatViewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showKaPostsSettings) {
        KaPostsSettingsOverlay(onClose = { showKaPostsSettings = false })
    }
    if (showLogout) {
        ConfirmActionSheet(
            title = stringResource(R.string.log_out),
            confirmTitle = stringResource(R.string.log_out),
            confirmSubtitle = "The account stays saved on this phone: sign back in from Saved Accounts on the " +
                "opening screen, where an account can also be deleted.",
            confirmIcon = Icons.AutoMirrored.Outlined.Logout,
            onConfirm = {
                showLogout = false
                walletViewModel.logout()
            },
            onDismiss = { showLogout = false },
        )
    }
    }
}

/** A Settings category page's inline title - iOS's `.navigationTitle` of each page. */
@Composable
private fun settingsSectionTitle(sectionKey: String): String = when (sectionKey) {
    "customization" -> stringResource(R.string.customization)
    "security" -> stringResource(R.string.security)
    "connection" -> stringResource(R.string.connection)
    "notifications" -> stringResource(R.string.notifications)
    "diagnostics" -> stringResource(R.string.diagnostics)
    else -> ""
}

/**
 * A Form button row with a leading symbol, both in [color] (iOS tints a Button's Label), and while
 * [busy] the activity indicator at the trailing edge. Disabled, it greys out as iOS's does.
 */
@Composable
private fun SettingsBusyActionItem(
    label: String,
    icon: ImageVector,
    busy: Boolean,
    enabled: Boolean,
    color: Color = KaspaTeal,
    onClick: () -> Unit,
) {
    val tint = if (enabled) color else LocalAppColors.current.textSecondary.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
    }
}


@Composable
fun SettingsSection(title: String?, headerAction: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // iOS 26's inset-grouped Form section: the header in title case, 17pt semibold in the
    // secondary label colour (no longer iOS's old small uppercase), over a card with 26pt corners.
    Column {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, bottom = 8.dp)
                )
                headerAction?.invoke()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(LocalAppColors.current.surface)
        ) {
            content()
        }
    }
}

/** iOS Form spacing, measured on the iPhone: 28 from a card to the next section's header, 15
 *  from a footer to it. */
val IosFormSectionGap = 28.dp
val IosFormFooterGap = 15.dp

/**
 * One section of an iOS Form page: [SettingsSection] (header, card), the [footer] under the card
 * rather than in it, and the gap to the next section ([IosFormSectionGap], or [IosFormFooterGap]
 * after a footer). Lay these out in a plain Column (no spacedBy) - see [SettingsFormPage].
 */
@Composable
fun SettingsFormSection(
    title: String?,
    footer: String? = null,
    /** A footer that reports an outcome (iOS colours an error red, a success green). */
    footerColor: Color = Color.Unspecified,
    /** The page's first section: without a header, iOS still starts its card where a first
     *  header's card would sit. */
    first: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // A section without a header keeps part of a header's room above its card on the
        // iPhone: 32 from the footer above it, against 23 here (measured) - and the whole of it
        // when it opens the page.
        if (title == null) Spacer(Modifier.height(if (first) 24.dp else 9.dp))
        SettingsSection(title = title, content = content)
        if (footer != null) {
            SettingsFormFooter(footer, color = footerColor)
            Spacer(Modifier.height(IosFormFooterGap))
        } else {
            Spacer(Modifier.height(IosFormSectionGap))
        }
    }
}

/** An iOS section footer under its card: 13pt secondary, 16 in, 7 below the card. */
@Composable
fun SettingsFormFooter(text: String, color: Color = Color.Unspecified) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = if (color != Color.Unspecified) color else LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp)
    )
}

/**
 * A pushed settings page's bar, iOS's inline navigation bar: the back chevron on a glass disc at
 * the left, [title] centred in headline (17 semibold), and the page's own [trailing] items.
 * No connection dot and no balance - those are the Settings hub's alone.
 */
@Composable
fun SettingsSubpageBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SheetNavBar(
        title = title,
        // Inside the Settings sheet the status bar is already consumed; on a full-screen page
        // (the accounts screen's App Settings) this keeps the bar below it.
        modifier = Modifier.background(LocalAppColors.current.background).statusBarsPadding(),
        leading = { SheetBarBackButton(onClick = onBack) },
        trailing = trailing,
    )
}

/**
 * A pushed settings page laid out as iOS's inset-grouped Form: [SettingsSubpageBar], then a
 * scrolling column of [SettingsFormSection]s 16 in from the edges, the first header 4 under the bar.
 */
@Composable
fun SettingsFormPage(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = { SettingsSubpageBar(title = title, onBack = onBack, trailing = trailing) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * iOS's inline Picker row (`.pickerStyle(.menu)` in a Form): [label] at the left, the selected
 * option in the accent colour with the menu's up/down chevrons at the right. A tap opens a small
 * menu of [options], a checkmark on the selected one.
 */
@Composable
fun <T> SettingsPickerItem(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    optionLabel(selected),
                    color = KaspaTeal,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        leadingIcon = {
                            if (option == selected) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            } else {
                                Spacer(Modifier.size(20.dp))
                            }
                        },
                        onClick = {
                            expanded = false
                            if (option != selected) onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * iOS's `AppWideCustomizationPickers`: Appearance, Language and Currency as menu Pickers, shared by
 * Settings > Customization and the accounts screen's App Settings (one source of truth).
 */
@Composable
fun AppWideCustomizationPickers(walletViewModel: WalletViewModel) {
    val appearance by walletViewModel.appearance.collectAsState()
    val currencyCode by walletViewModel.currency.collectAsState()
    var language by remember { mutableStateOf(currentAppLanguage()) }

    SettingsPickerItem(
        label = stringResource(R.string.appearance),
        options = com.kachat.app.models.AppAppearance.entries,
        selected = appearance,
        optionLabel = {
            when (it) {
                com.kachat.app.models.AppAppearance.SYSTEM -> stringResource(R.string.appearance_system)
                com.kachat.app.models.AppAppearance.LIGHT -> stringResource(R.string.appearance_light)
                com.kachat.app.models.AppAppearance.DARK -> stringResource(R.string.appearance_dark)
            }
        },
        onSelect = { walletViewModel.setAppearance(it) },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.language),
        options = AppLanguage.entries,
        selected = language,
        optionLabel = { if (it == AppLanguage.SYSTEM) stringResource(R.string.appearance_system) else it.displayName },
        onSelect = {
            language = it
            applyAppLanguage(it)
        },
    )
    SettingsDivider()
    SettingsPickerItem(
        label = stringResource(R.string.currency),
        options = AppCurrency.entries,
        selected = AppCurrency.fromCode(currencyCode),
        optionLabel = { it.displayName },
        onSelect = { walletViewModel.setCurrency(it.code) },
    )
}








@Composable
fun SettingsSwitchItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = LocalAppColors.current.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        com.kachat.app.ui.theme.IosSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsNavigationItem(
    label: String,
    icon: ImageVector?,
    value: String = "",
    showIcon: Boolean = true,
    /** iOS tints a row's symbol with the accent; a Label given `.primary` draws it white. */
    iconTint: Color = KaspaTeal,
    /** iOS writes a link row's trailing value in `.caption` (12): pass 12.sp for that. */
    valueFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    valueColor: Color = LocalAppColors.current.textSecondary,
    onClick: () -> Unit = {},
) {
    // iOS 26 list row: 52pt tall, a 24pt symbol in its own column, the label lined up with the
    // separator that starts under it (see SettingsDivider(inset)).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon && icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(text = value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontSize = valueFontSize, modifier = Modifier.padding(horizontal = 8.dp))
        }
        IosDisclosureChevron()
    }
}

/** iOS's disclosure chevron at the end of a navigation row. */
@Composable
fun IosDisclosureChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = LocalAppColors.current.textSecondary,
        modifier = Modifier.size(24.dp)
    )
}

/** Where a row's text starts when it has a leading symbol: 16 + 24 + 16. Separators inset to it. */
val IosRowTextInset = 56.dp

@Composable
fun SettingsActionItem(
    label: String,
    icon: ImageVector,
    color: Color,
    /** Defaults to [color]; set it when the label should read differently from the icon. */
    labelColor: Color = color,
    /** A row that opens a page (iOS's Danger Zone) carries the disclosure chevron. */
    showChevron: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = labelColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (showChevron) IosDisclosureChevron()
    }
}


@Composable
fun SettingsDivider(inset: androidx.compose.ui.unit.Dp = 16.dp) {
    // iOS separators start where the row's text starts and stop short of the right edge.
    HorizontalDivider(modifier = Modifier.padding(start = inset, end = 16.dp), color = LocalAppColors.current.divider, thickness = 0.5.dp)
}

/**
 * Settings > Connection (iOS `ConnectionHubPage`, 0c1af03; 010167f): the Testnet switch first -
 * with the restart note while the running network differs, and no footer - then Connection
 * Settings, then Kaspa Explorer in its own section. Shared by the in-account
 * Settings page and the accounts screen's App Settings.
 */
@Composable
fun ConnectionHubSections(
    explorerName: String,
    onOpenConnectionSettings: () -> Unit,
    onOpenExplorer: () -> Unit,
    connectionViewModel: com.kachat.app.viewmodels.ConnectionViewModel = hiltViewModel(),
) {
    val selected by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
    val isTestnet = selected == com.kachat.app.util.KaspaNetwork.Type.TESTNET
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val colors = LocalAppColors.current
    SettingsFormSection(title = stringResource(R.string.connection)) {
        // Mainnet <-> testnet, first in the section (no footer). The choice applies at the next
        // launch, all at once (iOS e6f664f): until then the running app stays on its network.
        // Each network keeps its own connection settings, so Connection Settings below shows
        // the chosen network's values - the ones the app uses after the restart.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.testnet),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            com.kachat.app.ui.theme.IosSwitch(
                checked = isTestnet,
                onCheckedChange = { on ->
                    connectionViewModel.switchNetwork(if (on) com.kachat.app.util.KaspaNetwork.Type.TESTNET else com.kachat.app.util.KaspaNetwork.Type.MAINNET)
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                onColor = colors.warning,
            )
        }
        if (selected != com.kachat.app.util.KaspaNetwork.launch) {
            SettingsDivider(inset = IosRowTextInset)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.warning, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(if (isTestnet) R.string.testnet_restart_to_testnet else R.string.testnet_restart_to_mainnet),
                    color = colors.textPrimary,
                    fontSize = 15.sp
                )
            }
        }
        SettingsDivider(inset = IosRowTextInset)
        SettingsNavigationItem(stringResource(R.string.connection_settings), Icons.Default.Language, onClick = onOpenConnectionSettings)
    }
    // Its own section, no header; the explorer's name in iOS's caption.
    SettingsFormSection(title = null) {
        SettingsNavigationItem(
            stringResource(R.string.kaspa_explorer),
            Icons.Default.Explore,
            explorerName,
            valueFontSize = 12.sp,
            onClick = onOpenExplorer,
        )
    }
}

@Composable
fun SettingsFooter(text: String) {
    // iOS section footer: 13pt secondary label.
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * The Security section's rows. The biometric toggles and Child Mode are app-wide, so the same
 * items render both in the in-account Settings > Security page and in the accounts screen's App
 * Settings sheet (see AppSettingsScreen.kt), one source of truth. Caller supplies the enclosing [SettingsSection] and the navigation to the Child Mode screen
 * (each host has its own NavHost).
 */
@Composable
fun SecuritySettingsItems(
    walletViewModel: WalletViewModel,
) {
    val biometricSeedPhraseEnabled by walletViewModel.biometricSeedPhraseEnabled.collectAsState()
    val biometricAccountLoginEnabled by walletViewModel.biometricAccountLoginEnabled.collectAsState()
    val biometricSpendingKeyEnabled by walletViewModel.biometricSpendingKeyEnabled.collectAsState()
    val securityContext = LocalContext.current
    // A biometric lock that can be switched off without passing the lock is not a lock - anyone
    // holding an unlocked phone could have turned all three off and then read the seed phrase.
    // So the switch itself is gated, in BOTH directions: turning one on is a claim about who is
    // holding the device too. The setting is only written after auth succeeds, so a cancelled
    // prompt leaves the switch exactly where it was rather than flipping and flipping back.
    fun gated(reason: String, apply: (Boolean) -> Unit): (Boolean) -> Unit = { enabled ->
        securityContext.authenticateWithDeviceCredential(
            title = reason,
            onSuccess = { apply(enabled) },
        )
    }
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_seed_phrase),
        biometricSeedPhraseEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_seed_phrase_lock)) {
            walletViewModel.setBiometricSeedPhraseEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_account_login),
        biometricAccountLoginEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_account_login_lock)) {
            walletViewModel.setBiometricAccountLoginEnabled(it)
        },
    )
    SettingsDivider()
    SettingsSwitchItem(
        stringResource(R.string.biometrics_for_address_private_keys),
        biometricSpendingKeyEnabled,
        onCheckedChange = gated(stringResource(R.string.unlock_change_private_key_lock)) {
            walletViewModel.setBiometricSpendingKeyEnabled(it)
        },
    )
}

/**
 * Settings > Diagnostics as iOS's DiagnosticsSettingsPage: the export button (its explanation in
 * the row, under the title) in a "Diagnostics" section, then Verbose API Logging in a section of
 * its own with its footer. The export gathers app/device/settings/node-pool info and is
 * account-tolerant (see DiagnosticsExportService's no-active-account fallbacks), so the same
 * sections render in the in-account Settings and in the accounts screen's App Settings.
 */
@Composable
fun DiagnosticsSettingsSections(
    chatViewModel: ChatViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
) {
    val diagnosticsExportState by chatViewModel.diagnosticsExportState.collectAsState()
    val verboseApiLogging by connectionViewModel.verboseApiLogging.collectAsState()
    val context = LocalContext.current
    val diagnosticsExportInFlight = diagnosticsExportState.status == ChatViewModel.ChatHistoryOpStatus.IN_PROGRESS
    // iOS reports a failed export as a toast; only a run started here is reported.
    var exportArmed by remember { mutableStateOf(false) }
    LaunchedEffect(diagnosticsExportState.status) {
        when (diagnosticsExportState.status) {
            ChatViewModel.ChatHistoryOpStatus.IN_PROGRESS -> exportArmed = true
            ChatViewModel.ChatHistoryOpStatus.FAILED -> if (exportArmed) {
                exportArmed = false
                IosToasts.error(diagnosticsExportState.message ?: "Failed to export diagnostics.")
            }
            else -> exportArmed = false
        }
    }

    SettingsFormSection(title = stringResource(R.string.diagnostics)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !diagnosticsExportInFlight) {
                    chatViewModel.exportDiagnostics { uri ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Diagnostics Archive"))
                    }
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // iOS: ladybug.fill in title3 and the title in body medium, both accent, 10 apart,
            // the spinner at the end while it runs; then the explanation in footnote.
            val tint = if (diagnosticsExportInFlight) KaspaTeal.copy(alpha = 0.5f) else KaspaTeal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.export_diagnostics_archive),
                    color = tint,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (diagnosticsExportInFlight) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
            }
            Text(
                stringResource(R.string.exports_app_device_info_connection_settings),
                color = LocalAppColors.current.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
    // Moved here from Connection Settings, as on iOS: it is a troubleshooting switch.
    SettingsFormSection(title = null, footer = stringResource(R.string.verbose_api_logging_footer)) {
        SettingsSwitchItem(stringResource(R.string.verbose_api_logging), verboseApiLogging) {
            connectionViewModel.setVerboseApiLogging(it)
        }
    }
}

/**
 * Kaspa-logo total-balance row — Android port of iOS's BalanceToolbarLabel
 * (SettingsView.swift): 15dp Kaspa logo + full 8-decimal balance in bold with
 * tabular figures and secondary color, so the header balance reads identically
 * on every main page (Chats/Settings via TopStatusBar; Broadcasts, Cold Storage,
 * Swap, Portfolio and KaPosts via BalanceTopBarLabel below).
 */
@Composable
fun BalanceLabelRow(
    balance: String,
    modifier: Modifier = Modifier,
    /** Chats, Profile and Settings: a tap copies the exact figure (iOS's balance toolbar label,
     *  "Balance copied to clipboard."). */
    copyOnTap: Boolean = false,
) {
    val balanceClipboard = LocalClipboardManager.current
    val balanceHaptic = com.kachat.app.util.rememberHaptics()
    Row(
        modifier = modifier.then(
            if (copyOnTap) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                balanceClipboard.setText(AnnotatedString(balance.substringBefore(' ')))
                balanceHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                IosToasts.show("Balance copied to clipboard.")
            } else Modifier
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_kaspa_logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = balance,
            color = LocalAppColors.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum"
            )
        )
    }
}

/**
 * Self-contained variant for screens that don't already collect the wallet balance:
 * observes WalletViewModel.fullBalance and refreshes it once on appearance (matches
 * the iOS label's `.task { refreshBalance() }`).
 */
@Composable
fun BalanceTopBarLabel(
    modifier: Modifier = Modifier,
    walletViewModel: WalletViewModel = hiltViewModel()
) {
    val balance by walletViewModel.fullBalance.collectAsState()
    LaunchedEffect(Unit) { walletViewModel.refreshBalance() }
    BalanceLabelRow(balance, modifier)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val network by viewModel.network.collectAsState()
    // The indexer this launch is connected to (a Testnet switch waits for the next launch).
    val indexerUrl by viewModel.runningIndexerUrl.collectAsState()

    val activeNodes by viewModel.activeNodes.collectAsState()
    val allNodes by viewModel.allNodes.collectAsState()
    // Pool sections only make sense when the POOL is choosing the node. Pinned to a specific
    // node (the shipped default or the user's own), there is no pool at work - discovery is
    // off and the app only ever talks to that one address - so pool status, pool actions, and
    // the node lists would just show a pool of one. Null (not yet loaded) hides them too, so
    // a pinned user never sees them flash on screen entry.
    val nodeSelectionIsAutomatic by viewModel.nodeSelectionIsAutomatic.collectAsState()
    val showPoolSections = nodeSelectionIsAutomatic == true
    // "Other Nodes" means genuinely other than what's already listed above under Active
    // Nodes — allNodes includes every known node (active and not), so without this filter
    // the same active nodes showed up twice, once in each section.
    val otherNodes = remember(allNodes) { allNodes.filterNot { it.status == "Active" } }
    val status by viewModel.status.collectAsState()
    val dotColorHex by viewModel.dotColorHex.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()
    val nodeConnectionsBlocked by viewModel.nodeConnectionsBlocked.collectAsState()
    val scrollState = rememberScrollState()
    val isRefreshingPool by viewModel.isRefreshingPool.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val poolActionsEnabled = !isRefreshingPool && !isReconnecting
    var showClearPoolConfirm by remember { mutableStateOf(false) }
    val connectionClipboard = LocalClipboardManager.current
    val connectionHaptic = com.kachat.app.util.rememberHaptics()
    val connectionContext = LocalContext.current
    // iOS: tapping an active node copies its host:port.
    val copyNodeEndpoint: (NodeInfo) -> Unit = { node ->
        connectionClipboard.setText(AnnotatedString(node.ip))
        connectionHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
        IosToasts.show(connectionContext.getString(R.string.node_endpoint_copied))
    }

    // status is derived from the exact same latency threshold as dotColorHex, so
    // the text here can never contradict the dot's color: green only says
    // Connected/Healthy, orange only says Degraded, red only says Disconnected/Unhealthy.
    val statusColor = Color(dotColorHex)
    val statusText = when (status) {
        ConnStatus.CONNECTED -> "Connected"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Disconnected"
    }
    val poolHealthText = when (status) {
        ConnStatus.CONNECTED -> "Healthy"
        ConnStatus.DEGRADED -> "Degraded"
        ConnStatus.DISCONNECTED -> "Unhealthy"
    }
    // "Verified" = currently reachable at all (Active or Suspect), a broader real count
    // than "Active" (Active additionally requires being in-sync and not recently failing).
    // Excludes nodes that haven't been probed even once yet (latency == "—") — those also
    // report as "Suspect" (see NodeRegistry.statusOf's lastProbe == null branch), but counting
    // them as "Verified" is misleading: right after a fresh launch/DNS-seed resolution this made
    // the whole pool look "Verified" before a single probe had actually completed.
    val verifiedCount = remember(allNodes) { allNodes.count { (it.status == "Active" || it.status == "Suspect") && it.latency != "—" } }

    if (showClearPoolConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showClearPoolConfirm = false },
            title = { Text(stringResource(R.string.clear_connection_pool_confirm_title), color = LocalAppColors.current.textPrimary) },
            text = { Text(stringResource(R.string.clear_connection_pool_confirm_message), color = LocalAppColors.current.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    showClearPoolConfirm = false
                    viewModel.clearPool()
                }) { Text(stringResource(R.string.clear), color = LocalAppColors.current.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPoolConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // iOS presents Connection Status as a sheet holding a Form (ConnectionStatusDetailView): the
    // grouped sheet palette, rounded top 10 below the status bar, the screen behind dimmed, an
    // inline bar with Done on glass.
    val primaryHost = activeNodes.firstOrNull()?.ip?.let { ip ->
        com.kachat.app.services.grpc.parseNodeAddress(ip)?.host ?: ip.substringAfter("://")
    }
    // iOS writes "138 ms" and colours it by speed: green under 100, the primary colour under
    // 200, orange under 500, red above.
    val latencyMs = activeNodes.firstOrNull()?.latency?.removeSuffix("ms")?.trim()?.toIntOrNull()
    val latencyColor = when {
        latencyMs == null -> LocalAppColors.current.textSecondary
        latencyMs < 100 -> LocalAppColors.current.success
        latencyMs < 200 -> LocalAppColors.current.textPrimary
        latencyMs < 500 -> LocalAppColors.current.warning
        else -> LocalAppColors.current.danger
    }
    // iOS shows the indexer's host alone (URLComponents.host): no scheme, port or path.
    val indexerHost = indexerUrl.substringAfter("://").substringBefore("/").substringBefore(":")

    IosSheetPage(grouped = true) {
        SheetNavBar(
            title = stringResource(R.string.connection_status),
            trailing = { SheetBarButton(stringResource(R.string.done), onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            SettingsSection(title = stringResource(R.string.connection_status)) {
                ConnectionStatusRow(stringResource(R.string.status), statusText, dot = statusColor)
                SettingsDivider()
                ConnectionStatusRow(
                    stringResource(R.string.protocol),
                    if (activeNodes.firstOrNull()?.ip?.let { com.kachat.app.services.grpc.parseNodeAddress(it)?.secure } == true) {
                        "gRPC (secure)"
                    } else {
                        "gRPC (plaintext)"
                    }
                )
                // Honest all-blocked explanation: only shown once the pool has concluded that
                // NO node answers at all on this network (see NodePoolManager's
                // nodeConnectionsBlocked doc) - a plain red "Disconnected" with no reason
                // otherwise reads as an app bug on firewalled/captive networks. Laid out as
                // iOS's "Node Network: Blocked" row with its caption under it.
                if (nodeConnectionsBlocked && status == ConnStatus.DISCONNECTED) {
                    SettingsDivider()
                    ConnectionStatusRow("Node Network", "Blocked", valueColor = LocalAppColors.current.warning)
                    Text(
                        stringResource(R.string.node_connections_blocked),
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }
                // iOS leaves the node and latency rows out until there is one.
                if (primaryHost != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.connected_node), primaryHost, monospaced = true)
                }
                if (latencyMs != null) {
                    SettingsDivider()
                    ConnectionStatusRow(stringResource(R.string.latency), "$latencyMs ms", valueColor = latencyColor)
                }
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.indexer), indexerHost, monospaced = true)
                SettingsDivider()
                ConnectionStatusRow(stringResource(R.string.last_sync), lastSyncAt)
            }

            if (showPoolSections) {
                SettingsSection(title = stringResource(R.string.pool_status)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        PoolStatItem(stringResource(R.string.active), activeNodes.size.toString(), LocalAppColors.current.success)
                        PoolStatItem(stringResource(R.string.verified), verifiedCount.toString(), Color(0xFF2196F3))
                        PoolStatItem(stringResource(R.string.total), allNodes.size.toString(), LocalAppColors.current.textSecondary)
                    }
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.pool_health), color = LocalAppColors.current.textPrimary)
                        Text(poolHealthText, color = statusColor)
                    }
                }
            }

            // iOS's Actions section: plain text buttons, a spinner in the row while it runs, no
            // toast; Clear asks first. The primary node's host is the section's footer.
            Column {
                SettingsSection(title = stringResource(R.string.actions)) {
                    // Refresh/Clear operate on pool discovery, which is off while pinned to a
                    // specific node - only Reconnect still means something there.
                    if (showPoolSections) {
                        ConnectionActionRow(
                            stringResource(R.string.refresh_pool),
                            color = KaspaTeal,
                            busy = isRefreshingPool,
                            enabled = poolActionsEnabled,
                        ) { viewModel.refreshPool() }
                        SettingsDivider()
                        ConnectionActionRow(
                            stringResource(R.string.clear_connection_pool),
                            color = LocalAppColors.current.danger,
                            busy = false,
                            enabled = poolActionsEnabled,
                        ) { showClearPoolConfirm = true }
                        SettingsDivider()
                    }
                    ConnectionActionRow(
                        stringResource(R.string.reconnect),
                        color = KaspaTeal,
                        busy = isReconnecting,
                        enabled = poolActionsEnabled,
                    ) { viewModel.reconnect() }
                }
                primaryHost?.let { primary ->
                    Text(
                        text = "Primary: $primary",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                    )
                }
            }

            KaspaNodeQuickAccessSection(viewModel)

            if (showPoolSections) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(LocalAppColors.current.success, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.active_nodes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = activeNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    activeNodes.forEachIndexed { index, node ->
                        Box(Modifier.clickable { copyNodeEndpoint(node) }) { ActiveNodeRow(node) }
                        if (index < activeNodes.size - 1) SettingsDivider()
                    }
                }
                Text(
                    text = stringResource(R.string.tap_a_node_to_copy_host_port),
                    fontSize = 13.sp,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(LocalAppColors.current.textSecondary, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.other_nodes), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textSecondary)
                    }
                    Text(text = otherNodes.size.toString(), color = LocalAppColors.current.textSecondary)
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalAppColors.current.surface)
                ) {
                    otherNodes.forEachIndexed { index, node ->
                        AllNodeRow(node)
                        if (index < otherNodes.size - 1) SettingsDivider()
                    }
                }

                Text(
                    text = stringResource(R.string.all_discovered_nodes_sorted_by_state),
                    fontSize = 13.sp,
                    color = LocalAppColors.current.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }


            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

/** A label/value row of iOS ConnectionStatusDetailView's first section: the label 17pt, the value
 *  trailing in the secondary colour (or [valueColor]) - a host name in monospaced caption, as iOS
 *  writes the node and indexer - and, for Status, a 10pt dot in [dot]'s colour before it. */
@Composable
private fun ConnectionStatusRow(
    label: String,
    value: String,
    valueColor: Color = LocalAppColors.current.textSecondary,
    monospaced: Boolean = false,
    dot: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LocalAppColors.current.textPrimary, fontSize = 17.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        if (dot != null) {
            Box(modifier = Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            value,
            color = valueColor,
            fontSize = if (monospaced) 12.sp else 17.sp,
            fontFamily = if (monospaced) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of iOS ConnectionStatusDetailView's Actions section: the label as a plain text button,
 *  and while [busy], the activity indicator at the trailing edge. Greyed out while disabled. */
@Composable
private fun ConnectionActionRow(label: String, color: Color, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) color else LocalAppColors.current.textSecondary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (busy) com.kachat.app.ui.theme.IosActivityIndicator(Modifier.size(20.dp))
    }
}

/**
 * Settings > Notifications > Chats, iOS's NotificationsSettingsView: Sound & Vibration with its
 * footer, then the Per Contact note. The master switch (and the permission it needs) is on the
 * Notifications page above this one; the push diagnostics are in the diagnostics export.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val soundEnabled by viewModel.notificationSoundEnabled.collectAsState()
    val vibrationEnabled by viewModel.notificationVibrationEnabled.collectAsState()

    // Settings > Notifications > Sound & Vibration (iOS KaPosts NotificationsSettingsView, which
    // was KaChat's Chats page): no per-contact modes, KaPosts has no contacts.
    SettingsFormPage(title = stringResource(R.string.sound_vibration), onBack = onBack) {
        SettingsFormSection(
            title = null,
            footer = "How a notification from KaPost sounds and feels when it arrives.",
        ) {
            SettingsSwitchItem(stringResource(R.string.play_sound), soundEnabled) {
                viewModel.setNotificationSoundEnabled(it)
            }
            SettingsDivider()
            SettingsSwitchItem(stringResource(R.string.vibration), vibrationEnabled) {
                viewModel.setNotificationVibrationEnabled(it)
            }
        }
    }
}

/**
 * Settings > Notifications > KaPosts: which KaPosts pings reach you (iOS KaPosts
 * KaPostsNotificationSettingsView). Mentions cannot be switched off at the push service, so the
 * server is told the other five through the push registration.
 */
@Composable
fun KaPostsNotificationSettingsScreen(onBack: () -> Unit, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val likes by settingsViewModel.kaPostsNotifyLikes.collectAsState()
    val reposts by settingsViewModel.kaPostsNotifyReposts.collectAsState()
    val follows by settingsViewModel.kaPostsNotifyFollows.collectAsState()
    val dislikes by settingsViewModel.kaPostsNotifyDislikes.collectAsState()
    val comments by settingsViewModel.kaPostsNotifyComments.collectAsState()
    val mentions by settingsViewModel.kaPostsNotifyMentions.collectAsState()
    SettingsFormPage(title = "KaPost", onBack = onBack) {
        SettingsFormSection(title = null) {
            SettingsSwitchItem("Likes", likes) { settingsViewModel.setKaPostsNotifyLikes(it) }
            SettingsDivider()
            SettingsSwitchItem("Reposts", reposts) { settingsViewModel.setKaPostsNotifyReposts(it) }
            SettingsDivider()
            SettingsSwitchItem("Follows", follows) { settingsViewModel.setKaPostsNotifyFollows(it) }
            SettingsDivider()
            SettingsSwitchItem("Dislikes", dislikes) { settingsViewModel.setKaPostsNotifyDislikes(it) }
            SettingsDivider()
            SettingsSwitchItem("Comments", comments) { settingsViewModel.setKaPostsNotifyComments(it) }
            SettingsDivider()
            SettingsSwitchItem("Mentions", mentions) { settingsViewModel.setKaPostsNotifyMentions(it) }
        }
    }
}

/** Settings > Chats > Quick Reactions - replace any of the 6 emojis shown in the double-tap
 *  [QuickReactionBar]. iOS's QuickReactionEmojisSettingsView: the six as 44 tiles in one card
 *  with the footer under it, then Reset to Default as a red button in its own card. A tap opens
 *  the emoji picker for that slot. */
@Composable
fun QuickReactionSettingsScreen(onBack: () -> Unit, settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val emojis by settingsViewModel.quickReactionEmojis.collectAsState()
    var editingSlotIndex by remember { mutableStateOf<Int?>(null) }

    SettingsFormPage(title = stringResource(R.string.quick_reactions), onBack = onBack) {
        SettingsFormSection(title = null, footer = stringResource(R.string.quick_reactions_footer)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            ) {
                emojis.forEachIndexed { index, emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalAppColors.current.surfaceVariant)
                            .clickable { editingSlotIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
        }
        SettingsFormSection(title = null) {
            Text(
                stringResource(R.string.reset_to_default),
                color = LocalAppColors.current.danger,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        settingsViewModel.setQuickReactionEmojis(com.kachat.app.repository.AppSettingsRepository.DEFAULT_QUICK_REACTION_EMOJIS)
                    }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }

    val slotIndex = editingSlotIndex
    if (slotIndex != null) {
        EmojiPickerDialog(
            onDismissRequest = { editingSlotIndex = null },
            onSelect = { emoji ->
                settingsViewModel.setQuickReactionEmojis(
                    emojis.toMutableList().also { it[slotIndex] = emoji }
                )
            }
        )
    }
}

/** Settings > Connection > Kaspa Explorer, iOS's KaspaExplorerSettingsView: the explorers under an
 *  "Explorer" header, a checkmark on the chosen one, and iOS's footer under the card. */
@Composable
fun KaspaExplorerSettingsScreen(onBack: () -> Unit, chatViewModel: ChatViewModel = hiltViewModel()) {
    val kaspaExplorer by chatViewModel.kaspaExplorer.collectAsState()

    SettingsFormPage(title = stringResource(R.string.kaspa_explorer), onBack = onBack) {
        SettingsFormSection(
            title = stringResource(R.string.explorer),
            footer = stringResource(R.string.explorer_footer),
        ) {
            com.kachat.app.models.KaspaExplorer.entries.forEachIndexed { index, explorer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { chatViewModel.updateKaspaExplorer(explorer) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(explorer.displayName, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (kaspaExplorer == explorer) {
                        Icon(Icons.Default.Check, null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
                    }
                }
                if (index < com.kachat.app.models.KaspaExplorer.entries.lastIndex) {
                    SettingsDivider()
                }
            }
        }
    }
}

/**
 * A single dropdown for quickly picking which node to connect to: the default node (recommended),
 * automatic discovery, or any address already saved in the IP Address Book. Used by both
 * [ConnectionStatusScreen] (tap the status dot) and [ConnectionSettingsScreen] (above the
 * [AddressBookSection], which is where the address book itself is managed - adding, removing, and
 * labeling entries) - there's no free-text entry anymore, only this dropdown.
 */
@Composable
private fun KaspaNodeQuickAccessSection(viewModel: ConnectionViewModel) {
    val trustedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val normalizedTrusted = trustedNodeAddress.trim()
    val defaultAddress = com.kachat.app.repository.AppSettingsRepository.DEFAULT_TRUSTED_NODE_ADDRESS
    val matchingSaved = savedNodeAddresses.firstOrNull { it.address.trim() == normalizedTrusted }

    val selectedLabel = when {
        normalizedTrusted.isEmpty() -> stringResource(R.string.automatic_scan)
        normalizedTrusted == defaultAddress.trim() -> stringResource(R.string.default_recommended)
        matchingSaved != null -> matchingSaved.label.ifBlank { matchingSaved.address }
        else -> normalizedTrusted
    }

    // iOS: a Picker row in a Form - the "Kaspa Node" label, the current choice trailing in the
    // accent colour with the menu's up/down chevrons - and the explanation as the section's
    // footer, under the card rather than in it.
    Column {
    SettingsSection(title = stringResource(R.string.kaspa_node)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.kaspa_node),
                color = LocalAppColors.current.textPrimary,
                fontSize = 17.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            Spacer(Modifier.weight(1f))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedLabel,
                        color = KaspaTeal,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.UnfoldMore, null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    // The shipped default node is a mainnet node - not offered on testnet (iOS bdae4b7).
                    val selectedNetwork by com.kachat.app.util.KaspaNetwork.selected.collectAsState()
                    if (selectedNetwork == com.kachat.app.util.KaspaNetwork.Type.MAINNET || normalizedTrusted == defaultAddress.trim()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.default_recommended)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress(defaultAddress)
                        }
                    )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.automatic_scan)) },
                        onClick = {
                            expanded = false
                            viewModel.setTrustedNodeAddress("")
                        }
                    )
                    savedNodeAddresses.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.label.ifBlank { entry.address }) },
                            onClick = {
                                expanded = false
                                viewModel.setTrustedNodeAddress(entry.address)
                            }
                        )
                    }
                }
            }
        }
        if (normalizedTrusted.isNotEmpty()) {
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.connected_only_to_this_node), color = KaspaTeal, fontSize = 13.sp)
            }
        }
    }
    Text(
        stringResource(R.string.kaspa_node_quick_access_footer),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = LocalAppColors.current.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
    )
    }
}

/**
 * "IP Address Book" - the full management UI (add/remove/label saved addresses), used only by
 * [ConnectionSettingsScreen], right below its own [KaspaNodeQuickAccessSection]. The trusted-node
 * override itself is now dropdown-only (default/automatic/a saved address) - no free-text entry -
 * so this section is just the address book, feeding entries into that dropdown.
 */
@Composable
private fun AddressBookSection(viewModel: ConnectionViewModel) {
    val savedNodeAddresses by viewModel.savedNodeAddresses.collectAsState()
    val pinnedNodeAddress by viewModel.trustedNodeAddress.collectAsState()
    val nodeBookHaptic = com.kachat.app.util.rememberHaptics()

    SettingsSection(title = stringResource(R.string.ip_address_book)) {
        var newLabel by remember { mutableStateOf("") }
        var newAddress by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf<String?>(null) }
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current

        TextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            placeholder = { Text(stringResource(R.string.label_optional), color = LocalAppColors.current.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(50.dp).clip(RoundedCornerShape(12.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LocalAppColors.current.surfaceVariant,
                unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                focusedTextColor = LocalAppColors.current.textPrimary,
                unfocusedTextColor = LocalAppColors.current.textPrimary,
                cursorColor = KaspaTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newAddress,
                onValueChange = { newAddress = it; addError = null },
                placeholder = { Text(stringResource(R.string.host_port_or_grpcs_host), color = LocalAppColors.current.textTertiary) },
                modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LocalAppColors.current.surfaceVariant,
                    unfocusedContainerColor = LocalAppColors.current.surfaceVariant,
                    focusedTextColor = LocalAppColors.current.textPrimary,
                    unfocusedTextColor = LocalAppColors.current.textPrimary,
                    cursorColor = KaspaTeal,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = {
                    val trimmed = newAddress.trim()
                    if (trimmed.isEmpty()) return@IconButton
                    if (com.kachat.app.services.grpc.parseNodeAddress(trimmed) == null) {
                        addError = "Enter as host:port or grpcs://host"
                    } else {
                        addError = null
                        viewModel.addSavedNodeAddress(newLabel.trim(), trimmed)
                        newLabel = ""
                        newAddress = ""
                    }
                },
                modifier = Modifier.size(40.dp).background(KaspaTeal, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        }
        addError?.let {
            Text(it, color = LocalAppColors.current.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (savedNodeAddresses.isEmpty()) {
            Text(
                stringResource(R.string.no_saved_addresses),
                color = LocalAppColors.current.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            savedNodeAddresses.forEachIndexed { index, entry ->
                if (index > 0) SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(entry.address))
                            nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                            IosToasts.show("Node address copied.")
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (entry.label.isNotBlank()) {
                            Text(entry.label, color = LocalAppColors.current.textPrimary)
                            Text(
                                entry.address,
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(entry.address, color = LocalAppColors.current.textPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                    // Immediate delete (matching this screen's convention: row taps copy with a
                    // toast, no confirm dialogs anywhere), so a toast confirms the removal.
                    // Deleting the entry the Kaspa Node dropdown currently pins does NOT touch
                    // node selection: only the book entry goes, the pinned address stays set
                    // and the dropdown falls back to showing the raw address (see
                    // KaspaNodeQuickAccessSection's selectedLabel).
                    IconButton(onClick = {
                        viewModel.removeSavedNodeAddress(entry.id)
                        nodeBookHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        // iOS deleteSavedNodeAddress: which of the two cases it was.
                        IosToasts.show(
                            context.getString(
                                if (pinnedNodeAddress.trim().isNotEmpty() && pinnedNodeAddress.trim() == entry.address.trim()) R.string.node_removed_still_connected
                                else R.string.node_address_removed
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, null, tint = LocalAppColors.current.textSecondary)
                    }
                }
            }
        }
        SettingsFooter(stringResource(R.string.save_your_own_node_addresses_here))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConnectionSettingsScreen(onBack: () -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val network by viewModel.network.collectAsState()
    val indexerUrl by viewModel.indexerUrl.collectAsState()
    val knsApiUrl by viewModel.knsApiUrl.collectAsState()
    val kaspaRestApiUrl by viewModel.kaspaRestApiUrl.collectAsState()
    val kapostIndexerUrl by viewModel.kapostIndexerUrl.collectAsState()
    val translationServiceUrl by viewModel.translationServiceUrl.collectAsState()
    var editingTranslationUrl by remember { mutableStateOf(false) }
    var editingKapostIndexerUrl by remember { mutableStateOf(false) }
    var editingNamesIndexerUrl by remember { mutableStateOf(false) }
    val broadcastIndexerUrl by viewModel.broadcastIndexerUrl.collectAsState()
    val pushIndexerUrl by viewModel.pushIndexerUrl.collectAsState()
    val onMainnetSettings = network.equals("mainnet", ignoreCase = true)
    // The field hint: the network's default. Testnet has no indexer (iOS KaPosts ae0b025).
    val testnetIndexerPlaceholder = if (onMainnetSettings) null else stringResource(R.string.no_testnet_indexer_yet)

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            SettingsSubpageBar(
                title = stringResource(R.string.connection_settings),
                onBack = onBack,
                trailing = { SheetBarButton(stringResource(R.string.save), onClick = onBack) },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // One tab per kind of connection, so the page stays short as the app talks to more
            // services: indexers, the Kaspa node, translation, name services, the block explorer
            // (iOS ee01f81). Every field keeps its value while you switch between them.
            val connectionTabs = listOf(
                stringResource(R.string.connection_tab_indexer),
                stringResource(R.string.connection_tab_node),
                stringResource(R.string.connection_tab_translation),
                stringResource(R.string.connection_tab_domains),
                stringResource(R.string.connection_tab_explorer),
            )
            var selectedConnectionTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
            // Swipe sideways between the tabs, or tap one (iOS KaPosts 4c0d5a8, the feeds' pager).
            // Save covers every tab: the fields keep their edits while you switch between them.
            val connectionPager = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = selectedConnectionTab,
                pageCount = { connectionTabs.size },
            )
            LaunchedEffect(connectionPager) {
                androidx.compose.runtime.snapshotFlow { connectionPager.settledPage }.collect { selectedConnectionTab = it }
            }
            LaunchedEffect(selectedConnectionTab) {
                if (connectionPager.currentPage != selectedConnectionTab) connectionPager.animateScrollToPage(selectedConnectionTab)
            }
            UnderlineTabBar(
                titles = connectionTabs,
                selectedIndex = selectedConnectionTab,
                onSelect = { selectedConnectionTab = it },
            )
        androidx.compose.foundation.pager.HorizontalPager(
            state = connectionPager,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Each section spaces itself, its footer under the card (SettingsFormSection).
            Spacer(Modifier.height(16.dp))
            when (page) {
            0 -> {
            // KaPost, Public Chats and Push. KaChat's message indexer is not here: KaPost has no 1:1
            // chats, and the same address (indexerUrl) is the .kachat names indexer, on the Domains
            // tab (iOS KaPosts 4c0d5a8).
            SettingsFormSection(title = stringResource(R.string.kapost_indexer), footer = stringResource(R.string.kapost_indexer_footer)) {
                // Editable, as on iOS: someone running their own K indexer points the app at it.
                ConnectionUrlField(
                    label = "KaPost Indexer URL",
                    value = kapostIndexerUrl,
                    onClick = { editingKapostIndexerUrl = true },
                    placeholder = testnetIndexerPlaceholder,
                )
            }

            SettingsFormSection(title = stringResource(R.string.broadcast_indexer), footer = stringResource(R.string.broadcast_indexer_footer)) {
                ConnectionUrlField(label = "Public Chats Indexer URL", value = broadcastIndexerUrl, placeholder = testnetIndexerPlaceholder)
            }

            SettingsFormSection(title = "Push Registration", footer = "Used only for push registration and updates") {
                ConnectionUrlField(label = "Push Indexer URL", value = pushIndexerUrl, placeholder = testnetIndexerPlaceholder)
            }

            // Verbose API Logging lives in Settings > Diagnostics, as on iOS.
            }
            1 -> {
            KaspaNodeQuickAccessSection(viewModel)
            Spacer(Modifier.height(24.dp))
            AddressBookSection(viewModel)
            }
            2 -> {
            SettingsFormSection(title = "Translation Service", footer = "Translates KaPost posts written in another language. Runs on the KaPost indexer's " +
                        "box by default; point this at your own if you host one (see " +
                        "TRANSLATION_SERVICE.md). Tap to change.") {
                // Editable, unlike the read-only fields around it: the point of this setting is
                // that someone can run their own translator and point the app at it.
                ConnectionUrlField(
                    label = "Translation Service URL",
                    value = translationServiceUrl,
                    onClick = { editingTranslationUrl = true }
                )
                // Same words as iOS's footer; TRANSLATION_SERVICE.md lives in the iOS repo.
            }
            }
            3 -> {
            // .kachat first: it is what every address field resolves before anything else (iOS 6ac48a7).
            SettingsFormSection(
                title = stringResource(R.string.kn_kachat_names),
                footer = if (com.kachat.app.services.NameServicesClient.OTHER_SERVICES_ENABLED) stringResource(R.string.kachat_names_resolved_first_footer)
                    else "The only names KaPost looks up, everywhere you can type an address. Run your own names " +
                        "indexer and put its address here, or leave it blank for the default. When the indexer " +
                        "doesn't answer or falls behind, names are read straight from the chain.",
            ) {
                val isMainnet = network.equals("mainnet", ignoreCase = true)
                // indexerUrl: KachatNamesRegistry reads names through it while it answers for this
                // network's registry and keeps up, else straight from the chain (iOS 5df42b4,
                // ef6b21e, KaPosts 4c0d5a8).
                ConnectionUrlField(
                    label = ".kachat Names Indexer URL",
                    value = indexerUrl,
                    onClick = { editingNamesIndexerUrl = true },
                    placeholder = testnetIndexerPlaceholder,
                )
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Registry", color = LocalAppColors.current.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(if (isMainnet) "Mainnet" else "Testnet (testnet-10)", color = LocalAppColors.current.textSecondary, fontSize = 17.sp)
                }
            }
            // The other name services KaChat reads (see NameServicesClient) - none in KaPosts,
            // which looks up .kachat names only (iOS KaPosts 865c9ab). Shown, not editable; the
            // .kas URL follows the selected network.
            if (com.kachat.app.services.NameServicesClient.OTHER_SERVICES_ENABLED) SettingsFormSection(title = stringResource(R.string.other_domains_title), footer = stringResource(R.string.other_domains_settings_footer)) {
                val isMainnet = network.equals("mainnet", ignoreCase = true)
                ConnectionUrlField(label = ".kas", value = knsApiUrl)
                listOf(com.kachat.app.services.NameServiceTLD.K, com.kachat.app.services.NameServiceTLD.KASPA).forEach { tld ->
                    ConnectionUrlField(
                        label = tld.suffix,
                        value = tld.apiBaseUrl(isMainnet) ?: stringResource(R.string.not_available_on_this_network),
                    )
                }
            }
            }
            else -> {
            SettingsFormSection(title = stringResource(R.string.kaspa_explorer_api), footer = stringResource(R.string.rest_api_for_transaction_history_and)) {
                ConnectionUrlField(label = "Kaspa REST API URL", value = kaspaRestApiUrl)
            }
            }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
        }
        }
    }

    if (editingNamesIndexerUrl) {
        var draft by remember { mutableStateOf(indexerUrl) }
        var rejected by remember { mutableStateOf(false) }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { editingNamesIndexerUrl = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(".kachat Names Indexer URL", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.kachat.app.ui.theme.IosTextField(
                        value = draft,
                        onValueChange = { draft = it; rejected = false },
                        singleLine = true,
                        isError = rejected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (rejected) "The URL must start with https://"
                        else if (onMainnetSettings) "Leave blank to use ${com.kachat.app.repository.AppSettingsRepository.DEFAULT_INDEXER_URL}"
                        else stringResource(R.string.no_testnet_indexer_yet),
                        color = if (rejected) Color(0xFFE57373) else LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = draft.trim().trimEnd('/')
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("https://", ignoreCase = true)) {
                        rejected = true
                    } else {
                        // Blank is the network's default (AppSettingsRepository.resolveIndexerUrl).
                        viewModel.setIndexerUrl(trimmed)
                        editingNamesIndexerUrl = false
                    }
                }) { Text("Save", color = KaspaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { editingNamesIndexerUrl = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    if (editingKapostIndexerUrl) {
        var draft by remember { mutableStateOf(kapostIndexerUrl) }
        var rejected by remember { mutableStateOf(false) }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { editingKapostIndexerUrl = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text("KaPost Indexer URL", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.kachat.app.ui.theme.IosTextField(
                        value = draft,
                        onValueChange = { draft = it; rejected = false },
                        singleLine = true,
                        isError = rejected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (rejected) "The URL must start with https://"
                        // The network's default: none on testnet.
                        else if (onMainnetSettings) "Leave blank to use ${com.kachat.app.repository.AppSettingsRepository.DEFAULT_KAPOST_INDEXER_URL}"
                        else stringResource(R.string.no_testnet_indexer_yet),
                        color = if (rejected) Color(0xFFE57373) else LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.setKapostIndexerUrl(draft)) editingKapostIndexerUrl = false else rejected = true
                }) { Text("Save", color = KaspaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { editingKapostIndexerUrl = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    if (editingTranslationUrl) {
        var draft by remember { mutableStateOf(translationServiceUrl) }
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { editingTranslationUrl = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text("Translation Service URL", color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    com.kachat.app.ui.theme.IosTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Leave blank to use ${com.kachat.app.repository.AppSettingsRepository.DEFAULT_TRANSLATION_SERVICE_URL}",
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTranslationServiceUrl(draft)
                    editingTranslationUrl = false
                }) { Text("Save", color = KaspaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { editingTranslationUrl = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }
}

/** Read-only for now — editing these let a mistyped URL crash the whole app (fixed at the NetworkService layer too, but not editable at all is safer). */
@Composable
fun ConnectionUrlField(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    /** Shown, dimmed, while [value] is blank (iOS's field placeholder). */
    placeholder: String? = null,
) {
    Column(
        modifier = Modifier
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp)
    ) {
        Text(label, color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        // iOS shows an address in the body font, monospaced.
        val showsPlaceholder = value.isBlank() && placeholder != null
        Text(
            text = if (showsPlaceholder) placeholder.orEmpty() else value,
            color = if (showsPlaceholder) LocalAppColors.current.textSecondary else LocalAppColors.current.textPrimary,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}


@Composable
fun PoolStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, color = LocalAppColors.current.textPrimary, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActiveNodeRow(node: NodeInfo) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // weight(1f) here (not on the status badge) so a long address — real discovered
            // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111,
            // much longer than the old hardcoded IPv4 seeds — elides instead of squeezing the
            // badge down to near-zero width, which forced its own text to wrap letter by letter.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(node.ip, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0xFF1E3A1E), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row {
                Text(node.type, color = Color(0xFF2196F3), fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
            }
            Text("DAA: ${node.daaScore}", color = LocalAppColors.current.textSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun AllNodeRow(node: NodeInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // weight(1f) here (not on the latency/status side) so a long address — real discovered
        // peers can be IPv6 literals like [2601:680:cc80:5630:e1e5:e6fa:c86b:b946]:16111, much
        // longer than the old hardcoded IPv4 seeds — elides instead of squeezing the status
        // badge down to near-zero width, which forced its own text to wrap letter by letter.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(8.dp).background(Color(node.color), CircleShape))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Shield, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(node.ip, color = LocalAppColors.current.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(node.latency, color = LocalAppColors.current.warning, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.background(Color(0x33FF3B30), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(node.status, color = Color(node.color), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun rememberQrBitmapPainter(
    content: String,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }
    
    val bitmap = remember(content) {
        if (content.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding)
            )
            val w = matrix.width
            val h = matrix.height
            // One setPixels() beats width*height individual bounds-checked setPixel() calls
            // (~262k for a 512px QR) by 1-2 orders of magnitude - this runs on the main thread
            // inside remember{}, and the animated (KSPT) variant re-encodes every 2.5s.
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}

/**
 * Byte-mode QR encoding for raw binary payloads (e.g. a KSPT transaction frame) — ISO-8859-1 maps
 * every byte 1:1 to a char and back, so wrapping [bytes] this way and forcing ZXing's
 * `CHARACTER_SET` hint round-trips the exact bytes through its String-based encoder in byte mode,
 * matching KasSigner's own raw-byte QR encoding (`qrcode::QrCode::new(&[u8])`) on the other end.
 */
@Composable
fun rememberQrBitmapPainter(
    bytes: ByteArray,
    size: Int = 512,
    padding: Int = 0
): BitmapPainter {
    val density = LocalDensity.current
    val sizePx = with(density) { size.dp.roundToPx() }

    val bitmap = remember(bytes) {
        if (bytes.isEmpty()) {
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
        } else {
            val content = String(bytes, Charsets.ISO_8859_1)
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                mapOf(EncodeHintType.MARGIN to padding, EncodeHintType.CHARACTER_SET to "ISO-8859-1")
            )
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        }
    }
    return BitmapPainter(bitmap)
}


/** How an [AddressQrPage] is closed - iOS puts a different control in its bar for each way it
 *  presents the page. */
enum class QrPageDismiss {
    /** Pushed (Profile): a back chevron, leading. */
    BACK,
    /** In a sheet with Close leading (Chatting Address details). */
    CLOSE,
    /** In a sheet with Done trailing (the Chats New sheet). */
    DONE,
}

/**
 * iOS's `ChattingAddressQRView`: the full white page with an address's QR code - the code in a
 * 3pt accent frame, the whole address under it, what the address is for, and "Tap anywhere to
 * copy" - placed a third of the way down. Its bar shows the address's balance in the title's
 * place (iOS's principal toolbar item, which takes precedence over the navigation title), or "—"
 * while it is unknown, with [dismiss]'s control. A tap anywhere copies the address.
 */
@Composable
fun AddressQrPage(
    address: String,
    balanceSompi: Long?,
    caption: String,
    dismiss: QrPageDismiss,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = com.kachat.app.util.rememberHaptics()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                clipboardManager.setText(AnnotatedString(address))
                haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                com.kachat.app.util.showAddressCopiedToast(context, address, haptic = false)
            },
    ) {
        AddressQrPageBar(dismiss = dismiss, onDismiss = onDismiss) {
            Text(
                balanceSompi?.let { "%.8f %s".format(java.util.Locale.US, it / 100_000_000.0, KaspaUnit.symbol) } ?: "—",
                // iOS's `.primary`, as iOS draws it: the page forces no colour scheme, so in dark
                // mode the balance is the theme's white over the white page, as on the iPhone.
                color = LocalAppColors.current.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    // iOS strokes 3pt on the edge and clips it to the shape: 1.5 shows.
                    .border(1.5.dp, KaspaTeal, RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Image(
                    painter = rememberQrBitmapPainter(address),
                    contentDescription = stringResource(R.string.qr_code),
                    modifier = Modifier.size(240.dp),
                )
            }
            Text(
                address,
                color = Color.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
            Text(
                caption,
                color = Color.Black.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Text(
                stringResource(R.string.tap_anywhere_to_copy),
                color = Color.Black.copy(alpha = 0.4f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The page's navigation bar: [dismiss]'s control and [center] in the title's place. */
@Composable
private fun AddressQrPageBar(dismiss: QrPageDismiss, onDismiss: () -> Unit, center: @Composable () -> Unit) {
    // A sheet's bar is 56 tall on iOS, a pushed page's 44.
    // iOS 26: each control on its own glass, 16 in from the edge.
    Box(Modifier.fillMaxWidth().height(if (dismiss == QrPageDismiss.BACK) 52.dp else 60.dp).padding(horizontal = 16.dp)) {
        // iOS's glass takes on what is under it: over the white page the control is light glass
        // with a black chevron, in dark mode too. (The balance keeps the theme's colour, below.)
        CompositionLocalProvider(LocalAppColors provides com.kachat.app.ui.theme.LightAppColors) {
            when (dismiss) {
                QrPageDismiss.BACK -> SheetBarBackButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.CLOSE -> SheetBarButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart))
                QrPageDismiss.DONE -> SheetBarButton(stringResource(R.string.done), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        Box(Modifier.align(Alignment.Center)) { center() }
    }
}

/**
 * The chatting address's QR page ([AddressQrPage] with the chatting caption, iOS
 * `ChattingAddressQRView`'s default).
 */
@Composable
fun ChattingAddressQrPage(address: String, balanceSompi: Long?, dismiss: QrPageDismiss, onDismiss: () -> Unit) {
    AddressQrPage(
        address = address,
        balanceSompi = balanceSompi,
        caption = stringResource(R.string.qr_chatting_address_subtitle),
        dismiss = dismiss,
        onDismiss = onDismiss,
    )
}


/**
 * A QR page presented the way iOS presents it from a sheet: a full-height sheet of its own over
 * the screen (the Chats New sheet's two, Chatting Address details' code). White while the code
 * shows, so the page reaches the sheet's edges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPageSheet(onDismiss: () -> Unit, white: Boolean, content: @Composable () -> Unit) {
    com.kachat.app.ui.theme.IosSheetColors(grouped = true) {
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (white) Color.White else LocalAppColors.current.background,
            dragHandle = null,
        ) {
            // The sheet runs down behind the navigation bar, as iOS's does behind the home indicator;
            // its content stays above it.
            Column(Modifier.navigationBarsPadding()) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    IosToastHost(bottomInsets = SheetToastInsets)
                }
            }
        }
    }
}

/** True while a white page shows over a tab - the dock draws its glass light then. */
object DockOverLightPage {
    var active by mutableStateOf(false)
}






/** One row in the group-member address list - supports both a raw Kaspa address and a KNS domain, resolved the same way the single-contact flow's address field does. */
/**
 * "Other domains": what the same typed name points to on the other name services, under the name
 * it resolved to, each one selectable (iOS a0dbc15, 6ac48a7 `OtherDomainsDropdown`). Every field
 * that takes an address shows it; the order is always .kachat first. Unregistered, unreachable or
 * not-yet-live ones are listed but not selectable. Opens by itself when nothing was picked but
 * another service has the name.
 */
@Composable
fun OtherDomainsDropdown(
    resolutions: List<com.kachat.app.services.NameResolution>,
    selected: com.kachat.app.services.NameServiceTLD?,
    onPick: (com.kachat.app.services.NameResolution) -> Unit,
) {
    // KaPosts reads .kachat names only, so there are no other services to offer.
    if (!com.kachat.app.services.NameServicesClient.OTHER_SERVICES_ENABLED) return
    val others = resolutions.filter { it.tld != selected }
    if (others.isEmpty()) return
    val openByItself = selected == null && resolutions.any { it.address != null }
    var expanded by remember(resolutions) { mutableStateOf(openByItself) }
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.other_domains), color = KaspaTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            others.forEach { resolution ->
                val address = resolution.address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = address != null) {
                            onPick(resolution)
                            expanded = false
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            resolution.display,
                            color = if (address == null) colors.textSecondary else colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MiddleEllipsisText(
                            address ?: stringResource(
                                when {
                                    resolution.notLive -> R.string.coming_soon
                                    resolution.failed -> R.string.couldnt_check
                                    else -> R.string.not_registered
                                }
                            ),
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (address != null) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}














/**
 * iOS `chatTitleChip` (1:1) and `groupTitleChip`: a 46pt avatar drawn over the top of a capsule
 * holding the name (subheadline bold) and a caption2 bold chevron - VStack(spacing: -12), the
 * capsule padded 12 either side, 15 above and 5 below, on an ultra-thin material with a 0.5
 * hairline of primary at 12% and a soft shadow. The avatar's top sits level with the bar's back
 * disc, 2 lower ([ChatNavBar] puts that disc 4 below the status bar).
 */
@Composable
internal fun ChatHeaderChipLayout(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val pillShape = RoundedCornerShape(percent = 50)
    // Wraps its content - NOT fillMaxWidth. Filling the width put a full-width tap target over
    // the whole bar row, so the back button and the trailing buttons sat underneath it and
    // tapping either one opened Chat Info instead.
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            // The capsule tucks 12 under the avatar - offset by the avatar less that overlap,
            // then padded back out at the top so the name still clears it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(top = 34.dp)
                    .iosShadow(100.dp, Color.Black.copy(alpha = 0.10f), 6.dp, 2.dp)
                    .clip(pillShape)
                    // .ultraThinMaterial as it reads over the thread (sampled on the iPhone:
                    // #202020 on black); a solid stand-in, as Android cannot blur what lies under.
                    .background(if (colors.isDark) Color(0xFF202020) else com.kachat.app.ui.theme.IosRegularMaterialLight)
                    .border(0.5.dp, colors.textPrimary.copy(alpha = 0.12f), pillShape)
                    .padding(start = 12.dp, end = 12.dp, top = 15.dp, bottom = 5.dp),
            ) {
                Text(
                    name,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // SF chevron.right at caption2 bold: about 6 x 10.
                ChatChevronGlyph(height = 10.dp, stroke = 1.8.dp)
            }
            avatar()
        }
    }
}

/** SF Symbols' chevron.right drawn as a thin open chevron of the given glyph [height], in
 *  secondary grey - the Material arrow carries padding iOS's glyph does not. */
@Composable
internal fun ChatChevronGlyph(height: Dp, stroke: Dp) {
    val color = LocalAppColors.current.textSecondary
    androidx.compose.foundation.Canvas(modifier = Modifier.size(width = height * 0.58f, height = height)) {
        val inset = stroke.toPx() / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(inset, inset)
            lineTo(size.width - inset, size.height / 2f)
            lineTo(inset, size.height - inset)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}




/** The composer's text: body, 17 on a 22 line. */
@Composable
internal fun chatComposerTextStyle(): androidx.compose.ui.text.TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = LocalAppColors.current.textPrimary)

/** The composer's text bubble: iOS glassBackground(cornerRadius: 20). */
@Composable
internal fun Modifier.chatComposerFieldModifier(): Modifier = this.iosGlass(cornerRadius = 20.dp)




