package com.kachat.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors

/**
 * The Terms of Use (iOS 9e22075): everyone agrees before using the app, and they stay readable in
 * Settings. Bump [CURRENT_VERSION] when the text changes, and everyone is asked again.
 */
object KaPostTerms {
    const val CURRENT_VERSION = 1
    private const val PREFS = "kapost_terms"
    private const val KEY_ACCEPTED_VERSION = "kaposts_terms_accepted_version"

    /** The version last agreed to on this device. */
    fun acceptedVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACCEPTED_VERSION, 0)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION).apply()
    }

    val sections = listOf(
        "No objectionable content or abuse" to
            "KaPost has no tolerance for objectionable content or abusive users. You may not post anything that is illegal, sexual content involving children, hate speech, threats, harassment, content that encourages violence or self-harm, or scams. Accounts that do can be removed from what KaPost shows, without notice.",
        "Your posts are public and permanent" to
            "Posts, replies, likes and room messages are public transactions on the Kaspa network. You are responsible for what you post. Deleting a post removes it from KaPost, but nobody can erase a transaction from the network.",
        "Reporting and blocking" to
            "Use Report on a post or room message to tell us about objectionable content; a person reads every report. Use Block to stop seeing an account's posts and room messages. We can remove content and accounts from the KaChat indexer, which KaPost reads by default.",
        "Your account and your funds" to
            "Your account is 12 or 24 words kept on your device. We never have them, so we can't recover lost ones or reverse a transaction. Posting pays a network fee in KAS, and you are responsible for the funds you hold and send.",
        "No warranty" to
            "KaPost is provided as it is, without warranty, and to the extent the law allows we are not liable for losses from using it or from what other users post. These terms are between you and KaPost's developer, not Google.",
        "Contact" to
            "Questions and reports: ${ContentReport.SUPPORT_EMAIL}",
    )
}

/** The terms' text, shared by the first-launch screen and Settings. */
@Composable
private fun KaPostTermsText(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        KaPostTerms.sections.forEach { (title, text) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = colors.textSecondary, fontSize = 15.sp)
            }
        }
    }
}

/** Settings > Terms of Use. */
@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.statusBarsPadding()) {
            SettingsSubpageBar(title = "Terms of Use", onBack = onBack)
        }
        KaPostTermsText(Modifier.weight(1f).verticalScroll(rememberScrollState()))
    }
}

/** The first thing the app shows until the current terms are agreed to. */
@Composable
fun TermsAcceptanceScreen(onAgree: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Text(
            "Terms of Use",
            color = colors.textPrimary,
            fontSize = LargeTitleSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        )
        HorizontalDivider(color = colors.surfaceVariant)
        KaPostTermsText(Modifier.weight(1f).verticalScroll(rememberScrollState()))
        HorizontalDivider(color = colors.surfaceVariant)
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("You need to agree to use KaPost.", color = colors.textSecondary, fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaspaTeal)
                    .clickable(onClick = onAgree)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("I Agree", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
