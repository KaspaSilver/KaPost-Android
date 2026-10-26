package com.kachat.app.ui.screens

import com.kachat.app.R
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.SubcomposeAsyncImage
import com.kachat.app.services.KnsProfileFields
import com.kachat.app.services.WalletService
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.launch
import com.kachat.app.util.KaspaUnit
import com.kachat.app.util.showAddressCopiedToast
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Refresh


















/**
 * The wizard's fixed two-button footer: Previous left, Next right, same place on every step so it
 * can be tapped through without chasing buttons. Matches the KaChat setup guide's bar.
 *
 * A disabled Previous renders dimmed rather than vanishing, so Next never moves between steps.
 */
@Composable
fun KnsWizardBottomBar(
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String = "Next",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onBack?.invoke() },
            enabled = onBack != null,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalAppColors.current.surface,
                contentColor = LocalAppColors.current.textPrimary,
            ),
        ) {
            Text(androidx.compose.ui.res.stringResource(com.kachat.app.R.string.kg_previous), fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = { onNext?.invoke() },
            enabled = onNext != null,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.Black),
        ) {
            Text(nextLabel, fontWeight = FontWeight.Bold)
        }
    }
}
