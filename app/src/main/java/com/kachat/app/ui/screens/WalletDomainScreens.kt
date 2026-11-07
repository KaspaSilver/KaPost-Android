package com.kachat.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.services.KnsAsset
import com.kachat.app.services.KnsInscriptionEngine
import com.kachat.app.services.NameServiceTLD
import com.kachat.app.services.kachatnames.KachatNamesService
import com.kachat.app.services.kachatnames.NameInfo
import com.kachat.app.ui.theme.IosActivityIndicator
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.IosTextField
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.IosHaptic
import com.kachat.app.util.KaspaAddress
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.rememberHaptics
import com.kachat.app.viewmodels.WalletViewModel

// Wallet > Your Domains (iOS 071e901 `Views/Wallet/WalletDomainViews.swift`): KaPay's list of the
// account's names, one card per name service, a KNS domain's detail with Set as Primary, and Send
// Domain. No Customize Profile: profiles are edited on KaPost's own Profile (iOS 071e901). KNS,
// dotk and Kaspa Names stay switched off as on iOS (`KnsService.IS_ENABLED`,
// `NameServicesClient.OTHER_SERVICES_ENABLED`), so their cards open empty lists.

/**
 * The Wallet screen's Your Domains row (iOS 071e901 `WalletAccountSections.yourDomainsRow`): a
 * glass card with the "at" label, how many names the account owns on every service (.kachat, KNS,
 * .k and .kaspa), and a chevron. [onClick] opens [KnsDomainsScreen].
 */
