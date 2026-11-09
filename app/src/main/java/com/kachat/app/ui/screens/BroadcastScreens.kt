package com.kachat.app.ui.screens

import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import com.kachat.app.util.UserFacingError
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.iosGlass
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Drafts
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.kachat.app.R
import com.kachat.app.models.BroadcastRetention
import com.kachat.app.models.addressDisplayName
import com.kachat.app.models.FeaturedBroadcastChannels
import com.kachat.app.repository.ChatRepository
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.ChatTimeFormat
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.MessageReply
import com.kachat.app.util.TextLinkify
import com.kachat.app.util.VoiceMessage
import com.kachat.app.viewmodels.BroadcastViewModel
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Broadcast room share links (`kachat://broadcast/<channel>`,
 * `https://kachat.app/broadcast/<channel>`) land here — from an inbound system intent
 * (MainActivity) or from a tap on an in-app invite card ([openKaChatLink]). MainShell watches
 * [pendingChannel] and navigates; the room screen itself consumes [consumeJoinRequest] so a room
 * the user isn't in yet is created/joined before it opens, landing in their own channel list.
 *
 * The Child Mode check lives with the navigation in MainShell, exactly like the KaPosts link and
 * broadcast-notification paths, so a link can never route into a hidden feature.
 */
object BroadcastDeepLink {
    private val _pendingChannel = MutableStateFlow<String?>(null)
    val pendingChannel: StateFlow<String?> = _pendingChannel.asStateFlow()

    /** Channels a link asked for that aren't curated — joined on open so they stick around. */
    private val joinRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Gates an untrusted channel name through [KaChatLink.sanitizeChannelName] (which is
     * [MessageProtocol.normalizeChannelName] + [MessageProtocol.isValidChannelName] plus the
     * route/rendering safety rules). Returns false — and does nothing at all — for a malformed or
     * hostile name, rather than joining it.
     */
    fun request(rawName: String): Boolean {
        val name = KaChatLink.sanitizeChannelName(rawName) ?: return false
        // Curated rooms are permanent fixtures that always exist, so they just open.
        if (name !in FeaturedBroadcastChannels.CURATED_NAMES) joinRequests.add(name)
        _pendingChannel.value = name
        return true
    }

    fun consumePending() {
        _pendingChannel.value = null
    }

    fun consumeJoinRequest(channelName: String): Boolean = joinRequests.remove(channelName)

