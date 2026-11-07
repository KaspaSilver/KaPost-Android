package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kachat.app.R
import com.kachat.app.services.ColdStorageAddressDiscovery
import com.kachat.app.services.WalletManager
import com.kachat.app.services.WalletService
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.ui.theme.iosGlass
import com.kachat.app.ui.theme.iosShadow
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.showAddressCopiedToast
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// KaPay's full wallet in KaPost (iOS 071e901, owner's call 2026-11-07): the Wallet screen's
// account sections (`WalletAccountSections`), Manage Addresses (`ManageAddressesView`), a spending
// address's page, Address Visibility, Help, and the Receive and Chatting Address QR pages. Ported
// from KaPay Android (its Screens.kt). No Contacts: people use KaChat or KaPay for that. Your
// Domains has its own file.

// MARK: - Wallet screen sections (iOS 071e901 `WalletAccountSections`)

/** Manage Addresses and Chatting Address, two squares 14 apart (iOS 071e901 `addressSquares`). */
@Composable
internal fun WalletAddressSquares(onManageAddresses: () -> Unit, onChattingAddress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WalletAddressSquare(
            icon = Icons.AutoMirrored.Filled.ListAlt,
            title = stringResource(R.string.manage_addresses),
            modifier = Modifier.weight(1f),
            onClick = onManageAddresses,
        )
        WalletAddressSquare(
            icon = Icons.Filled.QrCode,
            title = stringResource(R.string.chatting_address),
            modifier = Modifier.weight(1f),
            onClick = onChattingAddress,
        )
    }
}

/**
 * One square (iOS 071e901 `addressSquare`): the accent symbol in a 48 material circle over its
 * subheadline-semibold title, on glass (radius 18). Square on a phone; as wide as half a tablet a
 * square would fill most of the screen with nothing, so there it keeps the width and a set height
 * (iOS `addressSquareShape`).
 */
@Composable
private fun WalletAddressSquare(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    Column(
        modifier = modifier
            .then(if (isWide) Modifier.height(170.dp) else Modifier.aspectRatio(1f))
            .iosGlass(18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(24.dp))
        }
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * A row of the Wallet screen under the squares (iOS 071e901 `linkRow`, and Your Domains' row):
 * a label with its symbol, an optional count in secondary, and the caption chevron, padded 16 on
 * glass (radius 18).
 */
@Composable
internal fun WalletLinkRow(label: String, icon: ImageVector, value: String? = null, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosGlass(18.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.textPrimary, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = colors.textSecondary, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        ChatChevronGlyph(height = 11.dp, stroke = 1.6.dp)
    }
}

/** The Wallet screen's Help row (iOS 071e901 `linkRow(title: "Help")`). */
@Composable
internal fun WalletHelpRow(onClick: () -> Unit) {
    WalletLinkRow(
        label = stringResource(R.string.help),
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        onClick = onClick,
    )
}

/**
 * Wallet > Help (iOS 071e901 `ProfileHelpView`): the .kachat names guide, one row, pushed with a
 * large title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletHelpScreen(onBack: () -> Unit, onKachatGuide: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = { MainPageHeader(title = stringResource(R.string.help), onBack = onBack, showStatus = false) },
        // Runs on under the dock, as iOS's content runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = LocalDockInset.current),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onKachatGuide)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // iOS helpRow: a 20pt icon in a 30pt frame, 14 apart; subheadline semibold
                    // over caption.
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Badge, null, tint = KaspaTeal, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.kg_names_help_title), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(R.string.kg_names_help_body), color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// MARK: - QR pages

/**
 * The Chatting Address square's page (iOS 071e901: `ChattingAddressQRView` pushed from the
 * Wallet): the white QR page with a back chevron, the dock left in place.
 */
@Composable
fun ChattingAddressQrRoute(viewModel: WalletViewModel, onBack: () -> Unit) {
    val address by viewModel.address.collectAsState()
    // The dock's glass goes light over the white page, as iOS's tab bar does.
    DisposableEffect(Unit) {
        DockOverLightPage.active = true
        onDispose { DockOverLightPage.active = false }
    }
    // The white runs up under the status bar and down under the dock, as iOS's page does.
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color.White, size = androidx.compose.ui.geometry.Size(size.width, size.height + 200.dp.toPx()))
        }
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = LocalDockInset.current)) {
            ChattingAddressQrPage(
                address = address.orEmpty(),
                balanceSompi = null,
                dismiss = QrPageDismiss.BACK,
                onDismiss = onBack,
            )
        }
    }
}

/**
 * Receive Kaspa (iOS 071e901 `ReceiveKaspaQRView`, as in KaPay): a fresh, never-used address -
 * [address], resolved by the caller with [WalletViewModel.resolveFreshReceiveAddress] - on the
 * white QR page titled "Receive Kaspa" with Close. Until it is known the page says "Preparing a
 * fresh address" under a spinner, or, once the lookup has found nothing, that the spending
 * address is still unlocking. Never an old address swapped later: a QR is scanned the instant it
 * appears.
 */
