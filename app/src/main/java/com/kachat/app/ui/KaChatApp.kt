package com.kachat.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.navigation.navArgument
import com.kachat.app.ui.screens.*
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import com.kachat.app.viewmodels.ChatViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.CheckCircle

/**
 * Top-level navigation destinations.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    /**
     * True for tabs drawn with the bundled Kaspa mark instead of a Material icon. [icon] stays a
     * real ImageVector so anything not taught about the drawable still renders something sensible
     * rather than nothing.
     */
    val usesKaspaLogo: Boolean get() = this is KaspaHub

    object Settings    : Screen("settings",     "Settings",     Icons.Default.Settings)
    // KaPosts' Wallet: the one address's balance, history and coins (iOS KaPosts
    // `ChattingAddressManageView(address:title: "Wallet")`), pushed from the Wallet button on your
    // Profile - no longer a tab (iOS KaPosts 865c9ab).
    object Wallet      : Screen("wallet",       "Wallet",       Icons.Default.AccountBalanceWallet)
    // KaPosts' Notifications tab (iOS KaPosts `KaPostsNotificationsView`), with the unseen badge.
    object Notifications : Screen("notifications", "Notifications", Icons.Default.Notifications)
    object Chats       : Screen("chats",        "Chats",        Icons.Default.Forum)
    object Portfolio   : Screen("portfolio",    "Portfolio",    Icons.Default.PieChart)
    object Profile     : Screen("profile",      "Profile",      Icons.Default.AccountCircle)
    object Swap        : Screen("swap",         "Swap",         Icons.Default.SwapHoriz)
    // Labeled "Storage" (not "Cold Storage") and always in the default tab set, matching iOS's
    // AppTab.coldStorage — hideable like Portfolio/Swap via Settings > Customization > Menu, but
    // no longer a separate opt-in reached through Portfolio's old "Cold Storage Devices" row.
    object ColdStorage : Screen("cold_storage", "Storage",      Icons.Default.Security)
    // The Feed tab. The route stays "kaposts" - the links and notifications open it by that name.
    object KaPosts     : Screen("kaposts",      "Feed",         Icons.AutoMirrored.Filled.Article)
    // Named "Public Chats" everywhere a person reads it, matching iOS's AppTab.publicChats title.
    // The route stays "broadcasts": it is persisted in saved dock arrangements, and the thread id,
    // the share links and the on-chain subtype are all still broadcast-named on the wire.
    object Broadcasts  : Screen("broadcasts",   "Public Chats", Icons.Default.Forum)
    // A placeable tab like any other, matching iOS's AppTab.apps - it can sit in the dock or in
    // the Kaspa Hub, and it can be reordered in either. It used to be a hardcoded tile appended
    // to the Hub grid with no Screen behind it, which is exactly why Customize Dock could not
    // see it. Labeled "Websites" in the dock (the bar truncates hard); "Kaspa Websites" in full.
    object KaspaWebsites : Screen("kaspa_websites", "Websites", Icons.Default.Public)
    // Chess tournaments (5.1) - placeable like the others, in the Hub by default (iOS AppTab.chess).
    object Chess       : Screen("chess",        "Chess",        Icons.Default.GridOn)
    // .kachat names (5.2) - Kaspa Hub > .kachat: the marketplace for KaChat's own names (claim,
    // buy and sell, trustless). UI only until the name service is built (iOS AppTab.kachatNames).
    // Drawn as the ".kachat" wordmark wherever tabs are drawn (see tabIconPainter).
    object KachatNames : Screen("kachat_names", ".kachat",      Icons.Default.AlternateEmail)
    // KaChat Stats (5.2) - KaChat's transactions on Kaspa by kind, from the indexers' /stats
    // (iOS AppTab.kachatStats). Read-only, so Simple Mode shows it.
    object KachatStats : Screen("kachat_stats", "Stats",        Icons.Default.BarChart)
    // Address Book (iOS AppTab.addressBook, 00767a4) - Kaspa Hub > Address Book: saved Kaspa
    // addresses with names, per wallet, kept in KaChat (it replaced syncing with the phone's
    // Contacts). Your own saved addresses, so Simple Mode shows it.
    object AddressBook : Screen("address_book", "Address Book", Icons.Outlined.Book)
    // KaChat Bots (5.2.1, iOS AppTab.kachatBots, 82ab31d) - Kaspa Hub > KaChat Bots: Block Reward
    // Tracker, Wallet Tracker, Price Bot and Kaspa Answers; tapping one opens a chat with it.
    // Ordinary chats with the bots, so Simple Mode shows it.
    object KachatBots  : Screen("kachat_bots",  "Bots",         Icons.Outlined.Memory)
    // Holds whatever of the above is turned on but not in the dock - see [kaspaHubSections].
    // Route stays "kaspa_hub" once shipped: it is persisted in saved dock arrangements.
    object KaspaHub    : Screen("kaspa_hub",    "Kaspa Hub",    Icons.Default.BubbleChart)
    // The old "+ More" pseudo-tab (opened Customize Dock from the dock itself) is gone —
    // Customize Dock is reached via Settings > Customization instead. The dock still caps at
    // MAX_DOCK_ITEMS, with over-cap KaPosts/Broadcasts riding the Chats-slot cycle.
}

/**
 * The placeable tab a route belongs to, for the dock to light while you are on it: the route
 * itself when it is one of the tabs, the tab that opens it when it is a screen further in. Null
 * for a route the dock is not shown over, or one no tab owns.
 */
private fun dockOwnerRoute(route: String?): String? = when (route) {
    null -> null
    "chess_mode/{mode}", "chess_tournament/{tournamentId}" -> Screen.Chess.route
    "broadcast_channel/{channelName}" -> Screen.Broadcasts.route
    "chat/{contactId}?paymentMode={paymentMode}", "group_chat/{groupId}" -> Screen.Chats.route
    "cold_storage_detail/{accountId}", "cold_storage_tx_history/{address}" -> Screen.ColdStorage.route
    "help", "manage_addresses", "identity_address_detail", "spending_address_detail/{index}",
    "kns_domains", Screen.Wallet.route -> Screen.Profile.route
    "portfolio_price_chart", "portfolio_value_chart", "portfolio_hashrate_chart" -> Screen.Portfolio.route
    "broadcast_room_info/{channelName}" -> Screen.Broadcasts.route
    Screen.Chess.route, Screen.Broadcasts.route, Screen.KaPosts.route, Screen.Portfolio.route,
    Screen.Swap.route, Screen.ColdStorage.route, Screen.KaspaWebsites.route,
    Screen.KachatNames.route, Screen.KachatStats.route, Screen.AddressBook.route,
    Screen.KachatBots.route -> route
    else -> null
}

/**
 * The name shown inside the Kaspa Hub grid and at the top of the section it opens. Differs from
 * [Screen.label] only where a dock label has to stay short - the bar truncates hard, so the full
 * names live here. Matches iOS's `AppTab.ecosystemTitle`.
 */
