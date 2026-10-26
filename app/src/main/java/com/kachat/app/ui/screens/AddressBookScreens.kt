package com.kachat.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kachat.app.R
import com.kachat.app.services.AddressBookEntry
import com.kachat.app.services.AddressBookManager
import com.kachat.app.services.NextcloudService
import com.kachat.app.ui.theme.KaspaTeal
import com.kachat.app.ui.theme.LocalAppColors
import com.kachat.app.util.KaspaAddress
import com.kachat.app.viewmodels.WalletViewModel
import kotlinx.coroutines.delay

// The Address Book (Kaspa Hub > Address Book): saved Kaspa addresses with a name and a note, per
// wallet. It replaced syncing with the phone's Contacts - see AddressBookManager (iOS
// AddressBookView.swift, 00767a4).

/** This wallet's Address Book entries, sorted by name; re-renders on every change. */
@Composable
fun rememberAddressBookEntries(): List<AddressBookEntry> {
    val book = AddressBookManager.shared ?: return emptyList()
    val entries by book.entries.collectAsState()
    return entries
}





/** The search field the Address Book and its picker keep at the top, always visible. */
@Composable
private fun AddressBookSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    IosSearchField(value, onValueChange, stringResource(R.string.ab_search_prompt), modifier)
}


/** "No saved addresses", what the book is for, and [action]. [iconSize] is the iOS symbol's font
 *  size: "book.closed" at 44 is drawn 46 tall, which Material's Book (20/24 of its box) reaches
 *  at 1.25 times that. */
@Composable
private fun AddressBookEmptyState(
    body: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
    iconTint: Color = KaspaTeal,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Outlined.Book, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize * 1.25f))
        Text(stringResource(R.string.no_saved_addresses), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(body, color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/**
 * An Address Book picture: the photo you assigned to the entry, else exactly the avatar the
 * address set on its own profile, else the person glyph. Never a photo carried for a chat contact
 * (iOS `AddressBookAvatar`).
 */
@Composable
fun AddressBookAvatar(
    address: String,
    size: Dp = 44.dp,
    /** The editor's not-yet-saved choice: a new photo... */
    pendingBitmap: android.graphics.Bitmap? = null,
    /** ...or "removed". Neither: what is saved. */
    pendingRemoved: Boolean = false,
) {
    val book = AddressBookManager.shared
    val version = book?.photoVersion ?: 0
    val saved = remember(address, version) { book?.photo(address) }
    val assigned = when {
        pendingBitmap != null -> pendingBitmap
        pendingRemoved -> null
        else -> saved
    }
    if (assigned != null) {
        androidx.compose.foundation.Image(
            bitmap = assigned.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        // Exactly the avatar the address set on its own profile - no backup photo of the chat
        // contact (iOS `includeBackupPhoto: false`).
        ContactAvatar(imageUrl = null, fallbackText = "", size = size, address = address)
    }
}

/** One saved address in a list: avatar, name, short address. */
@Composable
fun AddressBookRow(entry: AddressBookEntry, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(modifier = modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AddressBookAvatar(entry.address, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // No name you gave it: who it is on chain - its .kachat name, else the address (iOS 2831fab).
            Text(entry.customName ?: com.kachat.app.models.addressDisplayName(entry.address), color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                KaspaAddress.shortDisplay(entry.address),
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}






/**
 * Pick from the Address Book, full screen everywhere (iOS 1be4f6e's full-screen cover; here a
 * full-height sheet only Cancel closes): one address (New Chat, the Send screens) - [onSelect],
 * tapping a row picks it and closes - or, with [onDoneMultiple], several at once with ticks (New
 * Group), starting from [preselected]; "Add (n)" hands back every ticked entry. [excluding] is
 * never offered (your own address, in a group). iOS `AddressBookPickerSheet`.
 */
@Composable
fun AddressBookPickerSheet(
    onDismiss: () -> Unit,
    onSelect: ((AddressBookEntry) -> Unit)? = null,
    preselected: Set<String> = emptySet(),
    excluding: String? = null,
    onDoneMultiple: ((List<AddressBookEntry>) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val entries = rememberAddressBookEntries()
    val isMultiple = onDoneMultiple != null
    val excluded = excluding?.let(AddressBookManager::normalize)
    var search by remember { mutableStateOf("") }
    var ticked by remember {
        val saved = entries.map { AddressBookManager.normalize(it.address) }.toSet()
        mutableStateOf(preselected.map(AddressBookManager::normalize).toSet().intersect(saved))
    }
    val shown = remember(entries, search, excluded) {
        (AddressBookManager.shared?.search(search) ?: emptyList()).filter { AddressBookManager.normalize(it.address) != excluded }
    }

    IosFullSheet(onDismissed = onDismiss, swipeToDismiss = false) { close ->
        BackHandler(onBack = close)
        Column(Modifier.fillMaxSize()) {
            SheetNavBar(
                title = stringResource(R.string.ab_address_book),
                leading = { SheetBarButton(stringResource(R.string.cancel), onClick = close) },
                trailing = {
                    if (isMultiple) {
                        SheetBarButton(
                            if (ticked.isEmpty()) stringResource(R.string.done) else stringResource(R.string.ab_add_count, ticked.size),
                            onClick = {
                                onDoneMultiple?.invoke(entries.filter {
                                    AddressBookManager.normalize(it.address) in ticked && KaspaAddress.isValidOnActiveNetwork(it.address)
                                })
                                close()
                            },
                        )
                    }
                },
            )
            if (entries.isEmpty()) {
                AddressBookEmptyState(
                    body = stringResource(R.string.ab_picker_empty_body),
                    iconSize = 36.dp,
                    iconTint = colors.textSecondary,
                )
            } else {
                // Always showing: hidden until you pulled the list down, it read as pull-to-refresh.
                AddressBookSearchField(search, { search = it }, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                ) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface)) {
                            shown.forEachIndexed { index, entry ->
                                if (index > 0) SettingsDivider(inset = 66.dp)
                                val key = AddressBookManager.normalize(entry.address)
                                // the other network's address can't be chatted with or added to a group (iOS 218dc42, IOS-063)
                                val otherNetwork = KaspaAddress.otherNetworkMessageRes(entry.address)?.let { stringResource(it) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(if (otherNetwork != null) 0.5f else 1f)
                                        .clickable(enabled = otherNetwork == null) {
                                            if (!KaspaAddress.isValidOnActiveNetwork(entry.address)) return@clickable
                                            if (isMultiple) {
                                                ticked = if (key in ticked) ticked - key else ticked + key
                                            } else {
                                                onSelect?.invoke(entry)
                                                close()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        AddressBookRow(entry)
                                        if (otherNetwork != null) {
                                            Text(otherNetwork, color = colors.warning, fontSize = 12.sp)
                                        }
                                    }
                                    if (isMultiple) {
                                        Spacer(Modifier.width(8.dp))
                                        val on = key in ticked
                                        Icon(
                                            if (on) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                            contentDescription = null,
                                            tint = if (on) KaspaTeal else colors.textSecondary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