    fun clearJoinRequests() {
        joinRequests.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BroadcastListScreen(
    navController: NavController,
    onBack: () -> Unit,
    /** True when this is the Chats screen's Public Chats tab rather than a destination of its
     *  own: the Chats header is already up, and back belongs to the Chats screen. */
    embeddedInChats: Boolean = false,
    broadcastViewModel: BroadcastViewModel = hiltViewModel(),
    /** Select mode, owned by the Chats screen when embedded there (its header and bottom bar act
     *  on the picked rooms, iOS e08c4cc): a tap picks a room instead of opening it. Standing alone
     *  (KaPost's Public Chats tab), the list has its own Select, Select All and bottom bar (iOS
     *  KaPosts eec2e30). */
    isSelectionMode: Boolean = false,
    selectedRooms: Set<String> = emptySet(),
    onToggleRoom: (String) -> Unit = {},
) {
    // The back arrow is gone from the header (iOS has none), so system back carries what it
    // did: from the Kaspa Hub this returns to the grid rather than leaving the Hub entirely.
    if (!embeddedInChats) BackHandler(onBack = onBack)

    val channels by broadcastViewModel.joinedChannels.collectAsState()
    val joinState by broadcastViewModel.joinChannelState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    /** The customize sheet: a switch for every default room. */
    var showCustomize by remember { mutableStateOf(false) }
    var channelInput by remember { mutableStateOf("") }
    var channelToLeave by remember { mutableStateOf<String?>(null) }
    /** The room whose long-press sheet is up. */
    var roomActionTarget by remember { mutableStateOf<String?>(null) }
    val roomClipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listed by broadcastViewModel.listedChannels.collectAsState()
    // Rooms pinned up out of the list into the circles row above it, saved per account (iOS
    // 9653c04). A room no longer listed finds nothing.
    val pinContext = LocalContext.current
    val pinAddress = remember { runCatching { broadcastViewModel.myAddress() }.getOrNull() }
    var roomPins by remember(pinAddress) {
        mutableStateOf(pinAddress?.let { KaPostRoomPins.load(pinContext, it) }.orEmpty())
    }
    val pinnedChannels = roomPins.mapNotNull { name -> listed.firstOrNull { it.channelName == name } }
    val unpinnedChannels = listed.filter { it.channelName !in roomPins }
    fun toggleRoomPin(name: String) {
        val pinned = name in roomPins
        roomPins = if (pinned) roomPins - name else listOf(name) + roomPins
        pinAddress?.let { KaPostRoomPins.save(pinContext, it, roomPins) }
        IosToasts.show(if (pinned) "Unpinned" else "Pinned to the top row", durationMs = IosToastDuration.LONG)
    }
    /** A default room is never really deleted - it is switched off, exactly like its toggle in
     *  Public Chats settings, and that toggle brings it back. Nothing is lost, so it needs no
     *  confirmation. A room you added asks first. */
    fun deleteRoom(name: String) {
        if (name in com.kachat.app.models.FeaturedBroadcastChannels.CURATED_NAMES) {
            broadcastViewModel.removeFromList(name)
            IosToasts.show(pinContext.getString(R.string.default_room_switched_off, name), durationMs = IosToastDuration.LONG)
        } else {
            channelToLeave = name
        }
    }
    fun toggleRoomNotify(name: String, notifyOn: Boolean) {
        broadcastViewModel.setNotifyEnabled(name, !notifyOn)
        // iOS toggleNotify's words.
        IosToasts.show(
            when {
                notifyOn -> "Notifications are off for this public chat"
                com.kachat.app.models.FeaturedBroadcastChannels.isIndexerTracked(name) -> "You'll get notifications for new messages in this public chat, even when the app is closed"
                else -> "You'll get a notification for new messages in this public chat as long as your app remains open"
            },
            durationMs = IosToastDuration.LONG,
        )
    }

    // Standing alone, Select mode is this list's own: Select / Cancel and Select All in the
    // header, and a bar acting on every picked room (iOS KaPosts eec2e30).
    var ownSelecting by remember { mutableStateOf(false) }
    var ownSelection by remember { mutableStateOf(setOf<String>()) }
    /** Picked rooms waiting on the bulk Delete's confirmation. */
    var showBulkDelete by remember { mutableStateOf(false) }
    val selecting = if (embeddedInChats) isSelectionMode else ownSelecting
    val pickedRooms = if (embeddedInChats) selectedRooms else ownSelection
    fun setSelecting(on: Boolean) {
        ownSelecting = on
        if (!on) ownSelection = emptySet()
    }
    val toggleRoom: (String) -> Unit = { name ->
        if (embeddedInChats) onToggleRoom(name)
        else ownSelection = if (name in ownSelection) ownSelection - name else ownSelection + name
    }
    // Back while picking rooms leaves Select mode first.
    BackHandler(enabled = ownSelecting) { setSelecting(false) }

    /** Opens a room - only while this list is the screen in front, so a quick double tap on a
     *  row or a circle can't push the room twice. */
    fun openRoom(name: String) {
        if (navController.currentBackStackEntry?.lifecycle?.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) return
        navController.navigate("broadcast_channel/$name")
    }

    LaunchedEffect(joinState.status) {
        if (joinState.status == BroadcastViewModel.JoinChannelStatus.SUCCESS) {
            showJoinDialog = false
        }
    }

    // Don't leave the user stuck on a tab that just got hidden.

    val dockInset = LocalDockInset.current
    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under the dock, as iOS's list runs on under its tab bar; it pads its own end.
        contentWindowInsets = WindowInsets(0),
        // Its own floating button for joining or creating a room (iOS 6639a0b) - only standalone
        // (Kaspa Hub). Inside Chats, the Chats screen's + covers it ("New Public Chat", in its New
        // sheet - iOS 5da8ccf / be0857a). Not while selecting.
        floatingActionButton = {
            if (embeddedInChats || selecting) return@Scaffold
            com.kachat.app.ui.theme.IosGlassFab(
                // Above the dock the list runs under.
                modifier = Modifier.padding(bottom = dockInset),
                onClick = {
                    channelInput = ""
                    broadcastViewModel.resetJoinChannelState()
                    showJoinDialog = true
                },
                icon = Icons.Default.AddComment,
                contentDescription = "Join or create a public room",
            )
        },
        topBar = {
            // Embedded, the Chats header and its tab row are already above this.
            if (!embeddedInChats) {
                MainPageHeader(
                    title = stringResource(R.string.broadcasts),
                    // No back arrow, matching iOS: Public Chats is a browsing destination you
                    // arrive at, not something you were pushed into. System back still runs
                    // onBack (see the BackHandler above), which is how the Hub gets back to
                    // its grid. Top left, the customize button: which default rooms the list
                    // shows (iOS KaPosts 865c9ab).
                    leading = {
                        val haptic = com.kachat.app.util.rememberHaptics()
                        ToolbarGlassGroup {
                            if (ownSelecting) {
                                val allRooms = unpinnedChannels.map { it.channelName }.toSet()
                                val everything = allRooms.isNotEmpty() && ownSelection == allRooms
                                ToolbarTextButton(if (everything) "Deselect All" else "Select All", onClick = {
                                    ownSelection = if (ownSelection == allRooms) emptySet() else allRooms
                                })
                            } else {
                                ToolbarIconButton(
                                    Icons.Default.Tune,
                                    "Choose rooms",
                                    onClick = {
                                        haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                                        showCustomize = true
                                    },
                                    iconSize = 20.dp,
                                )
                            }
                        }
                    },
                    showsConnectionDot = !ownSelecting,
                    showsDivider = true,
                    // The backup strip, above the title (iOS e5a9b11).
                    banner = { AccountBackupBanner(onOpenBackup = { navController.navigate("account_backup") }) },
                    actions = {
                        val haptic = com.kachat.app.util.rememberHaptics()
                        ToolbarTextButton(if (ownSelecting) stringResource(R.string.cancel) else "Select", onClick = {
                            haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                            setSelecting(!ownSelecting)
                        })
                    },
                )
            }
        },
        // Select mode's actions, for every picked room at once - above the dock.
        bottomBar = {
            if (ownSelecting && !embeddedInChats) {
                val picked = listed.filter { it.channelName in ownSelection }
                PublicChatsBulkBar(
                    picked = picked,
                    onRead = {
                        picked.forEach { broadcastViewModel.markRoomRead(it.channelName) }
                        setSelecting(false)
                        IosToasts.show("Marked ${picked.size} as read", durationMs = IosToastDuration.LONG)
                    },
                    onUnread = {
                        picked.forEach { broadcastViewModel.markRoomUnread(it.channelName) }
                        setSelecting(false)
                        IosToasts.show("Marked ${picked.size} as unread", durationMs = IosToastDuration.LONG)
                    },
                    onMute = { muted ->
                        // all off: turn them back on; else turn every picked room off
                        picked.forEach { broadcastViewModel.setNotifyEnabled(it.channelName, muted) }
                        setSelecting(false)
                        IosToasts.show(
                            if (muted) "Notifications on for ${picked.size}" else "Notifications off for ${picked.size}",
                            durationMs = IosToastDuration.LONG,
                        )
                    },
                    onDelete = { showBulkDelete = true },
                    modifier = Modifier.padding(bottom = dockInset),
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Rooms laid out like the Chats and Group Chats lists (iOS 83286b3): the two curated
            // rooms pinned on top, then every other room - the language rooms and your own - by
            // latest activity. Every default room is listed from the start (iOS KaPosts 865c9ab);
            // any can be switched off with the customize button. Keys carry a ':' prefix, which a
            // channel name can never contain, so nothing can collide.
            val summaries by broadcastViewModel.roomSummaries.collectAsState()
            val senderKnsNames by broadcastViewModel.senderKnsNames.collectAsState()
            val contactAliases by broadcastViewModel.contactAliases.collectAsState()
            val myAddressForRows = remember { runCatching { broadcastViewModel.myAddress() }.getOrNull() }
            // the app's one rule (addressDisplayName): your name for them, else their .kachat
            // name on testnet (KNS elsewhere), else the short address (iOS e52357d)
            fun senderName(address: String): String =
                if (address == myAddressForRows) "You"
                else addressDisplayName(address, contactAliases[address], senderKnsNames[address])

            // The rooms arrive a moment after the list is laid out, and it could keep its place
            // further down - opening scrolled past #kaspa and #kachat-bugs. The first time there
            // are rooms, it starts at the top.
            val roomListState = androidx.compose.foundation.lazy.rememberLazyListState()
            var roomsArrived by remember { mutableStateOf(false) }
            LaunchedEffect(listed.isNotEmpty()) {
                if (listed.isNotEmpty() && !roomsArrived) {
                    roomsArrived = true
                    roomListState.scrollToItem(0)
                }
            }
            // One swiped row open at a time, closed when the list scrolls.
            var openSwipeRow by remember { mutableStateOf<Any?>(null) }
            LaunchedEffect(roomListState.isScrollInProgress) {
                if (roomListState.isScrollInProgress) openSwipeRow = null
            }
            LazyColumn(
                state = roomListState,
                modifier = Modifier.fillMaxSize(),
                // Room at the bottom so the last row never sits under the floating button, nor
                // the dock the list runs under.
                contentPadding = PaddingValues(bottom = if (ownSelecting) 8.dp else 96.dp + dockInset),
            ) {
                if (pinnedChannels.isNotEmpty()) item(key = "pinned-rooms") {
                    // Tap opens, hold opens the room's half sheet (with Unpin).
                    PinnedCirclesRow {
                        pinnedChannels.forEach { channel ->
                            PinnedCircle(
                                name = "#${channel.channelName}",
                                unreadCount = summaries[channel.channelName]?.unreadCount ?: 0,
                                showsPin = true,
                                isSelecting = selecting,
                                onTap = { openRoom(channel.channelName) },
                                onLongPress = { roomActionTarget = channel.channelName },
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(KaspaTeal.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("#", color = KaspaTeal, fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFamily = IosRoundedFontFamily)
                                }
                            }
                        }
                    }
                }
                items(unpinnedChannels, key = { "room:${it.channelName}" }) { channel ->
                    val summary = summaries[channel.channelName]
                    val last = summary?.lastMessage
                    LaunchedEffect(last?.senderAddress) {
                        last?.senderAddress?.let { broadcastViewModel.ensureSenderProfileFetched(it) }
                    }
                    // iOS's List separator, inset to the room name (16 + the 50 avatar + 12).
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                        color = LocalAppColors.current.divider,
                        thickness = 0.5.dp,
                    )
                    val roomColors = LocalAppColors.current
                    val roomHasUnread = (summary?.unreadCount ?: 0) > 0
                    // Swiped left: Delete, Mute and Pin, with no full swipe, so a long swipe can't
                    // start a delete. Swiped right: the read state (iOS 9653c04).
                    SwipeActionsRow(
                        key = channel.channelName,
                        openKey = openSwipeRow,
                        onOpenKeyChange = { openSwipeRow = it },
                        enabled = !selecting,
                        leadingActions = listOf(
                            if (roomHasUnread) {
                                SwipeAction(Icons.Outlined.MarkEmailRead, stringResource(R.string.mark_as_read), KaspaTeal) {
                                    broadcastViewModel.markRoomRead(channel.channelName)
                                }
                            } else {
                                SwipeAction(Icons.Outlined.MarkEmailUnread, stringResource(R.string.mark_as_unread), KaspaTeal) {
                                    broadcastViewModel.markRoomUnread(channel.channelName)
                                }
                            }
                        ),
                        trailingActions = listOf(
                            SwipeAction(Icons.Outlined.Delete, stringResource(R.string.delete), roomColors.danger) {
                                deleteRoom(channel.channelName)
                            },
                            SwipeAction(
                                if (channel.notifyEnabled) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                                if (channel.notifyEnabled) "Mute" else "Unmute",
                                roomColors.warning,
                            ) {
                                toggleRoomNotify(channel.channelName, channel.notifyEnabled)
                            },
                            SwipeAction(Icons.Outlined.PushPin, "Pin", KaspaTeal) {
                                toggleRoomPin(channel.channelName)
                            },
                        ),
                    ) {
                        PublicChatRow(
                            channelName = channel.channelName,
                            notifyOff = !channel.notifyEnabled,
                            preview = last?.let { "${senderName(it.senderAddress)}: ${publicRoomPreview(it.content, stringResource(R.string.kapost_link_shared))}" },
                            emptyText = "No messages yet",
                            timeText = last?.let { publicRoomTime(it.blockTimestamp) },
                            unread = summary?.unreadCount ?: 0,
                            // In Select mode a tap picks the room, as on the Chats and Group Chats pages.
                            onClick = {
                                if (selecting) toggleRoom(channel.channelName)
                                else openRoom(channel.channelName)
                            },
                            onLongClick = if (selecting) null else ({ roomActionTarget = channel.channelName }),
                            selected = if (selecting) channel.channelName in pickedRooms else null,
                        )
                    }
                }

                // In Select mode too, as on iOS (PublicChatListView's List keeps it).
                item(key = "footer:note") {
                    Text(
                        "Public rooms are open to everyone. #kaspa, #kachat-bugs and the language rooms keep 30 days of history.",
                        color = LocalAppColors.current.textSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    // The long-press sheet, the same shape as a group's: read state, notifications, the room
    // link, and - for rooms you added yourself - listening, retention and delete. The curated
    // rooms are permanent, so they offer no delete.
    // Square tiles, three to a row, like every long-press menu (iOS cdac6d0).
    roomActionTarget?.let { name ->
        val channel = channels.firstOrNull { it.channelName == name }
        val isCurated = name in com.kachat.app.models.FeaturedBroadcastChannels.CURATED_NAMES
        // the indexer keeps it, so push covers it with the app closed (iOS 21c480d)
        val trackedRoomsVersion by com.kachat.app.services.IndexerTrackedRooms.changes.collectAsState()
        val isTracked = remember(name, trackedRoomsVersion) { com.kachat.app.models.FeaturedBroadcastChannels.isIndexerTracked(name) }
        val notifyOn = channel?.notifyEnabled == true
        val summaries by broadcastViewModel.roomSummaries.collectAsState()
        val hasUnread = (summaries[name]?.unreadCount ?: 0) > 0
        // iOS PublicChatListView's toast: up for 3 s.
        fun say(text: String) = IosToasts.show(text, durationMs = IosToastDuration.LONG)
        // iOS sizes this sheet for five tiles (two rows).
        TileActionSheet(title = "#$name", onDismiss = { roomActionTarget = null }, height = ActionSheetTileMetrics.sheetHeight(tiles = 6)) {
                if (hasUnread) {
                    ActionSheetRow(icon = Icons.Default.Drafts, title = stringResource(R.string.mark_as_read), subtitle = "Clears the unread badge on this room.") {
                        roomActionTarget = null
                        broadcastViewModel.markRoomRead(name)
                    }
                } else {
                    ActionSheetRow(icon = Icons.Default.MarkEmailUnread, title = stringResource(R.string.mark_as_unread), subtitle = "Puts the unread badge back so you come across it again.") {
                        roomActionTarget = null
                        broadcastViewModel.markRoomUnread(name)
                    }
                }
                if (channel != null) {
                    ActionSheetRow(
                        icon = if (notifyOn) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        title = if (notifyOn) "Turn Off Notifications" else "Turn On Notifications",
                        subtitle = when {
                            notifyOn -> "No notification for new messages in this room."
                            isTracked -> "Notifies you of new messages, even when the app is closed."
                            else -> "Notifies you of new messages while the app is open."
                        },
                    ) {
                        roomActionTarget = null
                        broadcastViewModel.setNotifyEnabled(name, !notifyOn)
                        // iOS toggleNotify's words.
                        say(
                            when {
                                notifyOn -> "Notifications are off for this public chat"
                                isTracked -> "You'll get notifications for new messages in this public chat, even when the app is closed"
                                else -> "You'll get a notification for new messages in this public chat as long as your app remains open"
                            }
                        )
                    }
                }
                val isPinned = name in roomPins
                ActionSheetRow(
                    icon = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    title = if (isPinned) "Unpin" else "Pin to Top Row",
                    subtitle = if (isPinned) "Goes back to its place in the list."
                    else "Moves this room out of the list into the row of circles above it.",
                ) {
                    roomActionTarget = null
                    toggleRoomPin(name)
                }
                ActionSheetRow(icon = Icons.Default.Link, title = "Copy Room Link", subtitle = "A kachat.app link that opens this room.") {
                    roomActionTarget = null
                    roomClipboard.setText(androidx.compose.ui.text.AnnotatedString(KaChatLink.broadcastWebUrl(name)))
                    say("Room link copied")
                }
                // No listening or retention rows: the bell is the one control. A room with
                // notifications on is listened to while the app is open, which is what lets it notify
                // and count unread (iOS ac34790).
                if (channel != null && !isCurated) {
                    ActionSheetRow(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.delete),
                        subtitle = "Removes this room and its messages from this device.",
                        tint = LocalAppColors.current.danger,
                    ) {
                        roomActionTarget = null
                        channelToLeave = name
                    }
                } else if (isCurated) {
                    // A default room is never really deleted - it is switched off, exactly like its
                    // toggle in Public Chats settings, and that toggle brings it back. Nothing is
                    // lost, so it needs no confirmation (iOS e08c4cc).
                    val offText = stringResource(R.string.default_room_switched_off, name)
                    ActionSheetRow(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.delete),
                        subtitle = stringResource(R.string.default_room_delete_subtitle),
                        tint = LocalAppColors.current.danger,
                    ) {
                        roomActionTarget = null
                        broadcastViewModel.removeFromList(name)
                        say(offText)
                    }
                }
        }
    }

    // Bulk Delete asks first: rooms you added go with their messages; default rooms are only
    // switched off (iOS KaPosts eec2e30 `bulkDeleteSheet`, BroadcastViewModel.removeFromList).
    if (showBulkDelete) {
        val names = listed.filter { it.channelName in ownSelection }.map { it.channelName }
        ActionSheetContainer(
            title = "Delete ${names.size} Public Chat${if (names.size == 1) "" else "s"}?",
            subtitle = "Rooms you added are removed with their messages. Default rooms are only switched off - turn them back on any time in Public Chats settings.",
            onDismiss = { showBulkDelete = false },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.delete),
                subtitle = "Removes the picked rooms from this device.",
                tint = LocalAppColors.current.danger,
            ) {
                names.forEach { broadcastViewModel.removeFromList(it) }
                showBulkDelete = false
                setSelecting(false)
                IosToasts.show("Deleted ${names.size}", durationMs = IosToastDuration.LONG)
            }
            ActionSheetRow(icon = Icons.Default.Close, title = "Keep", subtitle = "Leave the rooms as they are.") {
                showBulkDelete = false
            }
        }
    }

    if (showCustomize) {
        PublicChatsSettingsSheet(onDismiss = { showCustomize = false }, broadcastViewModel = broadcastViewModel)
    }

    if (showJoinDialog) {
        // Joining and creating are the same action - there is no ownership protocol, so a name
        // nobody has used becomes a room the moment you post in it. Worth a sentence, which an
        // AlertDialog's cramped text slot never gave it room for.
        ActionSheetContainer(
            title = stringResource(R.string.join_or_create_a_channel),
            subtitle = stringResource(R.string.anyone_who_joins_the_same_channel),
            onDismiss = { showJoinDialog = false },
        ) {
            com.kachat.app.ui.theme.IosTextField(
                value = channelInput,
                onValueChange = { channelInput = it },
                placeholder = { Text(stringResource(R.string.channel_name), color = LocalAppColors.current.textTertiary) },
                prefix = { Text("#", color = LocalAppColors.current.textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalAppColors.current.textPrimary,
                    unfocusedTextColor = LocalAppColors.current.textPrimary,
                    focusedBorderColor = KaspaTeal,
                    unfocusedBorderColor = LocalAppColors.current.textSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showJoinDialog = false },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textPrimary)
                }
                // Closes first, then joins: a failure is the "Couldn't Join Channel" alert
                // below, which shows once the sheet is gone (iOS PublicChatListView.submitJoin).
                Button(
                    onClick = {
                        showJoinDialog = false
                        broadcastViewModel.joinChannel(channelInput)
                    },
                    enabled = channelInput.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.join), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // A join that failed: an alert, once the join sheet has closed (iOS "Couldn't Join Channel").
    if (!showJoinDialog && joinState.status == BroadcastViewModel.JoinChannelStatus.FAILED) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { broadcastViewModel.resetJoinChannelState() },
            containerColor = LocalAppColors.current.surface,
            title = { Text("Couldn't Join Channel", color = LocalAppColors.current.textPrimary) },
            text = { Text(joinState.message.orEmpty(), color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = { broadcastViewModel.resetJoinChannelState() }) {
                    Text("OK", color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    // Deleting a room asks on a sheet, like every other menu here (iOS eff5c09).
    channelToLeave?.let { channelName ->
        ActionSheetContainer(
            title = "Delete #$channelName?",
            subtitle = stringResource(R.string.leaving_this_broadcast_permanently_deletes_every),
            onDismiss = { channelToLeave = null },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.delete),
                subtitle = "Removes this room and its messages from this device.",
                tint = LocalAppColors.current.danger,
            ) {
                broadcastViewModel.leaveChannel(channelName)
                channelToLeave = null
            }
            ActionSheetRow(
                icon = Icons.Default.Close,
                title = "Keep",
                subtitle = "Leave the room as it is.",
            ) { channelToLeave = null }
        }
    }

}

/**
 * The customize sheet, behind the button at the top left of the Public Chats tab: every default
 * room with a switch. On means the room is in the list (switching it on joins it); off takes it
 * out and silences it. The Languages switch turns every language room off at once; while it is
 * on, each language has its own switch. Mirrors iOS KaPosts' PublicChatsSettingsView (d37f64d).
 */
@Composable
fun PublicChatsSettingsSheet(
    onDismiss: () -> Unit,
    broadcastViewModel: BroadcastViewModel = hiltViewModel(),
) {
    val hidden by broadcastViewModel.hiddenCuratedRooms.collectAsState()
    val joinedChannels by broadcastViewModel.joinedChannels.collectAsState()
    val joinedNames = remember(joinedChannels) { joinedChannels.map { it.channelName }.toSet() }
    // iOS presents it as a full-height sheet holding a Form (PublicChatsSettingsView): an inline
    // bar with the title and Done on glass, each section's rooms on an inset-grouped card with
    // inset separators, the headers in the iOS 26 Form style and the footer under its card.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        IosSheetPage(grouped = true) {
            val colors = LocalAppColors.current
            SheetNavBar(
                title = stringResource(R.string.broadcasts),
                trailing = { SheetBarButton(stringResource(R.string.done), onClick = onDismiss) },
            )

            @Composable
            fun roomSwitch(name: String, showDivider: Boolean) {
                val language = com.kachat.app.models.FeaturedBroadcastChannels.languageDisplayName(name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("#$name", color = colors.textPrimary, fontSize = 17.sp)
                        if (language != null) Text(language, color = colors.textSecondary, fontSize = 12.sp)
                    }
                    com.kachat.app.ui.theme.IosSwitch(
                        // On only when the room really is in the list: joined and not switched
                        // off. Every default room is joined, so in practice "not switched off".
                        checked = name !in hidden && name in joinedNames,
                        onCheckedChange = { shown -> broadcastViewModel.setCuratedRoomShown(name, shown) }
                    )
                }
                if (showDivider) SettingsDivider()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column {
                    SettingsSection(title = "Popular") {
                        val names = com.kachat.app.models.FeaturedBroadcastChannels.NAMES
                        names.forEachIndexed { i, name -> roomSwitch(name, showDivider = i < names.size - 1) }
                    }
                    Text(
                        "A room that is switched off no longer appears in Public Chats and never sends a notification. Switch it back on at any time.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    )
                }
                Column {
                    val names = com.kachat.app.models.FeaturedBroadcastChannels.LANGUAGE_NAMES
                    // On while at least one language room is.
                    val anyLanguageOn = names.any { it !in hidden && it in joinedNames }
                    SettingsSection(title = null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Languages", color = colors.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            com.kachat.app.ui.theme.IosSwitch(
                                checked = anyLanguageOn,
                                onCheckedChange = { on -> broadcastViewModel.setAllLanguageRoomsShown(on) },
                            )
                        }
                        if (anyLanguageOn) {
                            SettingsDivider()
                            names.forEachIndexed { i, name -> roomSwitch(name, showDivider = i < names.size - 1) }
                        }
                    }
                    Text(
                        "Off takes every language room out of Public Chats. On, switch off the ones you don't want.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

/**
 * One room, laid out like a chat in the Chats list: a # avatar, the room name, the newest message
 * with who sent it and when, a bell-off mark, and the unread badge. Mirrors iOS's PublicChatRow.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PublicChatRow(
    channelName: String,
    notifyOff: Boolean,
    preview: String?,
    emptyText: String,
    timeText: String?,
    unread: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    /** Non-null in Select mode: whether this room is picked. */
    selected: Boolean? = null,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // iOS: the row's 4 inside a List row's own insets, 88pt from one room to the next.
            .padding(horizontal = 16.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) KaspaTeal else LocalAppColors.current.textSecondary,
                modifier = Modifier.padding(end = 12.dp).size(24.dp)
            )
        }
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).background(KaspaTeal.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("#", color = KaspaTeal, fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = IosRoundedFontFamily)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#$channelName",
                    color = colors.textPrimary,
                    // iOS .headline
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (notifyOff) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.NotificationsOff, contentDescription = "Notifications off", tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                }
                }
                if (timeText != null) Text(timeText, color = colors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview ?: emptyText,
                    color = colors.textSecondary,
                    // iOS .subheadline
                    fontSize = 15.sp,
                    fontStyle = if (preview == null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    // iOS PublicChatRow's badge: the count in white caption2 bold on a teal
                    // capsule, 6 either side and 2 above and below, the number uncapped (only
                    // the tab badges say 99+).
                    Text(
                        unread.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(KaspaTeal, RoundedCornerShape(percent = 50))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** One line of a room's newest message: replies, voice notes and links read as what they are,
 *  never as their raw JSON or a tappable link (the same rules as the chat lists). */
private fun publicRoomPreview(raw: String, sharedKaPostText: String): String {
    // A reply reads as its own text (iOS `MessageReplyCodec.previewText`), then the rules below.
    val content = com.kachat.app.util.MessageReply.parseOrNull(raw)?.text ?: raw
    com.kachat.app.util.InlineMediaSniff.mimeType(content)?.let { mime ->
        return when {
            mime.startsWith("audio/") -> "🎤 Audio message"
            mime.startsWith("image/") -> "📷 Photo"
            mime.startsWith("video/") -> "🎬 Video"
            else -> "📎 File"
        }
    }
    return com.kachat.app.util.NextcloudShareSniff.linkSafePreview(content, sharedKaPostText)
}

/** A clock time today, "Yesterday", or the month and day, the way the chat lists read it. */
private fun publicRoomTime(ms: Long): String {
    val now = System.currentTimeMillis()
    return when {
        // The clock the message time lines use (iOS SharedFormatting.chatTime, both places).
        com.kachat.app.util.ChatTimeFormat.isSameDay(ms, now) -> com.kachat.app.util.ChatTimeFormat.formatMessageTime(ms)
        com.kachat.app.util.ChatTimeFormat.isSameDay(ms, now - 24L * 60 * 60 * 1000) -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BroadcastChannelScreen(
    channelName: String,
    onBack: () -> Unit,
    navController: NavController,
    broadcastViewModel: BroadcastViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    settingsViewModel: com.kachat.app.viewmodels.SettingsViewModel = hiltViewModel()
) {
    // A room the indexer tracks (the curated ones, or one added on the indexer): history comes
    // from the public chat indexer and retention is fixed at 30 days. Any other room reaches only
    // whoever is in it at the time, which the info button in the header says (iOS 21c480d).
    val trackedRoomsVersion by com.kachat.app.services.IndexerTrackedRooms.changes.collectAsState()
    val isIndexedRoom = remember(channelName, trackedRoomsVersion) {
        com.kachat.app.models.FeaturedBroadcastChannels.isIndexerTracked(channelName)
    }
    var showOwnRoomExplainer by remember { mutableStateOf(false) }
    /** The bell in the top-right corner: this room's notifications, on a sheet. */
    var showNotifySheet by remember { mutableStateOf(false) }
    val joinedRooms by broadcastViewModel.joinedChannels.collectAsState()
    val roomNotifyEnabled = joinedRooms.firstOrNull { it.channelName == channelName }?.notifyEnabled == true
    // The bell's sheet: what notifications do in this room, and the switch (iOS eff5c09).
    if (showNotifySheet) {
        ActionSheetContainer(
            title = "#$channelName",
            subtitle = if (roomNotifyEnabled) {
                if (isIndexedRoom) "You're notified of new messages here, even when the app is closed."
                else "You're notified of new messages here while the app is open."
            } else {
                "New messages here don't notify you. The room still updates when you open it."
            },
            onDismiss = { showNotifySheet = false },
        ) {
            ActionSheetRow(
                icon = if (roomNotifyEnabled) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                title = if (roomNotifyEnabled) "Turn Off Notifications" else "Turn On Notifications",
                subtitle = if (roomNotifyEnabled) {
                    "No notification for new messages in this room."
                } else if (isIndexedRoom) {
                    "Notifies you of new messages, even when the app is closed."
                } else {
                    "Notifies you of new messages while the app is open."
                },
            ) {
                broadcastViewModel.setNotifyEnabled(channelName, !roomNotifyEnabled)
                showNotifySheet = false
            }
        }
    }
    if (showOwnRoomExplainer) {
        ActionSheetContainer(
            title = "About this room",
            subtitle = null,
            onDismiss = { showOwnRoomExplainer = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "No one can see your messages in here unless they are also active in the room at the same time.",
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 15.sp,
                )
                Text(
                    "If you want messages to persist and be seen by anyone who joins, you need to run your own indexer and have users add it to the room.",
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 15.sp,
                )
            }
        }
    }

    val showFeeEstimate by settingsViewModel.showFeeEstimate.collectAsState()
    // Each flow built once per room: built inside composition, every recomposition (a sheet, a
    // reaction landing) would restart its query.
    val messageWindowFlow = remember(channelName) { broadcastViewModel.getMessageWindow(channelName) }
    // Null until the store's first answer: drawn as nothing, so opening a room doesn't flash
    // "No messages yet" before its messages arrive.
    val loadedWindow by messageWindowFlow
        .collectAsState(initial = null as com.kachat.app.repository.BroadcastRepository.RoomWindow?)
    val messageWindow = loadedWindow ?: com.kachat.app.repository.BroadcastRepository.RoomWindow(emptyList(), false)
    val messages = messageWindow.messages
    val loadingEarlierFlow = remember(channelName) { broadcastViewModel.isLoadingEarlier(channelName) }
    val isLoadingEarlier by loadingEarlierFlow.collectAsState(initial = false)
    // Reactions aggregated per message txId — same shape as GroupChatScreen's groupReactionsByTxId.
    val channelReactionsFlow = remember(channelName) { broadcastViewModel.getReactions(channelName) }
    val channelReactions by channelReactionsFlow.collectAsState(initial = emptyList())
    // Translate from the long-press menu (iOS 03e5128) - read once here, not per row.
    val translationStates by broadcastViewModel.translations.collectAsState()
    val translationOriginals by broadcastViewModel.showingOriginal.collectAsState()
    val translatableKeys by broadcastViewModel.translatable.collectAsState()
    LaunchedEffect(Unit) { broadcastViewModel.refreshTranslationLanguages() }
    val reactionsByTxId = remember(channelReactions) { channelReactions.groupBy { it.targetTxId } }
    val quickReactionEmojis by settingsViewModel.quickReactionEmojis.collectAsState()
    val myAddress by walletViewModel.address.collectAsState()
    // Zero-balance funding gate — same behavior as the 1:1/group chat threads (confirmed 0 KAS
    // only, on-entry refresh + 10s re-poll while gated); see ZeroBalanceFundingUi.kt.
    val fundingGate = rememberZeroBalanceFundingGate()
    val sendState by broadcastViewModel.sendBroadcastState.collectAsState()
    val voiceRecordingState by broadcastViewModel.voiceRecordingState.collectAsState()
    // The composer's text, kept as the State itself and read only in the text field (inside
    // ComposerTextScope), in lambdas that run on a tap, and through messageTextIsEmpty - so a
    // keystroke recomposes just the field, not the composer around it (iOS 0977a5b).
    val messageTextState = broadcastViewModel.messageText.collectAsState()
    val messageTextIsEmpty by remember(messageTextState) { derivedStateOf { messageTextState.value.isEmpty() } }
    // What was typed in this room last time comes back, as in 1:1 chats (iOS 360e5d2).
    DisposableEffect(channelName) {
        broadcastViewModel.openDraft(channelName)
        onDispose { broadcastViewModel.closeDraft(channelName) }
    }
    val estimatedFee by broadcastViewModel.estimatedFeeSompi.collectAsState()
    val isEstimatingFee by broadcastViewModel.isEstimatingFee.collectAsState()
    val senderProfiles by broadcastViewModel.senderProfiles.collectAsState()
    val senderKnsNames by broadcastViewModel.senderKnsNames.collectAsState()
    val contactAliases by broadcastViewModel.contactAliases.collectAsState()
    val replyingTo by broadcastViewModel.replyingTo.collectAsState()
    // The newest edit per message (see MessageEdit), applied as each bubble is drawn - the
    // envelope itself is never a message row.
    val editsFlow = remember(channelName) { broadcastViewModel.getEdits(channelName) }
    val editsByTxId by editsFlow.collectAsState(initial = emptyMap())
    // Edits that never left the phone, waiting for Retry: their messages read as before (iOS ee5ec98).
    val failedEdits by broadcastViewModel.failedEdits.collectAsState()
    val editingMessage by broadcastViewModel.editingMessage.collectAsState()
    val kaspaExplorer by broadcastViewModel.kaspaExplorer.collectAsState()
    val networkFeeRate by broadcastViewModel.networkFeeRate.collectAsState()
    val feeRateOverride by broadcastViewModel.feeRateOverride.collectAsState()
    var showFeeEditor by remember { mutableStateOf(false) }
    var feeEditorInput by remember { mutableStateOf("") }
    // The sender whose avatar was tapped. One sheet serves every row - the parent presents it
    // for whichever sender was tapped, rather than a menu attached to each avatar in the list.
    var senderSheetTarget by remember { mutableStateOf<SenderSheetTarget?>(null) }
    // A report that couldn't go to a mail app.
    var unsentReport by remember { mutableStateOf(false) }
    val reportContext = LocalContext.current
    /** Reporting ([ContentReport], iOS 9e22075): the message's id, room, sender and text. */
    fun reportRoomMessage(messageId: String, senderAddress: String, text: String) {
        val report = ContentReport(
            buildString {
                appendLine("I want to report this public chat message.")
                appendLine()
                appendLine("Room: #$channelName")
                appendLine("Message: $messageId")
                appendLine("Sender: $senderAddress")
                appendLine("Text: ${text.take(500)}")
                appendLine()
                appendLine("What is wrong with it:")
                appendLine()
            }
        )
        unsentReport = !report.send(reportContext)
    }
    /** Send Kaspa from a member's menu: the send screen with their address filled in. */
    var sendTarget by remember { mutableStateOf<String?>(null) }
    // Same trick as 1:1 chat's fee pill — recover the mass implied by whatever's currently being
    // composed (text vs. voice) by dividing the live fee preview back out by the rate that
    // produced it, instead of duplicating estimatedFeeSompi's own calculation here.
    val effectiveRate = feeRateOverride?.toDouble() ?: networkFeeRate
    val openFeeEditor: (Long) -> Unit = { currentFeeSompi ->
        feeEditorInput = "%.8f".format(java.util.Locale.US, currentFeeSompi / 100_000_000.0)
        showFeeEditor = true
        // The shown fee may lag the text by a pause (see TypingFeeGate): price the exact text
        // now, so the rate Save derives from the fee matches what is being sent.
        broadcastViewModel.refreshFeePreview()
    }
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    // "Load earlier messages" is the list's first item while there is more: every message sits
    // one further down the list than its index in [messages].
    val listOffset = if (messageWindow.hasMore) 1 else 0
    val jumpToReply: (String) -> Unit = { targetId ->
        val index = messages.indexOfFirst { it.id == targetId }
        if (index < 0) {
            // A room's toast: up for 3 s, as iOS PublicChatChannelView.showToast.
            IosToasts.show("Original message not available.", durationMs = IosToastDuration.LONG)
        } else {
            coroutineScope.launch {
                listState.animateScrollToItem(index + listOffset)
                highlightedMessageId = targetId
                delay(1200)
                if (highlightedMessageId == targetId) highlightedMessageId = null
            }
        }
    }

    val micContext = LocalContext.current
    // With a Nextcloud server linked, the mic asks on chain or via Nextcloud, mirroring the
    // 1:1/group "+" sheet (iOS 8b13460): via Nextcloud, the recording uploads to the server and
    // the room only carries the share link. The choice has to survive the permission prompt.
    val nextcloudAccount by broadcastViewModel.nextcloudAccount.collectAsState()
    var showVoiceRouteSheet by remember { mutableStateOf(false) }
    var voiceViaNextcloud by remember { mutableStateOf(false) }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) broadcastViewModel.startVoiceRecording(channelName, viaNextcloud = voiceViaNextcloud)
        // iOS PublicChatAudioRecorder's failure, shown as the room's toast.
        else IosToasts.show("Microphone access denied", durationMs = IosToastDuration.LONG)
    }
    val startVoiceRecordingIfPermitted = { viaNextcloud: Boolean ->
        voiceViaNextcloud = viaNextcloud
        if (broadcastViewModel.voiceRecordingSupported) {
            if (ContextCompat.checkSelfPermission(micContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                broadcastViewModel.startVoiceRecording(channelName, viaNextcloud = viaNextcloud)
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val startVoiceMessage = {
        if (nextcloudAccount != null) showVoiceRouteSheet = true else startVoiceRecordingIfPermitted(false)
    }

    // First fill JUMPS to the newest message instantly - featured rooms hold the whole 30-day
    // window, and the old unconditional animateScrollToItem crawled the full history on every
    // open. After that, follow new arrivals only when the reader is already at the bottom, so
    // indexer backfill and live inserts never yank someone reading history (same policy as 1:1
    // chats). Keyed on the NEWEST message, not the count: each backfill page of OLDER history
    // grew the count and ran this again, so a filling room animated to the bottom once per page
    // (iOS 9c9e3e0).
    var hasPositionedAtLatest by remember(channelName) { mutableStateOf(false) }
    // Older messages land above the one the reader was at the top of: put them back on it
    // (iOS `keepInPlaceAfterOlderLoad`), rather than at the start of the new batch. Declared here:
    // the scroll to the newest below stands aside while it is set.
    var keepInPlaceId by remember(channelName) { mutableStateOf<String?>(null) }
    LaunchedEffect(channelName, messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!hasPositionedAtLatest) {
            listState.scrollToItem(messages.size - 1 + listOffset)
            hasPositionedAtLatest = true
        } else {
            // Earlier messages are loading: a new one arriving meanwhile doesn't pull the reader
            // down (iOS PublicChatChannelView's `guard keepInPlaceAfterOlderLoad == nil`).
            if (keepInPlaceId != null) return@LaunchedEffect
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Someone reading further up stays where they are; your own message still brings
            // you down to it (iOS e5a9b11).
            val mine = myAddress != null && messages.last().senderAddress == myAddress
            if (mine || (lastVisible >= messages.size - 3 + listOffset && !listState.isScrollInProgress)) {
                listState.animateScrollToItem(messages.size - 1 + listOffset)
            }
        }
    }
    // History paging in above a freshly opened room: someone at the bottom stays there instead
    // of being left in the history (iOS 33095ac). Keyed on the OLDEST message, so only a page of
    // older history runs it; the newest message showing is what "at the bottom" means.
    LaunchedEffect(channelName, messages.firstOrNull()?.id) {
        if (messages.isEmpty() || !hasPositionedAtLatest || keepInPlaceId != null) return@LaunchedEffect
        val lastVisibleKey = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.key
        if (lastVisibleKey == messages.last().id && !listState.isScrollInProgress) {
            listState.scrollToItem(messages.size - 1 + listOffset)
        }
    }

    // Tapping into the composer scrolls to the newest message rather than letting the keyboard
    // rise over wherever you happened to be reading. You opened the composer to reply to the
    // room as it stands now.
    var composerFocused by remember { mutableStateOf(false) }
    LaunchedEffect(composerFocused) {
        if (composerFocused && messages.isNotEmpty()) {
            // The keyboard is still animating up and the list has not been resized yet, so
            // scrolling immediately lands short of the bottom.
            kotlinx.coroutines.delay(120)
            listState.animateScrollToItem(messages.size - 1 + listOffset)
        }
    }

    // Put back on the anchor once the widened window's first answer is in, then cleared whether
    // or not that window added an earlier message: a stale anchor would jump the list on some
    // later change (iOS clears it on the first change after the load). Keyed on the load ending
    // too, since a window that came back the same as before is not a new value.
    LaunchedEffect(loadedWindow, isLoadingEarlier) {
        val anchor = keepInPlaceId ?: return@LaunchedEffect
        // Still the old window on screen; the view model's flag is the up-to-date one.
        if (broadcastViewModel.isLoadingEarlierNow(channelName)) return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == anchor }
        if (index > 0) listState.scrollToItem(index + listOffset)
        keepInPlaceId = null
    }

    // Only show the "jump to latest" button once the last message isn't even partially
    // visible — a real, deliberate scroll away from the bottom to read history — not just a
    // transient viewport shrink. Tolerates a 1-item gap for the same reason as 1:1 chat's
    // equivalent check (see MessageBubble's screen in Screens.kt).
    // The newest list size as state: a plain `messages` read here would stay the first (empty)
    // window forever, and the button would never show.
    val latestLastIndex by rememberUpdatedState(messages.lastIndex + listOffset)
    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex < latestLastIndex - 1
        }
    }

    // Fee preview needs real UTXOs/fee-rate data, not just whatever was last fetched (which
    // could be empty/stale) — refresh once on entry, matching how 1:1 chats always have this
    // available rather than gating it behind a separate "payment mode" (broadcasts have none).
    LaunchedEffect(Unit) {
        broadcastViewModel.refreshUtxos()
    }

    // Live messages appear while this screen is open even if this channel isn't marked
    // always-listen — bounded to exactly as long as this composable is on screen. Every room
    // additionally backfills from its broadcast indexer (once + every 8s) so history sent while
    // the app was closed shows up too.
    DisposableEffect(channelName) {
        broadcastViewModel.startLiveViewing(channelName)
        broadcastViewModel.startIndexerBackfill(channelName)
        // Opening a room is reading it. Marked again on the way out, so what arrived while it
        // was open does not come back as unread on the list behind it.
        broadcastViewModel.markRoomRead(channelName)
        onDispose {
            broadcastViewModel.markRoomRead(channelName)
            broadcastViewModel.stopLiveViewing()
            broadcastViewModel.stopIndexerBackfill()
        }
    }
    // Voice-note playback is owned per bubble; the room going away is what ends it.
    DisposableEffect(Unit) { onDispose { VoicePlayback.stopAllAfterChildrenDispose() } }
    val roomDotColorHex by androidx.hilt.navigation.compose.hiltViewModel<com.kachat.app.viewmodels.ConnectionViewModel>().dotColorHex.collectAsState()

    // Opened from a share link for a room the user isn't in: create/join it first so it lands in
    // "Your Channels" instead of vanishing the moment they navigate away. Curated rooms never
    // register a join request (they always exist), so they just open. joinChannel is idempotent
    // (INSERT IGNORE) and re-validates the name itself, so a stale request can't do damage.
    LaunchedEffect(channelName) {
        if (BroadcastDeepLink.consumeJoinRequest(channelName)) {
            broadcastViewModel.joinChannel(channelName)
        }
    }

    LaunchedEffect(myAddress) {
        myAddress?.let { broadcastViewModel.ensureSenderProfileFetched(it) }
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            // iOS 26's room header (PublicChatChannelView): no bar of its own - Back and the
            // connection dot each on a glass disc leading, the bell on glass trailing, and the
            // room's "#" circle over a "#name >" capsule in the middle, all floating over the
            // messages, which scroll on underneath and fade out under the status bar. The list
            // takes this header's height as its top content padding, not as a clip.
            val headerTapBand = remember { ChatHeaderTapBandState() }
            val headerBackground = LocalAppColors.current.background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to headerBackground.copy(alpha = 0.95f),
                            0.55f to headerBackground.copy(alpha = 0.7f),
                            1f to Color.Transparent,
                        )
                    )
                    // Icon and name open Room Info - and so does the 24dp either side of them;
                    // the space further out jumps to the very first message (iOS 1d5a555). The
                    // back button, the connection dot and the bell keep their own taps.
                    .chatHeaderTapBand(
                        headerTapBand,
                        onChip = { navController.navigate("broadcast_room_info/$channelName") },
                        onBand = { coroutineScope.launch { listState.scrollToItem(0) } },
                    )
            ) {
                // The chat thread's bar (ChatNavBar): Back on the left, the room's connection dot
                // far right after the bell, like every KaPosts screen (iOS KaPosts 865c9ab).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetBarBackButton(onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    // A room you made yourself: what "public" does and does not mean here. Rooms
                    // the indexer tracks keep their history there, so they need no such warning
                    // (iOS ac34790, 21c480d). iOS 26 puts neighbouring trailing items - the
                    // bell and the connection dot - on one piece of glass.
                    ToolbarGlassGroup {
                        if (!isIndexedRoom) {
                            ToolbarIconButton(Icons.Outlined.Info, "About this room", onClick = { showOwnRoomExplainer = true })
                        }
                        // This room's notifications, one tap away - the same switch the Public
                        // Chats list offers in its room menu (iOS eff5c09).
                        ToolbarIconButton(
                            if (roomNotifyEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            if (roomNotifyEnabled) "Notifications on" else "Notifications off",
                            onClick = { showNotifySheet = true },
                        )
                        ConnectionDotButton(onClick = { ConnectionStatusOverlayState.open() }, ownGlass = false)
                    }
                }
                // iOS PublicChatRoomTitleChip: the 1:1/group header chip measure for measure, the
                // circle carrying the room glyph (accent at 20%, "number" 18 semibold) where they
                // carry a photo. The title itself is the way in to everything about the room.
                ChatHeaderChipLayout(
                    name = "#$channelName",
                    onClick = { navController.navigate("broadcast_room_info/$channelName") },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .chatHeaderChip(headerTapBand)
                        .statusBarsPadding(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(LocalAppColors.current.background)
                            .background(KaspaTeal.copy(alpha = 0.2f)),
                    ) {
                        Text("#", color = KaspaTeal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        bottomBar = {
            // iOS: the compose bar floats over the list as a bottom safe-area inset - the
            // messages run on underneath it - with the zero-balance card right above it (padded
            // 16 either side, 4 below), never over the room. Only the composer under the card
            // dims and goes inert (Modifier.zeroBalanceComposerGate in ZeroBalanceFundingUi.kt).
            //
            // With the dock showing, the messages run on under it too and the composer rests just
            // above it (LocalDockInset, which already holds the system bar under the dock); with
            // the keyboard up the dock is behind it, the inset is 0 and the composer sits on the
            // keyboard as before.
            //
            // One fixed modifier chain: the bottom padding and imePadding() never come or go.
            // Swapping navigationBarsPadding() in and out ahead of imePadding() when the keyboard
            // opened froze the app (an endless consumed-insets loop - an ANR seen while typing in
            // a room). The navigation bar needs nothing of its own: without the keyboard the dock
            // inset already holds it, and with the keyboard up the IME inset covers it.
            val composerBackground = LocalAppColors.current.background
            val dockInset = LocalDockInset.current
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to composerBackground.copy(alpha = 0.85f),
                        )
                    )
                    .padding(bottom = dockInset)
                    .imePadding()
                    .padding(bottom = 2.dp)
            ) {
            if (fundingGate.active) {
                ZeroBalanceFundingCard(
                    walletAddress = fundingGate.chattingAddress,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            // iOS composeBar: VStack(spacing: 8), padded 16 by 8.
            Column(
                modifier = Modifier
                    .zeroBalanceComposerGate(fundingGate.active)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A failed send, edit or voice note says so in a toast (iOS showToast), not a
                // line above the composer.
                // Editing and replying are mutually exclusive - the banner says which one is in
                // progress (iOS editBanner / replyBanner: caption lines on secondarySystemBackground
                // in a 12-rounded box padded 12 by 8, inset a further 16 and 8 below the top).
                editingMessage?.let { editing ->
                    RoomComposerBanner(
                        icon = Icons.Default.Edit,
                        title = "Editing message",
                        // iOS editBanner: previewText of the current text.
                        preview = BroadcastViewModel.replyPreviewText(
                            editsByTxId[editing.id]?.text
                                ?: com.kachat.app.util.MessageEdit.unwrappedText(editing.content)
                        ),
                        cancelDescription = "Cancel editing",
                        onCancel = {
                            broadcastViewModel.cancelEditing()
                            broadcastViewModel.setMessageText("")
                        },
                    )
                }
                if (editingMessage == null) replyingTo?.let { reply ->
                    RoomComposerBanner(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        title = "Replying to ${addressDisplayName(reply.senderAddress, contactAliases[reply.senderAddress], senderKnsNames[reply.senderAddress])}",
                        // The quote the reply will carry (iOS replyBanner: MessageReplyCodec.previewText).
                        preview = BroadcastViewModel.replyPreviewText(reply.content),
                        cancelDescription = stringResource(R.string.cancel_reply),
                        onCancel = { broadcastViewModel.cancelReply() },
                    )
                }
                if (voiceRecordingState.status == BroadcastViewModel.VoiceRecordingStatus.RECORDING) {
                    if (showFeeEstimate && estimatedFee != null) {
                        ComposerFeePill(
                            feeSompi = estimatedFee,
                            estimating = false,
                            onTap = { openFeeEditor(it) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    // iOS recordingBar: trash, a small red mic and "Recording..." on the field's
                    // glass, and the send glyph on its own 36 glass square, 12 apart.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .iosGlass(cornerRadius = 20.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cancel_recording),
                                tint = LocalAppColors.current.danger,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { broadcastViewModel.cancelVoiceRecording() },
                            )
                            Icon(Icons.Default.Mic, contentDescription = null, tint = LocalAppColors.current.danger, modifier = Modifier.size(14.dp))
                            Text(
                                // iOS recordingBar: whole seconds, "Recording... 3s".
                                text = "Recording... ${voiceRecordingState.elapsedMs / 1000}s",
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 17.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        RoomComposerSquareButton(
                            contentDescription = stringResource(R.string.send),
                            onClick = { broadcastViewModel.stopAndSendVoiceRecording(channelName) },
                        ) {
                            RoomSendGlyph(enabled = true)
                        }
                    }
                } else {
                    if (showFeeEstimate && !messageTextIsEmpty && (estimatedFee != null || isEstimatingFee)) {
                        // iOS feeBubble: the fee, or - while a re-price waits for its pause - the shimmering
                        // placeholder (TypingFeeGate).
                        ComposerFeePill(
                            feeSompi = estimatedFee,
                            estimating = isEstimatingFee,
                            onTap = { openFeeEditor(it) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                    // iOS: the field on glassBackground(cornerRadius: 20) - a capsule at one line -
                    // and the mic or send on its own 36 x 36 glassBackground(cornerRadius: 12),
                    // 12 apart.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // The one place the text itself is read while composing - see
                        // messageTextState.
                        ComposerTextScope {
                            val composerText = messageTextState.value
                            androidx.compose.foundation.text.BasicTextField(
                                value = composerText,
                                onValueChange = {
                                    // Someone's payment request pasted in opens Send Kaspa instead of
                                    // landing in the message (iOS c6851d6).
                                    if (!PaymentRequestPaste.interceptInsert(messageTextState.value, it)) {
                                        broadcastViewModel.setMessageText(it, fromTyping = true)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .chatComposerFieldModifier()
                                    .onFocusChanged { composerFocused = it.isFocused },
                                textStyle = chatComposerTextStyle(),
                                cursorBrush = SolidColor(KaspaTeal),
                                // Load-bearing, same value as the 1:1 composer. Without a cap this
                                // field grows a line per line of text; the Scaffold measures its
                                // bottom bar against the screen height, so past ~a screenful the
                                // Column stopped growing and the newest lines - the ones with the
                                // caret - overflowed past its bottom edge and under the keyboard.
                                // Capped, the field scrolls internally and the caret stays put.
                                maxLines = 4,
                                decorationBox = { inner ->
                                    RoomComposerFieldDecoration(
                                        placeholder = "Message #$channelName",
                                        showPlaceholder = composerText.isEmpty(),
                                        innerTextField = inner,
                                    )
                                },
                            )
                        }
                        val sending = sendState.status == BroadcastViewModel.SendBroadcastStatus.SENDING
                        if (messageTextIsEmpty) {
                            // iOS: mic.fill at title3 in the accent colour.
                            RoomComposerSquareButton(
                                contentDescription = "Record voice message",
                                onClick = { startVoiceMessage() },
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
                            }
                            if (showVoiceRouteSheet) {
                                ComposerMediaRouteSheet(
                                    kind = ComposerMediaKind.VOICE,
                                    onChoose = { viaNextcloud ->
                                        showVoiceRouteSheet = false
                                        startVoiceRecordingIfPermitted(viaNextcloud)
                                    },
                                    onBack = { showVoiceRouteSheet = false },
                                    onDismiss = { showVoiceRouteSheet = false },
                                )
                            }
                        } else {
                            // iOS: arrow.up.circle.fill at title3, the accent colour - secondary
                            // while a send is in flight.
                            RoomComposerSquareButton(
                                contentDescription = "Send",
                                enabled = !sending,
                                onClick = {
                                    val messageText = messageTextState.value
                                    if (!sending && messageText.isNotBlank()) {
                                        // Editing: the composer's text replaces the message being
                                        // edited - one edit broadcast, no new bubble.
                                        val editing = editingMessage
                                        if (editing != null) {
                                            broadcastViewModel.sendEdit(channelName, editing.id, messageText)
                                        } else {
                                            broadcastViewModel.sendBroadcast(channelName, messageText)
                                        }
                                        broadcastViewModel.setMessageText("")
                                    }
                                },
                            ) {
                                RoomSendGlyph(enabled = !sending)
                            }
                        }
                    }
                }
            }
            }
        }
    ) { padding ->
        // Full height, NOT padded by the header and composer: the messages scroll on underneath
        // both (iOS), and the list takes their heights as content padding instead.
        Column(modifier = Modifier.fillMaxSize()) {
        // The in-room retention banner was removed to keep the chat clean; the retention note
        // now lives next to the "Popular" header on the broadcast list (matches iOS).
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
        if (loadedWindow == null) {
            // Not read yet: nothing, rather than a flash of the empty state.
        } else if (messages.isEmpty()) {
            // iOS emptyState: the radio-waves glyph at 48 in secondary, "No messages yet" in
            // headline and the invitation in subheadline secondary, 16 apart, centred.
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = LocalAppColors.current.textSecondary,
                    modifier = Modifier.size(52.dp),
                )
                Text(
                    "No messages yet",
                    color = LocalAppColors.current.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Be the first to post in #$channelName.",
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            sendTarget?.let { recipient ->
                val myAddress by walletViewModel.address.collectAsState()
                val myBalanceSompi by walletViewModel.balanceSompi.collectAsState()
                IosFullSheet(onDismissed = { sendTarget = null }, swipeToDismiss = false) { close ->
                    SpendingAddressSendFlow(
                        fromAddress = myAddress.orEmpty(),
                        balanceSompi = myBalanceSompi,
                        title = "Send Kaspa",
                        viewModel = walletViewModel,
                        onDone = close,
                        presentedAsSheet = true,
                        prefillAddress = recipient,
                    )
                }
            }
            if (unsentReport) ContentReportFallback(onDismiss = { unsentReport = false })
            senderSheetTarget?.let { target ->
                val address = target.address
                val sheetClipboard = LocalClipboardManager.current
                val sheetContext = LocalContext.current
                SenderActionsSheet(
                    address = address,
                    displayName = addressDisplayName(address, contactAliases[address], senderKnsNames[address]),
                    isOwnMessage = target.isOwnMessage,
                    onDismiss = { senderSheetTarget = null },
                    // Their KaPosts profile, on the Feed tab (iOS KaPosts `viewProfile`).
                    onViewProfile = {
                        KaPostsDeepLink.pendingProfileAddress.value = address
                        navController.navigate(com.kachat.app.ui.Screen.KaPosts.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    // Chats live in KaChat.
                    onOpenChat = null,
                    // The send screen with their address filled in.
                    onPayInKaspa = { sendTarget = address },
                    onCopyAddress = {
                        sheetClipboard.setText(AnnotatedString(address))
                        // A room's toasts stay up 3 s (iOS PublicChatChannelView.showToast).
                        com.kachat.app.util.showAddressCopiedToast(sheetContext, address, haptic = false, durationMs = IosToastDuration.LONG)
                    },
                    // Per-room since 4.0: hides this sender in THIS room only.
                    onHide = { broadcastViewModel.hideSender(address, channelName) },
                    hideSubtitle = "Stop seeing their messages in this room. Undo it in Room Info.",
                    onReport = { reportRoomMessage(target.messageId, target.address, target.messageText) },
                    onBlock = {
                        broadcastViewModel.blockSender(address)
                        IosToasts.show("Blocked. Undo it in the Feed's menu, under Blocked.", durationMs = IosToastDuration.LONG)
                    },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().closesQuickReactionBar().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // iOS: the LazyVStack padded 8 top and bottom, inside the header's and the
                // compose bar's insets.
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                )
            ) {
                // At the top of the room, above the oldest message the window reaches, exactly
                // where iOS puts it (02a4f58).
                if (messageWindow.hasMore) {
                    item(key = "load_earlier") {
                        // iOS: a plain button - caption semibold in the accent colour, the full
                        // width, 8 above and below; while it loads, a small spinner and
                        // "Loading…", and a tap does nothing.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (!isLoadingEarlier) {
                                        keepInPlaceId = messages.firstOrNull()?.id
                                        broadcastViewModel.loadEarlier(channelName)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLoadingEarlier) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = LocalAppColors.current.textSecondary,
                                )
                            }
                            Text(
                                if (isLoadingEarlier) "Loading…" else stringResource(R.string.load_earlier_messages),
                                color = KaspaTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val showDateDivider = index == 0 || !ChatTimeFormat.isSameDay(messages[index - 1].blockTimestamp, message.blockTimestamp)
                    if (showDateDivider) {
                        // iOS dateDivider: caption2 semibold in secondary, padded 12 by 4 on
                        // secondarySystemBackground in a 12-rounded box, 8 above and below.
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                ChatTimeFormat.formatDateDivider(message.blockTimestamp, stringResource(R.string.chat_day_today), stringResource(R.string.chat_day_yesterday)),
                                color = LocalAppColors.current.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(roomSecondarySystemBackground())
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        // The divider sits in the same list row as the message under it; iOS
                        // spaces them 8 apart in its stack.
                        Spacer(Modifier.height(8.dp))
                    }
                    // An edited message reads with its newest text; the stored row is untouched.
                    val editForRow = editsByTxId[message.id]
                    @Suppress("NAME_SHADOWING")
                    val message = remember(message, editForRow) {
                        if (editForRow == null) message
                        else message.copy(content = com.kachat.app.util.MessageEdit.apply(editForRow.text, message.content))
                    }
                    val isMine = message.senderAddress == myAddress
                    // The status under the bubble carries the edit's send state; `message` keeps
                    // the row's own for the actions (iOS ee5ec98).
                    val failedEditText = failedEdits[channelName]?.get(message.id)
                    val shownDeliveryStatus = when {
                        failedEditText != null -> "failed"
                        editForRow?.deliveryStatus == "unconfirmed" -> "unconfirmed"
                        else -> message.deliveryStatus
                    }
                    val retryMessage: () -> Unit = {
                        if (failedEditText != null) broadcastViewModel.retryFailedEdit(channelName, message.id)
                        else broadcastViewModel.retryBroadcast(message)
                    }
                    val replyContent = remember(message.content) { MessageReply.parseOrNull(message.content) }
                    val displayContent = replyContent?.text ?: message.content
                    val voiceContent = remember(displayContent) { VoiceMessage.parseOrNull(displayContent) }
                    // Same lone-URL detection 1:1's MessageBubble uses: nothing but a link means
                    // the preview card replaces the plain-text bubble entirely; a link mixed with
                    // other text keeps the bubble and stacks the card below it (like groups).
                    // Both checked only for content short enough to lay out inline — scanning a
                    // huge wall of text for links is wasted work (see MessageBubble's same guard).
                    val isEntirelyLinkMessage = remember(displayContent, voiceContent) {
                        voiceContent == null && displayContent.length <= MESSAGE_TEXT_TRUNCATION_THRESHOLD && TextLinkify.isEntirelyLink(displayContent)
                    }
                    val separateLinkPreviewUrl = remember(displayContent, voiceContent, isEntirelyLinkMessage) {
                        if (voiceContent == null && !isEntirelyLinkMessage && displayContent.length <= MESSAGE_TEXT_TRUNCATION_THRESHOLD) {
                            TextLinkify.findUrls(displayContent).firstOrNull()?.uri
                        } else null
                    }
                    // An in-app KaChat link (shared KaPosts post / broadcast room invite) takes
                    // priority over the generic link path: it previews from local data only and
                    // opens in-app. Detected separately from TextLinkify because the kachat://
                    // form isn't a web URL at all and so is never linkified.
                    val internalLinkMatch = remember(displayContent, voiceContent) {
                        if (voiceContent == null && displayContent.length <= MESSAGE_TEXT_TRUNCATION_THRESHOLD) {
                            KaChatLink.findFirst(displayContent)
                        } else null
                    }
                    // Worth considering for Translate: someone else's plain text. Voice notes, KaChat
                    // link cards and structured payloads have nothing to translate.
                    val translationKey = remember(message.id, displayContent) {
                        broadcastViewModel.translationKey(message.id, displayContent)
                    }
                    val considersTranslation = !isMine && voiceContent == null &&
                        !displayContent.trimStart().startsWith("{") && internalLinkMatch == null
                    LaunchedEffect(translationKey, considersTranslation) {
                        if (considersTranslation) broadcastViewModel.considerTranslation(translationKey, displayContent)
                    }
                    val translationState = translationStates[translationKey]
                    // The text the bubble draws: the translation while one is showing, otherwise the
                    // message. Everything that decides WHAT the message is still reads displayContent.
                    val shownText = strippingRoomBidiControls(
                        (translationState as? com.kachat.app.services.PostTranslationService.TranslationState.Translated)
                            ?.takeIf { translationKey !in translationOriginals }?.text ?: displayContent
                    )
                    val isEntirelyInternalLinkMessage =
                        // The card is the WHOLE message wherever a KaChat link appears - see
                        // 1:1's identical rule. Copy and the full-text dialog keep everything.
                        internalLinkMatch != null
                    val messageReactions = reactionsByTxId[message.id] ?: emptyList()
                    var showMenu by remember { mutableStateOf(false) }
                    // The link a long-press landed on, for its own Open / Copy sheet.
                    var linkMenuUrl by remember { mutableStateOf<String?>(null) }
                    // Who reacted to this message, when the reader asks from the long-press menu.
                    var showReactions by remember { mutableStateOf(false) }
                    val clipboardManager = LocalClipboardManager.current
                    val menuContext = LocalContext.current

                    LaunchedEffect(message.senderAddress) {
                        broadcastViewModel.ensureSenderProfileFetched(message.senderAddress)
                    }

                    // The sender's avatar. Tapping it opens the sender half sheet presented by
                    // the screen (see SenderActionsSheet above the list); the row itself no
                    // longer owns a menu.
                    val avatar: @Composable () -> Unit = {
                        ContactAvatar(
                            imageUrl = senderProfiles[message.senderAddress],
                            fallbackText = message.senderAddress.takeLast(8),
                            size = 32.dp,
                            address = message.senderAddress,
                            modifier = Modifier.clickable {
                                senderSheetTarget = SenderSheetTarget(message.senderAddress, isOwnMessage = isMine, messageId = message.id, messageText = displayContent)
                            }
                        )
                    }

                    val highlightColor by animateColorAsState(
                        if (message.id == highlightedMessageId) KaspaTeal.copy(alpha = 0.18f) else Color.Transparent,
                        label = "messageHighlight"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(highlightColor, RoundedCornerShape(12.dp))
                    ) {
                        // iOS PublicChatMessageRow: HStack(alignment: .bottom, spacing: 8) - the 32
                        // avatar on the sender's side, at least 40 left free on the other, and a
                        // VStack spacing 3 of name, quote, bubble, extras and the time line.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                        if (!isMine) {
                            avatar()
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                        Column(
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // The double-tap bar sits directly above the message, as iOS's does.
                            InlineQuickReactionBar(
                                messageId = message.id,
                                onReact = { emoji ->
                                    // Tapping your active emoji removes it; any other emoji
                                    // adds/replaces — same toggle rule as 1:1/group chats.
                                    val existing = messageReactions.firstOrNull { it.reactorAddress == myAddress }
                                    val action = if (existing?.emoji == emoji) "remove" else "add"
                                    broadcastViewModel.sendReaction(channelName, message.id, emoji, action)
                                },
                                onReply = { broadcastViewModel.startReplyTo(message) },
                                emojis = quickReactionEmojis,
                            )
                            // iOS: caption semibold in the accent colour, flush with the bubble's
                            // edge - "You" over your own messages.
                            Text(
                                if (isMine) stringResource(R.string.reactions_you)
                                else addressDisplayName(message.senderAddress, contactAliases[message.senderAddress], senderKnsNames[message.senderAddress]),
                                color = KaspaTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (replyContent != null) {
                                // iOS replyQuoteView: caption2 semibold sender over a two-line
                                // caption preview, 2 apart, padded 8 on tertiarySystemBackground in
                                // a 10-rounded box at most 240 wide.
                                Column(
                                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .widthIn(max = 240.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(roomTertiarySystemBackground())
                                        .clickable { jumpToReply(replyContent.replyToId) }
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        addressDisplayName(replyContent.replyToSender, contactAliases[replyContent.replyToSender], senderKnsNames[replyContent.replyToSender]),
                                        color = KaspaTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        replyContent.replyToPreview,
                                        color = LocalAppColors.current.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = if (isMine) TextAlign.End else TextAlign.Start,
                                    )
                                }
                            }
                            Box {
                                if (voiceContent != null) {
                                    AudioBubble(
                                        voiceContent,
                                        isSent = isMine,
                                        onLongPress = { showMenu = true },
                                        onDoubleClick = { QuickReactionBarState.activeId = message.id }
                                    )
                                } else if (displayContent.length > MESSAGE_TEXT_TRUNCATION_THRESHOLD) {
                                    // See MESSAGE_TEXT_TRUNCATION_THRESHOLD's doc comment in Screens.kt -
                                    // broadcast rooms are public/unencrypted, so a huge wall of text (e.g.
                                    // stray base64) landing here is if anything more likely than in a
                                    // private chat.
                                    var showFullText by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(roomBubbleColor(isMine))
                                            .combinedClickable(
                                                onClick = { showFullText = true },
                                                onLongClick = { showMenu = true },
                                                onDoubleClick = { QuickReactionBarState.activeId = message.id }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            shownText.take(MESSAGE_TEXT_PREVIEW_LENGTH) + "…",
                                            color = if (isMine) LocalAppColors.current.onOutgoingBubble else LocalAppColors.current.textPrimary,
                                            fontSize = 17.sp,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.show_more),
                                            color = if (isMine) LocalAppColors.current.onOutgoingBubble.copy(alpha = 0.85f) else KaspaTeal,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (showFullText) {
                                        FullMessageTextDialog(
                                            // Every way a room's body is drawn reads shownText (iOS ee5ec98).
                                            text = shownText,
                                            onDismiss = { showFullText = false },
                                            // iOS PublicChatChannelView's onCopyMessage: "Message copied." for 3 s.
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(displayContent))
                                                IosToasts.show("Message copied.", durationMs = IosToastDuration.LONG)
                                            }
                                        )
                                    }
                                } else if (isEntirelyInternalLinkMessage) {
                                    // Nothing but an in-app KaChat link: the invite/post card IS
                                    // the message. Built locally, so no autoFetch gate applies.
                                    KaChatInternalLinkCard(
                                        ref = internalLinkMatch!!.ref,
                                        url = internalLinkMatch.raw,
                                        txId = message.id,
                                        kaspaExplorer = kaspaExplorer,
                                        onDoubleTap = { QuickReactionBarState.activeId = message.id }
                                    )
                                } else if (isEntirelyLinkMessage) {
                                    // Message is nothing but a link — the shared preview card
                                    // (bare media for image/video, attachment card for
                                    // audio/files) replaces the plain-text bubble entirely,
                                    // exactly like 1:1/group bubbles. `fallbackText` keeps the
                                    // raw link visible/tappable if no preview data is found.
                                    // autoFetch = false for ALL broadcast messages (even own):
                                    // channel posters are strangers by definition, and fetching
                                    // their URL on render would leak every reader's IP to it.
                                    // Tap-to-load instead (2026-08 audit, decision 5A).
                                    LinkPreviewCard(
                                        url = TextLinkify.findUrls(displayContent).first().uri,
                                        txId = message.id,
                                        kaspaExplorer = kaspaExplorer,
                                        fallbackText = displayContent,
                                        onDoubleTap = { QuickReactionBarState.activeId = message.id },
                                        isOutgoing = isMine,
                                        // Only KaChat's own site loads by itself (iOS f81c467).
                                        autoFetch = KaChatSiteLink.isSiteLink(TextLinkify.findUrls(displayContent).first().uri)
                                    )
                                } else {
                                    // A plain tap on a link here does nothing: a room's senders are
                                    // anonymous, so a link opens only from the long-press menu's
                                    // Open Link (iOS `tapOpensLink: false`, PublicChatChannelView).
                                    // Sent bubbles are teal with black text/links for contrast —
                                    // matches 1:1/group chats' treatment of the same case.
                                    val linkColor = if (isMine) LocalAppColors.current.onOutgoingBubble else KaspaTeal
                                    val annotatedBody = remember(shownText, isMine) {
                                        buildAnnotatedString {
                                            append(shownText)
                                            for (match in TextLinkify.findUrls(shownText)) {
                                                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), match.range.first, match.range.last + 1)
                                                addStringAnnotation("URL", match.uri, match.range.first, match.range.last + 1)
                                            }
                                            // kachat:// isn't a web URL, so TextLinkify never
                                            // sees it - style/annotate it here so it's tappable
                                            // inline too.
                                            KaChatLink.findFirst(shownText)?.let { internal ->
                                                addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), internal.range.first, internal.range.last + 1)
                                                addStringAnnotation("URL", internal.raw, internal.range.first, internal.range.last + 1)
                                            }
                                        }
                                    }
                                    // iOS: body text padded 14 by 10 on secondarySystemBackground (the
                                    // outgoing teal for your own) in a 16-rounded bubble, 280 at most.
                                    // Where the text's links sit, for a long-press on one.
                                    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(roomBubbleColor(isMine))
                                    ) {
                                        Text(
                                            annotatedBody,
                                            color = if (isMine) LocalAppColors.current.onOutgoingBubble else LocalAppColors.current.textPrimary,
                                            fontSize = 17.sp,
                                            onTextLayout = { bodyLayout = it },
                                            modifier = Modifier
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                                .pointerInput(annotatedBody) {
                                                    detectTapGestures(
                                                        // On a link: Open / Copy for that link (iOS
                                                        // onLinkLongPress → LinkActionsSheet).
                                                        // Anywhere else: the message's menu.
                                                        onLongPress = { position ->
                                                            val url = bodyLayout?.let { layout -> linkAt(layout, annotatedBody, position) }
                                                            if (url != null) linkMenuUrl = url else showMenu = true
                                                        },
                                                        onDoubleTap = { QuickReactionBarState.activeId = message.id },
                                                    )
                                                },
                                        )
                                    }
                                }

                                linkMenuUrl?.let { url ->
                                    // iOS PublicChatChannelView's link sheet: open it anywhere
                                    // (a KaChat link opens its screen), or copy it.
                                    LinkActionsSheet(
                                        url = url,
                                        onDismiss = { linkMenuUrl = null },
                                        onOpen = {
                                            val internal = KaChatLink.parse(url)
                                            if (internal != null) openKaChatLink(internal)
                                            else uriHandler.openUri(url)
                                        },
                                        onCopy = { clipboardManager.setText(AnnotatedString(url)) },
                                    )
                                }

                                if (showMenu) {
                                    // The app's half sheet, as everywhere else (iOS 1bc6ba6).
                                    MessageActionsSheet(
                                        title = if (voiceContent != null) "Voice Message" else "Message",
                                        preview = displayContent.takeIf { voiceContent == null },
                                        onDismiss = { showMenu = false },
                                    ) {
                                        ActionSheetRow(
                                            icon = Icons.AutoMirrored.Filled.Reply,
                                            title = stringResource(R.string.reply),
                                            subtitle = "Quotes this message above your reply.",
                                        ) {
                                            broadcastViewModel.startReplyTo(message)
                                            showMenu = false
                                        }
                                        // Your own delivered text messages only - the same gate
                                        // 1:1 and group chats use.
                                        if (isMine && message.deliveryStatus == "sent" &&
                                            com.kachat.app.util.MessageEdit.isEditable(message.content)) {
                                            ActionSheetRow(
                                                icon = Icons.Default.Edit,
                                                title = "Edit",
                                                subtitle = "Changes the text. Everyone sees the new version.",
                                            ) {
                                                broadcastViewModel.startEditing(message)
                                                broadcastViewModel.setMessageText(
                                                    com.kachat.app.util.MessageEdit.unwrappedText(message.content)
                                                )
                                                showMenu = false
                                            }
                                        }
                                        // The message's first web link, opened or copied from here
                                        // only - a plain tap on it does nothing (iOS bubbleActions,
                                        // `firstLink`).
                                        val firstLink = remember(displayContent, voiceContent) {
                                            if (voiceContent != null) null
                                            else TextLinkify.findUrls(displayContent).firstOrNull {
                                                it.uri.startsWith("http://", ignoreCase = true) ||
                                                    it.uri.startsWith("https://", ignoreCase = true)
                                            }?.uri
                                        }
                                        if (firstLink != null) {
                                            ActionSheetRow(
                                                icon = Icons.Default.Language,
                                                title = stringResource(R.string.open_link),
                                                subtitle = "Opens it in your browser.",
                                            ) {
                                                showMenu = false
                                                val internal = KaChatLink.parse(firstLink)
                                                if (internal != null) openKaChatLink(internal)
                                                else uriHandler.openUri(firstLink)
                                            }
                                            // Not for a Nextcloud share: the link is the address of
                                            // someone's file, which the preview card won't hand out
                                            // either (iOS, same classifier).
                                            if (com.kachat.app.services.LinkPreviewService.nextcloudShareEndpoints(firstLink) == null) {
                                                ActionSheetRow(
                                                    icon = Icons.Default.Link,
                                                    title = stringResource(R.string.copy_link),
                                                    subtitle = "Copies the address to your clipboard.",
                                                ) {
                                                    clipboardManager.setText(AnnotatedString(firstLink))
                                                    showMenu = false
                                                }
                                            }
                                        }
                                        // Not for a voice note: its text is the encoded audio (iOS
                                        // offers it only with no voice payload).
                                        if (voiceContent == null) {
                                        ActionSheetRow(
                                            icon = Icons.Default.ContentCopy,
                                            title = stringResource(R.string.copy_message),
                                            subtitle = "Copies the text to your clipboard.",
                                        ) {
                                            clipboardManager.setText(AnnotatedString(displayContent))
                                            showMenu = false
                                            // iOS PublicChatChannelView: "Message copied." for 3 s.
                                            IosToasts.show("Message copied.", durationMs = IosToastDuration.LONG)
                                        }
                                        }
                                        if (!isMine) {
                                            ActionSheetRow(
                                                icon = Icons.Default.Flag,
                                                title = "Report",
                                                subtitle = "Report abusive or objectionable content. Opens an email with this message attached.",
                                            ) {
                                                showMenu = false
                                                reportRoomMessage(message.id, message.senderAddress, displayContent)
                                            }
                                        }
                                        when (translationState) {
                                            is com.kachat.app.services.PostTranslationService.TranslationState.Translated ->
                                                if (translationKey in translationOriginals) {
                                                    ActionSheetRow(
                                                        icon = Icons.Default.Translate,
                                                        title = stringResource(R.string.show_translation),
                                                        subtitle = stringResource(R.string.translation_show_translation_subtitle),
                                                    ) {
                                                        broadcastViewModel.showTranslation(translationKey)
                                                        showMenu = false
                                                    }
                                                } else {
                                                    ActionSheetRow(
                                                        icon = Icons.AutoMirrored.Filled.Undo,
                                                        title = stringResource(R.string.show_original),
                                                        subtitle = stringResource(R.string.translation_show_original_subtitle),
                                                    ) {
                                                        broadcastViewModel.showOriginal(translationKey)
                                                        showMenu = false
                                                    }
                                                }
                                            com.kachat.app.services.PostTranslationService.TranslationState.Translating,
                                            is com.kachat.app.services.PostTranslationService.TranslationState.Unavailable -> Unit
                                            null, com.kachat.app.services.PostTranslationService.TranslationState.Failed ->
                                                if (considersTranslation && translationKey in translatableKeys) {
                                                    ActionSheetRow(
                                                        icon = Icons.Default.Translate,
                                                        title = stringResource(R.string.translate),
                                                        subtitle = "Shows this message in ${broadcastViewModel.readerLanguageName()}.",
                                                    ) {
                                                        broadcastViewModel.translateMessage(translationKey, displayContent)
                                                        showMenu = false
                                                    }
                                                }
                                        }
                                        ActionSheetRow(
                                            icon = Icons.Default.Public,
                                            title = stringResource(R.string.view_in_explorer),
                                            subtitle = "Opens this transaction in the block explorer.",
                                        ) {
                                            uriHandler.openUri(kaspaExplorer.txUrl(message.id))
                                            showMenu = false
                                        }
                                        // Retry, then Reactions last, as iOS bubbleActions orders them.
                                        if (isMine && shownDeliveryStatus == "failed") {
                                            ActionSheetRow(
                                                icon = Icons.Default.Refresh,
                                                title = stringResource(R.string.retry_send),
                                                subtitle = "Sends this message again.",
                                            ) {
                                                retryMessage()
                                                showMenu = false
                                            }
                                        }
                                        // The pill on the bubble shows WHICH emoji; it has no
                                        // room to say how many or from whom. This does.
                                        if (messageReactions.isNotEmpty()) {
                                            ActionSheetRow(
                                                icon = Icons.Default.Favorite,
                                                title = "Reactions (${messageReactions.size})",
                                                subtitle = "Who reacted, and with what.",
                                            ) {
                                                showMenu = false
                                                showReactions = true
                                            }
                                        }
                                    }
                                }

                                if (showReactions) {
                                    ChatReactionsSheet(
                                        reactions = messageReactions,
                                        nameFor = { addr ->
                                            addressDisplayName(addr, contactAliases[addr], senderKnsNames[addr])
                                        },
                                        isMe = { it == myAddress },
                                        // Rooms already fetch every sender's KNS profile for the
                                        // bubbles, so the faces are in hand.
                                        avatarFor = { senderProfiles[it] },
                                        onDismiss = { showReactions = false },
                                    )
                                }

                                // Anchored INSIDE the bubble's own wrap-content Box, exactly like
                                // 1:1's MessageBubble - so BottomStart/BottomEnd resolve against
                                // the bubble itself. It used to live below this Box inside a
                                // `Box(Modifier.fillMaxWidth())`, which resolved the alignment
                                // against the full row width instead: the pill flew to the screen
                                // edge (up to ~280dp from a short bubble), and stretching the
                                // Column to full width also moved the double-tap QuickReactionBar
                                // to the left edge on any message that already had a reaction.
                                if (messageReactions.isNotEmpty()) {
                                    ReactionPill(
                                        reactions = messageReactions,
                                        myAddress = myAddress,
                                        modifier = Modifier
                                            .align(if (isMine) Alignment.BottomStart else Alignment.BottomEnd)
                                            .offset(y = 10.dp)
                                    )
                                }

                                // iOS: a small "edited" chip on the bubble's top inner corner
                                // (10pt secondary on a systemBackground capsule, 8 up), the mark
                                // every platform shows on an edited message.
                                if (editForRow != null) {
                                    Text(
                                        text = "edited",
                                        color = LocalAppColors.current.textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .align(if (isMine) Alignment.TopStart else Alignment.TopEnd)
                                            .offset(y = (-8).dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(if (LocalAppColors.current.isDark) Color.Black else Color.White)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // The pill is offset ~10dp below the bubble Box and offset reserves no
                            // layout space, so reserve it here - otherwise the pill overlaps the
                            // link preview card / next message below it. iOS pads the bubble 16
                            // below; the column's 3 spacing makes up the rest.
                            if (messageReactions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(13.dp))
                            }

                            // "Translating...", "Translated from Spanish - Show original", or why it
                            // could not be - the same line and words KaPosts uses, under the bubble
                            // rather than inside its Box, so the reaction pill stays on the bubble.
                            // Nothing at all until Translate is picked from the long-press menu.
                            if (translationState != null) {
                                TranslateAffordance(
                                    state = translationState,
                                    canTranslate = false,
                                    showingOriginal = translationKey in translationOriginals,
                                    onTranslate = { broadcastViewModel.translateMessage(translationKey, displayContent) },
                                    onShowOriginal = { broadcastViewModel.showOriginal(translationKey) },
                                    onShowTranslation = { broadcastViewModel.showTranslation(translationKey) },
                                )
                            }

                            // A link mixed with other text keeps the bubble above and stacks the
                            // shared preview card below it, as its own sibling — same placement
                            // (and same reasoning) as 1:1's MessageBubble/group's GroupMessageBubble.
                            // An internal link is always claimed above as the message itself,
                            // so only an external link can still want a card down here.
                            separateLinkPreviewUrl?.takeIf { internalLinkMatch == null }?.let { url ->
                                // Tap-to-load for all broadcast previews - see the entire-link
                                // branch above for why.
                                LinkPreviewCard(url = url, txId = message.id, kaspaExplorer = kaspaExplorer, onDoubleTap = { QuickReactionBarState.activeId = message.id }, isOutgoing = isMine, autoFetch = KaChatSiteLink.isSiteLink(url))
                            }


                            // The time under every message, exactly as in a 1:1 chat, and under your own
                            // the status after it: "Sending", "Sent" with the green check, "Failed · Tap
                            // to retry" (iOS fc6aec6), or "Not confirmed" with no Retry for a send whose
                            // outcome is unknown (iOS 33095ac). Rooms are long feeds, so other days carry the day.
                            MessageTimeLine(
                                timestampMs = message.blockTimestamp,
                                showsDay = true,
                            ) {
                                if (isMine) {
                                    DeliveryStatusLabel(
                                        status = deliveryStatusOf(shownDeliveryStatus),
                                        onRetry = if (shownDeliveryStatus == "failed") retryMessage else null,
                                    )
                                }
                            }

                            // A reaction (not the message) that failed to send: red "Retry" under the
                            // message, paired with the error icon on the reaction pill. Aligned with
                            // ColumnScope.align (NOT a fillMaxWidth Box) so it sits under the pill's
                            // side of the bubble without stretching this Column to the full row width.
                            messageReactions.firstOrNull { it.deliveryStatus == "failed" }?.let { failedReaction ->
                                Text(
                                    text = stringResource(R.string.retry),
                                    color = LocalAppColors.current.danger,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(if (isMine) Alignment.Start else Alignment.End)
                                        .clickable { broadcastViewModel.retryReaction(failedReaction) }
                                )
                            }
                        }
                        if (isMine) {
                            Spacer(Modifier.width(8.dp))
                            avatar()
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                        }
                    }
                }
            }
        }

        if (showScrollToBottom && messages.isNotEmpty()) {
            val scrollToLatestLabel = stringResource(R.string.scroll_to_latest)
            // iOS: a 36 disc of regularMaterial edged white at 18% with a soft shadow, holding
            // chevron.down 14 semibold in the label colour - 12 in from the trailing edge and 12
            // above the compose bar.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    .size(36.dp)
                    .iosGlass(cornerRadius = 18.dp)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch { listState.animateScrollToItem(messages.size - 1 + listOffset) }
                    }
                    .semantics { contentDescription = scrollToLatestLabel },
            ) {
                RoomChevronDown()
            }
        }

        }
    }


    if (showFeeEditor) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showFeeEditor = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.adjust_network_fee), color = LocalAppColors.current.textPrimary) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.if_the_network_is_busy_a),
                        color = LocalAppColors.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    com.kachat.app.ui.theme.IosTextField(
                        value = feeEditorInput,
                        onValueChange = { feeEditorInput = it },
                        label = { Text(KaspaUnit.label(stringResource(R.string.fee_kas))) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // The one exact parser for typed KAS (iOS 16b64bc): "1,5" or "1.5", never a crash.
                    val desiredFeeSompi = KaspaUnit.sompiFromUserText(feeEditorInput)
                    val currentFeeSompi = estimatedFee ?: 0L
                    if (desiredFeeSompi != null && desiredFeeSompi > 0 && currentFeeSompi > 0 && effectiveRate > 0) {
                        val impliedMass = currentFeeSompi / effectiveRate
                        broadcastViewModel.setFeeRateOverride(kotlin.math.ceil(desiredFeeSompi / impliedMass).toLong())
                    } else {
                        broadcastViewModel.setFeeRateOverride(null)
                    }
                    showFeeEditor = false
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { broadcastViewModel.setFeeRateOverride(null); showFeeEditor = false }) {
                        Text(stringResource(R.string.use_default), color = LocalAppColors.current.textSecondary)
                    }
                    TextButton(onClick = { showFeeEditor = false }) {
                        Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                    }
                }
            }
        )
    }
    }
}


// MARK: - Room screen pieces (iOS PublicChatChannelView)

private fun isRoomBidiControl(c: Char): Boolean = c.code in 0x202A..0x202E || c.code in 0x2066..0x2069

/** [text] without the bidi embedding, override and isolate controls: anyone can post them, and one
 *  reverses the order the rest of the text is read in. The plain direction marks (U+200E, U+200F)
 *  stay: right-to-left writing uses them (iOS ee5ec98 `strippingBidiControls`). */
private fun strippingRoomBidiControls(text: String): String =
    if (text.none { isRoomBidiControl(it) }) text else text.filterNot { isRoomBidiControl(it) }

/** iOS secondarySystemBackground: the room's incoming bubbles, its day dividers and the
 *  composer's reply/edit banners. */
@Composable
private fun roomSecondarySystemBackground(): Color =
    if (LocalAppColors.current.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

/** iOS tertiarySystemBackground: the reply quote above a room message. */
@Composable
private fun roomTertiarySystemBackground(): Color =
    if (LocalAppColors.current.isDark) Color(0xFF2C2C2E) else Color.White

/** A room bubble's fill: OutgoingBubble.color for your own, secondarySystemBackground for
 *  everyone else's (the room's own colour, a shade darker than the 1:1 thread's). */
@Composable
private fun roomBubbleColor(isMine: Boolean): Color =
    if (isMine) LocalAppColors.current.outgoingBubble else roomSecondarySystemBackground()

/** iOS editBanner / replyBanner: a caption glyph in the accent colour, a caption semibold accent
 *  title over a one-line caption preview in secondary, and xmark.circle.fill trailing - padded 12
 *  by 8 on secondarySystemBackground in a 12-rounded box, inset 16 and 8 below the bar's top. */
@Composable
private fun RoomComposerBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    preview: String,
    cancelDescription: String,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(roomSecondarySystemBackground())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                color = KaspaTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.Cancel,
            contentDescription = cancelDescription,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
        )
    }
}

