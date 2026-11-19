package com.kachat.app.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.showAddressCopiedToast
import com.kachat.app.viewmodels.WalletViewModel

/**
 * Opens the Welcome Guide as a replay (with Skip): Profile > Help calls it. Provided by
 * `MainShell` (ui/KaChatApp.kt); a no-op outside it.
 */
val LocalOpenWelcomeGuide = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The onboarding run's pending marker (iOS b78fdb0 `WelcomeGuideView.onboardingRunPendingKey`):
 * [WalletViewModel.pendingWelcomeGuide] is in memory only, so a guide cut short by a kill would
 * never come back on its own. Set when the onboarding run is presented, cleared by Finish.
 */
object WelcomeGuidePending {
    private const val PREFS = "kaposts_welcome_guide"
    private const val KEY = "kaposts_onboarding_guide_pending"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPending(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun mark(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}

/** The guide's steps, in order (iOS `WelcomeGuideView.Step`). */
private enum class WelcomeGuideStep { WELCOME, LANGUAGE, CURRENCY, FEES, FUNDING, FEATURES, ADDRESS_EXPLAINER }

/** One of the cards on What's in KaPost and Your Chatting Address. */
private class WelcomeGuideCard(val icon: ImageVector, val title: String, val address: String?, val caption: String)

/**
 * KaPost's first-run tour (iOS b78fdb0 `WelcomeGuideView`; owner's call, 2026-11-18), shaped like
 * KaChat's with KaPost's own steps: welcome, language, currency, how KaPost uses Kaspa, funding
 * the chatting address, what's in the app, and the chatting address explained.
 *
 * Shown once after an account is created or imported (`MainShell`), and replayable from
 * Profile > Help ([LocalOpenWelcomeGuide]). An onboarding run ([isOnboardingRun]) has no Skip and
 * comes back after a kill until Finish ([WelcomeGuidePending]); a replay keeps Skip.
 *
 * Picking a language recreates the Activity ([applyAppLanguage]); the step is saved, so the guide
 * carries on at the language step in the new language, as iOS's re-renders in place.
 */
@Composable
fun WelcomeGuideScreen(
    walletViewModel: WalletViewModel,
    isOnboardingRun: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val chattingAddress by walletViewModel.address.collectAsState()
    val address = chattingAddress.orEmpty()
    var stepName by rememberSaveable { mutableStateOf(WelcomeGuideStep.WELCOME.name) }
    val step = WelcomeGuideStep.valueOf(stepName)
    // The funding step's Show QR Code, pushed over the guide as iOS's NavigationLink pushes it.
    var showFundingQr by rememberSaveable { mutableStateOf(false) }

    fun go(target: WelcomeGuideStep) { stepName = target.name }
    fun goBack() {
        WelcomeGuideStep.entries.getOrNull(step.ordinal - 1)?.let { go(it) }
    }
    fun finishGuide() {
        walletViewModel.clearJustImportedWallet()
        WelcomeGuidePending.clear(context)
        onFinished()
    }

    // Only a replay can skip: the run after create or import goes to Finish (iOS
    // `interactiveDismissDisabled`). Android's Back on a replay is its Skip.
    BackHandler { if (!isOnboardingRun) onFinished() }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // A full-screen cover: nothing under it takes a tap.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // The inline navigation bar: Skip (replays only), leading.
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                if (!isOnboardingRun) {
                    SheetBarButton(stringResource(R.string.skip), onClick = onFinished, modifier = Modifier.align(Alignment.CenterStart))
                }
            }
            Crossfade(
                targetState = step,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "welcomeGuideStep",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { shown ->
                val onPrevious = { goBack() }
                val previousEnabled = shown != WelcomeGuideStep.WELCOME
                when (shown) {
                    WelcomeGuideStep.WELCOME -> GuideScaffold(
                        icon = Icons.Default.WavingHand,
                        title = stringResource(R.string.wg_welcome_to_kapost),
                        body = stringResource(R.string.wg_welcome_body),
                        previousEnabled = previousEnabled,
                        onPrevious = onPrevious,
                        onNext = { go(WelcomeGuideStep.LANGUAGE) },
                    )

                    WelcomeGuideStep.LANGUAGE -> {
                        var current by remember { mutableStateOf(currentAppLanguage()) }
                        GuideListStep(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.choose_your_language),
                            body = stringResource(R.string.wg_language_body),
                            previousEnabled = previousEnabled,
                            onPrevious = onPrevious,
                            onNext = { go(WelcomeGuideStep.CURRENCY) },
                        ) {
                            AppLanguage.entries.forEach { language ->
                                GuideOptionRow(language.displayName, selected = current == language) {
                                    current = language
                                    applyAppLanguage(language)
                                }
                            }
                        }
                    }

                    WelcomeGuideStep.CURRENCY -> {
                        val currencyCode by walletViewModel.currency.collectAsState()
                        val current = AppCurrency.fromCode(currencyCode)
                        GuideListStep(
                            icon = Icons.Default.MonetizationOn,
                            title = stringResource(R.string.choose_your_currency),
                            body = stringResource(R.string.select_the_currency_youd_like_prices),
                            previousEnabled = previousEnabled,
                            onPrevious = onPrevious,
                            onNext = { go(WelcomeGuideStep.FEES) },
                        ) {
                            AppCurrency.entries.forEach { currency ->
                                GuideOptionRow(currency.displayName, selected = current == currency) {
                                    walletViewModel.setCurrency(currency.code)
                                }
                            }
                        }
                    }

                    WelcomeGuideStep.FEES -> GuideScaffold(
                        icon = Icons.Default.Hub,
                        title = stringResource(R.string.wg_fees_title),
                        body = stringResource(R.string.wg_fees_body),
                        previousEnabled = previousEnabled,
                        onPrevious = onPrevious,
                        onNext = { go(WelcomeGuideStep.FUNDING) },
                    )

                    WelcomeGuideStep.FUNDING -> {
                        val clipboard = LocalClipboardManager.current
                        GuideScaffold(
                            icon = Icons.Default.QrCode,
                            title = stringResource(R.string.fund_your_chatting_address),
                            body = stringResource(R.string.wg_funding_body),
                            previousEnabled = previousEnabled,
                            onPrevious = onPrevious,
                            onNext = { go(WelcomeGuideStep.FEATURES) },
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // iOS: .footnote.monospaced(), at most two lines, 24 in from the sides;
                                // a tap copies it.
                                Text(
                                    address,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    modifier = Modifier
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            clipboard.setText(AnnotatedString(address))
                                            showAddressCopiedToast(context, address)
                                        }
                                        .padding(horizontal = 24.dp),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button) { showFundingQr = true }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.show_qr_code), color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    WelcomeGuideStep.FEATURES -> GuideCardsStep(
                        title = stringResource(R.string.wg_features_title),
                        subtitle = null,
                        cards = listOf(
                            WelcomeGuideCard(Icons.AutoMirrored.Filled.Article, stringResource(R.string.l53_feed_tab_feed), null,
                                stringResource(R.string.wg_feature_feed_caption)),
                            WelcomeGuideCard(Icons.Default.Forum, stringResource(R.string.broadcasts), null,
                                stringResource(R.string.wg_feature_public_chats_caption)),
                            WelcomeGuideCard(Icons.Default.CardGiftcard, stringResource(R.string.kaposts_tips), null,
                                stringResource(R.string.wg_feature_tips_caption)),
                        ),
                        buttonTitle = stringResource(R.string.next),
                        previousEnabled = previousEnabled,
                        onPrevious = onPrevious,
                        onNext = { go(WelcomeGuideStep.ADDRESS_EXPLAINER) },
                    )

                    // The guide's final step.
                    WelcomeGuideStep.ADDRESS_EXPLAINER -> GuideCardsStep(
                        title = stringResource(R.string.welcome_your_chatting_address),
                        subtitle = stringResource(R.string.wg_address_subtitle),
                        cards = listOf(
                            WelcomeGuideCard(Icons.Default.HowToReg, stringResource(R.string.wg_who_you_are), address,
                                stringResource(R.string.wg_who_you_are_caption)),
                            WelcomeGuideCard(Icons.AutoMirrored.Filled.Send, stringResource(R.string.wg_paying_for_posts), null,
                                stringResource(R.string.wg_paying_for_posts_caption)),
                            WelcomeGuideCard(Icons.Default.Visibility, stringResource(R.string.wg_what_others_can_see), null,
                                stringResource(R.string.wg_what_others_can_see_caption)),
                        ),
                        buttonTitle = stringResource(R.string.finish),
                        previousEnabled = previousEnabled,
                        onPrevious = onPrevious,
                        onNext = { finishGuide() },
                    )
                }
            }
        }