@Composable
fun ReceiveKaspaQrPage(address: String?, resolving: Boolean, onDismiss: () -> Unit) {
    val title = stringResource(R.string.receive_kaspa)
    if (address != null) {
        AddressQrPage(
            address = address,
            balanceSompi = null,
            caption = stringResource(R.string.receive_fresh_subtitle),
            dismiss = QrPageDismiss.CLOSE,
            onDismiss = onDismiss,
            showsBalance = false,
            title = title,
        )
        return
    }
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        AddressQrPageBar(dismiss = QrPageDismiss.CLOSE, onDismiss = onDismiss) {
            Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (resolving) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                    Text(stringResource(R.string.receive_preparing_fresh_address), color = colors.textSecondary, fontSize = 15.sp)
                }
            } else {
                Text(
                    stringResource(R.string.spending_address_unlocking),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A spending address's QR (iOS 071e901 `SpendingAddressQRView`): the code and the address on
 *  the white page, its name as the title, Close leading, in a sheet. */
@Composable
private fun SpendingAddressQrSheet(entry: WalletService.SpendingAddressEntry, onDismiss: () -> Unit) {
    QrPageSheet(onDismiss = onDismiss, white = true) {
        AddressQrPage(
            address = entry.address,
            balanceSompi = null,
            caption = null,
            dismiss = QrPageDismiss.CLOSE,
            onDismiss = onDismiss,
            showsBalance = false,
            title = entry.displayTitle,
        )
    }
}

// MARK: - Manage Addresses (iOS 071e901 `ManageAddressesView`)

/**
 * Every address of this account, from the Wallet's Manage Addresses square: the total, Notify on
 * receive, the chatting address first, then the spending addresses - unused ones first, newest
 * first, then the used ones in number order - each with its balance and whether it was used, and
 * an ellipsis for Rename, Copy, QR and Hide. The floating button (bottom right) opens Address
 * Actions: Generate, Discover and Address Visibility. Which address a send comes from is picked on
 * the send screen itself (From), so there is no "primary" here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAddressesScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
    onNavigateToTxHistory: (Int) -> Unit,
    /** The chatting address's own screen (history, UTXOs, Domains, Send / Receive). */
    onOpenChattingAddress: () -> Unit,
    onNavigateToVisibility: () -> Unit,
    settingsViewModel: com.kachat.app.viewmodels.SettingsViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var qrEntry by remember { mutableStateOf<WalletService.SpendingAddressEntry?>(null) }
    var renamingEntry by remember { mutableStateOf<WalletService.SpendingAddressEntry?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showActionsMenu by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val isDiscoveringAddresses by viewModel.discoveringAddresses.collectAsState()
    val discoveryProgress by viewModel.spendingDiscoveryProgress.collectAsState()
    var discoverySummary by remember { mutableStateOf<String?>(null) }
    val pullRefreshState = rememberPullToRefreshState()
    val noneFoundText = stringResource(R.string.ma_none_found)
    val hiddenText = stringResource(R.string.ma_address_hidden)
    val cantHideText = stringResource(R.string.ma_cant_hide)

    val domainOwningAddresses by viewModel.domainOwningAddresses.collectAsState()
    // Quantum-safe rows whose key a spend has put on chain (their shield is off for good).
    val quantumSpentAddresses by viewModel.quantumSpentAddresses.collectAsState()
    // Unused ones first, newest first - so a freshly generated address lands right under the
    // Chatting Address - then the used ones in number order (iOS `sortedEntries`).
    val visibleAddresses = remember(addresses) {
        fun used(a: WalletService.SpendingAddressEntry) = a.everUsed || a.balanceSompi > 0
        addresses.filterNot { it.hidden }.sortedWith { a, b ->
            val aUsed = used(a)
            val bUsed = used(b)
            when {
                aUsed != bUsed -> if (aUsed) 1 else -1
                aUsed -> a.index.compareTo(b.index)
                else -> b.index.compareTo(a.index)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadManageAddresses() }
    // Funds landing refresh the balances without a pull (iOS `.ownAddressUtxoActivity`).
    val activityChatViewModel: com.kachat.app.viewmodels.ChatViewModel =
        hiltViewModel(LocalContext.current as androidx.activity.ComponentActivity)
    LaunchedEffect(Unit) {
        activityChatViewModel.ownAddressUtxoActivityEvents.collect { viewModel.loadManageAddresses() }
    }
    // Pull to refresh owns its spinner: await the reload, then end it.
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            viewModel.loadManageAddressesAndAwait()
            pullRefreshState.endRefresh()
        }
    }

    val dockInset = LocalDockInset.current
    Scaffold(
        containerColor = colors.background,
        // Runs on under the dock, as iOS's list runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.manage_addresses), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                // An idle PullToRefreshContainer parks above its box; clip it out of the bar.
                .clipToBounds()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // iOS `accountSummary`: everything this account holds, hidden addresses too.
                item(key = "summary") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(stringResource(R.string.total_balance), color = colors.textSecondary, fontSize = 12.sp)
                        Text(
                            "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, (chattingBalanceSompi + addresses.sumOf { it.balanceSompi }) / 100_000_000.0),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        )
                    }
                }
                // This account's own switch: the spending addresses' receives, not every address
                // the wallet watches. A plain row under the summary, no card.
                item(key = "notify") {
                    val spendingNotify by settingsViewModel.spendingReceiveNotifications.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.ma_notify_on_receive), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(stringResource(R.string.ma_notify_on_receive_detail), color = colors.textSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        com.kachat.app.ui.theme.IosSwitch(
                            checked = spendingNotify,
                            onCheckedChange = { settingsViewModel.setSpendingReceiveNotifications(it) },
                        )
                    }
                }
                // The chatting address, first: it opens its own screen.
                chattingAddress?.let { chatting ->
                    item(key = "chatting") {
                        ManageChattingAddressRow(
                            address = chatting,
                            balanceSompi = chattingBalanceSompi,
                            onClick = onOpenChattingAddress,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                if (loading && addresses.isEmpty()) {
                    item(key = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                            com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal)
                        }
                    }
                } else if (visibleAddresses.isEmpty()) {
                    // iOS `emptyState`.
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CreditCard, null, tint = colors.textSecondary, modifier = Modifier.size(40.dp))
                            Text(stringResource(R.string.ma_no_spending_addresses), color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.ma_generate_one_below),
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(visibleAddresses, key = { it.index }) { entry ->
                        ManageAddressRow(
                            entry = entry,
                            showsDomainTag = entry.address in domainOwningAddresses,
                            quantumSpent = entry.address.lowercase() in quantumSpentAddresses,
                            onClick = { onNavigateToTxHistory(entry.index) },
                            onCopyClick = {
                                clipboardManager.setText(AnnotatedString(entry.address))
                                showAddressCopiedToast(context, entry.address)
                            },
                            onQrClick = { qrEntry = entry },
                            onRenameClick = { renamingEntry = entry; renameInput = entry.label ?: "" },
                            onHideClick = {
                                // Any address, funded too: it comes back from Address Visibility.
                                viewModel.setManageAddressHidden(entry.index, true) { ok ->
                                    IosToasts.show(if (ok) hiddenText else cantHideText)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                // Room to scroll the last row out from under the floating button and the dock.
                item(key = "foot") { Spacer(Modifier.height(72.dp + dockInset)) }
            }

            if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) {
                PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
            }

            // The floating glass button behind "Address Actions" (iOS `addressActionsButton`).
            val busy = isGenerating || isDiscoveringAddresses
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp + dockInset)
                    .size(56.dp)
                    .iosShadow(28.dp, Color.Black.copy(alpha = 0.12f), 10.dp, 5.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(enabled = !busy) { showActionsMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary)
                } else {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.address_actions), tint = KaspaTeal, modifier = Modifier.size(26.dp))
                }
            }
        }
    }

    if (showActionsMenu) {
        ManageAddressesActionsSheet(
            isDiscovering = isDiscoveringAddresses,
            progress = discoveryProgress,
            summary = discoverySummary,
            onGenerate = {
                showActionsMenu = false
                isGenerating = true
                viewModel.generateNewSpendingAddress { index ->
                    isGenerating = false
                    // The number people see (iOS 071e901 `WalletManager.addressNumber`).
                    if (index != null) IosToasts.show("${WalletManager.spendingAddressTitle(index)} is ready.")
                    else IosToasts.error("Could not check addresses. Try again when connected.")
                }
            },
            onDiscover = {
                discoverySummary = null
                viewModel.discoverSpendingAddresses { count ->
                    // Addresses that hold a balance or a domain - not "used".
                    val summary = if (count == 0) noneFoundText
                    else "Found $count address${if (count == 1) "" else "es"} with a balance or domain."
                    discoverySummary = summary
                    // Closed the sheet and carried on? Then say it here instead of finishing silently.
                    if (!showActionsMenu) IosToasts.show(summary)
                }
            },
            onVisibility = { showActionsMenu = false; onNavigateToVisibility() },
            onDismiss = { showActionsMenu = false; discoverySummary = null },
        )
    }

    qrEntry?.let { entry -> SpendingAddressQrSheet(entry = entry, onDismiss = { qrEntry = null }) }

    renamingEntry?.let { entry ->
        RenameAddressDialog(
            index = entry.index,
            nameInput = renameInput,
            onNameChange = { renameInput = it },
            onDismiss = { renamingEntry = null },
            onSave = {
                viewModel.setManageAddressLabel(entry.index, renameInput)
                renamingEntry = null
            },
        )
    }
}

