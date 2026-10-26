package com.kachat.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
 * presents it: Send Kaspa filled in for someone else's, the request itself for your own. Every
 * opening is its own sheet, even of the same request.
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
 * Pasting a payment request anywhere in KaChat opens Send Kaspa with it (iOS
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
 * Presents the payment request waiting in [PaymentRequestRouter] (iOS
 * `PaymentRequestSheetPresenter`): your own shows it ([OwnPaymentRequestSheet]); someone else's
 * opens Send Kaspa from the chatting address with the address locked and the amount and memo
 * filled in - every send check as always, and nothing goes out until slide to send.
 */
@Composable
fun PaymentRequestSheetHost(walletViewModel: WalletViewModel, chatViewModel: ChatViewModel) {
    val pending by PaymentRequestRouter.pending.collectAsState()
    val current = pending ?: return
    key(current.id) {
        var isOwn by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            isOwn = withContext(Dispatchers.Default) { current.request.isOwn }
        }
        when (isOwn) {
            null -> Unit
            true -> OwnPaymentRequestSheet(current.request, onDismissed = { PaymentRequestRouter.dismiss(current.id) })
            false -> {
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