        if (showFundingQr) {
            BackHandler { showFundingQr = false }
            // The white QR page, pushed (iOS `ChattingAddressQRView` in the guide's NavigationStack).
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.White) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    ChattingAddressQrPage(
                        address = address,
                        balanceSompi = null,
                        dismiss = QrPageDismiss.BACK,
                        onDismiss = { showFundingQr = false },
                    )
                }
            }
        }
    }
}

/** The icon, title and body every step leads with (iOS `heading`). */
@Composable
private fun GuideHeading(icon: ImageVector?, title: String, body: String?) {
    val colors = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(56.dp))
        Text(
            title,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (body != null) Text(
            body,
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Centred on a tall screen, scrolling on a short one, with the Previous / Next bar fixed below
 *  (iOS `centeringScroll`). */
@Composable
private fun ColumnScope.GuideCenteringScroll(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val minHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { content() }
        }
    }
}

/** A step with an icon, title, body and an optional [extra] under them (iOS `scaffold`). */
@Composable
private fun GuideScaffold(
    icon: ImageVector,
    title: String,
    body: String,
    previousEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    buttonTitle: String = stringResource(R.string.next),
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        GuideCenteringScroll {
            Column(
                Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                GuideHeading(icon, title, body)
                extra()
            }
        }
        GuideBottomBar(buttonTitle, previousEnabled, onPrevious, onNext)
    }
}