@Composable
fun WalletYourDomainsRow(viewModel: WalletViewModel, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val address by viewModel.address.collectAsState()
    val ownedKnsDomains by viewModel.ownedDomains.collectAsState()
    val ownedServiceNames by viewModel.nameServices.owned.collectAsState()
    val kachatRevision by viewModel.kachatRegistryRevision.collectAsState()
    var kachatOwnedCount by remember(address) { mutableStateOf(0) }
    // KNS cache-first, and the .k and .kaspa lookups, so the count includes them before the list opens.
    LaunchedEffect(address) {
        val owner = address ?: return@LaunchedEffect
        viewModel.refreshOwnedDomainsAndAwait()
        viewModel.nameServices.refresh(owner)
    }
    // Reloads whenever the registry moves (a registration, sale or transfer lands).
    val cacheContext = LocalContext.current
    LaunchedEffect(address, kachatRevision) {
        val owner = address ?: return@LaunchedEffect
        // the saved list's count at once; the read below corrects it when it answers
        if (kachatOwnedCount == 0 && KachatNamesService.isLaunched) {
            com.kachat.app.services.kachatnames.KachatOwnedNamesCache.load(cacheContext, owner)?.let {
                kachatOwnedCount = it.names.size
            }
        }
        viewModel.watchKachatOwnedCount(owner) { kachatOwnedCount = it }
    }
    val count = kachatOwnedCount + ownedKnsDomains.size + ownedServiceNames.values.sumOf { it.size }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.your_domains), color = colors.textPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text("$count", color = colors.textSecondary, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Your Domains (iOS 071e901 `KNSDomainsListView`, as KaPay's `KnsDomainsScreen`): one square card
 * per name ending, KaChat's own first - .kachat, KNS (.kas), dotk (.k), Kaspa Names (.kaspa) - each
 * with how many names this wallet holds there, opening that service's own list. A .kas domain
 * opens its detail (Set as Primary, Send); a .kachat name its live page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnsDomainsScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val ownedDomainAssets by viewModel.ownedDomainAssets.collectAsState()
    val walletAddress by viewModel.address.collectAsState()
    val ownedServiceNames by viewModel.nameServices.owned.collectAsState()
    val kachatCount = rememberKachatHeldCount(walletAddress ?: "")
    val primaryDomainName by viewModel.primaryDomainName.collectAsState()
    val setPrimaryState by viewModel.setPrimaryState.collectAsState()
    // A .kachat name opened from the .kachat card's list: its live page.
    var selectedKachat by remember { mutableStateOf<NameInfo?>(null) }
    // Inscribe: the .kachat marketplace over Your Domains; the list refreshes when it closes.
    var showKachatMarket by remember { mutableStateOf(false) }
    var kachatRefreshRequested by remember { mutableStateOf(false) }
    // The name service whose card was tapped, null on the cards themselves.
    var openedService by rememberSaveable { mutableStateOf<NameServiceTLD?>(null) }
    // The .kas domain whose detail is open. Held as it was tapped (iOS passes the domain itself),
    // so a refresh after its transfer doesn't pull the detail and the Sent sheet out from under it.
    var selectedDomainState by remember { mutableStateOf<KnsAsset?>(null) }
    var showSendSheet by remember { mutableStateOf(false) }
    // The domain a Set as Primary tap is for, until it lands or fails (for the toast).
    var pendingPrimary by remember { mutableStateOf<String?>(null) }
    val openChat: (String) -> Unit = { openChatInKaChat(context, it) }

    LaunchedEffect(Unit) {
        viewModel.refreshOwnedDomainsAndAwait()
    }
    LaunchedEffect(Unit) {
        viewModel.refreshServiceNamesAndAwait()
    }
    // Set as Primary landed: a toast on success (iOS onMessage "Primary domain set to %@."), and
    // the failure shows in place on the detail.
    LaunchedEffect(setPrimaryState) {
        val name = pendingPrimary ?: return@LaunchedEffect
        if (setPrimaryState.inFlight) return@LaunchedEffect
        pendingPrimary = null
        if (setPrimaryState.errorMessage == null) {
            haptic(IosHaptic.SUCCESS)
            IosToasts.show(context.getString(R.string.wd_primary_set_to, name))
        } else {
            haptic(IosHaptic.IMPACT_MEDIUM)
        }
    }

    if (showKachatMarket) {
        BackHandler { showKachatMarket = false; kachatRefreshRequested = true }
        // runs on under the dock, clearing it itself (LocalDockInset)
        KachatMarketScreen(
            onBack = { showKachatMarket = false; kachatRefreshRequested = true },
            onOpenChat = openChat,
        )
        return
    }

    selectedKachat?.let { info ->
        BackHandler { selectedKachat = null }
        AboveDock { KachatLiveNameDetailScreen(info, onBack = { selectedKachat = null }, onOpenChat = openChat) }
        return
    }

    val selectedDomain = selectedDomainState
    if (selectedDomain != null) {
        BackHandler { selectedDomainState = null }
        val isPrimary = isPrimaryDomain(selectedDomain.asset, primaryDomainName)
        val assetId = selectedDomain.assetId?.trim().orEmpty()
        AboveDock {
            KnsDomainDetailScreen(
                domain = selectedDomain,
                isPrimary = isPrimary,
                isSetPrimaryAllowed = assetId.isNotEmpty() && !isPrimary,
                isTransferAllowed = assetId.isNotEmpty(),
                settingThisOne = setPrimaryState.inFlight && setPrimaryState.assetId == selectedDomain.assetId,
                settingAny = setPrimaryState.inFlight,
                setPrimaryError = setPrimaryState.errorMessage
                    ?.takeIf { !setPrimaryState.inFlight && setPrimaryState.assetId == selectedDomain.assetId }
                    ?.trim()
                    ?.let { if (it.length > 160) it.take(157) + "..." else it },
                onSetPrimary = {
                    pendingPrimary = selectedDomain.asset.orEmpty()
                    viewModel.setPrimaryDomain(assetId)
                },
                onSend = {
                    viewModel.resetTransferDomainState()
                    showSendSheet = true
                },
                onBack = { selectedDomainState = null },
            )
        }
        if (showSendSheet) {
            IosFullSheet(
                onDismissed = {
                    showSendSheet = false
                    viewModel.resetTransferDomainState()
                },
                swipeToDismiss = false,
                grouped = true,
            ) { close ->
                KnsDomainSendScreen(
                    domain = selectedDomain,
                    viewModel = viewModel,
                    onCancel = close,
                    onComplete = { result ->
                        // the sheet and the detail go together: the domain has left this wallet
                        showSendSheet = false
                        viewModel.resetTransferDomainState()
                        haptic(IosHaptic.SUCCESS)
                        IosToasts.show(
                            if (result.verified) context.getString(R.string.wd_transferred_to, result.domain, result.toAddress)
                            else context.getString(R.string.wd_transfer_submitted, result.domain)
                        )
                        // the domain has left this wallet: back to the list
                        selectedDomainState = null
                    },
                )
            }
        }
        return
    }

    // The service a card opened: its own screen, titled by its ending.
    openedService?.let { tld ->
        BackHandler { openedService = null }
        Scaffold(
            containerColor = LocalAppColors.current.background,
            // Runs on under the dock; each list clears it (its pinned button above the dock, or
            // its scroll's end).
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(tld.suffix, color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        SheetBarBackButton(onClick = { openedService = null }, modifier = Modifier.padding(start = 12.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tld) {
                    NameServiceTLD.KAS -> KnsDomainListTab(
                        domains = ownedDomainAssets.filter { it.asset != null },
                        primaryDomainName = primaryDomainName,
                        onRefresh = { viewModel.refreshOwnedDomainsAndAwait() },
                        onSelect = { selectedDomainState = it },
                    )
                    NameServiceTLD.K, NameServiceTLD.KASPA -> ServiceNameListTab(tld = tld, viewModel = viewModel)
                    NameServiceTLD.KACHAT ->
                        if (KachatNamesService.isEnabled) {
                            KachatLiveDomainsTab(
                                walletAddress = walletAddress ?: "",
                                onOpen = { selectedKachat = it },
                                onInscribe = { showKachatMarket = true },
                                refreshRequested = kachatRefreshRequested,
                                onRefreshHandled = { kachatRefreshRequested = false },
                            )
                        } else {
                            KachatNamesComingSoon()
                        }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = LocalAppColors.current.background,
        // Runs on under the dock, as iOS's scroll runs on under its tab bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.your_domains), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        },
    ) { padding ->
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalDockInset.current),
        ) {
            items(NameServiceTLD.entries.size) { i ->
                val tld = NameServiceTLD.entries[i]
                // How many names this wallet has with each service, 0 included; null while the
                // answer is not in yet (first load, or the service unreachable).
                val count = when (tld) {
                    NameServiceTLD.KAS -> ownedDomainAssets.count { it.asset != null }
                    NameServiceTLD.K, NameServiceTLD.KASPA -> ownedServiceNames[tld]?.size
                    NameServiceTLD.KACHAT -> kachatCount
                }
                DomainServiceCard(tld = tld, count = count, onClick = { openedService = tld })
            }
        }
    }
}

/** iOS `isPrimaryDomain`: trimmed and case-insensitive. */
private fun isPrimaryDomain(domainName: String?, primary: String?): Boolean =
    domainName.orEmpty().trim().lowercase() == primary.orEmpty().trim().lowercase()

/**
 * A square tile (iOS 071e901 `serviceCard`): the name ending big in the brand style, who runs it
 * under it, and how many you have there. Fill, 18 radius and a 0.8 white hairline at 15%.
 */
@Composable
private fun DomainServiceCard(
    tld: NameServiceTLD,
    /** How many names you hold there; null draws a dash until the answer is in. */
    count: Int?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(LocalAppColors.current.surface)
            .border(0.8.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // iOS .title2 rounded, heavy, in the accent colour; one line, shrinking to 0.6.
            KachatFitText(
                tld.suffix,
                color = KaspaTeal,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = IosRoundedFontFamily,
                minScale = 0.6f,
            )
            // iOS .caption semibold, secondary.
            Text(
                tld.serviceName,
                color = LocalAppColors.current.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // How many you have there, 0 included; a dash until the answer is in.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .defaultMinSize(minWidth = 28.dp, minHeight = 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(KaspaTeal.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count?.toString() ?: "–",
                    color = KaspaTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A pull-to-refresh list with the service's "Get a ... domain" button pinned under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTabList(
    tld: NameServiceTLD,
    onRefresh: suspend () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            onRefresh()
            pullRefreshState.endRefresh()
        }
    }
    // With no "Get a domain" button pinned under it, the list itself clears the dock.
    val hasGetButton = tld.websiteUrl != null && tld.websiteName != null
    val dockInset = LocalDockInset.current
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(0.dp)) }
                content()
                item { Spacer(Modifier.height(if (hasGetButton) 0.dp else dockInset)) }
            }
            PullToRefreshContainer(state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
        GetDomainButton(tld)
    }
}