val Screen.hubTitle: String
    get() = when (this) {
        Screen.Swap -> "ChangeNOW Swap"
        Screen.KaspaWebsites -> "Kaspa Websites"
        Screen.Chess -> "Chess Online"
        Screen.KachatNames -> ".kachat Domains"
        Screen.KachatStats -> "KaChat Stats"
        Screen.KachatBots -> "KaChat Bots"
        else -> label
    }

/**
 * Tabs that always hold a dock slot and cannot be moved into Kaspa Hub (iOS
 * `AppTab.pinnedToDock`).
 *
 * Chats, because it is what KaChat is. Kaspa Hub, because it is what HOLDS whatever is not in the
 * dock - move it inside itself and everything it holds becomes unreachable. Profile, because it is
 * the way to Settings, the account list and the wallet's own addresses, so it must never be a
 * level deeper than the dock. Listed in the order a fresh dock places them, so a missing one can
 * be reinserted sensibly; where they sit among the five is the person's (Customize Dock), that
 * they are there is not.
 */
val PINNED_DOCK_ROUTES = listOf(Screen.KaspaHub.route, Screen.Chats.route, Screen.Profile.route)

/**
 * ChangeNOW Swap, off for now (iOS f94db5a). An in-app crypto exchange - a third-party one like
 * ChangeNOW included - is being treated by the stores as needing exchange licensing in every
 * country the app ships to, so Swap is hidden everywhere: [resolveDock] and [kaspaHubSections] are
 * the choke points every dock, Hub and Customize Dock reader goes through. The code stays, so
 * turning it back on is this one line.
 */
const val SWAP_FEATURE_ENABLED = false

/**
 * Routes drawn as iOS's Settings sheet (see IosSheetColors). None in KaPosts: Settings is a dock
 * tab, its pages pushed inside it, so they draw with the tabs' palette.
 */
val SETTINGS_SHEET_ROUTES = emptySet<String>()

/**
 * The KaPosts dock: Feed, Public Chats, Profile, Notifications, Settings - always these five, in
 * this order. KaPosts is one address doing one thing, so there is no Customize Dock. The Wallet is
 * a button on your Profile (iOS KaPosts `KaPostsTabView`, 865c9ab).
 */
val KAPOSTS_DOCK = listOf(Screen.KaPosts, Screen.Broadcasts, Screen.Profile, Screen.Notifications, Screen.Settings)

/** Tabs the user can place. Chats is listed but pinned (see [PINNED_DOCK_ROUTES]). */
val ASSIGNABLE_TAB_ROUTES = listOf(
    Screen.Chats.route, Screen.Portfolio.route, Screen.ColdStorage.route,
    Screen.Swap.route, Screen.KaPosts.route,
    Screen.KaspaWebsites.route, Screen.Chess.route,
    Screen.KachatNames.route, Screen.KachatStats.route, Screen.AddressBook.route,
    Screen.KachatBots.route
)

/** Route strings for tabs that can never be hidden — see [resolveTabOrder]. */
val ALWAYS_VISIBLE_TAB_ROUTES = setOf(Screen.Chats.route, Screen.Profile.route)

// All top-level tabs, in the app's default order (matches iOS's AppTab.defaultOrder). Settings
// isn't a tab at all (matches iOS) — it's reached one tap in from Profile's gear icon instead,
// see ProfileScreen. A default install shows exactly the five the dock can fit - Kaspa Hub,
// Portfolio, Chats, KaPosts, Profile - with Cold Storage, Swap and the websites list still
// ENABLED but living inside Kaspa Hub rather than competing for a dock slot.
val bottomNavItems = listOf(
    Screen.KaspaHub,
    Screen.Portfolio,
    Screen.Chats,
    Screen.KaPosts,
    Screen.Profile,
    Screen.ColdStorage,
    Screen.AddressBook,
    Screen.KachatNames,
    Screen.KachatStats,
    Screen.KachatBots,
    Screen.Swap,
    Screen.Broadcasts,
    Screen.KaspaWebsites,
    Screen.Chess
)

/** The dock renders at most this many items (matches iOS's AppTab.maxDockItems). */
const val MAX_DOCK_ITEMS = 5

/** Slots the person actually fills: the cap minus the pinned tabs (iOS `assignableDockSlots`). */
val ASSIGNABLE_DOCK_SLOTS: Int get() = MAX_DOCK_ITEMS - PINNED_DOCK_ROUTES.size

/**
 * Routes hard-hidden everywhere while Child Mode is on (Settings > Security > Child Mode) —
 * matches iOS's AppTab.isEnabled gate. [resolveTabOrder] below is the single choke point every
 * dock consumer flows through (the bar itself, the Chats-slot cycle, Customize Menu's preview),
 * so while it's on these tabs can't render in the dock NOR ride the Chats-slot cycle, regardless
 * of dock settings. Deliberately derived at render time only: the per-account stored dock prefs
 * (tab_order/hidden_tabs) are never rewritten with the masked state, so turning Child Mode off
 * restores exactly the arrangement the user had before.
 */
val CHILD_MODE_HIDDEN_ROUTES = setOf(
    Screen.Swap.route, Screen.KaPosts.route, Screen.Broadcasts.route,
    // A browser onto the open web - the one tab most obviously not for a child's phone.
    Screen.KaspaWebsites.route,
    // Public tournaments with strangers, paid per move (iOS hides Chess in Child Mode too).
    Screen.Chess.route,
    // A marketplace - buying and selling with real KAS - so Simple Mode hides it, like Swap.
    Screen.KachatNames.route
)

/**
 * Maps persisted route strings (from AppSettingsRepository.tabOrder) back to [Screen] objects,
 * in that order. Any route no longer recognized (e.g. a tab removed in a future update) is
 * dropped, and any [Screen] missing from the persisted list (e.g. a tab added since the user
 * last reordered) is appended at the end — so neither a stale persisted value nor an app update
 * can leave a tab permanently missing or crash on an unknown route. [hiddenTabs] then filters out
 * anything the user unchecked in Settings > Customization > Menu (never applied to
 * [ALWAYS_VISIBLE_TAB_ROUTES]). [childMode] hard-hides [CHILD_MODE_HIDDEN_ROUTES] on top of
 * whatever the user's own dock settings say.
 */
