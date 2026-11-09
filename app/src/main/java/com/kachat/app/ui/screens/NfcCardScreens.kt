package com.kachat.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.services.NfcCard
import com.kachat.app.services.NfcCardBackupFormat
import com.kachat.app.services.NfcCardBackupPayload
import com.kachat.app.services.NfcCardConnection
import com.kachat.app.services.NfcCardError
import com.kachat.app.services.NfcCardStatus
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * One scan's state, for [NfcScanSheet]. iOS draws its own sheet for a reader session and asks the
 * session to show the message; on Android the app draws it.
 */
private class NfcScanner(private val context: Context) {
    var scanning by mutableStateOf(false)
    var message by mutableStateOf(NfcCard.TAP_PROMPT)
    var job: Job? = null

    suspend fun <T> run(moving: String, work: suspend (NfcCardConnection) -> T): T {
        val activity = context.findActivity() ?: throw NfcCardError.NfcUnavailable()
        scanning = true
        message = NfcCard.TAP_PROMPT
        try {
            return NfcCard.run(activity) { connection ->
                message = moving
                work(connection)
            }
        } finally {
            scanning = false
        }
    }

    fun cancel() {
        job?.cancel()
        scanning = false
    }
}

/** Android's stand-in for the system NFC sheet: what to do with the card, and Cancel. */
@Composable
private fun NfcScanSheet(scanner: NfcScanner) {
    if (!scanner.scanning) return
    val colors = LocalAppColors.current
    ActionSheetContainer(
        title = "Ready to Scan",
        subtitle = null,
        onDismiss = { scanner.cancel() },
        headerIcon = {
            Icon(Icons.Filled.Contactless, contentDescription = null, tint = KaspaTeal, modifier = Modifier.padding(bottom = 8.dp).size(44.dp))
        },
    ) {
        Text(
            scanner.message,
            color = colors.textSecondary,
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ActionSheetRow(icon = Icons.Filled.Close, title = stringResource(R.string.cancel), subtitle = "Stops looking for the card.") {
            scanner.cancel()
        }
    }
}

/** A PIN or passphrase entry, hidden unless its eye is tapped, so it isn't in screenshots and
 *  screen shares, while a mistake can still be caught before it costs a PIN try (iOS ee5ec98). It
 *  never offers to save the value or autofill it, and the Password keyboard learns nothing. A PIN
 *  field only takes digits, up to the PIN's length. */
@Composable
private fun NfcCardSecretField(title: String, value: String, onValueChange: (String) -> Unit, isNumeric: Boolean) {
    val colors = LocalAppColors.current
    var revealed by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(if (isNumeric) typed.filter { it in '0'..'9' }.take(NfcCard.PIN_LENGTH) else typed)
        },
        placeholder = { Text(title, color = colors.textSecondary) },
        singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (revealed) R.string.hide_password else R.string.show_password),
                    tint = colors.textSecondary,
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = KaspaTeal,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun NfcCardNote(icon: ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(text, color = LocalAppColors.current.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NfcCardTextButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (enabled) KaspaTeal else LocalAppColors.current.textSecondary,
        fontSize = 17.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun NfcCardErrorAlert(message: String?, onDismiss: () -> Unit) {
    val text = message ?: return
    val colors = LocalAppColors.current
    com.kachat.app.ui.theme.IosAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("NFC Card", color = colors.textPrimary) },
        text = { Text(text, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
            }
        },
    )
}

private enum class NfcBackupStep { INTRO, DETAILS, ERASE, SAVED, ERASED }

/**
 * Settings > Back Up to NFC Card, behind the seed phrase's biometric gate and only offered for an
 * account with a passphrase (iOS 9653c04 `NFCCardBackupView`). Two taps: the first reads the card to
 * learn whether it needs a new PIN or already holds a backup, the second saves. The passphrase is
 * never written to the card. It never clears the "not backed up" card on the Feed: the written seed
 * phrase stays the main copy.
 */