/**
 * "Get a .kas domain at knsdomains.org" - the pinned button under each outside service's list,
 * opening that service's own site (iOS 071e901 `getNameButton`). The same outlined capsule.
 */
@Composable
private fun GetDomainButton(tld: NameServiceTLD) {
    val url = tld.websiteUrl ?: return
    val site = tld.websiteName ?: return
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Above the dock the page runs under.
            .padding(bottom = LocalDockInset.current)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(LocalAppColors.current.surface)
            .border(1.5.dp, KaspaTeal, RoundedCornerShape(28.dp))
            .clickable { runCatching { uriHandler.openUri(url) } }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.get_domain_at, tld.suffix, site),
            color = KaspaTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun KnsDomainListTab(
    domains: List<KnsAsset>,
    primaryDomainName: String?,
    onRefresh: suspend () -> Unit,
    onSelect: (KnsAsset) -> Unit,
) {
    DomainTabList(tld = NameServiceTLD.KAS, onRefresh = onRefresh) {
        if (domains.isEmpty()) {
            item {
                Text(text = stringResource(R.string.no_domains_yet), color = LocalAppColors.current.textSecondary, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(domains, key = { it.assetId ?: it.asset ?: it.hashCode().toString() }) { domainAsset ->
                KnsDomainCard(
                    domain = domainAsset,
                    isPrimary = isPrimaryDomain(domainAsset.asset, primaryDomainName),
                    modifier = Modifier.clickable { onSelect(domainAsset) }
                )
            }
        }
    }
}

@Composable
private fun ServiceNameListTab(tld: NameServiceTLD, viewModel: WalletViewModel) {
    val owned by viewModel.nameServices.owned.collectAsState()
    val loading by viewModel.nameServices.loading.collectAsState()
    val failed by viewModel.nameServices.failed.collectAsState()
    val names = owned[tld].orEmpty()
    DomainTabList(tld = tld, onRefresh = { viewModel.refreshServiceNamesAndAwait() }) {
        if (names.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    when {
                        tld in loading && owned[tld] == null -> IosActivityIndicator(color = KaspaTeal)
                        tld in failed -> Text(
                            stringResource(R.string.could_not_reach_service, tld.serviceName),
                            color = LocalAppColors.current.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        else -> Text(
                            stringResource(R.string.no_names_yet, tld.suffix),
                            color = LocalAppColors.current.textSecondary,
                        )
                    }
                }
            }
        } else {
            items(names, key = { it.display }) { name ->
                DomainNameCard(
                    title = name.display,
                    badge = if (name.isProvisional) stringResource(R.string.settling) else null,
                )
            }
        }
    }
}

/** iOS 071e901 `kachatComingSoon`: only where .kachat names are switched off. */
@Composable
private fun KachatNamesComingSoon() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp)
            .padding(bottom = LocalDockInset.current),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(KachatSymbols.AtCircle, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.kachat_names_are_coming), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            stringResource(R.string.wd_kachat_names_coming_body),
            color = LocalAppColors.current.textSecondary,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
        )
    }
}

