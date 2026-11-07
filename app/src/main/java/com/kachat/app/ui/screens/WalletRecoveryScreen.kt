package com.kachat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel

/**
 * Shown instead of the app when the account store can't be opened (iOS WalletRecoveryView): a
 * phone restored without KaPost's Keystore key, or a Keystore that refuses it. The store used to
 * throw while WalletManager was built, and the app closed before any screen. This says what
 * happened and leads into Import ([WalletViewModel.enterKeyRecoveryWords]). Nothing changes until
 * an import completes, and leaving Import comes back here (iOS WalletRecoveryView).
 *
 * Unlike iOS it can't name the account (its record is in the store that can't be read), and it
 * has no "Remove This Account From the Phone": a completed import already sets the unreadable
 * store aside, and the message history it would delete is not in that store.
 */
@Composable
fun WalletRecoveryScreen(viewModel: WalletViewModel) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.KeyOff,
            contentDescription = null,
            tint = KaspaTeal,
            modifier = Modifier.size(52.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.wallet_recovery_title),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.wallet_recovery_body),
                color = colors.textSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.enterKeyRecoveryWords() },
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .height(52.dp),
        ) {
            Text(
                stringResource(R.string.wallet_recovery_enter_words),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