@Deprecated("Superseded by resolveDock. Kept only so the one-time placement migration can read what a user could SEE under the old model.")
fun resolveTabOrder(routes: List<String>, hiddenTabs: Set<String>, childMode: Boolean = false): List<Screen> {
    val byRoute = bottomNavItems.associateBy { it.route }
    val resolved = routes.mapNotNull { byRoute[it] }
    val missing = bottomNavItems.filter { it !in resolved }
    // Public Chats moved INTO the Chats screen as its third tab, so the rooms are no longer a
    // destination of their own. The Screen stays so a dock arrangement saved by an older build
    // still decodes; it simply never renders (iOS keeps the AppTab case for the same reason).
    val ordered = (resolved + missing).filterNot { it.route == Screen.Broadcasts.route }
    var visible = ordered.filter { it.route in ALWAYS_VISIBLE_TAB_ROUTES || it.route !in hiddenTabs }
    if (childMode) {
        visible = visible.filter { it.route !in CHILD_MODE_HIDDEN_ROUTES }
    }
    // Dock cap (matches iOS AppTab.visible). Anything enabled that doesn't fit simply isn't in
    // the dock - it is not lost, it is reached through Kaspa Hub. There is no longer a Chats-tab
    // cycle: re-tapping or holding Chats to reach KaPosts and Broadcasts was removed once the Hub
    // gave them a place of their own.
    if (visible.size > MAX_DOCK_ITEMS) {
        visible = visible.take(MAX_DOCK_ITEMS)
    }
    return visible
}

/**
 * The dock, from the user's PLACEMENT rather than from the first few of an order.
 *
 * That derivation is what let a tab silently disappear: nothing hid it, it was simply pushed past
 * the cap by the tabs ahead of it. A tab is now placed in the dock or in the Hub, always exactly
 * one. The pinned tabs are reinserted whatever the stored list says, so no saved - or hand-edited
 * - arrangement can leave the app without them.
 */
fun resolveDock(dockRoutes: List<String>, hiddenTabs: Set<String>, childMode: Boolean = false): List<Screen> {
    val byRoute = bottomNavItems.associateBy { it.route }
    // The stored order (matches iOS AppTab.visible), deduplicated defensively - a repeated route
    // would render the same tab twice - with at most ASSIGNABLE_DOCK_SLOTS picks besides the
    // pinned tabs. A saved arrangement from before Chats was pinned keeps its first two picks;
    // anything past them is not lost, it is listed in Kaspa Hub (see kaspaHubSections).
    var picks = 0
    val placed = mutableListOf<Screen>()
    for (screen in dockRoutes.mapNotNull { byRoute[it] }) {
        if (!SWAP_FEATURE_ENABLED && screen == Screen.Swap) continue
        if (screen.route in hiddenTabs && screen.route !in ALWAYS_VISIBLE_TAB_ROUTES) continue
        if (childMode && screen.route in CHILD_MODE_HIDDEN_ROUTES) continue
        if (screen.route !in PINNED_DOCK_ROUTES && screen.route !in ASSIGNABLE_TAB_ROUTES) continue
        if (screen in placed) continue
        if (screen.route !in PINNED_DOCK_ROUTES) {
            if (picks >= ASSIGNABLE_DOCK_SLOTS) continue
            picks++
        }
        placed.add(screen)
    }
    // Reinserted whatever the stored list says, so no saved arrangement - or a hand-edited one -
    // can leave the app without Chats, the Hub that holds everything else, or Profile.
    for (route in PINNED_DOCK_ROUTES) {
        val screen = byRoute[route] ?: continue
        if (screen !in placed) {
            val position = AppSettingsRepository.DEFAULT_DOCK_TABS.indexOf(route).takeIf { it >= 0 } ?: placed.size
            placed.add(minOf(position, placed.size), screen)
        }
    }
    return placed
}

/**
 * What the Kaspa Hub grid actually shows: its candidates, minus anything hidden, minus anything
 * that already has its own dock slot.
 *
 * The dock subtraction is the point - a feature sitting in the dock has no reason to also be a
 * tile one level deeper, and listing it twice would just make the grid look padded. Matches iOS's
 * `AppTab.ecosystemSections`.
 *
 * The Kaspa Websites tile is NOT here: unlike iOS it has never been a dock tab on Android (it is
 * a route reached from Profile), so it has nothing to be deduplicated against and the grid always
 * shows it.
 */
fun kaspaHubSections(
    dockRoutes: List<String>,
    hubRoutes: List<String>,
    hiddenTabs: Set<String>,
    childMode: Boolean = false,
): List<Screen> {
    val byRoute = bottomNavItems.associateBy { it.route }
    val inDock = resolveDock(dockRoutes, hiddenTabs, childMode).toSet()
    fun eligible(screen: Screen) = screen.route in ASSIGNABLE_TAB_ROUTES &&
        screen.route !in PINNED_DOCK_ROUTES &&
        (SWAP_FEATURE_ENABLED || screen != Screen.Swap) &&
        screen !in inDock &&
        screen.route !in hiddenTabs &&
        !(childMode && screen.route in CHILD_MODE_HIDDEN_ROUTES)

    val ordered = hubRoutes.mapNotNull { byRoute[it] }.filter(::eligible).toMutableList()
    // Anything absent from the stored list - a tab added by an update, or one demoted before that
    // list knew about it - is appended rather than lost, which is the same failure that dropped a
    // tab from the dock.
    for (screen in bottomNavItems) {
        if (eligible(screen) && screen !in ordered) ordered.add(screen)
    }
    return ordered
}

/**
 * Root composable: bottom nav + NavHost.
 * Wallet onboarding is shown instead when no wallet exists.
 */