/** The teal card for a KNS domain, "Primary" in its corner when it is (iOS `KNSDomainCard`). */
@Composable
fun KnsDomainCard(domain: KnsAsset, isPrimary: Boolean = false, modifier: Modifier = Modifier) {
    DomainNameCard(title = domain.asset ?: "", badge = if (isPrimary) stringResource(R.string.primary) else null, modifier = modifier)
}

/**
 * One owned .kas domain (iOS 071e901 `KNSDomainDetailView`): its card, the Asset ID, Primary
 * Domain or Set as Primary (a failed attempt says why in place), and Send pinned at the bottom.
 * Android's KNS asset carries no listing status, so the Status row iOS shows for a listed domain
 * has nothing to read here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnsDomainDetailScreen(
    domain: KnsAsset,
    isPrimary: Boolean,
    isSetPrimaryAllowed: Boolean,
    isTransferAllowed: Boolean,
    /** This domain's Set as Primary is running (the row shows a spinner). */
    settingThisOne: Boolean,
    /** Any Set as Primary is running (the row can't be tapped). */
    settingAny: Boolean,
    /** Why the last Set as Primary tap on this domain failed; null when it succeeded or wasn't tried. */
    setPrimaryError: String?,
    onSetPrimary: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(domain.asset ?: "", color = colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    SheetBarBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            // iOS: an accent capsule, black label and arrow.up.circle.fill, dimmed when it can't send.
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .alpha(if (isTransferAllowed) 1f else 0.5f)
                    .clip(RoundedCornerShape(50))
                    .background(KaspaTeal)
                    .clickable(enabled = isTransferAllowed, onClick = onSend)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(Color.Black), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowUpward, null, tint = KaspaTeal, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.send), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            KnsDomainCard(domain = domain, isPrimary = isPrimary, modifier = Modifier.padding(top = 12.dp))
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .border(0.8.dp, Color.White.copy(alpha = 0.18f), shape)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.asset_id), color = colors.textPrimary)
                    Spacer(Modifier.width(12.dp))
                    MiddleEllipsisText(
                        domain.assetId ?: "",
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isPrimary) {
                    HorizontalDivider(color = colors.surfaceVariant, modifier = Modifier.padding(start = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.primary_domain), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Star, contentDescription = null, tint = KaspaTeal)
                    }
                } else if (isSetPrimaryAllowed) {
                    HorizontalDivider(color = colors.surfaceVariant, modifier = Modifier.padding(start = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !settingAny) { onSetPrimary() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.set_as_primary), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (settingThisOne) {
                            IosActivityIndicator(color = KaspaTeal, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.StarBorder, contentDescription = null, tint = KaspaTeal)
                        }
                    }
                    if (setPrimaryError != null && !settingAny) {
                        // In place rather than as a popup (iOS 071e901).
                        Text(
                            setPrimaryError,
                            color = colors.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Send Domain (iOS 071e901 `KNSDomainSendView`): moves one KNS domain to an address or a name,
 * with the editable network fee and no amount. A Form in a sheet: Cancel and Send in its bar, and
 * Send asks for the phone's lock first ("Confirm sending this domain"). The transfer's progress
 * shows in its own sheet; once sent, the Sent sheet with the transaction, and Done hands the
 * result to [onComplete].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnsDomainSendScreen(
    domain: KnsAsset,
    viewModel: WalletViewModel,
    onCancel: () -> Unit,
    onComplete: (com.kachat.app.services.WalletService.TransferDomainResult) -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberHaptics()
    val recipientPreview by viewModel.transferRecipientPreview.collectAsState()
    val transferState by viewModel.transferDomainState.collectAsState()
    val kaspaExplorer by viewModel.kaspaExplorer.collectAsState()

    var recipientInput by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var feeTier by remember { mutableStateOf(ColdFeeTier.NORMAL) }
    var customFeeSompi by remember { mutableStateOf<Long?>(null) }
    var isEditingFee by remember { mutableStateOf(false) }
    var customFeeText by remember { mutableStateOf("") }

    val domainName = domain.asset ?: ""
    val assetId = domain.assetId ?: ""
    val baseFeeSompi = KnsInscriptionEngine.REVEAL_PRIORITY_FEE_SOMPI
    val priorityFeeSompi = customFeeSompi ?: (baseFeeSompi * feeTier.multiplier)
    val inFlight = transferState.status !in listOf(
        WalletViewModel.KnsInscribeUiStatus.IDLE,
        WalletViewModel.KnsInscribeUiStatus.SUCCESS,
        WalletViewModel.KnsInscribeUiStatus.FAILED
    )
    val canSend = !inFlight && recipientPreview?.resolvedAddress != null && recipientPreview?.checking != true
    val confirmTitle = stringResource(R.string.wd_confirm_sending_domain)

    LaunchedEffect(transferState.status) {
        if (transferState.status == WalletViewModel.KnsInscribeUiStatus.FAILED) haptic(IosHaptic.IMPACT_MEDIUM)
    }

    fun trimmedKas(sompi: Long): String {
        var text = "%.8f".format(java.util.Locale.US, sompi / 100_000_000.0)
        while (text.endsWith("0")) text = text.dropLast(1)
        if (text.endsWith(".")) text = text.dropLast(1)
        return text
    }

    fun onInput(value: String) {
        recipientInput = value
        viewModel.checkTransferRecipient(value)
    }

    Column(Modifier.fillMaxSize()) {
        SheetNavBar(
            title = stringResource(R.string.send_domain),
            leading = { SheetBarButton(stringResource(R.string.cancel), onClick = onCancel, enabled = !inFlight) },
            trailing = {
                if (inFlight) {
                    IosActivityIndicator(color = KaspaTeal)
                } else {
                    SheetBarButton(
                        stringResource(R.string.send),
                        enabled = canSend,
                        emphasized = true,
                        onClick = {
                            // Giving a domain away can't be undone, so the phone's lock comes first,
                            // whatever the settings say. Declining leaves the form as it was.
                            context.authenticateWithDeviceCredential(title = confirmTitle, onSuccess = {
                                viewModel.transferDomain(domainName, assetId, priorityFeeSompi)
                            })
                        },
                    )
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            SettingsFormSection(title = stringResource(R.string.wd_domain), first = false) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(domainName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    MiddleEllipsisText(assetId, color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            SettingsFormSection(
                title = stringResource(R.string.recipient_address),
                footer = stringResource(R.string.enter_kaspa_address_or_domain_short_kachat_first),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IosTextField(
                        value = recipientInput,
                        onValueChange = { onInput(it) },
                        placeholder = { Text(stringResource(R.string.kaspa_qr_or_domain)) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = colors.textPrimary),
                        singleLine = true,
                        enabled = !inFlight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val preview = recipientPreview
                    AddressResolutionCard(address = preview?.resolvedAddress, domain = preview?.resolvedName)
                    if (recipientInput.isNotEmpty()) {
                        when {
                            preview == null || preview.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                IosActivityIndicator(color = colors.textSecondary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.looking_up_domain), color = colors.textSecondary, fontSize = 12.sp)
                            }
                            preview.errorMessage != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cancel, null, tint = colors.danger, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(preview.errorMessage, color = colors.danger, fontSize = 12.sp)
                            }
                            preview.resolvedAddress != null && preview.resolvedName != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = colors.success, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.resolved_name, preview.resolvedName), color = colors.success, fontSize = 12.sp)
                                }
                                MiddleEllipsisText(preview.resolvedAddress, color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            preview.resolvedAddress != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = colors.success, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                MiddleEllipsisText(preview.resolvedAddress, color = colors.success, fontSize = 12.sp)
                            }
                        }
                        if (preview != null && !preview.checking) {
                            OtherDomainsDropdown(preview.nameResolutions, preview.selectedTld) {
                                viewModel.selectTransferResolution(it)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clickable(enabled = !inFlight) {
                                clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { onInput(it) }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.ContentPaste, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.paste), color = KaspaTeal, fontSize = 15.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clickable(enabled = !inFlight) { showScanner = true }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scan_qr), color = KaspaTeal, fontSize = 15.sp)
                        }
                    }
                }
            }

            SettingsFormSection(
                title = stringResource(R.string.wd_fee),
                footer = stringResource(R.string.wd_transfer_fee_footer),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceVariant)
                    ) {
                        ColdFeeTier.entries.forEach { tier ->
                            val selected = feeTier == tier
                            Text(
                                stringResource(
                                    when (tier) {
                                        ColdFeeTier.NORMAL -> R.string.fee_tier_normal
                                        ColdFeeTier.FAST -> R.string.fee_tier_fast
                                        ColdFeeTier.PRIORITY -> R.string.fee_tier_priority
                                    }
                                ),
                                color = if (selected) Color.Black else colors.textPrimary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) KaspaTeal else Color.Transparent)
                                    .clickable(enabled = !inFlight) {
                                        feeTier = tier
                                        customFeeSompi = null
                                        isEditingFee = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.network_fee), color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (isEditingFee) {
                            IosTextField(
                                value = customFeeText,
                                onValueChange = { customFeeText = it },
                                singleLine = true,
                                placeholder = { Text("0.00") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                modifier = Modifier.width(110.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = KaspaTeal,
                                modifier = Modifier.clickable {
                                    KaspaUnit.sompiFromUserText(customFeeText)?.let { customFeeSompi = it }
                                    isEditingFee = false
                                },
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = !inFlight) {
                                    customFeeText = trimmedKas(priorityFeeSompi)
                                    isEditingFee = true
                                }
                            ) {
                                Text("${trimmedKas(priorityFeeSompi)} ${KaspaUnit.symbol}", color = KaspaTeal, textDecoration = TextDecoration.Underline)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Edit, null, tint = KaspaTeal, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            if (transferState.status == WalletViewModel.KnsInscribeUiStatus.FAILED) {
                SettingsFormSection(title = null) {
                    Text(
                        transferState.errorMessage ?: stringResource(R.string.something_went_wrong),
                        color = colors.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }

    if (showScanner) {
        // a payment link's ?query is dropped
        QrScannerSheet(onScanned = { onInput(KaspaAddress.fromScanned(it)) }, onDismiss = { showScanner = false })
    }
    // Progress in its own sheet: a transfer is a commit, a wait, then a reveal, and a spinner
    // through all of it is indistinguishable from being stuck. Not dismissible.
    if (inFlight) {
        DomainTransferProgressSheet(domainName = domainName, status = transferState.status)
    }
    val result = transferState.result
    if (transferState.status == WalletViewModel.KnsInscribeUiStatus.SUCCESS && result != null) {
        SentConfirmationSheet(
            transaction = SentTransaction(txId = result.revealTxId),
            explorerName = kaspaExplorer.displayName,
            explorerUrl = kaspaExplorer.txUrl(result.revealTxId),
            onDone = { onComplete(result) },
        )
    }
}

/**
 * Where a domain transfer has got to (iOS 071e901 `domainTransferProgressSheet`): "Sending ...", a
 * bar moving in known steps, the step, and why it takes a moment. Held open for the duration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainTransferProgressSheet(
    domainName: String,
    status: WalletViewModel.KnsInscribeUiStatus,
) {
    // iOS KNSDomainTransferService.TransferStage's titles and fractions (English on iOS too).
    val (label, fraction) = when (status) {
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_COMMIT -> "Submitting commit transaction" to 0.3f
        WalletViewModel.KnsInscribeUiStatus.SUBMITTING_REVEAL -> "Submitting reveal transaction" to 0.8f
        WalletViewModel.KnsInscribeUiStatus.VERIFYING -> "Confirming the new owner" to 0.95f
        else -> "Preparing transfer" to 0.1f
    }
    com.kachat.app.ui.theme.IosSheetColors {
        val colors = LocalAppColors.current
        ModalBottomSheet(
            scrimColor = com.kachat.app.ui.theme.IosSheetScrim,
            shape = com.kachat.app.ui.theme.IosSheetShape,
            tonalElevation = com.kachat.app.ui.theme.IosSheetTonalElevation,
            windowInsets = WindowInsets.statusBars,
            // Held open: a dismissed sheet would leave an on-chain transfer with nothing reporting on it.
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.background,
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Sending $domainName",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = KaspaTeal,
                        trackColor = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(label, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.wd_transfer_two_transactions),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
