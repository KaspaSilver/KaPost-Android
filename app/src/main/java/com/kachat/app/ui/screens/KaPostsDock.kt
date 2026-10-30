package com.kachat.app.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.KAPOSTS_DOCK
import com.kachat.app.ui.Screen
import com.kachat.app.ui.tabIconModifier
import com.kachat.app.ui.tabIconPainter
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Which dock tab is selected, shared by the real dock (MainShell) and the copy drawn over
 * KaPosts' full-screen screens ([KaPostsDockCopy]) - iOS KaPosts `KaPostsDockRouter`.
 */
object KaPostsDock {
    /** The route of the dock tab that is lit, kept by MainShell. */
    val selectedRoute = MutableStateFlow(Screen.KaPosts.route)

    /** The Notifications tab's unseen count, kept by MainShell for the copy's badge. */
    val unseenNotifications = MutableStateFlow(0)

    /** The Public Chats tab's unread messages across the rooms, kept by MainShell for the copy's
     *  badge (iOS KaPosts eec2e30 `PublicChatUnreadBadge`). */
    val unreadPublicChats = MutableStateFlow(0)

    /**
     * Every KaPosts full-screen screen closes at once (iOS `.kaPostsCloseCovers`): a tab was
     * tapped on the dock or on its copy, and the tab is what should show.
     */
    val closeCovers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A tab tapped on the copy, for MainShell to select. */
    val tabRequests = MutableSharedFlow<Screen>(extraBufferCapacity = 1)

    /**
     * The Feed tab tapped on either dock, whether it was already showing or you came from another
     * tab: the feed goes back to its top (iOS 45de945 `.kaPostsScrollFeedToTop`). A flag rather
     * than an event, so it still lands when the feed is composed only after the tap.
     */
    val scrollFeedToTop = MutableStateFlow(false)

    /** A tab tapped on either dock (iOS `KaPostsDockRouter.select`). */
    fun tabTapped(screen: Screen) {
        if (screen == Screen.KaPosts) scrollFeedToTop.value = true
    }

    fun select(screen: Screen) {
        closeCovers.tryEmit(Unit)
        tabTapped(screen)
        tabRequests.tryEmit(screen)
    }
}

/**
 * A dock tab's label: 10sp medium, as iOS's tab bar, shrinking only when it does not fit its slot
 * (iOS shrinks a label to fit rather than by its length - "Notifications" stays 10 where there is
 * room for it). Medium weight selected or not: only the colour marks the selected tab.
 */
@Composable
fun DockTabLabel(text: String, color: Color) {
    var fontSize by remember(text) { mutableStateOf(10.sp) }
    var fits by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        fontWeight = FontWeight.Medium,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && fontSize.value > 7f) fontSize = (fontSize.value - 0.5f).sp
            else fits = true
        },
        // Nothing is drawn while it is still shrinking, so no frame shows it clipped.
        modifier = Modifier.drawWithContent { if (fits) drawContent() },
    )
}

/** The dock's height, as MainShell's real one. */
private val DockHeight = 60.dp

/** The gap under the dock, above the system navigation bar, as MainShell's real one. */
private val DockBottomGap = 12.dp

/**
 * How far content on a screen with the dock copy over it keeps clear of the bottom, so its last
 * rows and its reply box sit above the dock rather than under it. Zero while the keyboard is up:
 * the dock steps aside for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun kaPostsDockCopyInset(): Dp {
    if (WindowInsets.isImeVisible) return 0.dp
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return DockHeight + DockBottomGap + navBar
}

/**
 * [kaPostsDockCopyInset] above the navigation bar: what a screen that already keeps clear of the
 * system bars adds for the dock copy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun kaPostsDockCopyInsetAboveNavBar(): Dp =
    if (WindowInsets.isImeVisible) 0.dp else DockHeight + DockBottomGap

/**
 * The dock, drawn again on top of KaPosts' full-screen screens (profiles, the menu's screens,
 * Post Activity). Those are Dialog windows, which Android draws over the real dock; this copy
 * keeps the dock in reach on every screen (iOS KaPosts `KaPostsDockCopy`).
 *
 * Call it in the Dialog's root Box, OUTSIDE the screen's slide, so it holds still while screens
 * slide in and out under it the way the real dock does. Tapping a tab closes every cover and
 * selects that tab (the one you are on just closes them, like tapping the current tab). Hidden
 * while the keyboard is up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.KaPostsDockCopy() {
    if (WindowInsets.isImeVisible) return
    val colors = LocalAppColors.current
    val selectedRoute by KaPostsDock.selectedRoute.collectAsState()
    val unseen by KaPostsDock.unseenNotifications.collectAsState()
    val unreadChats by KaPostsDock.unreadPublicChats.collectAsState()
    val haptic = com.kachat.app.util.rememberHaptics()
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            // iOS 26's tab bar: 21pt in from each side, as the real dock.
            .padding(bottom = DockBottomGap, start = 21.dp, end = 21.dp)
            .height(DockHeight)
            .clip(RoundedCornerShape(30.dp))
            .background(toolbarGlassFill())
            .border(0.5.dp, toolbarGlassEdge(), RoundedCornerShape(30.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(DockHeight).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            KAPOSTS_DOCK.forEach { screen ->
                val selected = screen.route == selectedRoute
                // Notifications: unseen activity. Public Chats: unread messages across the rooms.
                val badge = when (screen) {
                    Screen.Notifications -> unseen
                    Screen.Broadcasts -> unreadChats
                    else -> 0
                }
                val badgeWord = if (screen == Screen.Broadcasts) "unread" else "unseen"
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            when {
                                !selected -> Color.Transparent
                                colors.isDark -> Color(0xFF3A3A3A)
                                else -> Color.Black.copy(alpha = 0.07f)
                            }
                        )
                        .clickable {
                            haptic(com.kachat.app.util.IosHaptic.SELECTION)
                            KaPostsDock.select(screen)
                        }
                        .semantics {
                            contentDescription = if (badge > 0) "${screen.label}, $badge $badgeWord" else screen.label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(
                                painter = screen.tabIconPainter(),
                                contentDescription = null,
                                tint = if (selected) KaspaTeal else colors.textPrimary,
                                modifier = screen.tabIconModifier(24.dp),
                            )
                            if (badge > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-6).dp)
                                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFE0245E))
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (badge > 99) "99+" else badge.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        DockTabLabel(
                            text = screen.label,
                            color = if (selected) KaspaTeal else colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