/** The inside of the room's text field: the 1:1 composer's ([ChatComposerFieldDecoration]) with
 *  the room's own placeholder, "Message #name". */
@Composable
private fun RoomComposerFieldDecoration(
    placeholder: String,
    showPlaceholder: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (showPlaceholder) {
            Text(
                placeholder,
                color = LocalAppColors.current.textTertiary,
                style = chatComposerTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        innerTextField()
    }
}

/** The room composer's mic / send: a 36 x 36 glassBackground(cornerRadius: 12) beside the field. */
@Composable
private fun RoomComposerSquareButton(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .iosGlass(cornerRadius = 12.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        content()
    }
}

/** SF Symbols' arrow.up.circle.fill at title3: an accent disc (secondary while a send is in
 *  flight) with the arrow cut out of it in the glass behind. */
@Composable
private fun RoomSendGlyph(enabled: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .background(if (enabled) KaspaTeal else LocalAppColors.current.textSecondary, CircleShape),
    ) {
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = null,
            tint = LocalAppColors.current.surface,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** SF Symbols' chevron.down at 14 semibold in the label colour, drawn as a thin open chevron -
 *  the Material arrow carries padding iOS's glyph does not. */
@Composable
private fun RoomChevronDown() {
    val color = LocalAppColors.current.textPrimary
    androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 14.dp, height = 8.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(inset, inset)
            lineTo(size.width / 2f, size.height - inset)
            lineTo(size.width - inset, inset)
        }
        drawPath(
            path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Select mode's bottom bar on the Public Chats tab (iOS KaPosts eec2e30 `bulkActionBar`): Read,
 * Unread, Mute / Unmute and Delete, for every picked room at once. Mute turns notifications off
 * for every picked room; once they are all off, it turns them back on ([onMute] gets true then).
 * Dimmed until a room is picked.
 */
@Composable
private fun PublicChatsBulkBar(
    picked: List<com.kachat.app.models.BroadcastChannelEntity>,
    onRead: () -> Unit,
    onUnread: () -> Unit,
    onMute: (allMuted: Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val haptic = com.kachat.app.util.rememberHaptics()
    val enabled = picked.isNotEmpty()
    val allMuted = enabled && picked.all { !it.notifyEnabled }
    @Composable
    fun RowScope.BulkButton(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = KaspaTeal, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) {
                    haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                    onClick()
                }
                .padding(vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(title, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BulkButton("Read", Icons.Default.Drafts, onClick = onRead)
        BulkButton("Unread", Icons.Default.MarkEmailUnread, onClick = onUnread)
        BulkButton(
            if (allMuted) "Unmute" else "Mute",
            if (allMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            onClick = { onMute(allMuted) },
        )
        BulkButton("Delete", Icons.Default.Delete, tint = colors.danger, onClick = onDelete)
    }
}

/**
 * The "URL" annotation under [position] in a message's text, or null when the press landed off
 * every link - past the end of a line counts as off, not as the line's last character.
 */
private fun linkAt(layout: TextLayoutResult, text: AnnotatedString, position: Offset): String? {
    val line = layout.getLineForVerticalPosition(position.y)
    if (position.y < layout.getLineTop(line) || position.y > layout.getLineBottom(line)) return null
    if (position.x < layout.getLineLeft(line) || position.x > layout.getLineRight(line)) return null
    val offset = layout.getOffsetForPosition(position)
    return text.getStringAnnotations("URL", offset, offset).firstOrNull()?.item
}