/** Rename Address (iOS alert): an empty name clears back to the default "Address #N". */
@Composable
private fun RenameAddressDialog(
    index: Int,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.rename_address), color = colors.textPrimary) },
        text = {
            Column {
                Text(stringResource(R.string.ma_rename_message), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                com.kachat.app.ui.theme.IosTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.ma_label)) },
                    placeholder = { Text(WalletManager.spendingAddressTitle(index)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = colors.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = colors.textSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = colors.textSecondary)
            }
        },
    )
}

/** "Contains domain": the capsule on an address row that owns at least one domain (iOS
 *  `ContainsDomainTag`). */
@Composable
fun ContainsDomainTag() {
    Text(
        text = stringResource(R.string.contains_domain),
        color = KaspaTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(KaspaTeal.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * One spending address on Manage Addresses (iOS `addressRow`): a glass card (radius 18), its name
 * and quantum shield, the short address, the balance, and Used / Unused / Checking - never a
 * guess: "Unused" only when this load's live check confirmed it - then the upright ellipsis that
 * opens its half sheet.
 */
@Composable
private fun ManageAddressRow(
    entry: WalletService.SpendingAddressEntry,
    onClick: () -> Unit,
    onCopyClick: () -> Unit,
    onQrClick: () -> Unit,
    onRenameClick: () -> Unit,
    onHideClick: () -> Unit,
    showsDomainTag: Boolean,
    quantumSpent: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.displayLabel, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (entry.isQuantum) QuantumShield(spent = quantumSpent, funded = entry.balanceSompi > 0L)
            }
            Text(
                "${entry.address.take(14)}...${entry.address.takeLast(6)}",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, entry.balanceSompi / 100_000_000.0),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val (usedText, usedColor) = when {
                    entry.everUsed || entry.balanceSompi > 0 -> stringResource(R.string.pr_used) to colors.warning
                    entry.liveChecked -> stringResource(R.string.pr_unused) to colors.success
                    else -> stringResource(R.string.pr_checking) to colors.textSecondary
                }
                Text(usedText, color = usedColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (showsDomainTag) ContainsDomainTag()
            }
        }
        // iOS: a bold "ellipsis" turned upright, secondary, in a 24 frame.
        Box(
            modifier = Modifier.size(24.dp).clickable { showMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.address_actions), tint = colors.textSecondary, modifier = Modifier.requiredSize(28.dp))
        }
    }

    // A half sheet rather than a menu: each row has room to say what it does (iOS
    // `addressRowActionsSheet`).
    if (showMenu) {
        AddressRowActionsSheet(
            title = entry.displayLabel,
            subtitle = "${entry.address.take(14)}...${entry.address.takeLast(6)}",
            onDismiss = { showMenu = false },
        ) {
            ActionSheetRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.rename_address),
                subtitle = stringResource(R.string.ma_rename_detail),
            ) { showMenu = false; onRenameClick() }
            ActionSheetRow(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.copy_address),
                subtitle = stringResource(R.string.ma_copy_detail),
            ) { showMenu = false; onCopyClick() }
            ActionSheetRow(
                icon = Icons.Default.QrCode,
                title = stringResource(R.string.show_qr_code),
                subtitle = stringResource(R.string.ma_qr_detail),
            ) { showMenu = false; onQrClick() }
            // Same effect as unchecking it in Address Visibility. Any address, funded too.
            ActionSheetRow(
                icon = Icons.Default.VisibilityOff,
                title = stringResource(R.string.ma_hide_address),
                subtitle = stringResource(R.string.ma_hide_detail),
                tint = LocalAppColors.current.warning,
            ) { showMenu = false; onHideClick() }
        }
    }
}