@Composable
fun KaChatApp(
    walletViewModel: WalletViewModel = hiltViewModel(),
    pendingChannelName: String? = null,
    onPendingChannelHandled: () -> Unit = {},
    pendingWalletActivityKind: String? = null,
    onPendingWalletActivityHandled: () -> Unit = {},
    pendingOwnAddress: String? = null,
    onPendingOwnAddressHandled: () -> Unit = {}
) {
    val isLoggedIn by walletViewModel.isLoggedIn.collectAsState()
    val mnemonic by walletViewModel.mnemonic.collectAsState()
    val startupResolved by walletViewModel.startupResolved.collectAsState()

    if (!startupResolved) {
        // Cold start, routing decision still loading (the async biometrics-for-login read that
        // gates auto-login into the last-used account). Compose NOTHING yet — composing the
        // Onboarding/accounts screen as a transient default flashed the account list for a few
        // frames before jumping into the account. The themed Surface behind this composable
        // keeps the window seamlessly on the background color, iOS-style; the wait is a few
        // milliseconds of DataStore read.
        Box(modifier = Modifier.fillMaxSize())
    } else if (!isLoggedIn || mnemonic != null) {
        OnboardingScreen(walletViewModel)
    } else {
        MainShell(
            walletViewModel = walletViewModel,
            pendingChannelName = pendingChannelName,
            onPendingChannelHandled = onPendingChannelHandled,
            pendingWalletActivityKind = pendingWalletActivityKind,
            onPendingWalletActivityHandled = onPendingWalletActivityHandled,
            pendingOwnAddress = pendingOwnAddress,
            onPendingOwnAddressHandled = onPendingOwnAddressHandled
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainShell(
    walletViewModel: WalletViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    pendingChannelName: String? = null,
    onPendingChannelHandled: () -> Unit = {},
    pendingWalletActivityKind: String? = null,
    onPendingWalletActivityHandled: () -> Unit = {},
    pendingOwnAddress: String? = null,
    onPendingOwnAddressHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // The whole stack, for the dock to light the tab a pushed screen sits in.
    val dockBackStack by navController.currentBackStack.collectAsState()
    val currentDestination = navBackStackEntry?.destination
    // The dock stays in reach on every screen (iOS KaPosts 865c9ab): the five tabs, and every
    // screen pushed from them - a public chat room included - which keeps clear of it (see
    // aboveDock below). It steps aside for the keyboard.
    val hideBottomBar by walletViewModel.hideBottomBar.collectAsState()
    val imeVisible = WindowInsets.isImeVisible
    val dockShown = !hideBottomBar && !imeVisible
    val localTabOrder = KAPOSTS_DOCK
    val currentTopRoute = currentDestination?.route

    /** Brings a dock tab to the front, the way tapping it does. */
    fun showTab(screen: Screen) {
        val poppedToExisting = navController.popBackStack(route = screen.route, inclusive = false, saveState = true)
        if (!poppedToExisting) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // A notify-enabled public chat room's new message notification opens that room.
    // Activity-scoped, only to start the notification prefetch; the room keeps its own instance.
    val notificationBroadcastViewModel: com.kachat.app.viewmodels.BroadcastViewModel = hiltViewModel()
    LaunchedEffect(pendingChannelName) {
        if (pendingChannelName != null) {
            // The name can come straight off a server push payload (see MainActivity's
            // applyFcmNotificationTarget), so it gets the same sanitizing the share-link path
            // uses - an unnormalized or hostile name would otherwise be pasted raw into the
            // route and land on nothing.
            val channel = com.kachat.app.ui.screens.KaChatLink.sanitizeChannelName(pendingChannelName)
            if (channel == null) {
                showTab(Screen.Broadcasts)
            } else {
                // Newest rows first, alongside the navigation rather than after it.
                notificationBroadcastViewModel.prefetchNewest(channel)
                navController.navigate("broadcast_channel/$channel")
            }
            onPendingChannelHandled()
        }
    }

    /** Your Wallet, pushed on the Profile tab, as its button there opens it. */
    fun showWallet() {
        showTab(Screen.Profile)
        navController.navigate(Screen.Wallet.route) { launchSingleTop = true }
    }

    // A wallet "Received" receipt, from a notification or with the address it names: KaPosts has
    // one address, so every one opens the Wallet.
    LaunchedEffect(pendingWalletActivityKind) {
        if (pendingWalletActivityKind != null) {
            showWallet()
            onPendingWalletActivityHandled()
        }
    }
    LaunchedEffect(pendingOwnAddress) {
        if (pendingOwnAddress != null) {
            showWallet()
            onPendingOwnAddressHandled()
        }
    }

    // The dock copy over KaPosts' full-screen screens asks for a tab (see KaPostsDock); it has
    // already closed them.
    LaunchedEffect(Unit) {
        com.kachat.app.ui.screens.KaPostsDock.tabRequests.collect { screen ->
            if (navController.currentDestination?.route == screen.route) {
                walletViewModel.notifyTabReselected(screen.route)
            } else {
                showTab(screen)
            }
        }
    }

    // The Notifications tab's unseen count: its badge, on the dock and on the dock copy. Collected
    // here, not inside the dock, so the copy's badge stays right while the dock steps aside.
    val dockNotifVm: com.kachat.app.viewmodels.NotificationCenterViewModel = hiltViewModel()
    // The store is a singleton keyed per wallet internally; reload whenever the active account
    // changes so a switch never leaves the previous account's unread dot (and entries) showing
    // until something else happens to poke it.
    val dockNotifAddress by walletViewModel.address.collectAsState()
    LaunchedEffect(dockNotifAddress) {
        dockNotifVm.store.reloadIfNeeded()
        dockNotifVm.kaPostsUnseen.reloadIfNeeded()
    }
    val dockKaPostsUnseen by dockNotifVm.kaPostsUnseen.unseenCount.collectAsState()
    LaunchedEffect(dockKaPostsUnseen) {
        com.kachat.app.ui.screens.KaPostsDock.unseenNotifications.value = dockKaPostsUnseen
    }

    // A .kachat registration in flight: its progress half sheet, back up after a relaunch (iOS 61fb0fc).
    com.kachat.app.ui.screens.KachatRegistrationPresenter()

    // KaPosts share/deep links (kachat://kapost/<txid>, https://kachat.app/post/…):
    // surface the KaPosts tab; KaPostsScreen itself consumes the pending txid and opens the
    // post's thread once visible.
    // A .kachat name notification: the .kachat screen opens the name (iOS beeedd4). The name stays
    // pending until that screen takes it, so a cold start lands on it too.
    val pendingKachatName by com.kachat.app.ui.screens.KachatDeepLink.pendingName.collectAsState()
    LaunchedEffect(pendingKachatName) {
        // Only where names are live (iOS 7227d69 `KachatLive.isEnabled` = isLaunched).
        if (pendingKachatName != null && com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
            navController.navigate(Screen.KachatNames.route) { launchSingleTop = true }
        }
    }

    // A tapped KaPosts notification push lands on the Notifications tab; a post link, or a post
    // opened from a notification row, on the Feed (iOS KaPosts KaPostsTabView `.openKaPost`).
    val pendingKaPostTxId by KaPostsDeepLink.pendingPostTxId.collectAsState()
    val pendingOpenNotifications by KaPostsDeepLink.pendingOpenNotifications.collectAsState()
    LaunchedEffect(pendingKaPostTxId, pendingOpenNotifications) {
        if (pendingOpenNotifications) {
            KaPostsDeepLink.pendingOpenNotifications.value = false
            // The blank post id is the tab-only sentinel: nothing for the feed to open.
            if (pendingKaPostTxId.isNullOrEmpty()) KaPostsDeepLink.pendingPostTxId.value = null
            com.kachat.app.ui.screens.KaPostsDock.closeCovers.tryEmit(Unit)
            showTab(Screen.Notifications)
        } else if (!pendingKaPostTxId.isNullOrEmpty()) {
            showTab(Screen.KaPosts)
        } else if (pendingKaPostTxId != null) {
            KaPostsDeepLink.pendingPostTxId.value = null
        }
    }

    // Broadcast room share/deep links (kachat://broadcast/<channel>,
    // https://kachat.app/broadcast/…) and taps on an in-app room-invite card. The name has
    // already been normalized and validated by BroadcastDeepLink.request; a room that isn't one of
    // the curated ones carries a join request that BroadcastChannelScreen consumes on open, so it
    // lands in the user's own channel list rather than disappearing when they navigate away.
    // Someone's profile link (kachat:// or kaposts://profile/<address>, https://kachat.app/u/<address>,
    // or a tapped profile card): their KaPosts profile, over the feed (iOS KaPosts
    // KaChatLinkRouter `.profile`).
    val pendingProfileAddress by com.kachat.app.ui.screens.ProfileDeepLink.pendingAddress.collectAsState()
    LaunchedEffect(pendingProfileAddress) {
        val address = pendingProfileAddress ?: return@LaunchedEffect
        com.kachat.app.ui.screens.ProfileDeepLink.consumePending()
        KaPostsDeepLink.pendingProfileAddress.value = address
        showTab(Screen.KaPosts)
    }

    // A payment request tapped, pasted or opened from outside the app (iOS c6851d6
    // PaymentRequestSheetPresenter): Send Kaspa with it filled in, or your own to copy or share.
    com.kachat.app.ui.screens.PaymentRequestSheetHost(walletViewModel = walletViewModel, chatViewModel = chatViewModel)

    val pendingBroadcastLinkChannel by BroadcastDeepLink.pendingChannel.collectAsState()
    LaunchedEffect(pendingBroadcastLinkChannel) {
        val channel = pendingBroadcastLinkChannel ?: return@LaunchedEffect
        BroadcastDeepLink.consumePending()
        showTab(Screen.Broadcasts)
        navController.navigate("broadcast_channel/$channel")
    }

    // KaChat's welcome guide used to lift the hold create/import put on syncing; KaPosts has no
    // guide, so the dock lifts it, and the address's node subscription (balance, coins for
    // sending) starts (iOS KaPosts KaPostsTabView.onAppear).
    val pendingWelcomeGuide by walletViewModel.pendingWelcomeGuide.collectAsState()
    LaunchedEffect(pendingWelcomeGuide) {
        if (pendingWelcomeGuide) {
            walletViewModel.clearJustImportedWallet()
            walletViewModel.consumePendingWelcomeGuide()
        }
        chatViewModel.releaseOnboardingHoldIfIdle()
    }

    // Which tab the glass pill sits under - the same test each item makes for
    // itself below, so the pill and the lit icon can never disagree.
    // The tab you are IN, the way iOS's tab bar keeps the tab that pushed a screen
    // lit. A dock tab's own route lights itself. A screen that is not a dock tab
    // lights the tab it belongs to (chess lobbies and tournaments belong to Chess,
    // a public chat room to Public Chats, an account to Cold Storage) - and when
    // that tab is not in the dock it lives in the Kaspa Hub, which is always there,
    // so the Hub stays lit. It used to light nothing on any of those screens, so the
    // glass pill vanished the moment you opened 1v1 or Tournaments from the Hub.
    val selectedDockIndex = run {
        val direct = localTabOrder.indexOfFirst { screen ->
            currentDestination?.hierarchy?.any { it.route == screen.route } == true
        }
        if (direct >= 0) return@run direct
        // The tab whose stack the screen was pushed onto: the nearest dock tab
        // under it. iOS keeps that tab lit - a room opened from Chats lights
        // Chats, not Public Chats or the Hub.
        val underneath = dockBackStack.asReversed().drop(1).firstNotNullOfOrNull { entry ->
            localTabOrder.indexOfFirst { it.route == entry.destination.route }.takeIf { it >= 0 }
        }
        if (underneath != null) return@run underneath
        val owner = dockOwnerRoute(currentTopRoute) ?: return@run -1
        localTabOrder.indexOfFirst { it.route == owner }.takeIf { it >= 0 }
            ?: localTabOrder.indexOfFirst { it.route == Screen.KaspaHub.route }
    }
    // The copy over KaPosts' full-screen screens lights the same tab.
    LaunchedEffect(selectedDockIndex) {
        localTabOrder.getOrNull(selectedDockIndex)?.let {
            com.kachat.app.ui.screens.KaPostsDock.selectedRoute.value = it.route
        }
    }

    // Frosted glass under the floating dock: the NavHost below is the backdrop, and the dock
    // draws a blurred copy of whatever sits behind it (RenderEffect on Android 12+, a
    // translucent scrim below that) - the way iOS's bar is a material rather than a colour.
    val dockHaze = remember { HazeState() }
    Scaffold(
        containerColor = LocalAppColors.current.background,
        bottomBar = {
            // Over a white page (Profile's QR pages) iOS's tab bar glass turns light, in dark mode
            // too: the glass takes on what is behind it.
            CompositionLocalProvider(
                LocalAppColors provides if (com.kachat.app.ui.screens.DockOverLightPage.active) com.kachat.app.ui.theme.LightAppColors else LocalAppColors.current
            ) {
                // Only show the floating tab bar on the top-level tab destinations —
                // "pushed" detail screens (chat thread, settings sub-screens, etc.) fill
                // the whole screen with their own Scaffold and must not have this
                // overlaid on top of them (it was blocking the chat input entirely).
                if (dockShown) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // navigationBarsPadding() first so the 24dp visual margin sits above
                            // the system nav bar (gesture pill or 3-button bar) rather than being
                            // eaten by it — its height isn't the same on every device, so a fixed
                            // dp value alone left the tab bar sitting under/behind it on some phones.
                            .navigationBarsPadding()
                            // iOS 26's tab bar: 21pt in from each side (measured on the iPhone).
                            .padding(bottom = 12.dp, start = 21.dp, end = 21.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Sliding a finger along the bar carries the lens; releasing lands on the
                        // tab under it, the way the glass follows a finger on iOS. Null while nobody
                        // is dragging, so the lens follows the selection instead.
                        var dockDragIndex by remember { mutableStateOf<Int?>(null) }

                        /** What a tap on a dock tab does - shared so a drag lands in the same place. */
                        fun goToDockTab(screen: Screen) {
                            // Every KaPosts full-screen screen closes, so the tab is what shows.
                            com.kachat.app.ui.screens.KaPostsDock.closeCovers.tryEmit(Unit)
                            val onOwnRoute = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            if (onOwnRoute) {
                                walletViewModel.notifyTabReselected(screen.route)
                                return
                            }
                            val poppedToExisting = navController.popBackStack(
                                route = screen.route,
                                inclusive = false,
                                saveState = true,
                            )
                            if (!poppedToExisting) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }

                        // The slide handler below is keyed only on the tab order and the slot width,
                        // so it is created once and outlives every navigation. Calling goToDockTab
                        // from it directly used the closure from THAT first composition, whose
                        // currentDestination was the tab you started on: sliding back onto that tab
                        // later read as "already here" and did nothing. This always holds the current one.
                        val latestGoToDockTab by androidx.compose.runtime.rememberUpdatedState<(Screen) -> Unit> {
                            goToDockTab(it)
                        }

                        // A new dock arrangement builds a fresh bar (iOS d7c3fcf keys its TabView the
                        // same way), so every icon, label and the glass lens start from the tabs the
                        // bar now holds rather than from whichever tab held that slot before. The key
                        // changes only when the arrangement does.
                        val dockArrangementKey = localTabOrder.joinToString(",") { it.route }
                        key(dockArrangementKey) {
                        // Fixed at the bar's own height so the lens can stand proud of it without
                        // growing the dock or shifting it up the screen.
                        // iOS's bar is 60pt tall (measured on the iPhone 17).
                        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
                        // The pill slides between tabs rather than appearing under the new one, the
                        // way iOS's glass tab bar moves: one piece of glass that travels.
                        val dockItemWidth = (maxWidth - 4.dp) / localTabOrder.size.coerceAtLeast(1)
                        val pillIndex = dockDragIndex ?: selectedDockIndex
                        val pillOffset by animateDpAsState(
                            targetValue = 2.dp + dockItemWidth * pillIndex.coerceAtLeast(0),
                            // Under a finger it keeps up; left to itself it settles with a spring.
                            animationSpec = if (dockDragIndex != null) tween(90) else spring(dampingRatio = 0.75f, stiffness = 420f),
                            label = "dockPill",
                        )
                        // The bar itself: the backdrop blurred, tinted with the surface colour so it
                        // still reads as a bar in either theme. It holds no items - they are in the
                        // Row below, over the pill.
                        Box(
                            Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(30.dp))
                                // The shape goes to hazeChild itself: it paints the blurred backdrop
                                // to the shape it is given, and its default is a rectangle - which is
                                // what put a square panel behind the rounded bar.
                                .hazeChild(
                                    state = dockHaze,
                                    shape = RoundedCornerShape(30.dp),
                                    style = HazeStyle(
                                        // iOS's bar on black reads #191919 (the toolbar glass)
                                        tint = com.kachat.app.ui.screens.toolbarGlassFill().copy(alpha = 0.92f),
                                        blurRadius = 24.dp,
                                    ),
                                )
                                .border(0.5.dp, com.kachat.app.ui.screens.toolbarGlassEdge(), RoundedCornerShape(30.dp)),
                        )
                        // Drawn between the bar and its icons: a brighter pane of the same glass.
                        // Also while a finger is sliding: on a screen that is not a dock tab nothing is
                        // lit, and the slide used to carry no lens at all - it looked like it had not
                        // registered, though releasing still went to the tab.
                        if (dockDragIndex == null && selectedDockIndex >= 0) {
                            // At rest iOS 26 marks the tab with a filled capsule - #3A3A3A on the
                            // #191919 bar, 4pt inside its height (measured on the iPhone) - and no rim.
                            val restShape = RoundedCornerShape(percent = 50)
                            Box(
                                Modifier
                                    .offset(x = pillOffset)
                                    .padding(vertical = 4.dp)
                                    .width(dockItemWidth)
                                    .height(52.dp)
                                    .clip(restShape)
                                    .background(
                                        if (LocalAppColors.current.isDark) Color(0xFF3A3A3A)
                                        else Color.Black.copy(alpha = 0.07f)
                                    ),
                            )
                        } else if (dockDragIndex != null) {
                            // While a finger slides, the lens: a nearly clear bubble with a bright
                            // rim that stands a little proud of the bar, as iOS's does.
                            val pillShape = RoundedCornerShape(percent = 50)
                            Box(
                                Modifier
                                    .offset(x = pillOffset - 3.dp, y = (-4).dp)
                                    .width(dockItemWidth + 6.dp)
                                    .height(68.dp)
                                    .clip(pillShape)
                                    .hazeChild(
                                        state = dockHaze,
                                        shape = pillShape,
                                        style = HazeStyle(
                                            tint = Color.White.copy(alpha = 0.06f),
                                            blurRadius = 30.dp,
                                        ),
                                    )
                                    // The rim catches the light round its edge rather than being one
                                    // flat stroke - iOS's is iridescent.
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.55f),
                                                KaspaTeal.copy(alpha = 0.55f),
                                                Color.White.copy(alpha = 0.30f),
                                            ),
                                        ),
                                        shape = pillShape,
                                    ),
                            )
                        }
                        val dockDensity = LocalDensity.current
                        Row(
                            modifier = Modifier
                                .height(60.dp)
                                .fillMaxWidth()
                                // Watched on the INITIAL pass, so a slide can take over from the
                                // items underneath. A press that never moves is left to their tap;
                                // anything that travels carries the lens, whether it slides straight
                                // away or is held first, as on the iPhone. Nothing else on the bar
                                // wants this finger - tabs are arranged in Settings, not by dragging.
                                // Gesture navigation reserves a strip at each screen edge for Back,
                                // wider than the dock's side margin at higher Back sensitivity. A slide
                                // starting on the outermost tab began inside it, and Android took it.
                                .systemGestureExclusion()
                                .pointerInput(localTabOrder, dockItemWidth) {
                                    val slotPx = with(dockDensity) { dockItemWidth.toPx() }
                                    val startPadPx = with(dockDensity) { 2.dp.toPx() }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        var sliding = false
                                        try {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                if (sliding) {
                                                    dockDragIndex?.let { index ->
                                                        localTabOrder.getOrNull(index)?.let { latestGoToDockTab(it) }
                                                    }
                                                }
                                                dockDragIndex = null
                                                break
                                            }
                                            val movedX = kotlin.math.abs(change.position.x - down.position.x)
                                            if (!sliding && movedX > viewConfiguration.touchSlop) sliding = true
                                            if (sliding) {
                                                change.consume()
                                                dockDragIndex = (((change.position.x - startPadPx) / slotPx).toInt())
                                                    .coerceIn(0, localTabOrder.lastIndex.coerceAtLeast(0))
                                            }
                                        }
                                        } finally {
                                            // A slide the system takes away - the back gesture, an
                                            // incoming call - cancels this rather than delivering a
                                            // release, and the lens used to stay stranded wherever the
                                            // finger was until the next touch.
                                            dockDragIndex = null
                                        }
                                    }
                                }
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            localTabOrder.forEach { screen ->
                                // Keyed by route (not position), so a tab's own state stays with that
                                // tab when the arrangement changes in Settings rather than with
                                // whichever slot happens to render it.
                                key(screen.route) {
                                    // Standing ON this tab's own route, which is what the reselect
                                    // gesture below keys off.
                                    val onOwnRoute =
                                        currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                    // Cold Storage's account and address-history screens keep the
                                    // dock, so the Storage tab stays lit while you are down there
                                    // rather than the bar going blank - iOS's tab bar does. Lit only:
                                    // a tap from down there still has to pop back up to the list, so
                                    // this must not feed the reselect check.
                                    // Lit by the same answer the glass pill sits under - see
                                    // selectedDockIndex - so the two can never disagree. Lit only: a
                                    // tap from a screen below the tab still has to pop back up to it,
                                    // so this must not feed the reselect check (onOwnRoute does).
                                    val selected = localTabOrder.getOrNull(selectedDockIndex) == screen
                                    Box(
                                        modifier = Modifier
                                            .height(52.dp)
                                            .weight(1f)
                                            .clip(RoundedCornerShape(26.dp))
                                            .clickable {
                                                // Every KaPosts full-screen screen closes, so the tab is
                                                // what shows (iOS `.kaPostsCloseCovers`).
                                                com.kachat.app.ui.screens.KaPostsDock.closeCovers.tryEmit(Unit)
                                                // Already there — let that screen know it was re-tapped (so it can
                                                // dismiss its own transient UI, e.g. a full-screen QR overlay) rather
                                                // than running the popBackStack/navigate logic below, which for a tab
                                                // with nothing "pushed" above it falls through to navigate() and
                                                // lands back on the graph's start destination (Chats) instead of
                                                // staying put.
                                                if (onOwnRoute) {
                                                    walletViewModel.notifyTabReselected(screen.route)
                                                    return@clickable
                                                }

                                                // Tapping back to the graph's start destination (Chats) from a
                                                // "pushed" screen like Broadcasts via navigate()+popUpTo alone is
                                                // silently a no-op in Navigation Compose — popBackStack to the
                                                // route directly first (it's already on the back stack) actually
                                                // pops it. Only fall back to navigate() when the tab isn't already
                                                // present on the stack (first visit to a peer tab).
                                                val poppedToExisting = navController.popBackStack(
                                                    route = screen.route,
                                                    inclusive = false,
                                                    saveState = true
                                                )
                                                if (!poppedToExisting) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box {
                                                Icon(
                                                    painter = screen.tabIconPainter(),
                                                    contentDescription = screen.label,
                                                    tint = if (selected) KaspaTeal else LocalAppColors.current.textPrimary,
                                                    modifier = screen.tabIconModifier(24.dp)
                                                )
                                                // How many are waiting, not just that some are. A
                                                // bare dot said "something happened"; the number is
                                                // the difference between deciding to look now and
                                                // deciding to look later, and the dock is where that
                                                // decision gets made.
                                                // Notifications carries KaPosts' unseen count (iOS
                                                // KaPosts KaPostsTabView `.badge`).
                                                val badgeCount = when (screen) {
                                                    Screen.Notifications -> dockKaPostsUnseen
                                                    else -> 0
                                                }
                                                if (badgeCount > 0) {
                                                    // A BOX with a minimum square size, not a Text
                                                    // with padding. Padding alone gave a one-digit
                                                    // badge its text's width plus 8dp against its
                                                    // height plus 2dp - wider than it was tall, which
                                                    // is the oval. defaultMinSize floors both to the
                                                    // same 16dp, so one digit is a circle and only a
                                                    // longer label stretches it into a pill.
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            // Offset out over the icon's corner so a
                                                            // two- or three-character badge grows
                                                            // outward instead of shoving the icon.
                                                            .offset(x = 8.dp, y = (-6).dp)
                                                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                                            .clip(RoundedCornerShape(50))
                                                            .background(Color(0xFFE0245E))
                                                            .padding(horizontal = 4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            // Capped in the LABEL, not in the stored
                                                            // count: the real number survives a long
                                                            // absence and only its rendering shortens.
                                                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = screen.label,
                                                color = if (selected) KaspaTeal else LocalAppColors.current.textPrimary,
                                                // Longer labels ("Public Chats") don't fit at 10sp once there are
                                                // enough tabs that each weight(1f) slot narrows below their natural
                                                // width — shrink just those instead of letting them clip/wrap and
                                                // get cut off by the fixed-height Box. Twelve characters need a third
                                                // step: at six tabs on a 320dp screen a slot is ~53dp, which 8sp
                                                // would run past, and softWrap = false clips rather than wraps.
                                                fontSize = when {
                                                    screen.label.length > 11 -> 7.sp
                                                    screen.label.length > 9 -> 8.sp
                                                    else -> 10.sp
                                                },
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                // iOS 26's tab labels are medium weight, selected or not -
                                                // only the colour marks the selected tab.
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                        } // key(dockArrangementKey)
                        }
                    }
                }
        }
        }
    ) { innerPadding ->
        // The green dot's half sheet, hosted once for the whole app rather than by each of the
        // eight headers that draw a dot - so it comes up OVER whatever you were reading.
        ConnectionStatusOverlayHost()

        // Only the bottom-tab destinations (Settings/Chats/Profile) sit inside this
        // shell's floating nav bar and need innerPadding reserved beneath them.
        // "Pushed" detail screens (chat thread, settings sub-screens, etc.) fill the
        // whole screen with their own Scaffold — applying innerPadding to the NavHost
        // as a whole left permanent dead space at the bottom of every one of those,
        // which became a visible gap once a Scaffold there also added imePadding().
        // Settings is a sheet on iOS, so its whole stack draws with the sheet palette - one step
        // lighter than a tab in dark mode (see IosSheetColors).
        // Always the same provider (only the value changes), so moving in and out of Settings
        // never rebuilds the NavHost.
        val inSettingsSheet = currentTopRoute in SETTINGS_SHEET_ROUTES
        // What a pushed screen keeps clear at the bottom for the dock floating over it (zero
        // while the dock steps aside for the keyboard). The tabs, the Wallet and a room run on
        // under the dock instead, through LocalDockInset.
        val pushedDockPad = if (dockShown) innerPadding.calculateBottomPadding() else 0.dp
        val baseColors = LocalAppColors.current
        CompositionLocalProvider(
            LocalAppColors provides if (inSettingsSheet && baseColors.isDark) com.kachat.app.ui.theme.DarkSheetAppColors else baseColors
        ) {
        NavHost(
            navController = navController,
            startDestination = Screen.KaPosts.route,
            // In the Settings stack the whole host draws as iOS's sheet: its rounded top 10 below
            // the status bar, black above it. The status-bar padding is consumed here, so the
            // settings screens' own statusBarsPadding adds nothing inside the sheet.
            // One fixed modifier chain: only the values change with inSettingsSheet, so no inset
            // modifier ever comes or goes (swapping one in and out is what froze the public room
            // with an endless consumed-insets loop). Outside Settings the insets are zero, the
            // padding 0 and the clip a plain rectangle - the same as no modifier at all.
            // What the dock's glass samples.
            modifier = Modifier
                .windowInsetsPadding(if (inSettingsSheet) WindowInsets.statusBars else WindowInsets(0))
                .padding(top = if (inSettingsSheet) 10.dp else 0.dp)
                .clip(if (inSettingsSheet) com.kachat.app.ui.theme.IosSheetShape else androidx.compose.ui.graphics.RectangleShape)
                .haze(state = dockHaze),
            // NavHost's own default is a 700ms crossfade — noticeably sluggish for something
            // that happens on every single tab switch/screen push. A short, snappy fade reads
            // as instant without the jarring hard-cut of no animation at all.
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) }
        ) {
            // The Settings tab (iOS KaPosts SettingsView): its list stops above the dock.
            composable(
                Screen.Settings.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                Box(modifier = Modifier.background(LocalAppColors.current.background).padding(bottom = innerPadding.calculateBottomPadding())) {
                    SettingsScreen(navController, walletViewModel = walletViewModel)
                }
            }
            // Your Wallet, pushed from the Wallet button on your Profile, so the dock stays
            // underneath (iOS KaPosts 865c9ab). Runs on under the dock; its Receive / Send bar is
            // lifted above it (LocalDockInset).
            composable(Screen.Wallet.route) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    IdentityAddressDetailScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = walletViewModel,
                        title = "Wallet",
                    )
                }
            }
            // The Notifications tab (iOS KaPosts `KaPostsNotificationsView`); runs on under the dock.
            composable(
                Screen.Notifications.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    com.kachat.app.ui.screens.KaPostsNotificationsTab(navController = navController)
                }
            }
            composable(
                Screen.KaPosts.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    KaPostsScreen(navController, walletViewModel = walletViewModel)
                }
            }
            composable(
                Screen.Profile.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                // Your own KaPosts profile (iOS KaPosts `KaPostsView(rootMode: .myProfile)`).
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    KaPostsScreen(navController, walletViewModel = walletViewModel, rootMode = KaPostsRootMode.MY_PROFILE)
                }
            }

            composable("seed_phrase") {
                AboveDock(pushedDockPad) {
                    SeedPhraseScreen(
                        viewModel = walletViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("edit_kachat_profile") {
                AboveDock(pushedDockPad) {
                    com.kachat.app.ui.screens.KachatProfileEditorScreen(
                        onBack = { navController.popBackStack() },
                        showSetupGuides = walletViewModel.showSetupGuides.collectAsState().value,
                        onSetupGuide = { navController.navigate("kachat_setup_guide") },
                    )
                }
            }
            // The .kachat profile setup guide (iOS b000310) - from Edit KaChat Profile and Help.
            composable("kachat_setup_guide") {
                AboveDock(pushedDockPad) {
                    com.kachat.app.ui.screens.KachatSetupGuideScreen(onClose = { navController.popBackStack() })
                }
            }

            composable("settings_section/{sectionKey}") { backStackEntry ->
                AboveDock(pushedDockPad) {
                    SettingsScreen(
                        navController = navController,
                        sectionKey = backStackEntry.arguments?.getString("sectionKey")
                    )
                }
            }

            composable(
                "in_app_browser?url={url}&title={title}",
                arguments = listOf(
                    androidx.navigation.navArgument("url") { defaultValue = "" },
                    androidx.navigation.navArgument("title") { defaultValue = "" }
                )
            ) { backStackEntry ->
                AboveDock(pushedDockPad) {
                    InAppBrowserScreen(
                        url = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8"),
                        title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8"),
                        onClose = { navController.popBackStack() }
                    )
                }
            }

            composable("cache_settings") {
                AboveDock(pushedDockPad) {
                    val cacheManager: com.kachat.app.services.CacheManager = hiltViewModel<com.kachat.app.viewmodels.CacheViewModel>().cacheManager
                    CacheSettingsScreen(
                        onBack = { navController.popBackStack() },
                        cacheManager = cacheManager,
                    )
                }
            }

            composable("open_source_licenses") {
                AboveDock(pushedDockPad) {
                    OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
                }
            }

            composable("connection_settings") {
                AboveDock(pushedDockPad) {
                    ConnectionSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("language_settings") {
                AboveDock(pushedDockPad) {
                    LanguageSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("currency_settings") {
                AboveDock(pushedDockPad) {
                    CurrencySettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("kaspa_explorer_settings") {
                AboveDock(pushedDockPad) {
                    KaspaExplorerSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("notification_settings") {
                AboveDock(pushedDockPad) {
                    NotificationSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("kaposts_notification_settings") {
                AboveDock(pushedDockPad) {
                    com.kachat.app.ui.screens.KaPostsNotificationSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("quick_reaction_settings") {
                AboveDock(pushedDockPad) {
                    QuickReactionSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // A .kachat name a notification names (see KachatDeepLink above).
            composable(Screen.KachatNames.route) {
                AboveDock(pushedDockPad) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    com.kachat.app.ui.screens.KachatMarketScreen(
                        onBack = { navController.popBackStack() },
                        // Chats live in KaChat.
                        onOpenChat = { address -> com.kachat.app.ui.screens.openChatInKaChat(context, address) }
                    )
                }
            }

            composable(
                "broadcasts",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                // A dock/cycle page since 4.0 (see the Chats-slot cycle) with the floating
                // bottom nav visible over it: runs on under it, as Chats/Profile do.
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    BroadcastListScreen(
                        navController = navController,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                "broadcast_room_info/{channelName}",
                arguments = listOf(navArgument("channelName") { type = NavType.StringType })
            ) { backStackEntry ->
                val room = backStackEntry.arguments?.getString("channelName") ?: ""
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides innerPadding.calculateBottomPadding()) {
                    BroadcastRoomInfoScreen(
                        channelName = room,
                        onBack = { navController.popBackStack() },
                        onOpenHiddenUsers = { navController.navigate("broadcast_hidden_users/$room") }
                    )
                }
            }

            composable(
                "broadcast_hidden_users/{channelName}",
                arguments = listOf(navArgument("channelName") { type = NavType.StringType })
            ) { backStackEntry ->
                AboveDock(pushedDockPad) {
                    BroadcastHiddenUsersScreen(
                        channelName = backStackEntry.arguments?.getString("channelName") ?: "",
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("broadcast_channel/{channelName}") { backStackEntry ->
                val channelName = backStackEntry.arguments?.getString("channelName") ?: return@composable
                // The messages run on under the compose bar and the dock, as iOS's do under its
                // tab bar - KaPosts keeps the dock in a room (865c9ab); the compose bar floats just
                // above it (LocalDockInset). Only while the dock is actually visible: once the
                // keyboard opens the dock steps aside, and this screen's own bottomBar already
                // applies imePadding() to clear the keyboard itself — reserving both at once left a
                // dead black gap between the message input and the keyboard.
                CompositionLocalProvider(com.kachat.app.ui.screens.LocalDockInset provides if (dockShown) innerPadding.calculateBottomPadding() else 0.dp) {
                    BroadcastChannelScreen(
                        channelName = channelName,
                        onBack = { navController.popBackStack() },
                        navController = navController
                    )
                }
            }
        }
        }

        // iOS's in-app toast for every screen: it sits above the dock, as iOS's sits above the
        // tab bar; with the dock aside, above the navigation bar and keyboard.
        com.kachat.app.ui.screens.IosToastHost(
            modifier = Modifier.padding(bottom = if (dockShown) innerPadding.calculateBottomPadding() else 0.dp),
            bottomInsets = if (dockShown) {
                androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            } else {
                androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
            },
        )
    }
}

/**
 * A pushed screen under the floating dock: laid out above it, with the bottom it covers taken
 * off the window insets, so the screen's own navigation-bar padding is not added on top.
 */
@Composable
private fun AboveDock(bottom: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottom)
            .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = bottom)),
    ) {
        content()
    }
}
