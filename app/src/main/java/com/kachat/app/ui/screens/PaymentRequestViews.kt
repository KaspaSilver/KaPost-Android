package com.kachat.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.kachat.app.R
import com.kachat.app.models.PaymentRequest
import com.kachat.app.ui.theme.IosRoundedFontFamily
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaUnit
import com.kachat.app.viewmodels.ChatViewModel
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// MARK: - Opening one

/**
 * The payment request waiting to be shown - a tapped request card, a pasted link, a link opened
 * from outside the app (iOS `PaymentRequestRouter`). [PaymentRequestSheetHost], in MainShell,
 * opens it: in KaPay for someone else's, Send Kaspa filled in for a chat payment, the request
 * itself for your own. Every opening is its own, even of the same request.
 */
object PaymentRequestRouter {
    data class Pending(val request: PaymentRequest, val id: Long)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun open(request: PaymentRequest) {
        nextId += 1
        _pending.value = Pending(request, nextId)
    }

    /** The sheet for [id] has gone; a newer opening stays. */
    @Synchronized
    fun dismiss(id: Long) {
        if (_pending.value?.id == id) _pending.value = null
    }
}

/**
 * Pasting a payment request anywhere in KaChat opens it, as a tap does (iOS
 * `PaymentRequestPaste`): the chat composers (1:1, group, public), the KaPosts composer. Only text
 * that is the link and nothing else, and only someone else's request: your own pastes as text, so
 * you can send it to the person who should pay.
 */
object PaymentRequestPaste {
    /** Opens [text] when it is someone else's payment request and nothing more; true if it did. */
    fun openIfRequest(text: String?): Boolean {
        val pasted = text ?: return false
        if (!pasted.contains("kachat", ignoreCase = true)) return false
        val match = KaChatLink.findFirst(pasted) ?: return false
        if (pasted.trim() != match.raw) return false
        val request = (match.ref as? KaChatLinkRef.PaymentRequest)?.request ?: return false
        if (request.isOwn) return false
        PaymentRequestRouter.open(request)
        return true
    }

    /**
     * For a text field's change from [old] to [new]: when what was put in at once (a paste) is
     * someone else's payment request, it opens Send Kaspa and the field keeps [old] - returns true,
     * and the caller drops the change.
     */
    fun interceptInsert(old: String, new: String): Boolean {
        // A link is far longer than anything typed in one change.
        if (new.length < 20) return false
        val max = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < max && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val inserted = new.substring(prefix, new.length - suffix)
        if (inserted.length < 20) return false
        return openIfRequest(inserted)
    }
}

/**
 * Someone else's payment request is paid in KaPay (KaChat's wallet app, `kapay.kachat.app`): it
 * opens there on Send, filled in, through KaPay's own scheme (`kapay://pay/...`, the same tail as
 * `kachat://pay/`). Two stay here: your own (nothing to pay - [OwnPaymentRequestSheet]), and one
 * asking for a chat payment ([PaymentRequest.toChattingAddress], made in a KaChat 1:1 chat), paid
 * as an encrypted chat payment so it lands in the chat with them - KaPay can't. Without KaPay
 * installed, the Play Store opens to get it. KaChat follows the same rule (iOS KaPosts 0b9fd28,
 * 871bf34, 6238276 `KaPayHandoff`).
 */
object KaPayHandoff {
    const val SCHEME = "kapay"

    /** Where a request goes when KaPay isn't installed. KaPay isn't listed yet, so this is a Play
     *  Store search for it; swap in its page (`details?id=kapay.kachat.app`) once it is (iOS
     *  `appStoreURL`). */
    const val STORE_URL = "market://search?q=KaPay&c=apps"
    private const val STORE_WEB_URL = "https://play.google.com/store/search?q=KaPay&c=apps"

    /** True when [request] is paid in KaPay rather than here. [isOwn] is [PaymentRequest.isOwn],
     *  worked out off the main thread by the caller. */
    fun paysInKaPay(request: PaymentRequest, isOwn: Boolean): Boolean = !isOwn && !request.toChattingAddress

    /** Opens [request] in KaPay, or the store when KaPay isn't installed. */
    fun open(context: android.content.Context, request: PaymentRequest) {
        fun view(url: String): Boolean = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
        if (view("$SCHEME://pay/${request.linkTail}")) return
        if (!view(STORE_URL)) view(STORE_WEB_URL)
    }
}

