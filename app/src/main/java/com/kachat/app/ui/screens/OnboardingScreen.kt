package com.kachat.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kachat.app.R
import com.kachat.app.models.ImportAccountCandidate
import com.kachat.app.models.ImportAccountChoice
import com.kachat.app.models.WalletSourceFamily
import com.kachat.app.services.WalletManager
import com.kachat.app.ui.theme.KaspaSubtext
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.DarkAppColors
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.CreateAccountSteps
import com.kachat.app.util.authenticateWithDeviceCredential
import com.kachat.app.util.hasDeviceCredential
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Main entry point for the onboarding flow.
 */
@Composable
fun OnboardingScreen(viewModel: WalletViewModel) {
    val navController = rememberNavController()
    // The recovery screen's "Enter 12 or 24 Words" opens Import over Welcome (iOS 1ab014f
    // WalletRecoveryView: the same flow onboarding uses, from the name screen on).
    val openImport by viewModel.openImportAfterRecovery.collectAsState()
    // Set once that Import is on screen: leaving it for Welcome without importing goes back to
    // the recovery screen (iOS: Back from its pushed import returns to WalletRecoveryView).
    var recoveryImportOpen by remember { mutableStateOf(false) }
    LaunchedEffect(openImport) {
        if (openImport) {
            viewModel.consumeOpenImportAfterRecovery()
            navController.navigate("import_name")
            recoveryImportOpen = true
        }
    }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        // Read from the controller, not the (possibly lagging) state, before leaving.
        if (recoveryImportOpen && navController.currentDestination?.route == "welcome") {
            recoveryImportOpen = false
            viewModel.leaveRecoveryImport()
        }
    }
    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(
                viewModel,
                onCreateWithPassphrase = {
                    viewModel.beginCreateWithPassphrase()
                    navController.navigate("passphrase_create")
                },
                onOpenAppSettings = { navController.navigate("app_settings") },
                onNavigateToImport = {
                    // A stale SUCCESS/FAILED status left over from a previous import would
                    // otherwise fire ImportWalletScreen's success LaunchedEffect immediately on
                    // entry (this state lives in the singleton WalletViewModel, not the screen),
                    // silently logging into whatever account is currently active instead of
                    // letting the user type a new phrase.
                    viewModel.resetImportWalletState()
                    // a new import starts with no words from an earlier one
                    viewModel.abandonImport()
                    // No "which wallet" list any more (iOS 1ab014f): the name comes first, and the
                    // passphrase step finds the wallet type from the words.
                    navController.navigate("import_name")
                }
            )
        }
        composable("nfc_restore") {
            NfcCardRestoreScreen(viewModel, onBack = { navController.popBackStack() })
        }
        // Import Account (iOS dd0aab1, 1ab014f): the name (the same screen as Create Account's
        // first step, with Restore from NFC Card at the bottom), the seed length, the words, then
        // the passphrase step, which finds the wallet those words belong to.
        composable("import_name") {
            ImportNameScreen(
                onBack = { navController.popBackStack() },
                onNext = { name -> navController.navigate("import_length?name=${android.net.Uri.encode(name)}") },
                onRestoreFromNfcCard = { navController.navigate("nfc_restore") },
            )
        }
        composable(
            "import_length?name={name}",
            arguments = listOf(androidx.navigation.navArgument("name") { defaultValue = "" })
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            ImportLengthScreen(
                onBack = { navController.popBackStack() },
                onNext = { count -> navController.navigate("import_wallet?name=${android.net.Uri.encode(name)}&count=$count") }
            )
        }
        composable(
            "import_wallet?name={name}&count={count}",
            arguments = listOf(
                androidx.navigation.navArgument("name") { defaultValue = "Imported Account" },
                androidx.navigation.navArgument("count") { type = androidx.navigation.NavType.IntType; defaultValue = 24 }
            )
        ) { backStackEntry ->
            // Back past the words leaves the import: they are forgotten. Back from the passphrase
            // step returns here and keeps them (ImportWalletScreen refills its slots).
            val leaveWords: () -> Unit = {
                navController.popBackStack()
                viewModel.abandonImport()
            }
            BackHandler(onBack = leaveWords)
            ImportWalletScreen(
                viewModel,
                accountName = backStackEntry.arguments?.getString("name") ?: "Imported Account",
                initialWordCount = backStackEntry.arguments?.getInt("count") ?: 24,
                onBack = leaveWords,
                // Seed validated + stashed by prepareImport(); the actual import happens on the
                // passphrase screen (which arms the Welcome Guide + logs in on success).
                onProceed = { navController.navigate("passphrase_import") }
            )
        }
        composable("passphrase_create") {
            val createError by viewModel.createWalletError.collectAsState()
            // Nothing is shown or saved before the passphrase: leaving forgets the words, and a
            // step restored after the process was recreated starts from new ones.
            LaunchedEffect(Unit) { viewModel.ensureCreateWithPassphrase() }
            val abandon: () -> Unit = {
                navController.popBackStack()
                viewModel.abandonCreatedWallet()
            }
            BackHandler(onBack = abandon)
            PassphraseSetupScreen(
                mode = PassphraseMode.CREATE,
                errorMessage = createError,
                onDismissError = { viewModel.clearCreateWalletError() },
                onBack = abandon,
                previewAddress = { viewModel.previewChattingAddress(it) },
                previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                // commitCreatedWallet() derives + saves with the passphrase, arms the guide, and
                // logs in (which swaps onboarding for the main shell).
                onProceed = { passphrase -> viewModel.commitCreatedWallet(passphrase) }
            )
        }
        composable("passphrase_import") {
            val importState by viewModel.importWalletState.collectAsState()
            // Restored after the process was recreated: the words are gone, so back to the words
            // screen to type them again.
            LaunchedEffect(Unit) {
                if (viewModel.pendingMnemonicWords.isEmpty()) navController.popBackStack()
            }
            PassphraseSetupScreen(
                mode = PassphraseMode.IMPORT,
                isBusy = importState.status == WalletViewModel.ImportWalletStatus.IMPORTING,
                errorMessage = importState.errorMessage.takeIf {
                    importState.status == WalletViewModel.ImportWalletStatus.FAILED
                },
                onDismissError = { viewModel.resetImportWalletState() },
                onBack = { navController.popBackStack() },
                previewAddress = { viewModel.previewChattingAddress(it) },
                previewBalance = { viewModel.previewAddressBalanceSompi(it) },
                // Every wallet type these words could be (iOS 1ab014f findImportAccounts).
                findAccounts = { viewModel.findImportAccounts(it) },
                everyWalletType = { viewModel.importAddressesAtZero(it) },
                onSelectImportChoice = { viewModel.setPendingImportChoice(it) },
                // commitImport() imports with the passphrase, arms the guide, and logs in on success.
                onProceed = { passphrase -> viewModel.commitImport(passphrase) }
            )
        }

        // App Settings (the gear on the accounts screen, matching iOS's gearshape sheet on
        // OnboardingView): ONLY the app-wide settings tier - Customization/Security/Connection/
        // Diagnostics - every leaf reusing the exact screens the in-account Settings uses.
        composable("app_settings") {
            AppSettingsHubScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomization = { navController.navigate("app_settings_customization") },
                onOpenSecurity = { navController.navigate("app_settings_security") },
                onOpenConnection = { navController.navigate("app_settings_connection") },
                onOpenDiagnostics = { navController.navigate("app_settings_diagnostics") }
            )
        }
        composable("app_settings_customization") {
            AppCustomizationScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLanguage = { navController.navigate("app_settings_language") },
                onNavigateToCurrency = { navController.navigate("app_settings_currency") },
                walletViewModel = viewModel
            )
        }
        composable("app_settings_language") {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("app_settings_currency") {
            CurrencySettingsScreen(onBack = { navController.popBackStack() }, walletViewModel = viewModel)
        }
        composable("app_settings_security") {
            AppSecurityScreen(
                onBack = { navController.popBackStack() },
                walletViewModel = viewModel
            )
        }
        // The same Connection page as in-account Settings (iOS ConnectionHubPage): Connection
        // Settings, the Testnet switch, Kaspa Explorer.
        composable("app_settings_connection") {
            AppConnectionScreen(
                onBack = { navController.popBackStack() },
                onOpenConnectionSettings = { navController.navigate("app_settings_connection_settings") },
                onOpenExplorer = { navController.navigate("app_settings_explorer") },
            )
        }
        composable("app_settings_connection_settings") {
            ConnectionSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("app_settings_explorer") {
            KaspaExplorerSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("app_settings_diagnostics") {
            AppDiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun WelcomeScreen(
    viewModel: WalletViewModel,
    onCreateWithPassphrase: () -> Unit,
    onNavigateToImport: () -> Unit,
    onOpenAppSettings: () -> Unit = {}
) {
    // The background is dark in both themes, so the colors drawn on top of it have to be the
    // dark set too - otherwise textPrimary renders near-black on near-black in light mode.
    // Scoped to this screen.
    CompositionLocalProvider(LocalAppColors provides DarkAppColors) {
    Surface(
        color = KaChatSignInBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        // Top-right gear (matches iOS OnboardingView's gearshape toolbar button): app-wide
        // settings that make sense with no account active - see AppSettingsHubScreen. iOS 26
        // draws a toolbar item on a glass disc, the outlined gear white (the screen is always dark).
        SheetBarIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.app_settings),
            onClick = onOpenAppSettings,
            tint = Color.White,
            iconSize = 22.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
        )
        // iOS keeps Create and Import 40 above the safe area: the navigation bar (gesture handle
        // or 3-button bar) is cleared first, then the 40 - a fixed chain, nothing swapped in.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top spacer to help center the middle content
            Spacer(modifier = Modifier.weight(1f))

            val hasWallet by viewModel.hasWallet.collectAsState()
            val accounts by viewModel.accounts.collectAsState()

            // Wordmark with the app mark beside it - the launcher icon itself, so the two never
            // drift apart. Replaces the 160dp logo above the title: on a device with accounts
            // saved, that block was most of why the list below had nowhere to go.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "KaPost",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = LocalAppColors.current.textPrimary
                )
                Image(
                    // The icon as one picture (kaposts_app_mark), NOT R.mipmap.ic_launcher: on API 26+
                    // that id resolves to the adaptive-icon XML, which painterResource cannot
                    // load - it throws "Only VectorDrawables and rasterized asset types are
                    // supported" and takes the whole welcome screen down with it. The dark colours
                    // get the icon's dark appearance (iOS KaPostsLogo-dark) - which is always, here:
                    // this screen is dark in both themes, as iOS's OnboardingView is.
                    painter = painterResource(
                        id = if (LocalAppColors.current.isDark) R.drawable.kaposts_app_mark_dark else R.drawable.kaposts_app_mark
                    ),
                    contentDescription = "KaPost logo",
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                )
            }

            // Shown whether or not there are accounts saved: it is what the screen is, and
            // hiding it on a returning device left the wordmark sitting on its own with nothing
            // saying what it belongs to.
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Posts and public chats on Kaspa BlockDAG",
                style = MaterialTheme.typography.bodyLarge,
                color = KaspaSubtext,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            val biometricAccountLoginEnabled by viewModel.biometricAccountLoginEnabled.collectAsState()
            val deleteAccountError by viewModel.deleteAccountError.collectAsState()
            val isCreatingAccount by viewModel.isCreatingAccount.collectAsState()
            val createAccountError by viewModel.createAccountError.collectAsState()
            val backupPending by viewModel.accountBackupStatus.pendingAddresses.collectAsState()

            if (hasWallet) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.saved_accounts),
                        color = LocalAppColors.current.textPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Four at a time, then this scrolls inside itself - the fifth account is
                    // where scrolling starts. A dozen saved accounts used to just make the page
                    // taller, pushing Create and Import off the bottom of the screen.
                    // heightIn(max) rather than a fixed height so three accounts still take
                    // three rows' worth of space, not four.
                    val visibleRows = minOf(accounts.size, 4)
                    // Whatever is left after the rest of the page, floored at two rows, so a short
                    // phone keeps Create, Import and the Advanced link on screen (iOS 9e22075).
                    val spareHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 400).coerceAtLeast(2 * 84)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = minOf(visibleRows * 84, spareHeight).dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        accounts.forEach { account ->
                            // Creating, signing in or removing: one at a time, since each one
                            // replaces the account in the active slot. Signing in and removing
                            // finish on the spot here; creating runs on (iOS d816404). Checked
                            // again after the phone's lock, which may have let a create start.
                            SavedAccountCard(
                                account = account,
                                requireBiometricLogin = biometricAccountLoginEnabled,
                                notBackedUp = account.address in backupPending,
                                enabled = !isCreatingAccount,
                                onLogin = { if (!viewModel.isCreatingAccount.value) viewModel.login(account.address) },
                                onRename = { newName ->
                                    if (!viewModel.isCreatingAccount.value) viewModel.renameAccount(account.address, newName)
                                },
                                onDelete = { alsoRemoveBackup ->
                                    if (!viewModel.isCreatingAccount.value) viewModel.deleteWallet(account.address, alsoRemoveBackup)
                                },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        // The account is already gone by the time this can be known, so this row is
                        // the only place it can be said: the archive is still on their Nextcloud and
                        // they have to remove it there.
                        deleteAccountError?.let { failure ->
                            Surface(
                                color = LocalAppColors.current.danger.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearDeleteAccountError() },
                            ) {
                                Text(
                                    failure,
                                    color = LocalAppColors.current.danger,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            // Create new wallet button: one tap, straight into the new account (iOS 9e22075).
            Button(
                onClick = { viewModel.createAccountInOneTap() },
                enabled = !isCreatingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                // iOS: white body text on the accent, the icon plus.circle.fill.
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTeal,
                    contentColor = Color.White,
                    disabledContainerColor = KaspaTeal,
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCreatingAccount) {
                        com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.create_new_account),
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import existing wallet button
            Button(
                onClick = onNavigateToImport,
                enabled = !isCreatingAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppColors.current.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // iOS: square.and.arrow.down, body text in the primary colour.
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = LocalAppColors.current.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.import_existing_account),
                        fontSize = 17.sp,
                        color = LocalAppColors.current.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // A passphrase is the only thing Create New Account doesn't do: it moved here.
            Text(
                text = "Advanced: Create with a Passphrase",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = KaspaTeal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isCreatingAccount, onClick = onCreateWithPassphrase)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            createAccountError?.let { message ->
                com.kachat.app.ui.theme.IosAlertDialog(
                    onDismissRequest = { viewModel.clearCreateAccountError() },
                    containerColor = LocalAppColors.current.surface,
                    title = { Text("Couldn't Create Account", color = LocalAppColors.current.textPrimary) },
                    text = { Text(message, color = LocalAppColors.current.textSecondary) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearCreateAccountError() }) {
                            Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
        // Which build this phone runs, right where sign-in problems get reported from (iOS
        // e8f4617). Drawn inside the column's 40dp bottom padding, 10 above the navigation bar
        // (iOS: 10pt above the safe area), so the height budget that keeps Create and Import on
        // screen is untouched and a 3-button bar never covers it.
        Text(
            text = "KaPost ${com.kachat.app.util.AppVersion.display}",
            fontSize = 11.sp,
            color = LocalAppColors.current.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedAccountCard(
    account: WalletManager.Account,
    requireBiometricLogin: Boolean = false,
    /** This account hasn't finished the backup steps: the Delete warning says so. */
    notBackedUp: Boolean = false,
    /** False while another account action runs (a create): the row and its pencil wait. */
    enabled: Boolean = true,
    onLogin: () -> Unit,
    onRename: (String) -> Unit,
    /** True would also remove the encrypted Nextcloud archive; the sign-in screen, like iOS's,
     *  only removes the account from this device (false). */
    onDelete: (Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignInFailed by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(account.name) }
    val context = LocalContext.current
    val menuScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KaChatSignInCard)
            .clickable(enabled = enabled) {
                if (requireBiometricLogin) {
                    context.authenticateWithDeviceCredential(
                        title = "Unlock to sign in to ${account.name.trim().ifEmpty { "Saved Account" }}",
                        onSuccess = onLogin,
                        // With no screen lock the Screen Lock Required dialog has said why.
                        onFailure = { if (context.hasDeviceCredential()) showSignInFailed = true }
                    )
                } else {
                    onLogin()
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF2C3E50), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = KaspaTeal,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                color = LocalAppColors.current.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = account.address,
                color = LocalAppColors.current.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { showMenu = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_account),
                tint = KaspaTeal,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    // Half sheet rather than a dropdown, like every other chooser in the app - and Delete gets a
    // line saying what it takes with it, which a menu of bare verbs cannot.
    if (showMenu) {
        val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var menuChoiceMade by remember { mutableStateOf(false) }
        // What the half sheet's choice opens, once the half sheet has finished closing (iOS
        // d816404 `afterAccountActions`): the sheet goes down first, then Rename or Delete.
        fun closeMenuThen(next: () -> Unit) {
            if (menuChoiceMade) return
            menuChoiceMade = true
            menuScope.launch { menuSheetState.hide() }.invokeOnCompletion {
                showMenu = false
                next()
            }
        }
        ActionSheetContainer(
            title = account.name,
            subtitle = account.address,
            onDismiss = { showMenu = false },
            sheetState = menuSheetState,
        ) {
            ActionSheetRow(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.rename),
                subtitle = "Gives this account a name of your own.",
            ) {
                closeMenuThen {
                    nameInput = account.name
                    showRenameDialog = true
                }
            }
            ActionSheetRow(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.delete),
                subtitle = "Removes this account and its local data from this device.",
                tint = LocalAppColors.current.danger,
            ) {
                closeMenuThen { showDeleteConfirm = true }
            }
        }
    }

    if (showSignInFailed) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showSignInFailed = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text("Sign In Failed", color = LocalAppColors.current.textPrimary) },
            text = { Text("Authentication failed.", color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = { showSignInFailed = false }) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showRenameDialog) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.rename_account), color = LocalAppColors.current.textPrimary) },
            text = {
                // iOS's alert message, then its "Account name" field.
                Column {
                Text("Enter a new name for this saved account.", color = LocalAppColors.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                com.kachat.app.ui.theme.IosTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Account name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LocalAppColors.current.textPrimary,
                        unfocusedTextColor = LocalAppColors.current.textPrimary,
                        focusedBorderColor = KaspaTeal,
                        unfocusedBorderColor = LocalAppColors.current.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        onRename(nameInput)
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel), color = LocalAppColors.current.textSecondary)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        // The warning Settings' Delete Account shows, without its seed phrase and backup rows: this
        // account is signed out (iOS 9e22075). The row names the account and its short address
        // (`shortPublicAddress`: the first 10 and last 6 characters).
        val shortAddress = account.address.let { if (it.length > 16) "${it.take(10)}...${it.takeLast(6)}" else it }
        DeleteAccountSheet(
            confirmSubtitle = "Deletes ${account.name.trim().ifEmpty { "Saved Account" }} ($shortAddress) and its local data from this device.",
            notBackedUp = notBackedUp,
            onConfirm = {
                // The phone's lock first, as Settings' Delete Account (iOS e5a9b11).
                context.authenticateWithDeviceCredential(
                    title = "Confirm deleting this account",
                    onSuccess = { onDelete(false) },
                )
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * Import Account's first screen (iOS dd0aab1, 1ab014f `ImportNameStep`): the account's name,
 * local to this device - the same screen as Create Account's first step. Then the seed length
 * ([ImportLengthScreen]), the words ([ImportWalletScreen]), and the passphrase step, which finds
 * the wallet those words belong to. Restore from NFC Card sits at the bottom (iOS 1ab014f: moved
 * here from the deleted source wallet list).
 */
@Composable
fun ImportNameScreen(onBack: () -> Unit, onNext: (String) -> Unit, onRestoreFromNfcCard: () -> Unit = {}) {
    val nfcAvailable = com.kachat.app.services.NfcCard.isAvailable(LocalContext.current)
    AccountNameForm(
        title = stringResource(R.string.import_account),
        onBack = onBack,
        onNext = onNext,
        bottomBar = {
            if (nfcAvailable) {
                // iOS: subheadline semibold in the accent, 12 above and below.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onRestoreFromNfcCard)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Contactless, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore from NFC Card", color = KaspaTeal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    )
}

/**
 * The account name step of Create Account and Import Account (iOS dd0aab1 `AccountNameForm`): a
 * name local to this device. Next stays off until there is a name; [onNext] gets it trimmed.
 */
@Composable
private fun AccountNameForm(
    title: String,
    onBack: () -> Unit,
    onNext: (String) -> Unit,
    /** Pinned below the scrolling form (iOS 1ab014f `.safeAreaInset(edge: .bottom)`). */
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    // Starts empty on purpose: the name is the user's choice. Kept across the trip to the next
    // step and back, as iOS keeps it.
    var accountName by rememberSaveable { mutableStateOf("") }
    val canContinue = CreateAccountSteps.canContinue(accountName)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val next = {
        if (canContinue) {
            focusManager.clearFocus()
            onNext(CreateAccountSteps.accountName(accountName))
        }
    }

    CreateAccountStepScaffold(title = title, onBack = onBack, bottomBar = bottomBar) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.create_wallet_name_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.create_wallet_name_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        // iOS .textFieldStyle(.roundedBorder): the page's own background, a hairline border.
        TextField(
            value = accountName,
            onValueChange = { accountName = it },
            placeholder = { Text("Enter account name", color = colors.textSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(0.5.dp, if (colors.isDark) Color(0xFF545458).copy(alpha = 0.65f) else Color(0xFF3C3C43).copy(alpha = 0.29f), RoundedCornerShape(6.dp))
                .focusRequester(focus),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.background,
                unfocusedContainerColor = colors.background,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = KaspaTeal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { next() }),
            singleLine = true
        )

        CreateWalletNextButton(title = stringResource(R.string.next), enabled = canContinue, onClick = next)
    }
}

/**
 * Import Account: how many words the seed phrase being imported has (iOS dd0aab1
 * `ImportLengthStep`) - the same 12 / 24 buttons as Create Account, nothing chosen until the
 * user taps one. Next goes to the words ([ImportWalletScreen]).
 */
@Composable
fun ImportLengthScreen(onBack: () -> Unit, onNext: (Int) -> Unit) {
    val colors = LocalAppColors.current
    var wordCount by rememberSaveable { mutableStateOf<Int?>(null) }

    CreateAccountStepScaffold(title = stringResource(R.string.import_account), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.choose_seed_phrase_length),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.import_seed_length_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            CreateAccountSteps.WORD_COUNTS.forEach { count ->
                SeedLengthButton(
                    title = stringResource(if (count == 12) R.string.n_12_words else R.string.n_24_words),
                    chosen = wordCount == count,
                    enabled = true,
                    onClick = { wordCount = count },
                )
            }
        }

        CreateWalletNextButton(
            title = stringResource(R.string.next),
            enabled = CreateAccountSteps.canChooseImportLength(wordCount),
        ) {
            val count = wordCount
            if (count != null && CreateAccountSteps.canChooseImportLength(count)) onNext(count)
        }
    }
}

/**
 * What a seed phrase is, in plain words (iOS 712bb4d `SeedPhraseExplainerPage`): the page behind
 * "What is this?" on the seed length step. A full page on the plain background, pushed like
 * "What is a passphrase?" and in the same headline-and-text sections, so it reads easily.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeedPhraseExplainerPage(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.what_is_a_seed_phrase), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_short_version),
                stringResource(R.string.seed_phrase_explainer_master_key)
            )
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_anyone_title),
                stringResource(R.string.seed_phrase_explainer_anyone)
            )
            PassphraseExplainerSection(
                stringResource(R.string.seed_phrase_explainer_safe_title),
                stringResource(R.string.seed_phrase_explainer_paper)
            )
        }
    }
}

/** The Create Account and Import Account steps' frame: Back, the large title ("Create Account"
 *  or "Import Account"), then the step's content 24dp apart, scrolling above the keyboard. */
@Composable
private fun CreateAccountStepScaffold(
    title: String = stringResource(R.string.create_account),
    onBack: () -> Unit,
    /** Pinned under the scrolling steps, above the keyboard (Import Account's Restore from NFC
     *  Card, iOS 1ab014f). */
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge: Compose reacts to the keyboard inset itself, so the field, the
                // button and the bottom bar stay above it.
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Below the status bar, as on iOS (edge-to-edge would draw Back under the clock).
                    .statusBarsPadding()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // iOS 26: the back chevron on a glass disc.
                SheetBarIconButton(Icons.Filled.ArrowBackIosNew, stringResource(R.string.back), onClick = onBack, tint = LocalAppColors.current.textPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
                Spacer(modifier = Modifier.height(24.dp))
            }
            bottomBar?.invoke()
        }
    }
}

/** The accent-filled continue button of the Import Account and backup steps, dimmed while disabled
 *  (iOS `CreateWalletNextButton`). */
@Composable
internal fun CreateWalletNextButton(title: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            // Not dimmed while busy, as on iOS.
            disabledContainerColor = if (busy) KaspaTeal else KaspaTeal.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            // iOS: white body text; the whole button dims to 40% while disabled.
            Text(
                text = title,
                fontSize = 17.sp,
                color = if (enabled || busy) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

/** One of the two seed length choices (12 or 24 words): full width, filled with the accent when
 *  chosen, outlined otherwise (iOS dd0aab1 `SeedLengthButton`, shared by Create Account and
 *  Import Account). */
@Composable
private fun SeedLengthButton(title: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // The whole card is the button, its padding and outline included (iOS ceca9fa), and it dims
    // while held instead of rippling (iOS `.buttonStyle(.plain)`).
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) IOS_PLAIN_PRESSED_ALPHA else 1f }
            .clip(shape)
            .background(if (chosen) KaspaTeal else Color.Transparent)
            .border(1.5.dp, KaspaTeal, shape)
            .selectable(
                selected = chosen,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(16.dp)
    ) {
        Icon(
            imageVector = if (chosen) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (chosen) Color.White else KaspaTeal,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = if (chosen) Color.White else KaspaTeal,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Import Account: the seed phrase's words (in-app keyboard, or Paste), then the passphrase step
 * (iOS `ImportWalletView`). The name ([ImportNameScreen]) and the length ([ImportLengthScreen])
 * come before it (iOS dd0aab1); a pasted phrase of the other length switches the length.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    viewModel: WalletViewModel,
    accountName: String,
    initialWordCount: Int,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    // Back from the passphrase step brings the words typed before (iOS keeps this screen, and
    // its words, under that step): the slots refill from the view model's pending words.
    val pendingWords = remember { viewModel.pendingMnemonicWords.takeIf { it.size == 12 || it.size == 24 } }
    var wordCount by remember { mutableIntStateOf(pendingWords?.size ?: CreateAccountSteps.importWordCount(initialWordCount)) }
    // Fixed-capacity backing store; only the first [wordCount] entries are used.
    var words by remember { mutableStateOf(List(24) { index -> pendingWords?.getOrNull(index) ?: "" }) }
    var activeSlot by remember { mutableIntStateOf(pendingWords?.let { it.size - 1 } ?: 0) }
    val importState by viewModel.importWalletState.collectAsState()
    val wordList = remember { viewModel.bip39Words }

    val slots = words.take(wordCount)
    val filled = slots.count { it.isNotEmpty() && wordList.contains(it.lowercase()) }
    val allValid = slots.size == wordCount && slots.all { it.isNotEmpty() && wordList.contains(it.lowercase()) }
    val canImport = allValid && accountName.isNotBlank()

    // The typed-in phrase is fully visible in the slot grid — exactly as sensitive as the reveal
    // screens, so it gets the same screenshot/recording block. The custom keyboard already keeps
    // it away from the OS keyboard and clipboard; this closes the screen-capture side. Cleared
    // on dispose like CreateAccountSeedScreen/SeedPhraseScreen's identical guards.
    SecureWindowWhileShown()

    // iOS ImportWalletView: "Import Account" as an inline title with Back, then the label, Paste
    // and the count on one line, the keyboard, and Continue - 12 apart, 16 from the edges.
    Scaffold(
        containerColor = LocalAppColors.current.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_account), color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalAppColors.current.background)
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(16.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.import_words_enter_recovery_phrase),
                    color = LocalAppColors.current.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                // A phrase on the clipboard fills the slots in one tap, and the word count
                // follows whichever length was pasted (iOS f9beba1).
                val pasteContext = LocalContext.current
                val pasteHaptic = com.kachat.app.util.rememberHaptics()
                var pasteError by remember { mutableStateOf<String?>(null) }
                pasteError?.let { message ->
                    com.kachat.app.ui.theme.IosAlertDialog(
                        onDismissRequest = { pasteError = null },
                        containerColor = LocalAppColors.current.surface,
                        title = { Text(stringResource(R.string.error), color = LocalAppColors.current.textPrimary) },
                        text = { Text(message, color = LocalAppColors.current.textSecondary) },
                        confirmButton = {
                            TextButton(onClick = { pasteError = null }) {
                                Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                val pasteClipboard = pasteContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                // A bordered button, small, as on iOS - not a bare text link.
                OutlinedButton(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KaspaTeal.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(50),
                    onClick = {
                        // iOS's pasteSeedPhrase: a refusal goes up in the "Error" alert, a
                        // success is a haptic and the filled slots.
                        val raw = pasteClipboard?.primaryClip?.getItemAt(0)?.coerceToText(pasteContext)?.toString()
                        // A clip with no text (an image, a blank copy) is nothing to paste either,
                        // and the clipboard is left alone (iOS: no string on the pasteboard).
                        if (raw.isNullOrBlank()) {
                            pasteError = "Nothing to paste."
                            return@OutlinedButton
                        }
                        // Cleared as soon as it is read, whether or not the words are accepted:
                        // secret words do not belong there either way (iOS d816404).
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pasteClipboard?.clearPrimaryClip()
                        } else {
                            pasteClipboard?.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                        }
                        val pasted = raw.lowercase()
                            .split(Regex("[\\s,]+"))
                            .map { it.trim('.', ')') }
                            .filter { it.isNotEmpty() && !it.all { c -> c.isDigit() } }
                        when {
                            pasted.size != 12 && pasted.size != 24 ->
                                pasteError = "You need 12 or 24 words - the clipboard holds ${pasted.size}."
                            else -> {
                                val unknown = pasted.filterNot { wordList.contains(it) }
                                if (unknown.isNotEmpty()) {
                                    // never repeats what was pasted
                                    pasteError = if (unknown.size == 1)
                                        "One of the pasted words isn't a valid word. Check your copy and try again."
                                    else
                                        "${unknown.size} of the pasted words aren't valid words. Check your copy and try again."
                                } else {
                                    wordCount = pasted.size
                                    words = List(24) { index -> pasted.getOrElse(index) { "" } }
                                    activeSlot = (pasted.size - 1).coerceAtLeast(0)
                                    pasteHaptic(com.kachat.app.util.IosHaptic.SUCCESS)
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = KaspaTeal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste", color = KaspaTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$filled/$wordCount",
                    color = if (allValid) LocalAppColors.current.success else LocalAppColors.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom in-app keyboard + numbered slot grid + autocomplete (no OS keyboard, no paste)
            SeedPhraseKeyboard(
                wordCount = wordCount,
                words = words,
                activeSlot = activeSlot,
                wordList = wordList,
                modifier = Modifier.weight(1f),
                onWordsChange = { words = it },
                onActiveSlotChange = { activeSlot = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Continue to the passphrase step: the arrow and "Continue", gray until every word is
            // in (iOS ImportWalletView).
            Button(
                onClick = { if (viewModel.prepareImport(accountName, slots)) onProceed() },
                enabled = canImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, disabledContainerColor = IosGray),
                shape = RoundedCornerShape(12.dp)
            ) {
                // white on the accent and on gray alike (iOS `.foregroundColor(.white)`)
                val contentColor = Color.White
                Icon(Icons.Default.ArrowCircleRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.import_words_continue),
                    color = contentColor,
                    fontSize = 17.sp
                )
            }
        }
    }

    if (importState.status == WalletViewModel.ImportWalletStatus.FAILED) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { viewModel.resetImportWalletState() },
            containerColor = LocalAppColors.current.surface,
            title = { Text(stringResource(R.string.error), color = LocalAppColors.current.textPrimary) },
            text = { Text(importState.errorMessage ?: "Something went wrong", color = LocalAppColors.current.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetImportWalletState() }) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** How far a control in iOS's plain button style fades while it is held. */
private const val IOS_PLAIN_PRESSED_ALPHA = 0.4f

/** iOS `Color.gray`, a disabled button's fill. */
private val IosGray = Color(0xFF8E8E93)

enum class PassphraseMode { CREATE, IMPORT }

/**
 * Optional BIP39 passphrase ("25th word") step, shown after seed backup (create) or seed entry
 * (import) and before the Welcome Guide. Explains what a passphrase does, how it helps, and the
 * risk of forgetting it, then lets the user proceed with a passphrase or skip. The caller performs
 * the actual create/import commit via [onProceed], which receives the chosen passphrase ("" when
 * skipped). Copy is English-only for now — a follow-up can move these literals into strings.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassphraseSetupScreen(
    mode: PassphraseMode,
    isBusy: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onBack: () -> Unit,
    /** Address #0 for a candidate passphrase, for the live preview on the entry step. Null when
     *  the caller has no words to derive from. */
    previewAddress: (String) -> String? = { null },
    /** What an address holds, for the balance under the live preview; null when it couldn't be
     *  checked. */
    previewBalance: suspend (String) -> Long? = { null },
    /** Set on import: the preview searches every wallet type for the words and reports the
     *  choice (iOS 1ab014f `importChoice`). */
    findAccounts: (suspend (String) -> List<ImportAccountCandidate>?)? = null,
    /** Address #0 of every wallet type, for "See Every Wallet Type" (iOS 1ab014f). */
    everyWalletType: (String) -> List<ImportAccountCandidate> = { emptyList() },
    onSelectImportChoice: (ImportAccountChoice) -> Unit = {},
    onProceed: (String) -> Unit
) {
    // Creating only reaches here through "Advanced: Create with a Passphrase", which has already
    // answered the question (iOS e5a9b11).
    var step by remember { mutableStateOf(if (mode == PassphraseMode.CREATE) PassphraseStep.ENTRY else PassphraseStep.QUESTION) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    // Where the explainer's Back returns: the question (import) or the entry screen, which links
    // it too (iOS d816404).
    var explainerReturnStep by remember { mutableStateOf(PassphraseStep.QUESTION) }
    // The import's search for the account holding funds is running: Import waits for it, or it
    // would import onto #0 while the funds sit on another address (iOS 1ab014f
    // `isSearchingAddresses`, held here so No can set it before the search starts).
    var isSearchingAddresses by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    /** [skippingSearch]: Import on address #0 without waiting for the funds search. */
    fun submitFromEntry(skippingSearch: Boolean = false) {
        if (isBusy) return
        if (!skippingSearch && isSearchingAddresses) return
        when {
            // Empty, not blank: spaces are a valid passphrase (iOS `isEmpty`).
            passphrase.isEmpty() ->
                localError = "Enter a passphrase, or go back and choose No."
            mode == PassphraseMode.CREATE && passphrase != confirm ->
                localError = "The passphrases do not match. Please re-enter them."
            else -> {
                if (skippingSearch) onSelectImportChoice(ImportAccountChoice())
                onProceed(passphrase)
            }
        }
    }

    // Back always lands one step nearer the question, and only leaves the flow from the question
    // itself - reading the explainer must return to the choice, never skip past it. Creating has
    // no question, so Back on its entry step leaves.
    fun backFromEntry() {
        passphrase = ""
        confirm = ""
        if (mode == PassphraseMode.CREATE) onBack() else step = PassphraseStep.QUESTION
    }
    BackHandler(enabled = step != PassphraseStep.QUESTION) {
        when (step) {
            PassphraseStep.ENTRY -> backFromEntry()
            PassphraseStep.FOUND -> if (!isBusy) step = PassphraseStep.QUESTION
            else -> step = explainerReturnStep
        }
    }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        when (step) {
            PassphraseStep.QUESTION -> PassphraseQuestionStep(
                mode = mode,
                isBusy = isBusy,
                onYes = { step = PassphraseStep.ENTRY },
                // No opens "Your Account" (iOS 1ab014f). Held from here, not only once the search
                // starts: Import must not be tappable before the search has run.
                onNo = {
                    if (!isBusy) {
                        isSearchingAddresses = true
                        step = PassphraseStep.FOUND
                    }
                },
                onExplain = {
                    explainerReturnStep = PassphraseStep.QUESTION
                    step = PassphraseStep.EXPLAINER
                },
                onBack = onBack,
            )
            PassphraseStep.ENTRY -> PassphraseEntryStep(
                mode = mode,
                isBusy = isBusy,
                passphrase = passphrase,
                onPassphraseChange = { passphrase = it },
                confirm = confirm,
                onConfirmChange = { confirm = it },
                reveal = reveal,
                onToggleReveal = { reveal = !reveal },
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAccounts = findAccounts,
                everyWalletType = everyWalletType,
                onSelectImportChoice = onSelectImportChoice,
                isSearchingAddresses = isSearchingAddresses,
                onSearchingChange = { isSearchingAddresses = it },
                onSubmit = { submitFromEntry() },
                // Import on address #0 without waiting for the funds search (iOS 33095ac).
                onSkipSearch = { submitFromEntry(skippingSearch = true) },
                onExplain = {
                    explainerReturnStep = PassphraseStep.ENTRY
                    step = PassphraseStep.EXPLAINER
                },
                onBack = { backFromEntry() },
            )
            PassphraseStep.FOUND -> PassphraseFoundStep(
                isBusy = isBusy,
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAccounts = findAccounts,
                everyWalletType = everyWalletType,
                onSelectImportChoice = onSelectImportChoice,
                isSearchingAddresses = isSearchingAddresses,
                onSearchingChange = { isSearchingAddresses = it },
                onImport = { if (!isBusy) onProceed("") },
                onSkipSearch = {
                    if (!isBusy) {
                        onSelectImportChoice(ImportAccountChoice())
                        onProceed("")
                    }
                },
                onBack = { step = PassphraseStep.QUESTION },
            )
            PassphraseStep.EXPLAINER -> PassphraseExplainerStep(
                mode = mode,
                onBack = { step = explainerReturnStep },
            )
        }
    }

    val shownError = localError ?: errorMessage
    if (shownError != null) {
        com.kachat.app.ui.theme.IosAlertDialog(
            onDismissRequest = { localError = null; onDismissError() },
            containerColor = colors.surface,
            title = { Text("Error", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
            text = { Text(shownError, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { localError = null; onDismissError() }) {
                    Text(stringResource(R.string.ok), color = KaspaTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** iOS 1ab014f: the question, the entry, `found` (import with no passphrase: the wallet these
 *  words were found in) and the explainer. */
private enum class PassphraseStep { QUESTION, ENTRY, FOUND, EXPLAINER }

/** The question, alone on its own screen, with a way to go and read about it first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseQuestionStep(
    mode: PassphraseMode,
    isBusy: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onExplain: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Passphrase", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onClick = onBack, modifier = Modifier.padding(start = 12.dp), enabled = !isBusy, tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            // iOS lock.shield.fill: a solid accent shield with the lock cut out of it.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = KaspaTeal, modifier = Modifier.fillMaxSize())
                Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.background, modifier = Modifier.size(26.dp).offset(y = (-1).dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Did you create these words with a passphrase?",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            // Import keeps a line of steer, because someone who has never heard of a passphrase
            // still has to answer a question about their own past. Create needs none: the choice
            // is theirs to make, and "What is a passphrase?" is right there if they want it.
            if (mode == PassphraseMode.IMPORT) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "If you are not sure, the answer is almost certainly no. A passphrase is something you would have typed in on purpose.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))

            Button(
                onClick = onYes,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                // iOS: .headline, white on the accent.
                colors = ButtonDefaults.buttonColors(containerColor = KaspaTeal, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Yes", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNo,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaspaTeal.copy(alpha = 0.15f),
                    contentColor = KaspaTeal,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                // No longer imports from here (iOS 1ab014f), so no spinner: it opens Your Account.
                Text("No", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onExplain, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                // iOS: subheadline semibold in the primary colour.
                Text("What is a passphrase?", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * The field, with the chatting address it produces underneath.
 *
 * That live address is the whole point of this screen: a passphrase does not protect one account,
 * it opens a different one. Watching #0 change with every character is what makes that concrete,
 * and on import it is how someone confirms they typed the right thing before committing to an
 * account that would otherwise just look empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseEntryStep(
    mode: PassphraseMode,
    isBusy: Boolean,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    confirm: String,
    onConfirmChange: (String) -> Unit,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    previewAddress: (String) -> String?,
    previewBalance: suspend (String) -> Long?,
    findAccounts: (suspend (String) -> List<ImportAccountCandidate>?)?,
    everyWalletType: (String) -> List<ImportAccountCandidate>,
    onSelectImportChoice: (ImportAccountChoice) -> Unit,
    /** The import's search for the account holding funds is running: Import waits for it. */
    isSearchingAddresses: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onSkipSearch: () -> Unit,
    onExplain: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    // The passphrase is as secret as the seed words, and "Show" puts it on screen in plain text:
    // no screenshots, screen recording or Recents thumbnail while this step is up, like the seed
    // screens (audit AND-030).
    SecureWindowWhileShown()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Passphrase", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onClick = onBack, modifier = Modifier.padding(start = 12.dp), enabled = !isBusy, tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                if (mode == PassphraseMode.CREATE) "Choose your passphrase" else "Enter your passphrase",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (mode == PassphraseMode.CREATE)
                    "Write it down somewhere safe. Without it this account cannot be recovered, even with your 12 or 24 words."
                else
                    "It has to be exactly what you used before, including capital letters and spaces.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            // Nothing changes the account once it is being made (iOS 1ab014f).
            PassphraseInputField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                reveal = reveal,
                onToggleReveal = onToggleReveal,
                placeholder = "Passphrase",
                enabled = !isBusy,
            )

            if (mode == PassphraseMode.CREATE) {
                Spacer(Modifier.height(12.dp))
                PassphraseInputField(
                    value = confirm,
                    onValueChange = onConfirmChange,
                    reveal = reveal,
                    onToggleReveal = onToggleReveal,
                    placeholder = "Re-enter passphrase",
                    enabled = !isBusy,
                )
            }

            Spacer(Modifier.height(20.dp))

            PassphraseAddressPreview(
                passphrase = passphrase,
                showsFundsHint = mode == PassphraseMode.IMPORT,
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAccounts = findAccounts,
                everyWalletType = everyWalletType,
                onSelectChoice = onSelectImportChoice,
                isSearching = isSearchingAddresses,
                onSearchingChange = onSearchingChange,
                enabled = !isBusy,
            )

            Spacer(Modifier.height(24.dp))

            PassphrasePrimaryButton(
                title = if (mode == PassphraseMode.CREATE) "Continue" else "Import",
                isBusy = isBusy,
                searching = isSearchingAddresses && passphrase.isNotEmpty(),
                // iOS 1ab014f `canSubmitPassphrase`: a passphrase, and no search running.
                enabled = passphrase.isNotEmpty() && !isSearchingAddresses,
                onClick = onSubmit,
            )

            // The search is a network round trip: never the only way forward (iOS 33095ac).
            if (mode == PassphraseMode.IMPORT && isSearchingAddresses && passphrase.isNotEmpty() && !isBusy) {
                Spacer(Modifier.height(20.dp))
                PassphraseSkipSearchButton(onSkipSearch)
            }

            // Creating opens on this screen, so the explainer is linked here too (iOS d816404).
            Spacer(Modifier.height(20.dp))
            Text(
                "What is a passphrase?",
                // iOS: subheadline semibold in the accent.
                color = KaspaTeal,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isBusy, onClick = onExplain)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * Import with no passphrase (iOS 1ab014f `foundScreen`): "Your Account", the wallet these words
 * were found in. KaPost checks the words against every wallet type, picks the likeliest account
 * and lets the user pick another; Import waits for that search at most 12 s, with Skip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseFoundStep(
    isBusy: Boolean,
    previewAddress: (String) -> String?,
    previewBalance: suspend (String) -> Long?,
    findAccounts: (suspend (String) -> List<ImportAccountCandidate>?)?,
    everyWalletType: (String) -> List<ImportAccountCandidate>,
    onSelectImportChoice: (ImportAccountChoice) -> Unit,
    isSearchingAddresses: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onSkipSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_account), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = {
                    SheetBarIconButton(Icons.Filled.ArrowBackIosNew, "Back", onClick = onBack, modifier = Modifier.padding(start = 12.dp), enabled = !isBusy, tint = LocalAppColors.current.textPrimary)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.import_your_account),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_checks_every_wallet_type),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            PassphraseAddressPreview(
                passphrase = "",
                showsFundsHint = true,
                previewAddress = previewAddress,
                previewBalance = previewBalance,
                findAccounts = findAccounts,
                everyWalletType = everyWalletType,
                onSelectChoice = onSelectImportChoice,
                isSearching = isSearchingAddresses,
                onSearchingChange = onSearchingChange,
                enabled = !isBusy,
            )

            Spacer(Modifier.height(24.dp))

            PassphrasePrimaryButton(
                title = "Import",
                isBusy = isBusy,
                searching = isSearchingAddresses,
                enabled = !isSearchingAddresses,
                onClick = onImport,
            )

            if (isSearchingAddresses && !isBusy) {
                Spacer(Modifier.height(20.dp))
                PassphraseSkipSearchButton(onSkipSearch)
            }
        }
    }
}

/**
 * The passphrase screens' Continue / Import (iOS 1ab014f `primaryButton`): .headline, white on the
 * accent, gray while not [enabled]; a spinner while busy, and "Finding your address..." while
 * Import waits on the search.
 */
@Composable
private fun PassphrasePrimaryButton(
    title: String,
    isBusy: Boolean,
    searching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KaspaTeal,
            contentColor = Color.White,
            // iOS: the fill follows `enabled` alone, so a busy button keeps the accent.
            disabledContainerColor = if (enabled) KaspaTeal else IosGray,
            disabledContentColor = Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (isBusy) {
            com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else if (searching) {
            // Import is waiting on the search: it says so (iOS d816404).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kachat.app.ui.theme.IosActivityIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text("Finding your address...", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        } else {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

/** "Skip the Search: Import on Address #0" (iOS 1ab014f `skipSearchButton`): subheadline semibold
 *  in the accent. */
@Composable
private fun PassphraseSkipSearchButton(onClick: () -> Unit) {
    Text(
        stringResource(R.string.passphrase_skip_search),
        color = KaspaTeal,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/** Plain-language explainer. Back only, so reading about it always returns to the screen it was
 *  opened from (the question, or the entry screen, which links it too). */
/**
 * The whole reason this screen shows an address: a passphrase does not protect one account, it
 * opens a different one. Watching #0 change with every character is what makes that concrete, and
 * on import it is how someone confirms they typed the right thing before committing to an account
 * that would otherwise look simply empty. With [findAccounts] the account isn't known yet: it
 * searches every wallet type, picks the likeliest account, and lets the user pick another (iOS
 * 1ab014f `PassphraseAddressPreview`).
 */
/** How long Import waits for the funds search before letting go and importing onto #0 (iOS
 *  33095ac `searchTimeoutNanoseconds`). */
private const val PASSPHRASE_SEARCH_TIMEOUT_MS = 12_000L

@Composable
internal fun PassphraseAddressPreview(
    passphrase: String,
    showsFundsHint: Boolean,
    previewAddress: (String) -> String?,
    previewBalance: suspend (String) -> Long?,
    findAccounts: (suspend (String) -> List<ImportAccountCandidate>?)? = null,
    /** Address #0 of every wallet type, for words the search can't place (only with [findAccounts]). */
    everyWalletType: (String) -> List<ImportAccountCandidate> = { emptyList() },
    onSelectChoice: (ImportAccountChoice) -> Unit = {},
    /** True while that search runs, so the caller can hold its Import until the pick is made. */
    isSearching: Boolean = false,
    onSearchingChange: (Boolean) -> Unit = {},
    /** False while the account is being made: nothing here changes it then (iOS `.disabled(isBusy)`). */
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    // Debounced: the derivation is PBKDF2 over 2048 rounds, quick but not free, and running it on
    // every keystroke would be felt.
    var shownAddress by remember { mutableStateOf<String?>(null) }
    // What [shownAddress] holds: null while looking it up, or when the lookup failed.
    var balanceSompi by remember { mutableStateOf<Long?>(null) }
    var balanceFailed by remember { mutableStateOf(false) }
    var shownHasHistory by remember { mutableStateOf(false) }
    // What the search found, likeliest first.
    var foundAddresses by remember { mutableStateOf(emptyList<ImportAccountCandidate>()) }
    // Address #0 of every wallet type, for words the search can't place.
    var everyWalletTypeRows by remember { mutableStateOf(emptyList<ImportAccountCandidate>()) }
    var showsEveryWalletType by remember { mutableStateOf(false) }
    var selectedChoice by remember { mutableStateOf(ImportAccountChoice()) }
    // The funds search failed or ran out of time: Import goes to #0, and the screen says so
    // (iOS 33095ac). Search Again bumps [searchRun], which runs the lookup once more.
    var searchIncomplete by remember { mutableStateOf(false) }
    var searchRun by remember { mutableStateOf(0) }
    fun select(found: ImportAccountCandidate) {
        selectedChoice = found.choice
        onSelectChoice(found.choice)
        shownAddress = found.address
        balanceSompi = found.balanceSompi
        shownHasHistory = found.hasHistory
    }
    // iOS onDisappear: a search left behind holds nothing.
    DisposableEffect(Unit) {
        onDispose { if (findAccounts != null) onSearchingChange(false) }
    }
    LaunchedEffect(passphrase, searchRun) {
        searchIncomplete = false
        balanceSompi = null
        balanceFailed = false
        shownHasHistory = false
        foundAddresses = emptyList()
        everyWalletTypeRows = emptyList()
        showsEveryWalletType = false
        selectedChoice = ImportAccountChoice()
        onSelectChoice(ImportAccountChoice())
        // Held from the first keystroke, not only once the lookup starts: an Import tapped in the
        // pause would otherwise go to #0 (iOS 1ab014f).
        if (findAccounts != null) onSearchingChange(true)
        kotlinx.coroutines.delay(250)
        val address = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { previewAddress(passphrase) }
        shownAddress = address
        if (address == null) {
            balanceFailed = true
            if (findAccounts != null) onSearchingChange(false)
            return@LaunchedEffect
        }
        // a further pause, so only the passphrase someone stopped typing is looked up
        kotlinx.coroutines.delay(450)
        if (findAccounts != null) {
            everyWalletTypeRows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { everyWalletType(passphrase) }
            // A slow or unreachable node lets Import go after the timeout, onto #0, and says so;
            // the search is stopped and an answer that comes later is dropped (iOS 33095ac, 1ab014f).
            val found = kotlinx.coroutines.withTimeoutOrNull(PASSPHRASE_SEARCH_TIMEOUT_MS) { findAccounts(passphrase) }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            onSearchingChange(false)
            if (found == null) {
                searchIncomplete = true
                balanceFailed = true
                return@LaunchedEffect
            }
            foundAddresses = found
            val first = found.firstOrNull()
            if (first != null) select(first) else balanceSompi = 0L
            return@LaunchedEffect
        }
        val sompi = previewBalance(address)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        balanceSompi = sompi
        balanceFailed = sompi == null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text("Your address", color = colors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        // Every character, wrapped over as many lines as it takes. This is the one place
        // the address has to be readable in full: truncating it would hide exactly the
        // part that changes when the passphrase does, which is why it is on screen.
        Text(
            shownAddress ?: "Checking...",
            color = if (shownAddress == null) colors.textSecondary else colors.textPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 12.sp,
            softWrap = true,
            overflow = TextOverflow.Visible,
        )
        // Only the chatting address's: funds can also sit on the account's other
        // addresses, so an empty one suggests a wrong passphrase rather than proving it.
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Balance", color = colors.textSecondary, fontSize = 12.sp)
            val sompi = balanceSompi
            when {
                sompi != null -> Text(
                    "${com.kachat.app.util.KaspaUnit.plain(sompi)} ${com.kachat.app.util.KaspaUnit.symbol}",
                    color = if (sompi > 0) colors.success else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                balanceFailed -> Text("Couldn't check", color = colors.textSecondary, fontSize = 13.sp)
                else -> com.kachat.app.ui.theme.IosActivityIndicator(color = colors.textSecondary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            }
        }
        val checkedSompi = balanceSompi
        if (showsFundsHint && checkedSompi != null) {
            Spacer(Modifier.height(4.dp))
            // iOS 1ab014f `fundsHint`: history counts as much as funds, and with no passphrase an
            // empty address is only said, not warned about.
            val (hint, hintColor) = when {
                checkedSompi > 0 -> "This address holds funds, so this looks like the right account." to colors.success
                shownHasHistory -> stringResource(R.string.import_address_has_history) to colors.success
                passphrase.isEmpty() -> stringResource(R.string.import_no_funds_or_history) to colors.textSecondary
                else -> "No funds on this address. If you expected funds here, check your passphrase." to colors.warning
            }
            Text(hint, color = hintColor, fontSize = 12.sp)
        }
        if (foundAddresses.size > 1 || showsEveryWalletType) {
            val visibleRows = if (!showsEveryWalletType) foundAddresses else {
                val foundIds = foundAddresses.map { it.id }.toSet()
                foundAddresses + everyWalletTypeRows.filterNot { it.id in foundIds }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_picked_most_likely_account),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            visibleRows.forEach { found ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { select(found) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (selectedChoice == found.choice) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = KaspaTeal,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(importWalletTypeName(found.family), color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Address #${found.index}", color = colors.textSecondary, fontSize = 11.sp)
                        Text(
                            "${found.address.take(14)}...${found.address.takeLast(6)}",
                            color = colors.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (found.balanceSompi > 0) {
                        Text(
                            "${com.kachat.app.util.KaspaUnit.plain(found.balanceSompi)} ${com.kachat.app.util.KaspaUnit.symbol}",
                            color = colors.success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else if (found.hasHistory) {
                        Text(stringResource(R.string.import_has_history), color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        if (findAccounts != null && !isSearching && everyWalletTypeRows.isNotEmpty() && !showsEveryWalletType) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_see_every_wallet_type),
                color = KaspaTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = enabled) { showsEveryWalletType = true },
            )
        }
        if (searchIncomplete) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.passphrase_search_incomplete),
                color = colors.warning,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.passphrase_search_again),
                color = KaspaTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = enabled) { searchRun++ },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (passphrase.isEmpty())
                "This is the account your 12 or 24 words open on their own."
            else
                "A different passphrase gives a different address, and a different account.",
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

/** How import names a wallet type (iOS 1ab014f `WalletSourceFamily.displayName`): Standard's is
 *  translated, the others are product names. */
@Composable
private fun importWalletTypeName(family: WalletSourceFamily): String =
    if (family == WalletSourceFamily.KASPA_STANDARD) stringResource(R.string.import_standard_kaspa_wallet)
    else family.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseExplainerStep(mode: PassphraseMode, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("What is a passphrase?", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                navigationIcon = { SheetBarBackButton(onBack, Modifier.padding(start = 12.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PassphraseExplainerSection(
                "The short version",
                "A passphrase is an extra word or sentence you add on top of your 12 or 24 words. It is optional, and most people do not use one."
            )
            PassphraseExplainerSection(
                "It does not lock your account",
                "This is the part people get wrong. A passphrase does not put a password on your account. It opens a completely different account. Your 12 or 24 words with no passphrase open one account. The same words with the word \"apple\" opens another one. With \"banana\", another one again. Every passphrase is its own separate account, with its own address and its own balance."
            )
            PassphraseExplainerSection(
                "Why anyone bothers",
                "If someone finds your written 12 or 24 words, they get the account those words open on their own. They do not get the one behind your passphrase, because they do not know there is one, and they could not guess it anyway."
            )
            PassphraseExplainerSection(
                "The catch",
                "There is no reset and no recovery. If you forget your passphrase, the account it opened is gone for good. Your 12 or 24 words alone will not bring it back, and nobody can help you. Treat it exactly like the words themselves: written down, somewhere safe, before you rely on it."
            )
            PassphraseExplainerSection(
                "One more thing to know",
                "If you type the wrong passphrase, nothing will tell you. You will simply land in a different account, and it will look empty. That is not a bug and your money is not lost, it just means you are in the wrong account."
            )
            PassphraseExplainerSection(
                "So do you need one?",
                if (mode == PassphraseMode.CREATE)
                    "If you are not sure, choose No. Your account is still protected by your 12 or 24 words, and you can always create another account with a passphrase later."
                else
                    "If you never set one up, choose No. A passphrase is something you would have typed in on purpose, so if this is the first you are hearing of it, you do not have one."
            )
        }
    }
}

@Composable
private fun PassphraseExplainerSection(title: String, body: String) {
    val colors = LocalAppColors.current
    Column {
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseInputField(
    value: String,
    onValueChange: (String) -> Unit,
    reveal: Boolean,
    onToggleReveal: () -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(placeholder, color = LocalAppColors.current.textSecondary) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        // Password keyboard even while revealed: a visual transformation doesn't change what the
        // IME is told, and only the Password type keeps keyboards from learning, suggesting or
        // syncing what is typed (audit AND-030; RevealableSecureField does the same).
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false
        ),
        trailingIcon = {
            TextButton(onClick = onToggleReveal) {
                Text(if (reveal) "Hide" else "Show", color = KaspaTeal, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalAppColors.current.surface,
            unfocusedContainerColor = LocalAppColors.current.surface,
            // the same field, unchangeable while the account is being made (iOS 1ab014f)
            disabledContainerColor = LocalAppColors.current.surface,
            focusedTextColor = LocalAppColors.current.textPrimary,
            unfocusedTextColor = LocalAppColors.current.textPrimary,
            disabledTextColor = LocalAppColors.current.textPrimary,
            cursorColor = KaspaTeal,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

/**
 * The brand's near-black with a green cast, taken from the KaChat banner rather than the app's
 * usual true black. Scoped to the sign-in screen, and the same in both themes on purpose - it is
 * the one place the product introduces itself.
 */
internal val KaChatSignInBackground = Color(0xFF0E1614)

/** One step up from [KaChatSignInBackground], same cast, for the saved-account rows. */
internal val KaChatSignInCard = Color(0xFF1A2624)

/**
 * FLAG_SECURE (no screenshots, screen recording or Recents thumbnail) for as long as the calling
 * screen is composed. Counted per window, because the onboarding steps that use it follow one
 * another: on seed step -> passphrase step the next screen adds the flag before the previous one
 * is disposed, and a plain clear on dispose would lift it while the passphrase is on screen
 * (audit AND-030). The flag is cleared only when the last secure screen goes.
 */
@Composable
internal fun SecureWindowWhileShown() {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        if (window != null) SecureWindowCount.acquire(window)
        onDispose {
            if (window != null) SecureWindowCount.release(window)
        }
    }
}

private object SecureWindowCount {
    private val counts = java.util.WeakHashMap<android.view.Window, Int>()

    fun acquire(window: android.view.Window) {
        val n = counts[window] ?: 0
        counts[window] = n + 1
        if (n == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: android.view.Window) {
        val n = (counts[window] ?: 1) - 1
        if (n <= 0) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            counts[window] = n
        }
    }
}