/** Language and currency: the option list is the part that scrolls (iOS `listStep`). */
@Composable
private fun GuideListStep(
    icon: ImageVector,
    title: String,
    body: String,
    previousEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    rows: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        GuideHeading(icon, title, body)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { rows() }
        GuideBottomBar(stringResource(R.string.next), previousEnabled, onPrevious, onNext)
    }
}

/** One language or currency, with a check on the one in use (iOS `optionRow`). */
@Composable
private fun GuideOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .clickable(role = Role.Button) { if (!selected) onSelect() }
            .padding(12.dp),
    ) {
        Text(label, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(20.dp))
    }
}

/** A title, optional subtitle and cards (iOS `cardsStep`). */
@Composable
private fun GuideCardsStep(
    title: String,
    subtitle: String?,
    cards: List<WelcomeGuideCard>,
    buttonTitle: String,
    previousEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        GuideCenteringScroll {
            Column(
                Modifier.padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                GuideHeading(null, title, subtitle)
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cards.forEach { GuideCard(it) }
                }
            }
        }
        GuideBottomBar(buttonTitle, previousEnabled, onPrevious, onNext)
    }
}

@Composable
private fun GuideCard(card: WelcomeGuideCard) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(12.dp),
    ) {
        Icon(card.icon, contentDescription = null, tint = KaspaTeal, modifier = Modifier.width(28.dp).height(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!card.address.isNullOrEmpty()) {
                MiddleEllipsisText(
                    card.address,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(card.caption, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

/** Previous and Next (or Finish), always in the same place; Previous is dimmed on Welcome (iOS
 *  `bottomBar`). */
@Composable
private fun GuideBottomBar(title: String, previousEnabled: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .alpha(if (previousEnabled) 1f else 0.35f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.incomingBubble)
                .clickable(enabled = previousEnabled, role = Role.Button, onClick = onPrevious)
                .padding(vertical = 14.dp),
        ) {
            Text(stringResource(R.string.previous), color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(KaspaTeal)
                .clickable(role = Role.Button, onClick = onNext)
                .padding(vertical = 14.dp),
        ) {
            Text(title, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