/**
 * Presents the payment request waiting in [PaymentRequestRouter] (iOS
 * `PaymentRequestSheetPresenter`, `KaChatLinkRouter`'s payment case): someone else's is paid in
 * KaPay ([KaPayHandoff]); your own shows it ([OwnPaymentRequestSheet]); a chat payment opens
 * Send Kaspa from the chatting address with the address locked and the amount and memo filled in
 * - every send check as always, and nothing goes out until slide to send.
 *
 * Its sheets are windows of their own, so they open over a profile or a thread too (iOS draws
 * them from the post's card for that, `KaPostPaymentRequestCard`).
 */
@Composable
fun PaymentRequestSheetHost(walletViewModel: WalletViewModel, chatViewModel: ChatViewModel) {
    val pending by PaymentRequestRouter.pending.collectAsState()
    val current = pending ?: return
    val context = LocalContext.current
    key(current.id) {
        var isOwn by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            isOwn = withContext(Dispatchers.Default) { current.request.isOwn }
        }
        when (isOwn) {
            null -> Unit
            true -> OwnPaymentRequestSheet(current.request, onDismissed = { PaymentRequestRouter.dismiss(current.id) })
            false -> if (KaPayHandoff.paysInKaPay(current.request, isOwn = false)) {
                LaunchedEffect(Unit) {
                    KaPayHandoff.open(context, current.request)
                    PaymentRequestRouter.dismiss(current.id)
                }
            } else {
                val address by walletViewModel.address.collectAsState()
                val balanceSompi by walletViewModel.balanceSompi.collectAsState()
                val from = address
                if (from == null) {
                    LaunchedEffect(Unit) { PaymentRequestRouter.dismiss(current.id) }
                } else {
                    IosFullSheet(onDismissed = { PaymentRequestRouter.dismiss(current.id) }, swipeToDismiss = false) { close ->
                        SpendingAddressSendFlow(
                            fromAddress = from,
                            balanceSompi = balanceSompi,
                            title = stringResource(R.string.chats_send_kaspa),
                            viewModel = walletViewModel,
                            onDone = close,
                            presentedAsSheet = true,
                            paymentRequest = current.request,
                            chatViewModel = chatViewModel,
                        )
                    }
                }
            }
        }
    }
}