/**
 * The quantum (KIP-22) state of a row (iOS `quantumShield`): a green shield while the key has
 * never been on chain; a slashed one once the address spent - orange when funds sit on it again,
 * grey when empty.
 */
@Composable
private fun QuantumShield(spent: Boolean, funded: Boolean) {
    if (spent) {
        Icon(
            Icons.Filled.RemoveModerator,
            contentDescription = stringResource(R.string.key_revealed_by_a_spend),
            tint = if (funded) LocalAppColors.current.warning else LocalAppColors.current.textSecondary,
            modifier = Modifier.size(11.dp),
        )
    } else {
        Icon(
            Icons.Filled.Shield,
            contentDescription = stringResource(R.string.quantum_safe),
            tint = LocalAppColors.current.success,
            modifier = Modifier.size(11.dp),
        )
    }
}

/** A row's half sheet: its name and short address on top, then the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressRowActionsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(subtitle, color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    content()
                }
            }
        }
    }
}

/**
 * The chatting address's row at the top of Manage Addresses (iOS `chattingAddressRow`): "Chatting
 * Address" with its icon, the short address, its balance and "Used", and a chevron.
 */
@Composable
private fun ManageChattingAddressRow(address: String, balanceSompi: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .manageAddressGlass()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.chatting_address), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.Forum, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(14.dp))
            }
            Text(
                "${address.take(14)}...${address.takeLast(6)}",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                "%.8f ${KaspaUnit.symbol}".format(java.util.Locale.US, balanceSompi / 100_000_000.0),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.pr_used), color = colors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp).offset(x = 6.dp),
        )
    }
}

/** iOS ManageAddressesView `glassBackground(cornerRadius: 18)` behind the address cards. */
@Composable
private fun Modifier.manageAddressGlass(): Modifier {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    val fill = if (colors.isDark) Color(0xFF202020) else colors.surface
    return this
        .iosShadow(18.dp, Color.Black.copy(alpha = 0.12f), 10.dp, 5.dp)
        .clip(shape)
        .background(fill)
        .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
}