@Composable
fun NfcCardBackupScreen(viewModel: WalletViewModel, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NfcScanner(context) }
    var step by remember { mutableStateOf(NfcBackupStep.INTRO) }
    var card by remember { mutableStateOf<NfcCardStatus?>(null) }
    var pin by remember { mutableStateOf("") }
    // A new PIN, typed again: a typo here would lock the backup behind a PIN nobody knows
    // (iOS 33095ac).
    var confirmPin by remember { mutableStateOf("") }
    var pinCleared by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEraseConfirmation by remember { mutableStateOf(false) }
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    val pinValid = NfcCard.isValidPin(pin)
    // The PIN can go to the card: a valid one, typed twice when it's a new one (iOS 33095ac).
    val canSave = card?.let { pinValid && (it.pinSet || pin == confirmPin) } ?: false
    // The PIN shows as typed: no screenshots or Recents thumbnail while it can be on screen.
    SecureWindowWhileShown()

    fun show(error: Throwable) {
        if (error is CancellationException) throw error
        errorMessage = NfcCard.messageFor(error)
    }

    fun backToStart() {
        pin = ""
        confirmPin = ""
        card = null
        step = NfcBackupStep.INTRO
    }

    fun working(block: suspend () -> Unit) {
        busy = true
        scanner.job = scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun scanCard() = working {
        try {
            val status = scanner.run("Reading. Keep the card still.") { it.select() }
            if (status.pinBlocked) throw NfcCardError.PinBlocked()
            card = status
            step = NfcBackupStep.DETAILS
        } catch (e: Exception) {
            show(e)
        }
    }

    fun save() {
        val scanned = card ?: return
        if (!canSave) return
        val enteredPin = pin
        working {
            try {
                val payload = withContext(Dispatchers.Default) { viewModel.nfcCardBackupPayload() }
                val backup = withContext(Dispatchers.Default) { NfcCardBackupFormat.seal(payload, enteredPin) }
                if (backup.size > scanned.maxBackup) throw NfcCardError.BackupTooLarge()
                scanner.run("Saving. Keep the card still.") { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked()
                    // The confirmations were for the card scanned first. An interrupted save can
                    // legitimately leave that card with its new PIN and no backup yet.
                    // A PIN where the scanned card had none is another card too (this save sets the
                    // PIN it then finds, and records that below): stopped before verifyPin, which
                    // would cost that card a try with a PIN that was never its own (iOS 33095ac).
                    if ((current.hasBackup && !scanned.hasBackup) || (scanned.pinSet && !current.pinSet) ||
                        (!scanned.pinSet && current.pinSet)
                    ) {
                        throw NfcCardError.CardChanged()
                    }
                    if (!current.pinSet) {
                        connection.setPin(enteredPin)
                        // The card has this PIN now, whatever happens next: a retry after an
                        // interrupted save finds it set and asks for it as the card's PIN.
                        card = scanned.copy(pinSet = true, pinBlocked = false, triesRemaining = current.triesRemaining)
                        confirmPin = ""
                    }
                    connection.verifyPin(enteredPin)
                    connection.writeBackup(backup)
                    if (!connection.readBackup().contentEquals(backup)) throw NfcCardError.SaveNotConfirmed()
                }
                // What's on the card has to open with this PIN, not just match what was sent.
                withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                pin = ""
                confirmPin = ""
                step = NfcBackupStep.SAVED
            } catch (e: NfcCardError.WrongPin) {
                pin = ""
                confirmPin = ""
                // the PIN this save may have just set counts (iOS 33095ac)
                card = scanned.copy(pinSet = card?.pinSet ?: scanned.pinSet, pinBlocked = false, triesRemaining = e.triesRemaining)
                show(e)
            } catch (e: Exception) {
                if (e is NfcCardError.PinBlocked || e is NfcCardError.CardChanged) backToStart()
                show(e)
            }
        }
    }

    fun eraseBackup() {
        if (!pinValid) return
        val enteredPin = pin
        working {
            try {
                pinCleared = scanner.run("Erasing. Keep the card still.") { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked()
                    if (!current.pinSet) throw NfcCardError.NoPin()
                    if (!current.hasBackup) throw NfcCardError.NoBackup()
                    connection.verifyPin(enteredPin)
                    connection.wipe()
                    // Cards loaded with older software keep their PIN. The erase has already
                    // worked, so a card pulled away here only costs the wording.
                    runCatching { connection.select() }.getOrNull()?.pinSet == false
                }
                pin = ""
                step = NfcBackupStep.ERASED
            } catch (e: Exception) {
                pin = ""
                show(e)
            }
        }
    }

    BackHandler(enabled = !busy, onBack = onClose)
    BackupScaffold(
        title = "NFC Card Backup",
        onClose = if (busy) ({}) else onClose,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (step) {
                NfcBackupStep.INTRO -> {
                    Text("Back up to an NFC card", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Saves this account's 12 or 24 words on your card, behind a 6-digit PIN.", color = colors.textSecondary, fontSize = 17.sp)
                    NfcCardNote(Icons.Filled.Key, KaspaTeal, "Your passphrase is never saved to the card. You'll enter it when you restore, and it's what keeps this account safe if someone gets past the PIN.")
                    NfcCardNote(Icons.Filled.Warning, colors.warning, "This isn't cold storage. The card only holds a copy and never creates keys, so keep your 12 or 24 words and passphrase written down too.")
                    NfcCardNote(Icons.Filled.SdCard, KaspaTeal, "Your card needs the KaChat-NFC software first. Load it from the NFC Card page in Kaspa Quick Start.")
                    CreateWalletNextButton("Scan Card", enabled = !busy, busy = busy) { scanCard() }
                    NfcCardTextButton("Erase a Backup on a Card", enabled = !busy) { step = NfcBackupStep.ERASE }
                }
                NfcBackupStep.DETAILS -> card?.let { scanned ->
                    if (scanned.hasBackup) {
                        NfcCardNote(Icons.Filled.Warning, colors.warning, "This card already holds a backup. Saving replaces it once the new one is confirmed.")
                    }
                    if (scanned.pinSet) {
                        NfcCardSecretField("Card PIN", pin, { pin = it }, isNumeric = true)
                        if (scanned.triesRemaining < 5) {
                            Text("${scanned.triesRemaining} PIN tries left on this card.", color = colors.warning, fontSize = 13.sp)
                        }
                    } else {
                        NfcCardSecretField("New 6-digit PIN", pin, { pin = it }, isNumeric = true)
                        // typed twice (iOS 33095ac)
                        NfcCardSecretField(stringResource(R.string.nfc_card_reenter_pin), confirmPin, { confirmPin = it }, isNumeric = true)
                        if (NfcCard.isValidPin(confirmPin) && pin != confirmPin) {
                            Text(stringResource(R.string.nfc_card_pins_dont_match), color = colors.danger, fontSize = 13.sp)
                        }
                        Text(
                            "Pick one you'll remember. After 5 wrong tries the PIN is blocked, and the only fix is reloading the card in Kaspa Quick Start, which erases it.",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    CreateWalletNextButton(
                        if (scanned.hasBackup) "Replace Backup" else "Save to Card",
                        enabled = canSave && !busy,
                        busy = busy,
                    ) {
                        if (scanned.hasBackup) showReplaceConfirmation = true else save()
                    }
                }
                NfcBackupStep.ERASE -> {
                    Text("Erase a backup", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Enter the card's PIN, then tap the card to erase the backup on it.", color = colors.textSecondary, fontSize = 17.sp)
                    NfcCardSecretField("Card PIN", pin, { pin = it }, isNumeric = true)
                    CreateWalletNextButton("Erase Backup", enabled = pinValid && !busy, busy = busy) { showEraseConfirmation = true }
                    NfcCardTextButton(stringResource(R.string.back), enabled = !busy) {
                        pin = ""
                        step = NfcBackupStep.INTRO
                    }
                }
                NfcBackupStep.SAVED -> NfcCardFinished(
                    title = "Backup saved",
                    detail = "It was read back from the card and checked. To restore it, choose Import Existing Account, then Restore from NFC Card, and enter your PIN and passphrase. Your passphrase isn't on the card, so keep it and your written 12 or 24 words safe.",
                    onDone = onClose,
                )
                NfcBackupStep.ERASED -> NfcCardFinished(
                    title = "Backup erased",
                    detail = if (pinCleared) "The backup and PIN are gone from this card. The next backup on it will ask you to set a new PIN."
                    else "The backup is gone from this card. Its PIN stays the same.",
                    onDone = onClose,
                )
            }
        }
    }
    NfcScanSheet(scanner)
    NfcCardErrorAlert(errorMessage) { errorMessage = null }
    if (showEraseConfirmation) {
        ConfirmActionSheet(
            title = "Erase the backup on this card?",
            confirmTitle = "Erase Backup",
            confirmSubtitle = "This can't be undone.",
            confirmIcon = Icons.Filled.Delete,
            onConfirm = {
                showEraseConfirmation = false
                eraseBackup()
            },
            onDismiss = { showEraseConfirmation = false },
        )
    }
    if (showReplaceConfirmation) {
        ConfirmActionSheet(
            title = "Replace the backup on this card?",
            confirmTitle = "Replace Backup",
            confirmSubtitle = "The old backup is removed once the new one is confirmed.",
            confirmIcon = Icons.Filled.Contactless,
            onConfirm = {
                showReplaceConfirmation = false
                save()
            },
            onDismiss = { showReplaceConfirmation = false },
        )
    }
}

@Composable
private fun NfcCardFinished(title: String, detail: String, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(40.dp))
        Text(title, color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = colors.textSecondary, fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
        CreateWalletNextButton("Done", enabled = true, onClick = onDone)
    }
}

/**
 * Import Account > Restore from NFC Card (iOS 9653c04 `NFCCardRestoreView`): the PIN and a tap read
 * the backup, then the passphrase, which was never on the card, opens the account. A wrong passphrase
 * needs no second tap, since the backup is in memory.
 */
@Composable
fun NfcCardRestoreScreen(viewModel: WalletViewModel, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NfcScanner(context) }
    var pin by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<NfcCardBackupPayload?>(null) }
    var reading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val importState by viewModel.importWalletState.collectAsState()
    val importing = importState.status == WalletViewModel.ImportWalletStatus.IMPORTING
    val busy = reading || importing
    val pinValid = NfcCard.isValidPin(pin)
    SecureWindowWhileShown()

    fun readCard() {
        if (!pinValid) return
        val enteredPin = pin
        reading = true
        scanner.job = scope.launch {
            try {
                val backup = scanner.run("Reading. Keep the card still.") { connection ->
                    val current = connection.select()
                    if (current.pinBlocked) throw NfcCardError.PinBlocked()
                    if (!current.pinSet) throw NfcCardError.NoPin()
                    if (!current.hasBackup) throw NfcCardError.NoBackup()
                    connection.verifyPin(enteredPin)
                    connection.readBackup()
                }
                payload = withContext(Dispatchers.Default) { NfcCardBackupFormat.open(backup, enteredPin) }
                pin = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pin = ""
                errorMessage = NfcCard.messageFor(e)
            } finally {
                reading = false
            }
        }
    }

    fun restore() {
        val backup = payload ?: return
        val entered = passphrase
        reading = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { viewModel.checkNfcCardPassphrase(backup, entered) }
                viewModel.restoreFromNfcCard(backup, entered)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = NfcCard.messageFor(e)
            } finally {
                reading = false
            }
        }
    }

    BackHandler(enabled = !busy, onBack = onBack)
    BackupScaffold(title = "Import Account", onBack = if (busy) ({}) else onBack, onClose = if (busy) ({}) else onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val backup = payload
            if (backup == null) {
                Text("Restore from an NFC card", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Enter the card's PIN, then tap the card to read your backup.", color = colors.textSecondary, fontSize = 17.sp)
                NfcCardSecretField("Card PIN", pin, { pin = it }, isNumeric = true)
                NfcCardNote(Icons.Filled.Warning, colors.warning, "After 5 wrong tries the PIN is blocked for good, and this backup can no longer be read.")
                CreateWalletNextButton("Scan Card", enabled = pinValid && !busy, busy = reading) { readCard() }
            } else {
                Text("Enter your passphrase", color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "The backup was read from your card. Your passphrase was never on the card, so enter it exactly as you set it, including capitals and spaces.",
                    color = colors.textSecondary,
                    fontSize = 17.sp,
                )
                NfcCardSecretField("Passphrase", passphrase, { passphrase = it }, isNumeric = false)
                val family = backup.walletFamily
                if (passphrase.isNotEmpty() && family != null) {
                    PassphraseAddressPreview(
                        passphrase = passphrase,
                        showsFundsHint = true,
                        previewAddress = { viewModel.previewNfcCardAddress(backup, it) },
                        previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                    )
                }
                CreateWalletNextButton("Restore Account", enabled = passphrase.isNotEmpty() && !busy, busy = busy) { restore() }
            }
        }
    }
    NfcScanSheet(scanner)
    NfcCardErrorAlert(errorMessage) { errorMessage = null }
    val importError = importState.errorMessage.takeIf { importState.status == WalletViewModel.ImportWalletStatus.FAILED }
    NfcCardErrorAlert(importError) { viewModel.resetImportWalletState() }
}