/** Shares [link] through the system share sheet (iOS `ShareLink`). */
private fun sharePaymentRequestLink(context: android.content.Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/** iOS's `Label("Share", systemImage: "square.and.arrow.up")` button. */
@Composable
private fun PaymentRequestShareButton(enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Outlined.IosShare, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.share), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Request Payment (Chats > + > Request Payment, and a 1:1 chat's + sheet)




// MARK: - Paying one

/**
 * What a payment request asks for, above the amount on Send Kaspa (iOS `PaymentRequestSummary`):
 * the amount, the memo, how it is paid, and who made it - "Requested by" with that address's name
 * and picture once the signature checked out, else "Not signed".
 */
@Composable
fun PaymentRequestSummary(request: PaymentRequest, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sendKaspaGlass(14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.pr_requested).uppercase(),
            color = KaspaTeal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${request.amountText} ${KaspaUnit.symbol}",
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (request.memo.isNotEmpty()) {
            Text(request.memo, color = colors.textPrimary, fontSize = 15.sp)
        }
        Text(
            stringResource(if (request.toChattingAddress) R.string.pr_paid_as_chat_payment else R.string.pr_paid_to_their_spending),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))

        val creator = request.creator
        if (creator != null) {
            // Who made it - checked against the signature in the link when it was opened.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.pr_requested_by), color = colors.success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            AddressResolutionCard(address = creator)
            if (!creator.equals(request.address, ignoreCase = true)) {
                Text(stringResource(R.string.pr_signed_by_their_chatting), color = colors.textSecondary, fontSize = 12.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                Text(stringResource(R.string.pr_not_signed), color = colors.warning, fontSize = 12.sp)
            }
        }
    }
}

/**
 * One of your own payment requests, tapped in a chat (iOS `OwnPaymentRequestView`, 1fe8388): what
 * it asks for and where it pays into, with its link to copy or share again. Nothing to pay - it's
 * yours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnPaymentRequestSheet(request: PaymentRequest, onDismissed: () -> Unit) {
    IosFullSheet(onDismissed = onDismissed, swipeToDismiss = true) { close ->
        BackHandler(onBack = close)
        val colors = LocalAppColors.current
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val haptic = com.kachat.app.util.rememberHaptics()
        var copied by remember { mutableStateOf(false) }
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // iOS 26's sheet bar: the title in headline, Done on a glass capsule.
                SheetNavBar(
                    title = stringResource(R.string.pr_your_payment_request),
                    trailing = { SheetBarButton(stringResource(R.string.done), onClick = close) },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${request.amountText} ${KaspaUnit.symbol}",
                        color = colors.textPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IosRoundedFontFamily,
                        textAlign = TextAlign.Center,
                    )
                    if (request.memo.isNotEmpty()) {
                        Text(request.memo, color = colors.textPrimary, fontSize = 17.sp, textAlign = TextAlign.Center)
                    }
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (request.toChattingAddress) R.string.pr_paid_to_your_chatting else R.string.pr_paid_to_your_spending
                        ).uppercase(),
                        color = KaspaTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    AddressResolutionCard(address = request.address)
                    Text(
                        stringResource(if (request.toChattingAddress) R.string.pr_chatting_footer else R.string.pr_spending_footer_new),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }

                SendActionButton(
                    title = stringResource(if (copied) R.string.ab_copied else R.string.pr_copy),
                    isBusy = false,
                    isEnabled = true,
                    requiresSlide = false,
                    onSend = {
                        clipboard.setText(AnnotatedString(request.shareUrlString))
                        haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                        copied = true
                    },
                )
                PaymentRequestShareButton { sharePaymentRequestLink(context, request.shareUrlString) }
            }
        }
    }
}

// MARK: - In a KaPost

/**
 * A payment request link in a KaPost, drawn as a card instead of the link's long text (iOS
 * `KaPostPaymentRequestCard`, KaPosts 0b9fd28): the amount (and what it comes to in the app's
 * currency), the memo, who signed it, and a Pay button. A tap opens it as any payment request
 * link does ([PaymentRequestRouter]): in KaPay ([KaPayHandoff]), or here for your own and for a
 * chat payment. Long-press copies the link.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KaPostPaymentRequestCard(request: PaymentRequest, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val haptic = com.kachat.app.util.rememberHaptics()
    var showMenu by remember { mutableStateOf(false) }

    // Whether it is yours, worked out off the main thread (the wallet's addresses may need
    // deriving once).
    val isOwn by androidx.compose.runtime.produceState(initialValue = false, request) {
        value = withContext(Dispatchers.Default) { request.isOwn }
    }

    val portfolioViewModel: com.kachat.app.viewmodels.PortfolioViewModel = hiltViewModel()
    val currency by portfolioViewModel.currency.collectAsState()
    // Read so the card redraws when the live price lands.
    val livePrice by portfolioViewModel.currentPriceUsd.collectAsState()
    val fiatText = remember(request.amountSompi, currency, livePrice) {
        portfolioViewModel.priceIn(currency)?.takeIf { it > 0 }?.let { price ->
            "≈ " + com.kachat.app.util.formatFiatAmount(request.amountSompi / 100_000_000.0 * price, currency)
        }
    }

    // The maker, as the signature in the link proves: their name, else their short address.
    val creator = request.creator
    var creatorName by remember(creator) { mutableStateOf(creator?.let { com.kachat.app.models.addressDisplayName(it) }) }
    if (creator != null) {
        val resolver: AddressResolutionViewModel = hiltViewModel()
        LaunchedEffect(creator) {
            val contact = resolver.contactFor(creator)
            creatorName = com.kachat.app.models.addressDisplayName(creator, contact?.alias)
        }
    }

    val hint = stringResource(
        when {
            isOwn -> R.string.pr_card_hint_own
            request.toChattingAddress -> R.string.pr_card_hint_chat
            else -> R.string.pr_card_hint_kapay
        }
    )
    val shape = RoundedCornerShape(16.dp)
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(KaspaTeal.copy(alpha = 0.08f))
                .border(1.dp, KaspaTeal.copy(alpha = 0.35f), shape)
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = { showMenu = true },
                    onClick = {
                        haptic(com.kachat.app.util.IosHaptic.IMPACT_LIGHT)
                        PaymentRequestRouter.open(request)
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = hint }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_kaspa_logo),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.pr_payment_request).uppercase(),
                        color = KaspaTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${request.amountText} ${KaspaUnit.symbol}",
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = IosRoundedFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                        )
                        fiatText?.let {
                            Text(
                                it,
                                color = colors.textSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    if (request.memo.isNotEmpty()) {
                        Text(
                            request.memo,
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = creatorName
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (creator != null && name != null) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.success, modifier = Modifier.size(14.dp))
                        MiddleEllipsisText(
                            name,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.pr_card_not_signed),
                            color = colors.warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isOwn) KaspaTeal.copy(alpha = 0.15f) else KaspaTeal)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val tint = if (isOwn) KaspaTeal else Color.White
                    Icon(
                        if (isOwn) Icons.Outlined.Visibility else Icons.Filled.ArrowCircleUp,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(if (isOwn) R.string.pr_card_view else R.string.pr_card_pay),
                        color = tint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    showMenu = false
                    clipboard.setText(AnnotatedString(request.shareUrlString))
                    haptic(com.kachat.app.util.IosHaptic.SUCCESS)
                },
            )
        }
    }
}