/**
 * The half sheet behind Address Actions (iOS `addressActionsSheet`): Generate New Spending
 * Address, Discover Addresses and Address Visibility. While discovery walks the addresses it
 * counts up here; closing the sheet leaves the scan running, and its result arrives as a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageAddressesActionsSheet(
    isDiscovering: Boolean,
    progress: com.kachat.app.services.SpendingAddressDiscovery.DiscoveryProgress?,
    summary: String?,
    onGenerate: () -> Unit,
    onDiscover: () -> Unit,
    onVisibility: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.address_actions), color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (isDiscovering) {
                        Spacer(Modifier.height(8.dp))
                        com.kachat.app.ui.theme.IosActivityIndicator(color = KaspaTeal, modifier = Modifier.size(32.dp))
                        Text(
                            "Checking address #${progress?.checkingIndex ?: 0}",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Text(
                            if ((progress?.foundCount ?: 0) == 0) stringResource(R.string.ma_none_yet)
                            else "${progress?.foundCount} found so far",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            stringResource(R.string.ma_scan_note),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        // The scan belongs to the view model, not the sheet: closing it stops nothing.
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.ma_close_keep_scanning), color = KaspaTeal, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        ActionSheetRow(
                            icon = Icons.Default.AddCircleOutline,
                            title = stringResource(R.string.generate_new_spending_address),
                            subtitle = stringResource(R.string.ma_generate_detail),
                            onClick = onGenerate,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Search,
                            title = stringResource(R.string.discover_addresses),
                            subtitle = stringResource(R.string.finds_addresses_balance_or_domain),
                            onClick = onDiscover,
                        )
                        ActionSheetRow(
                            icon = Icons.Default.Checklist,
                            title = stringResource(R.string.address_visibility),
                            subtitle = stringResource(R.string.ma_visibility_detail),
                            onClick = onVisibility,
                        )
                        if (summary != null) {
                            Text(summary, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Address Visibility (iOS 071e901 `SpendingAddressVisibilityView`)

/**
 * Address Visibility, from Address Actions: a card per chain - Quantum Addresses and Non-Quantum
 * Addresses, with how many of each Manage Addresses shows - then that chain's checklist. Hide All
 * (confirmed, leading) hides every revealed address on both chains, starting the list over with
 * only the chatting address; Done (trailing) goes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressVisibilityScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val loading by viewModel.manageAddressesLoading.collectAsState()
    var showHideAllConfirm by remember { mutableStateOf(false) }
    // The chain whose card was tapped (true = quantum), null on the cards themselves.
    var openedChain by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Fresh flags on entry: the checklist's toggles read them.
    LaunchedEffect(Unit) { viewModel.loadManageAddresses() }

    openedChain?.let { quantum ->
        BackHandler { openedChain = null }
        AddressVisibilityChainList(viewModel = viewModel, quantum = quantum, onBack = { openedChain = null })
        return
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.address_visibility), color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarButton(stringResource(R.string.hide_all), onClick = { showHideAllConfirm = true }, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    SheetBarButton(stringResource(R.string.done), onClick = onBack, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
    ) { padding ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Quantum first.
            listOf(true, false).forEach { quantum ->
                val chain = addresses.filter { it.isQuantum == quantum }
                AddressVisibilityChainCard(
                    quantum = quantum,
                    shown = chain.count { !it.hidden },
                    total = chain.size,
                    loading = loading,
                    onClick = { openedChain = quantum },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showHideAllConfirm) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showHideAllConfirm = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.hide_all_addresses_title), color = colors.textPrimary) },
            text = { Text(stringResource(R.string.hide_all_addresses_message), color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showHideAllConfirm = false
                    viewModel.hideAllSpendingAddresses()
                }) {
                    Text(stringResource(R.string.hide_all), color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideAllConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            },
        )
    }
}

/** A chain's square (iOS `chainCard`): its glyph, its name, and "n of m shown". */
@Composable
private fun AddressVisibilityChainCard(
    quantum: Boolean,
    shown: Int,
    total: Int,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(colors.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (quantum) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(26.dp))
            } else {
                // "circle.grid.2x2.fill": four dots in a square.
                androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                    val r = size.minDimension * 0.2f
                    for (cx in listOf(0.27f, 0.73f)) for (cy in listOf(0.27f, 0.73f)) {
                        drawCircle(KaspaTeal, radius = r, center = Offset(size.width * cx, size.height * cy))
                    }
                }
            }
            Text(
                stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (loading) {
                com.kachat.app.ui.theme.IosActivityIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.addresses_shown_count, shown, total), color = colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * One chain's checklist (iOS `AddressVisibilityChainList`): every address on it, 50 a page from
 * the chain's own start, numbered as people see them (`WalletManager.addressNumber`). The legacy
 * chain's first page also holds #0, the Chatting Address, always shown and checked. The right
 * arrow never runs out: future pages derive addresses past the revealed bound, and checking one
 * reveals it while the indices in between stay hidden. Every address toggles both ways, funded
 * ones too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressVisibilityChainList(viewModel: WalletViewModel, quantum: Boolean, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val chattingAddress by viewModel.address.collectAsState()
    val chattingBalanceSompi by viewModel.balanceSompi.collectAsState()
    var page by remember { mutableStateOf(0) }
    val pageSize = 50
    val byIndex = remember(addresses) { addresses.associateBy { it.index } }
    // The legacy bound: a quantum row's index sits a million up and must not stretch it.
    val listMax = remember(addresses) { addresses.filterNot { it.isQuantum }.maxOfOrNull { it.index } ?: -1 }
    // This chain's own revealed bound - past it, rows are derived on the fly.
    val chainMax = if (quantum) {
        addresses.filter { it.isQuantum }.maxOfOrNull { it.index } ?: (WalletManager.QUANTUM_INDEX_BASE - 1)
    } else {
        listMax
    }
    val usedCache = remember { mutableStateMapOf<Int, Boolean>() }
    val chainBase = if (quantum) WalletManager.QUANTUM_INDEX_BASE else 0
    val start = chainBase + page * pageSize
    val end = start + pageSize - 1
    // The numbers on this page; the legacy chain's first page also holds #0 (iOS `pagerRange`).
    val pagerLower = if (!quantum && page == 0) 0 else WalletManager.addressNumber(start)
    val pagerUpper = WalletManager.addressNumber(end)
    // Rows past the loaded list derive off the main thread, once per page, the seed paid once.
    var derivedAddresses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var derivedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(page, chainMax) {
        val needed = (start..end).filter { it > chainMax }
        if (needed.isEmpty()) {
            derivedAddresses = emptyMap()
            derivedPage = page
            return@LaunchedEffect
        }
        derivedPage = null
        derivedAddresses = withContext(Dispatchers.Default) { viewModel.spendingAddressesAt(needed) }
        derivedPage = page
    }
    // Used badges for derived rows, one at a time; used-ness never goes back.
    LaunchedEffect(page, derivedAddresses) {
        for ((index, address) in derivedAddresses) {
            if (address.isEmpty() || index in usedCache) continue
            usedCache[index] = viewModel.hasSpendingAddressBeenUsed(address)
            delay(60)
        }
    }
    val pageEntries = remember(byIndex, page, derivedAddresses, derivedPage) {
        (start..end).mapNotNull { index ->
            if (index !in byIndex && derivedPage == page && derivedAddresses[index].isNullOrEmpty()) return@mapNotNull null
            byIndex[index] ?: WalletService.SpendingAddressEntry(
                index = index,
                address = derivedAddresses[index].orEmpty(),
                balanceSompi = 0L,
                everUsed = false,
                isCurrent = false,
                hidden = true,
                label = null,
            )
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(if (quantum) R.string.quantum_addresses else R.string.non_quantum_addresses),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (page > 0) page -= 1 }, enabled = page > 0) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        "Previous page",
                        tint = if (page > 0) KaspaTeal else colors.textSecondary.copy(alpha = 0.4f),
                    )
                }
                Text(
                    "#$pagerLower - #$pagerUpper",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = { page += 1 }) {
                    Icon(Icons.Default.ChevronRight, "Next page", tint = KaspaTeal)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pageEntries.isEmpty()) {
                item(key = "derive_failed") {
                    Text(
                        stringResource(R.string.address_visibility_derive_failed),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            // #0: always shown on Manage Addresses, so it's checked and tapping it does nothing.
            val chatting = chattingAddress
            if (!quantum && page == 0 && chatting != null) {
                item(key = "chatting") {
                    VisibilityRowFrame(
                        visible = true,
                        dimmedCheck = true,
                        number = 0,
                        isQuantum = false,
                        label = stringResource(R.string.chatting_address),
                        address = chatting,
                        trailing = {
                            if (chattingBalanceSompi > 0) {
                                Text(
                                    "%.4f ${KaspaUnit.symbol}".format(java.util.Locale.US, chattingBalanceSompi / 100_000_000.0),
                                    color = KaspaTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        },
                        onToggle = null,
                    )
                }
            }
            items(pageEntries, key = { it.index }) { entry ->
                val funded = entry.balanceSompi > 0
                // A revealed quantum row sits a million past the legacy listMax, so it shows and
                // toggles by its own hidden flag; only a slot the list doesn't hold is revealed.
                val listedQuantum = entry.isQuantum && entry.index in byIndex
                val visible = (listedQuantum || entry.index <= listMax) && !entry.hidden
                val used = if (listedQuantum || entry.index <= listMax) entry.everUsed else usedCache[entry.index]
                VisibilityRowFrame(
                    visible = visible,
                    dimmedCheck = false,
                    number = WalletManager.addressNumber(entry.index),
                    isQuantum = entry.isQuantum,
                    label = entry.label?.takeIf { it.isNotBlank() },
                    address = entry.address,
                    trailing = {
                        when {
                            funded -> Text(
                                "%.4f ${KaspaUnit.symbol}".format(java.util.Locale.US, entry.balanceSompi / 100_000_000.0),
                                color = KaspaTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            used != null -> Text(
                                stringResource(if (used) R.string.pr_used else R.string.pr_unused),
                                color = if (used) colors.warning else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            else -> Text("…", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    },
                    onToggle = {
                        when {
                            listedQuantum -> viewModel.setManageAddressHidden(entry.index, !entry.hidden)
                            entry.index > listMax -> viewModel.revealSpendingAddress(entry.index)
                            else -> viewModel.setManageAddressHidden(entry.index, !entry.hidden)
                        }
                    },
                )
            }
        }
    }
}

/** One checklist row: the check, "#n" (with the shield on the quantum chain) and its name, the
 *  short address, then [trailing]. The whole row toggles; [onToggle] null leaves it fixed. */
@Composable
private fun VisibilityRowFrame(
    visible: Boolean,
    dimmedCheck: Boolean,
    number: Int,
    isQuantum: Boolean,
    label: String?,
    address: String,
    trailing: @Composable () -> Unit,
    onToggle: (() -> Unit)?,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .alpha(if (visible) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (visible) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            if (visible) "Visible" else "Hidden",
            tint = if (visible) KaspaTeal else colors.textSecondary,
            modifier = Modifier.size(24.dp).alpha(if (dimmedCheck) 0.45f else 1f),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("#$number", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (isQuantum) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = stringResource(R.string.quantum_safe),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                }
                label?.let { Text(it, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1) }
            }
            Text(
                if (address.isNotEmpty()) "${address.take(16)}…${address.takeLast(6)}" else "…",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        trailing()
    }
}

// MARK: - A spending address's page (iOS 071e901 `SpendingAddressTransactionHistoryView`)

/**
 * One spending address, from its Manage Addresses row: the balance, History / UTXOs / Domains,
 * Export (its own private key, behind the address keys' lock) and Explorer in the bar, and
 * Receive and Send at the foot. Send is Send Kaspa with this address as the From and no other to
 * pick; Compound UTXOs combines its coins.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAddressTxHistoryScreen(
    index: Int,
    onBack: () -> Unit,
    viewModel: WalletViewModel,
    portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel(),
) {
    val colors = LocalAppColors.current
    val addresses by viewModel.manageAddresses.collectAsState()
    val entry = remember(addresses, index) { addresses.firstOrNull { it.index == index } }
    val address = entry?.address.orEmpty()

    val txHistory by viewModel.spendingAddressTxHistory.collectAsState()
    val isLoadingTxHistory by viewModel.loadingSpendingAddressTxHistory.collectAsState()
    val txHistoryFailed by viewModel.spendingAddressTxHistoryFailed.collectAsState()
    val utxos by viewModel.spendingAddressUtxos.collectAsState()
    val isLoadingUtxos by viewModel.loadingSpendingAddressUtxos.collectAsState()
    val utxosLoadError by viewModel.spendingAddressUtxosError.collectAsState()
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()
    val biometricSpendingKeyEnabled by viewModel.biometricSpendingKeyEnabled.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var transactionActionTarget by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    var portfolioCandidate by remember { mutableStateOf<ColdStorageAddressDiscovery.AddressTransaction?>(null) }
    var addedPortfolioName by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showCompoundFlow by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }
    var utxoLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var labelingUtxoKey by remember { mutableStateOf<String?>(null) }
    var labelInput by remember { mutableStateOf("") }
    var openedKachat by remember { mutableStateOf<com.kachat.app.services.kachatnames.NameInfo?>(null) }
    var openedDomainService by remember { mutableStateOf<com.kachat.app.services.NameServiceTLD?>(null) }
    val unlockTitle = stringResource(R.string.unlock_address_private_key)

    LaunchedEffect(address) {
        if (address.isNotEmpty()) {
            viewModel.loadSpendingAddressTxHistory(address)
            viewModel.loadSpendingAddressUtxos(address)
            utxoLabels = viewModel.getSpendingUtxoLabels(address)
        }
    }

    openedKachat?.let { info ->
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { openedKachat = null }, onOpenChat = {}) }
        return
    }
    openedDomainService?.let { tld ->
        AboveDock {
            AddressDomainServiceScreen(
                address = address,
                tld = tld,
                onBack = { openedDomainService = null },
                onOpenKachat = { openedKachat = it },
            )
        }
        return
    }

    // Send from this address's own page: it is the From, with no other to pick.
    val sendChattingAddress by viewModel.address.collectAsState()
    val sendChattingBalance by viewModel.balanceSompi.collectAsState()
    val sendFrom = sendChattingAddress
    if (showWithdraw && entry != null && sendFrom != null) {
        IosFullSheet(
            onDismissed = {
                showWithdraw = false
                viewModel.loadSpendingAddressTxHistory(address)
                viewModel.loadSpendingAddressUtxos(address)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = sendFrom,
                balanceSompi = sendChattingBalance,
                title = "Send Kaspa",
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                onDone = close,
                presentedAsSheet = true,
                lockedSource = SendSource.Spending(entry.index, entry.address, entry.displayLabel),
            )
        }
    }
    if (showCompoundFlow && entry != null) {
        IosFullSheet(
            onDismissed = {
                showCompoundFlow = false
                viewModel.loadSpendingAddressTxHistory(address)
                viewModel.loadSpendingAddressUtxos(address)
            },
            swipeToDismiss = false,
        ) { close ->
            SpendingAddressSendFlow(
                fromAddress = entry.address,
                balanceSompi = entry.balanceSompi,
                title = "Send from ${entry.displayTitle}",
                spendingIndex = entry.index,
                viewModel = viewModel,
                portfolioViewModel = portfolioViewModel,
                isCompoundMode = true,
                onDone = close,
                presentedAsSheet = true,
            )
        }
    }

    val displayName = entry?.displayLabel ?: WalletManager.spendingAddressTitle(index)

    Scaffold(
        containerColor = colors.background,
        // Runs on under Receive / Send and the dock, as iOS's list runs on under its bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(displayName, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp).heightIn(min = 44.dp).barGlass(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolbarIconButton(Icons.Default.IosShare, stringResource(R.string.export), onClick = {
                            if (biometricSpendingKeyEnabled) {
                                context.authenticateWithDeviceCredential(title = unlockTitle, onSuccess = { showPrivateKey = true })
                            } else {
                                showPrivateKey = true
                            }
                        })
                        ToolbarIconButton(Icons.Default.Public, stringResource(R.string.view_in_explorer), onClick = {
                            if (address.isNotEmpty()) uriHandler.openUri(kaspaExplorer.addressUrl(address))
                        })
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background),
            )
        },
        bottomBar = {
            AddressReceiveSendBar(
                receiveEnabled = address.isNotEmpty(),
                sendEnabled = (entry?.balanceSompi ?: 0L) > 0L,
                onReceive = { showQr = true },
                onSend = { showWithdraw = true },
            )
        },
    ) { padding ->
        val barInset = maxOf(padding.calculateBottomPadding(), LocalDockInset.current)
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AddressPageBalanceHeader(entry?.balanceSompi ?: 0L)
            UnderlineTabBar(
                titles = listOf(stringResource(R.string.address_tab_history), stringResource(R.string.utxos), stringResource(R.string.address_tab_domains)),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(top = 4.dp),
            )
            when (selectedTab) {
                0 -> AddressHistoryList(
                    txHistory = txHistory,
                    isLoading = isLoadingTxHistory,
                    failed = txHistoryFailed,
                    onRetry = { viewModel.loadSpendingAddressTxHistory(address) },
                    onTransactionClick = { transactionActionTarget = it },
                    bottomPadding = barInset,
                )
                // KaPost reads .kachat names only, as on the chatting address's page.
                2 -> if (com.kachat.app.services.NameServicesClient.OTHER_SERVICES_ENABLED) {
                    AddressDomainsTab(address, onOpenService = { openedDomainService = it }, bottomPadding = barInset)
                } else {
                    KaPostAddressDomainsTab(address, bottomPadding = barInset)
                }
                else -> AddressUtxoList(
                    utxos = utxos,
                    isLoading = isLoadingUtxos,
                    loadError = utxosLoadError,
                    labels = utxoLabels,
                    onRetry = { if (address.isNotEmpty()) viewModel.loadSpendingAddressUtxos(address) },
                    onCompound = { showCompoundFlow = true },
                    onRename = { key ->
                        labelingUtxoKey = key
                        labelInput = utxoLabels[key] ?: ""
                    },
                    bottomPadding = barInset,
                    compoundFooter = stringResource(R.string.compound_utxos_spending_description),
                )
            }
        }
    }

    if (showQr && entry != null) {
        SpendingAddressQrSheet(entry = entry, onDismiss = { showQr = false })
    }

    if (showPrivateKey) {
        SpendingAddressPrivateKeyOverlay(privateKeyHex = viewModel.getSpendingPrivateKeyHex(index), onDismiss = { showPrivateKey = false })
    }

    labelingUtxoKey?.let { key ->
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { labelingUtxoKey = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.rename_utxo), color = colors.textPrimary) },
            text = {
                com.kachat.app.ui.theme.IosTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = colors.textSecondary,
                        focusedLabelColor = KaspaTeal,
                        unfocusedLabelColor = colors.textSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSpendingUtxoLabel(address, key, labelInput)
                    utxoLabels = viewModel.getSpendingUtxoLabels(address)
                    labelingUtxoKey = null
                }) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { labelingUtxoKey = null }) {
                    Text(stringResource(R.string.cancel), color = colors.textSecondary)
                }
            },
        )
    }

    transactionActionTarget?.let { tapped ->
        TransactionActionsSheet(
            tx = tapped,
            onOpenExplorer = {
                transactionActionTarget = null
                uriHandler.openUri(kaspaExplorer.txUrl(tapped.txId))
            },
            // No Add to Portfolio: KaPost iOS offers Open in Explorer only.
            onDismiss = { transactionActionTarget = null },
        )
    }
    portfolioCandidate?.let { candidate ->
        AddToPortfolioSheet(
            tx = candidate,
            address = address,
            viewModel = portfolioViewModel,
            onDismiss = { portfolioCandidate = null },
            onAdded = { addedPortfolioName = it },
        )
    }
    addedPortfolioName?.let { name ->
        PortfolioAddedSnackbar(name) { addedPortfolioName = null }
    }
}
