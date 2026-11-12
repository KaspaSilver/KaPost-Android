# CLAUDE.md

Guidance for Claude Code in this repository.

## What KaPosts is

KaPosts is a standalone Android app for KaPosts (the on-chain social feed) and Public Chats, split
out of KaChat so KaChat can stay focused on 1:1 and group chats. It is the Android twin of
KaPosts-iPhone (`KaspaSilver/KaPosts-iPhone`) and follows it screen for screen. One account = one
Kaspa address, used to sign and pay for every post, reply, like, follow and room message.

The app is called **KaPost** wherever a person sees it: the launcher name (`app_name`, "KaPost
APK" for the GitHub flavour), notification titles, every on-screen string and the translations
(iOS KaPosts 4c0d5a8). The code, the `kaposts://` scheme, the push topic and log tags keep
"KaPosts". Write "KaPost" in any new user-facing text.

The source started as a copy of KaChat-Android (`KaspaSilver/KaChat-Android`) at commit `9289fad8`
(5.2.2 build 7, 2026-10-22), the point where KaChat-Android matched KaChat-iPhone `8b31f71`, which
KaPosts-iPhone was cut from. KaChat's other features are removed: 1:1 and group chat screens,
calls (and WebRTC), Chess, Swap, Cold Storage, Portfolio, the Address Book screens, Child Mode,
KaChat's Profile tab and notification bell, the Kaspa Hub, Stats, Bots, the welcome guide, the
share-sheet target, Customize Dock.

What is still KaChat-shaped underneath:
- The Kotlin package and namespace stay `com.kachat.app` (only the applicationId changed), and
  class names are KaChat's (`KaChatApp`, `KaChatApplication`, `ChatRepository`...).
- `ChatRepository` keeps the send path posts, tips and room messages go through. Its 1:1 chat
  sync is switched off (`ChatRepository.ONE_TO_ONE_CHATS_ENABLED = false`, iOS
  `oneToOneChatsEnabled`), but the code behind it (handshakes, contextual messages, groups,
  Nextcloud backup) is still there, unreachable.
- `.kachat` names: the services, the profile editor and the name screens are kept; only the
  profile editor and the Wallet's Domains tab reach them. The Domains tab
  (`KaPostAddressDomainsTab`) only lists the names: registering, renewing, selling and editing
  them is KaChat Wallet's, which its button opens (KaPay, `kapay.kachat.app`, listed in the manifest's
  `<queries>`). The Wallet's Your Domains (iOS 071e901, below) reaches the rest: the .kachat
  list (`KachatLiveDomainsTab`), a name's live page and the marketplace (Inscribe). `.kachat` is the
  only name service KaPosts reads: KNS (.kas), dotk (.k) and Kaspa Names (.kaspa) are switched
  off at their entry points (`KnsService.IS_ENABLED`, `NameServicesClient.OTHER_SERVICES_ENABLED`),
  so the Domains tab, address fields, mentions and search see .kachat names only. iOS 071e901
  keeps them off, so Your Domains' .kas, .k and .kaspa cards open empty lists. Name pushes stay
  dropped (see Protocol and servers).
- Chats live in KaChat: a profile's Chat button opens `kachat://profile/<address>?chat=1` (or its
  kachat.app page).

## Full wallet (KaPay's Wallet tab, iOS 071e901)

Every KaChat app has KaPay's full wallet (owner's call, 2026-11-07; it replaced "chatting address
only" from 2026-11-05). Profile > Wallet (`KaPostWalletScreen`) is KaPay's Wallet tab: the total
split into chatting and spending, Send, Receive and Request (iOS ee5ec98: Compound UTXOs and an
address's keys are in Manage Addresses, under that address), Backup Hygiene when due, then the
Manage Addresses and Chatting Address squares (`WalletAddressSquares`), Your Domains (below) and
Help (`WalletHelpScreen`, the .kachat names guide). No transaction history on this screen (owner's
call, 2026-11-09). Each single-address page (the Chatting Address and a spending address) has one
options icon at the top right (`AddressOptionsButton`), opening `AddressActionsSheet`: View Private
Key, View Public Key (not for a quantum address, whose key stays hidden until it spends) and View
in Explorer. The squares, Manage Addresses (`ManageAddressesScreen`, its
floating Address Actions button: Generate, Discover, Address Visibility), a spending address's page
(`SpendingAddressTxHistoryScreen`, route `spending_address_detail/{index}`), Address Visibility, the
Chatting Address QR page (`chatting_address_qr`) and Receive's fresh address (`ReceiveKaspaQrPage`)
are in `ui/screens/WalletAccountScreens.kt`, ported from KaPay Android; Manage Addresses' Chatting
Address row opens `IdentityAddressDetailScreen` (`identity_address_detail`). No Contacts (saved
addresses): people use KaChat or KaPay for that (owner's call, 2026-11-07).
Send has the From step (`SpendingAddressSendFlow`'s `lockedSource` is null unless an address's own
Send opens it), Receive shows a fresh address (`WalletViewModel.resolveFreshReceiveAddress`, warmed
when the Wallet opens, revealed when the QR shows), and Request Payment offers Fresh Address or
Choose an Address. Ported from KaPay: keep these in unison with it, and port KaPay changes here.
Spending addresses number from #1 (`WalletManager.addressNumber`; the quantum chain from #0).

## Your Domains (iOS 071e901)

Profile > Wallet > Your Domains (`WalletYourDomainsRow` on `KaPostWalletScreen`, route
`kns_domains`; `ui/screens/WalletDomainScreens.kt`, iOS `Views/Wallet/WalletDomainViews.swift`),
ported from KaPay's: the row counts every name the account owns (.kachat, KNS, .k, .kaspa).
`KnsDomainsScreen` is one square card per service, each opening its list; .kachat is
`KachatLiveDomainsTab` (names, My Offers, Inscribe opening `KachatMarketScreen`; a name opens
`KachatLiveNameDetailScreen`; the list shows the saved copy first, `KachatOwnedNamesCache`). A .kas
domain opens `KnsDomainDetailScreen`: Asset ID, Primary Domain or Set as Primary (a failure is said in
place, success is a toast) and Send, which opens `KnsDomainSendScreen` in a sheet (Cancel / Send,
the phone's lock first, the progress sheet, then `SentConfirmationSheet`). No Customize Profile:
profiles are edited on KaPost's own Profile. Android's `KnsAsset` has no listing status, so iOS's
Status: Listed row has nothing to show.

## Dock

`app/src/main/java/com/kachat/app/ui/KaChatApp.kt` - `KAPOSTS_DOCK`, five fixed tabs:

| Tab | Route | Screen |
|---|---|---|
| Feed | `kaposts` | `KaPostsScreen` (`ui/screens/KaPostsScreens.kt`); the ☰ slides out a card (`KaPostsFeedDrawer`); Following, Feed and Popular swipe sideways |
| Public Chats | `broadcasts` | `BroadcastListScreen` (`ui/screens/BroadcastScreens.kt`); every default room is listed, the customize button (top left) switches rooms off, or all language rooms at once; Select (top right) picks rooms for Read, Unread, Mute and Delete; rows swipe (`SwipeActionsRow`; left: Delete, Mute, Pin; right: read state), and a pinned room moves into the circles row above the list, saved per account (`KaPostRoomPins`); the tab's badge is the unread count across the rooms (`KaPostsDock.unreadPublicChats`) |
| Profile | `profile` | `KaPostsScreen(rootMode = MY_PROFILE)` - your KaPosts profile; Edit Profile opens `edit_kachat_profile`; Log Out (red, beside Wallet, iOS ee5ec98); Wallet pushes `wallet` (`KaPostWalletScreen`, iOS e5a9b11 `KaPostWalletView`: the total with Chatting and Spending, Send, Receive and Request (iOS ee5ec98), the backup hygiene card, the Manage Addresses and Chatting Address squares, Your Domains and Help (iOS 071e901, see Full wallet)) |
| Notifications | `notifications` | `KaPostsNotificationsTab`, with the unseen badge; a row opens its post on the Feed (a follow opens the follower's profile); the cog (top left) opens `settings_section/notifications` (post notifications only - no wallet ones) |
| Settings | `settings` | `SettingsScreen`, alphabetical (iOS e5a9b11): About (version, Terms of Use, Open Source Licenses), Account Name, Back Up Account (Complete Your Backup, View 12 or 24 Words, Back Up to NFC Card), Connection (its tabs swipe; the .kachat names indexer URL is on Domains), Customization, Diagnostics, KaPost (`KaPostsSettingsOverlay`: Cool down before post, and a default tip in KAS or the app's currency; instant tips are capped at 10 KAS and ask for the phone's lock once when turned on; Tips From, iOS 071e901), Notifications, Security, Storage; then Delete Account (`DeleteAccountSheet`, the warning the opening screen's Delete also shows; both ask for the phone's lock first; `WalletViewModel.deleteCurrentAccount`). Log Out is on the Profile |

The dock stays in reach on every screen and steps aside for the keyboard. Pushed screens keep the
real one (`AboveDock` lays them out above it; the Wallet, a room and the tabs run on under it
through `LocalDockInset`). A thread is drawn inside the KaPosts screen, so the real dock floats
over it too. KaPosts' other full-screen screens are Dialog windows (profiles, the menu's screens,
Post Activity): they draw `KaPostsDockCopy` (`ui/screens/KaPostsDock.kt`) outside their slide, and
a tap closes every cover (`KaPostsDock.closeCovers`) and selects the tab. The composers and the
menu card have no dock. The connection dot sits top right on every screen, leaving the left to
Back.

Account setup follows KaPay (iOS 9e22075): `OnboardingScreen` (create / import a seed) ->
`MainShell`. There is no welcome guide: the shell lifts the onboarding sync hold itself. Create New
Account makes an account in one tap: 24 words, no passphrase, the next free default name
(`WalletManager.nextDefaultAccountName`). "Advanced: Create with a Passphrase" is the only other
way. Import still takes 12 or 24 words. User-facing text says "12 or 24 words", never "seed phrase"
or "recovery phrase" (iOS e5a9b11). Creating never shows the words: an account made here
is owed a backup (`AccountBackupStatus`), and until the backup steps (`AccountBackupScreen`) are
finished `AccountBackupBanner` sits above every tab's title (Feed, Public Chats, Profile,
Notifications, Settings, the Wallet; red once the chatting or spending addresses hold funds) and
the Delete Account warning leads with Back Up Account; deleting is never blocked. Imported and older accounts are never owed one. Import has no "which
wallet" list (KaChat's change, 2026-11-10, kept in unison; iOS 1ab014f): name (`ImportNameScreen`), 12
or 24, the words, then the passphrase question (`PassphraseSetupScreen`). No opens "Your Account"
(`PassphraseFoundStep`), Yes searches live as the passphrase is typed. Both run
`WalletViewModel.findImportAccounts`: every `WalletSourceFamily` (Standard 50 addresses, KDX and OneKey
20, Chainge 1: its one address at m/44'/111111'/0'/0') in one batched balance call
(`WalletService.balancesOrNull`), plus each type's address #0 on Kaspa REST's transactions-count
(`WalletService.addressesWithHistory`), so an emptied wallet still counts. The type holding the most
comes first (ties go to Standard) and its first address is picked; several found are listed, and See
Every Wallet Type adds each type's #0. Nothing found, a failure or the 12 s timeout means Standard #0
(the last two with Search Again; Skip while it runs, iOS 33095ac). With or without a passphrase the
import opens the picked type and address.
"Advanced: Create with a Passphrase" opens straight on the passphrase field. Importing or finishing the steps starts a
six-month timer; when it runs out the same place shows `BackupHygieneCard`, and `BackupHygieneScreen` asks for
five random words, then the passphrase if there is one. A cold start goes back into the active
account unless Log Out was the last thing done (`WalletManager.isLoggedOut`). Biometrics when
signing back in to a saved account are off by default (Settings > Security turns them on); an
older saved "on" is switched off once per install (`KEY_ACCOUNT_LOGIN_BIOMETRICS_OFF_MIGRATED`).

## Security rules (audit 2026-11-05)

- Every link tap goes through `SafeUriHandler` (`LinkPreviewCard.kt`, provided as `LocalUriHandler`
  in `MainActivity`; iOS `openAnywhere`): a KaChat link opens its screen, http/https/mailto go out,
  anything else does nothing. Deep links in `MainActivity` take exactly their own path parts
  (`linkTarget`), and room names from links pass `KaChatLink.sanitizeChannelName` (no bidi,
  zero-width, `..` or URL characters).
- The screens showing the words (`seed_phrase`, `account_backup`, `nfc_card_backup`) open only
  straight after `unlockAccountBackup` (`SecretRouteGate`): a route Android restores after killing
  the app closes instead of showing them unlocked.
- The phone's lock (`util/DeviceAuth.kt`) takes the weak biometric tier on Android 9-10, where
  androidx.biometric can't pair strong biometrics with the screen lock.
- Signed out (`WalletManager.isLoggedOut`): push doesn't register, the KaPosts poller and the
  scheduled-post sender do nothing. Deleting the active account with others saved opens the
  sign-in screen.
- No cloud backup or device transfer of app data (`res/xml/data_extraction_rules.xml`), and no
  battery-exemption prompt or permission (KaPost's pushes are drawn by the system).
- `MainActivity` is exported for links, so it honours our own notification extras only with
  `NotificationHelper.EXTRA_INTERNAL_TOKEN` (a per-install random token on every intent it builds).
  FCM's own taps can't carry it and only do what a public link could.
- The notification permission is asked only from Settings > Notifications, as iOS.
- Encrypted stores open through `StartupSafety.encryptedPrefsOrInMemory`, never
  `EncryptedSharedPreferences.create` directly: a phone restored without the Keystore key must not
  crash at launch. `WalletManager`'s own store failing shows `WalletRecoveryScreen`.
- Sends pay a fixed 100 sompi/gram, as iOS `KaspaFeePolicy`; no preview reads the node's fee
  estimate. Fast / Priority / custom are a flat extra on top. The Wallet's Send spends only
  confirmed, non-coinbase coins (iOS `sendWithdrawal`).
- Endpoint URLs refuse `http://` ("Use https. Unencrypted connections are not supported.") and add
  `https://` to a bare host (`ConnectionViewModel.httpsUrlOrNull`).
- Accounts (iOS 33095ac): deleting an account clears both networks' stores and its spending-address
  data (`WalletManager.removeAccountEntries`, its quantum-spent marks too). A failed write puts the
  previous account list back (`saveAccountsKeepingExisting`). Import or Log Out clears a stale
  recovery screen. The Screen Lock Required dialog is its own window and shows once at a time. iOS's
  Keychain-only items (snapshots, tightening seed items at launch, the active slot) have no Android
  counterpart: every account sits in the one encrypted store.
- Accounts (iOS ee5ec98): Delete Account also clears the account's follows, mutes and blocks
  (`AppSettingsRepository.removeKaPostsAccountData`, from `ChatRepository.wipeAllLocalDataForAddress`).
  iOS's key repair at launch has no Android counterpart: words and address are one record, written
  in one commit. The recovery screen tries the store again whenever it comes to the front
  (`WalletViewModel.retryKeyRecovery`, iOS `reloadOnceUnlocked`). Log Out and Delete never wait for the
  push unregister (`unregisterAsync` snapshots the key first), which iOS now caps at 4 s.
- A link that would add a room asks first (iOS ee5ec98 `confirmJoin`, in `MainShell`): a curated room
  or one already on the list opens, anything else shows Join #name?, with a warning when the name
  mixes alphabets. Cancel withdraws the join request.
- The diagnostics export shortens every Kaspa address in the logs it bundles to its last 8
  characters (`redactingKaspaAddresses`, iOS `AppLog.log`).

## NFC card backup (as iOS 9653c04)

`services/NfcCard.kt`, `ui/screens/NfcCardScreens.kt`. The card commands and backup format are
`docs/PROTOCOL.md` in KaChat-NFC, so a card works across apps; `family` uses iOS's names, and iOS's
payload version 2 (the doc still shows version 1). In every build, Play too (iOS e5a9b11).
Settings > Back Up Account > Back Up to NFC Card only exists for an account with a BIP39 passphrase,
which is never written to the card. A new PIN is typed twice (iOS 33095ac), and PIN and passphrase
fields are hidden unless their eye is tapped (iOS ee5ec98). Restore from NFC Card
(`NfcCardRestoreScreen`) sits at the bottom of Import Account's name screen (`ImportNameScreen`, iOS
1ab014f); an account already saved here keeps its name. Android has no system
scan sheet, so the app draws `NfcScanSheet` while reader mode runs.

## Rooms during an indexer outage (iOS ee5ec98)

`BroadcastRepository.noteIndexerResult`, per indexer (a room can have its own), apart from the KaPost
indexer's detector: three failures in a row inside two minutes confirm it down (offline, a cancelled
request or a body that won't decode don't count; any answer, a 4xx too, resets it; failures inside 5 s
are one). While down a room stays on the block stream on cellular too (`unreachableIndexers`, read by
`BroadcastScanningService.hasIndexerDelivery`). On recovery each backfilled room pages that indexer again
from ten minutes before the outage (`rewindRoomsAfterOutage`, `deepBackfillFloor`); a steady poll whose
whole page is newer than anything seen pages back the same way (`indexerNewestSeen`). Older room history
has no other source; saved rows stay. A failed room edit deletes its row, so the message reads as before,
and keeps its text in memory for Retry (`failedEdits`); an unknown outcome shows "Not confirmed". A single
voice note plays straight through `MediaPlayer`; a multi-part note is read by `WebMOpusPartsDecoder`, which
has iOS's crafted-voice-note limit (`MAX_CONTAINER_DEPTH`, 16 levels).

## Show .kachat Endings (iOS 1b15521)

Settings > Customization > Show .kachat Endings, on by default, with iOS's footer. Off shows a
.kachat name as `alice` instead of `alice.kachat`. Every shown name goes through
`KachatNameLabel.display` (`models/Models.kt`, key `kachat_show_name_endings` in `kachat_prefs`, read
in `KaChatApplication.attachBaseContext`; snapshot state, so names on screen redraw when it flips):
`KachatNamesRegistry.kachatName` (the display-name rule, posters, `AddressResolutionCard`),
`NameInfo.display`, `NameResolution.display` for .kachat, the owned names, the name screens, the
registration messages and the name notifications. Display only: typing, copying and anything sent
keep the full name. A post's @mention is written with `NameResolution.fullName`
(`KaPostsViewModel.resolveMentionQuery`), and the @ suggestions list full names. The `.kachat`
wordmark under a name tile and the one-page claim button ("Claim %1$s.kachat", no iOS counterpart)
keep the ending.

## .kachat names with the indexer down (iOS ee5ec98)

`KachatNamesRegistry`, as iOS: after 3 failures in a row inside 120 s (`indexerOutageConfirmed`; an
undecodable body doesn't count, nor does being offline) the registry moves to its chain copy
(`fromIndexer`, `failOverToChain`), and a refresh goes back when it recovers. One missed status check
doesn't drop a working indexer; a wrong covenant id, `synced == false` or too much lag still do. A chain
copy answers only if this session's walk reached the tip in the last 10 minutes (`chainTipAt`,
`readChainState`), so saved names keep showing and a name never reads as free from a stale or partial
copy. After a failover the indexer isn't asked for 60 s (`chainHoldUntil`). A failed read keeps what's on
screen and never marks a pending claim failed. `confirmOnChain` always checks the live UTXO, whatever
the source: no record is trusted to pay on its own. A commit from the chain copy forces a walk first
(`lookupForCommit`), and a commit resend reuses its own coins (`PendingRegistration.commitInputs`), so it
can't land twice. A walk repeats until the tip (`walkToTip`), the standby copy is walked every 30
minutes (2 hours on a metered network), and without an indexer a person's profile is read from their
own chain record (`chainProfile`). `.kachat` funding leaves out the coins a send in flight spent
(`KachatNamesService.unreservedFundingUtxos`).

Download caps (iOS ee5ec98): a link preview's page stops at 1 MB, its image at 5 MB, and Nextcloud's
photo and PDF viewer at 15 MB (`BoundedImageFetcher`, `LinkPreviewService.downloadToCacheFile`), whatever
Content-Length says. Coil decodes each to the size it is shown at; the viewer to 4096 px at most.

## With the indexer down (iOS ee5ec98)

What relies on the indexer keeps working from the node where it can, shows what it saved where it
can't, and recovers by itself (`services/KaPostsChainFallback.kt`).

- The indexer is the source whenever it's online. `KaPostsIndexerHealth`: every KaPost read and write
  reports. Confirmed down only after 3 failures in a row inside 120 s (a network failure while the
  phone has a connection, 429 or 5xx), and any answer resets the count; a body that won't decode is
  an answer. Until then a failed request throws as before. One small request a minute looks for
  its return (`KaPostsService.probeIndexer`).
- Saved pages: `KaPostsService.get` keeps each read's first page on disk (`KaPostsPageCache`, per
  account, cleared by Delete Account) and serves it only while confirmed down or with the phone
  offline.
- From the chain, first pages only: a person's posts and replies from Kaspa REST
  (`KaPostsChainPosts`, through `KaPostChainReader.addressPayloads`), the Following feed from the people
  followed (the first 20), and new posts and replies from the block stream (`KaPostsLiveChainFeed`, only
  while down and in front, its own owner on the connection `BroadcastScanningService` streams from). Every
  one must pass its Schnorr signature, since a payload can name anyone. Nothing but the indexer lists every post, or who reacted to yours, so the global
  feed is the saved page plus what's new, and notifications are the saved page until it returns.
- The feed says so while it lasts. When the indexer answers, the feed, your profile and notifications
  reload, and every load gate is cleared, so it has the final word.
- Scheduled posts: only a node's rejection fails one. Offline, connecting or no answer leaves it
  scheduled with its coins held. Past its time, a post the indexer doesn't send within 150 s goes from
  the phone. A Cancel the indexer didn't answer stays scheduled and says so.
- Posts not on chain yet (`KaPostsHeldPostStore`): their words are kept until they land, and what a kill
  leaves comes back as a draft.

## Voice notes in rooms (iOS 1b15521)

Since 2026-11-11 rooms record KaChat's long voice notes (KaChat MESSAGING.md, "Long voice notes
(multi-part)"): 16 kbps CBR Opus in 60 ms frames, up to 2:50, as long as 1 KAS of fees allows
(`VoiceNotePolicy.PUBLIC_CHAT` in `util/VoiceNoteParts.kt`, where the math is). A room message is plain
base64 JSON, which costs what KaChat's 1:1 ECIES + base64 does, so the numbers match 1:1: parts of at
most 14.5 s / 31,000 bytes (0.083 KAS each), 12 parts and 0.976 KAS at 2:50. The mic records PCM
(`VoiceRecorderService.startVoiceNoteRecording`, `OpusWebMPartsRecorder`) and Stop encodes it into its
parts ("Encoding…"): the platform encoder only makes 20 ms frames, so each three are joined into one
60 ms code 3 packet (`OpusPacket.joinThree`; a part whose frames can't be joined keeps 20 ms packets),
and each part is fed the next one's look-ahead. Without the platform Opus encoder MediaRecorder records
one 14.5 s part. A note of one part or less is one ordinary voice message; a longer one goes as
`voicePart`-tagged parts (`MediaFileEnvelope.json`, iOS's bytes), one transaction each
(`BroadcastRepository.sendVoiceParts`: the balance for all parts first, then in order, never a reply).
Parts belong together by (room, sender, id, count) (`BroadcastVoiceParts`, worked out in
`getMessageWindow` into `RoomWindow.voiceNotes`, the other parts left out of the window): one bubble
at the first part (`RoomVoiceNoteBubble`) with gapless playback and one scrubber (the parts decoded one
by one, pre-skip cut and trimmed to their Info Duration, joined into one WAV for `MediaPlayer`:
`WebMOpusPartsDecoder`), "Receiving… N of M" until all are in, one status, Retry resends only the failed
parts (`retryVoiceParts`), and unread counts the note once (`roomSummary`). The recording bar is
KaChat's 1:1 one: a ring toward the limit, "Recording... 00:10 / 02:50", the trash on the right. Room
pushes come from the indexer, one per part ("Voice message"), with no part tag. While the app is open,
`RoomVoicePushBursts` (`NotificationHelper.kt`) applies KaChat's burst rule: a voice or photo push from
the same sender in the same room within 60 s of the last is listed quietly (silent, its own id per room
and sender, so the quiet one before it is replaced). In the background or closed, the system shows every
part's push. A failed part waiting for a confirmed coin isn't retried by itself: Android's room sends
have no deferred retry, so it shows Failed and Retry sends the rest.

## KaPost links in rooms (as iOS 23920c8)

A post link (`kachat://kapost/<id>`, `kaposts://kapost/<id>` or `https://kachat.app/post/<id>`) in a
room shows as a card (`KaChatInternalLinkCard` in `LinkPreviewCard.kt`) with the poster and the
post's text; a tap opens the post. The same card as KaChat's (owner's call, 2026-11-07): the KNS
domain or `.kachat` name first, then a contact name; the text on one line with control and
direction marks gone, 300 characters, three lines; "A post on KaPost" until it loads; "Shared a
KaPost" in the room list (`NextcloudShareSniff.linkSafePreview`). `KaPostLinkPreviewService` reads
the post from the chain and asks again for one it couldn't find after 5 minutes.

## Moderation (as iOS 9e22075)

- Terms: `KaChatApp` shows `TermsAcceptanceScreen` until `KaPostTerms.CURRENT_VERSION` is agreed to
  on this device; bump it when the text changes. Settings > Terms of Use shows them again.
- Report: a post's three dots, or in a room the message's long-press and the sender half sheet.
  `ContentReport` emails support in any mail app; when none opens it, it copies the report and says
  where to send it.
- Block: `AppSettingsRepository.kapostsBlocked` owns the list. `BroadcastRepository.getHiddenSenders`
  adds it as every-room rows, so a blocked account also leaves every room and its pushes stop. A
  room's own Hide User stays separate (`getRoomHiddenSenders`).
- Removal: no word filter, by the owner's choice (2026-11-02). The owner removes bad content by hand
  with the indexer's admin tool (`kachat-admin`), from reports or from seeing it.

## Identifiers

- applicationId `kapost.kachat.app` (`.debug` suffix on debug builds), the package registered for
  KaPost in Play Console; it can never change once a bundle is uploaded. The iPhone app's bundle is
  `com.kachat.kaposts`. URL scheme `kaposts`
- Version: `app/build.gradle.kts` (`versionName`, `versionCode`, `kachatBuildNumber`). KaPost's version
  number matches KaChat iOS's, from 5.2.3 on (iOS f81c467)
- Flavours as KaChat: `play` (store, reports plain "1.0") and `github` (handed-out APK, "1.0 (n)")
- The launcher icon is a vector drawn from the iOS icon design: `scripts/make_app_icon.py`; the
  avatar's backward K is KaChat's own, traced from its icon (iOS KaPosts 499275c).
  `app/src/main/ic_launcher-playstore.png` is iOS's light icon at 512 px. The setup screen's logo is
  the whole icon as one vector, light (`kaposts_app_mark`) and dark (`kaposts_app_mark_dark`, iOS
  KaPostsLogo-dark); the welcome screen is dark in both themes, so it shows the dark one

## Build

Use Android Studio's bundled JDK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleGithubDebug
```

Claude may build debug variants and run them on an emulator. Release signing (`keystore.properties`)
and store builds are the owner's.

Signing: the GitHub APK (`assembleGithubRelease`) has always been signed with the debug key, so
installed copies keep updating in place. The Play bundle (`bundlePlayRelease`) is signed with the
upload key `kapost-upload.jks` (alias `kapost-upload`, made 2026-10-30, SHA-256 AD:08:55:92:...),
named in `keystore.properties` as `uploadStoreFile`. Both files are gitignored and exist only on the
owner's Mac: back them up together. Once the first bundle is uploaded, Play takes updates only
signed with this key, and losing it means asking Play support to reset the upload key.

## Sending Kaspa

Every send is the step-by-step flow iOS KaPosts took from KaPay (da3e953: `WithdrawKaspaView`,
`Views/Shared/SendKaspaComponents.swift`), built from the shared pieces in
`ui/screens/SendKaspaComponents.kt` and `ui/screens/SwipeUpSendScreen.kt` (copied from KaPay
Android). Change those pieces, not one screen.

1. **To**: `SendStepScreen` + `SendStepQuestion` ("Who are you sending to?") + `SendRecipientCard`
   (Paste and Scan QR pills under the field; no Address Book in KaPost). On a first step Cancel
   sits at the bottom beside Continue (`SendContinueButton`); later steps have Back and Cancel in
   `SendStepHeader`.
2. **From**: `SendFromStep` ("Where are you sending from?"): Chatting Address (iOS 7d8b258), or Choose an Address
   with your addresses listed right there (`OwnAddressList`, the same cards `OwnAddressPicker`'s
   sheet shows). Skipped when the From is fixed (an address's own Send). The tip sheet has its
   own From (`KaPostTipFromStep`, iOS 071e901).
3. **Amount**: `KaspaKeypadAmountEntry`, the app's own number pad (`KaspaKeypad`,
   `KeypadAmountText`), so no system keyboard, under "To ..." and "From ..." chips
   (`SendStepChip`). The From's balance is the Available under the amount, red when the amount is
   more. Continue is "Review".
4. **Review**: `SwipeUpToSendScreen` with Back: the amount and its fiat value, To / From / Total
   (`SendReviewCard`), then `SendFeeControls`. Only the fee speed, coin control and the fee's Edit
   can change; a Max amount is worked out again when they (or the From) change. Swipe up to Send
   shows the BlockDAG and the outcome on its own screen (no `SentConfirmationSheet`).
5. **Fee**: Edit (`onEditFee`) opens `SendFeeEditorScreen` (the fee on the keypad, never under the
   network's minimum, a warning above ten times it); Confirm goes back to the review.

Swipe up to Send asks for the phone's lock first (`authenticateWithDeviceCredential`, "Confirm
sending Kaspa") and sends at 30% of the screen (120dp at least). Send and tips have the From step
again (iOS 071e901, KaPay's full wallet; see Full wallet and Tips).
The fee total includes change too small to keep (`SendFeeModel` folded change).

Send safety (from KaPay): a node's answer must carry the locally computed transaction id, and a send
the network never answered throws `SendOutcomeUnknownException` ("This may have gone through...").
That shows "Not confirmed" with only Done on the swipe screen, and posts, replies, room messages and
instant tips marked `UNCONFIRMED` offer no Retry, which could post and pay twice. A room message
whose outcome is unknown keeps its row as `deliveryStatus` "unconfirmed" under the transaction's
id, and its delivered copy (indexer or block scan) makes it "sent"; a pending row an earlier run
left is settled the same way, never marked failed (iOS 33095ac). What a send spent stays reserved
(`NodePoolManager.submittedInputs`) until it is in a block, but an unknown outcome is deliberately
not held (iOS ee5ec98): a send made after "Not confirmed" picks the same coins, so only one of the
two can land. Coin Control lists every coin at
the address and greys out, unselectable, the ones the send won't spend (spent by a send in flight,
held by a scheduled post, unconfirmed, or a mining reward where the send takes neither).

Steps slide sideways (`SendStepTransition`). Where they are used:
- `SpendingAddressSendFlow` (`Screens.kt`): the Wallet's Send, a room sender's Send, paying a
  chat-payment request (other requests go to KaPay), Compound UTXOs. A pre-filled recipient or a
  request opens on From (a request's amount is fixed: no keypad), Compound on the review.
- `KaPostTipSheet` (`KaPostsScreens.kt`): no To (the poster is the recipient); it opens on From
  (`KaPostTipFromStep`, built from the From step's pieces, with a line under the pills saying what
  the choice means), set to Tips From, then the amount; the review also has the tip's encrypted
  memo and the small-amount warning.
- Not ported: the .kachat sheets (`KachatTxSheet`, `KachatClaimSheet` in
  `KachatNamesLiveScreens.kt`) keep their one-page form; Your Domains' .kachat list and
  marketplace now open them (iOS 071e901). Request (`CreatePaymentRequestSheet`, the Wallet's More > Request
  Payment) is where (Fresh Address, reserved only once the link is copied or shared, or Choose an
  Address), amount, then review (iOS 071e901, as in KaPay).
- `SpendingAddressSendFlow` from a spending address's page (`SpendingAddressTxHistoryScreen`) has
  that address locked as the From; the Wallet's Send, a room sender's Send and a chat-payment
  request open on From (iOS 071e901 `lockedSource` nil).

Tips (iOS 071e901, KAPOSTS_INDEXER.md 5.11): only a tip from the chatting address is a tip: it
names the post, counts on it and lists the tipper. From another address a tip is private (owner's
call, 2026-11-07): `ChatViewModel.sendPayment` drops the tip and sends a plain payment with the chat
notice, so the author gets it in your chat but it names no post, isn't counted and doesn't list
you; the fee preview and Max price it the same way (`previewPaymentFee`, `paymentMaxSompi`). KaPost
Settings > Tips From (`KaPostTipSourcePage`, per account: `WalletViewModel.kaPostsTipSourceIndex`,
iOS ee5ec98 `KaPostsTipSourceStore`; the old device-wide value seeds each account once; resolved by
`kaPostDefaultTipSource`, which answers null for a spending index the account hasn't revealed or
can't derive, so nothing goes out on its own and the row says Not available) picks the instant tip's
address and the tip sheet's first choice; an instant tip from
another address isn't counted on the post and its toast says "privately". Compound Max at Fast /
Priority already priced its own coins and skipped coins in flight
(`WalletViewModel.maxConsolidatableChunk`), so iOS 071e901's fix there needed no change.

## Protocol and servers

Same as KaChat: payloads under `kchat:1:`, the KaChat indexer for KaPosts and Public Chats, the
node pool for transactions. The mainnet default for the indexer, KaPosts, Public Chats, push and
translation is `https://indexer.kachat.app` (`AppSettingsRepository.DEFAULT_INDEXER_URL`). Its old
name, `kachat.duckdns.org`, is the same server; settings saved on it read as the new one
(`renamedIndexer`), and push sends no unregister for the rename. Links on the old name are no
longer recognized (only `kachat.app` and `kachat://`). Testnet has no indexer (blank, so chat,
push, KaPosts and Public Chats are off there); a saved `tnkachat.duckdns.org` reads as blank
(iOS KaPosts ae0b025). The protocol and server docs live in KaChat-iPhone
(`KAPOSTS_INDEXER.md`, `PUBLIC_CHATS_INDEXER.md`, `PUSH_EXTENSIONS.md`, `TRANSLATION_SERVICE.md`).

Push needs the Firebase Android apps `kapost.kachat.app` and `kapost.kachat.app.debug` in project
`kachat-fc22a` (`app/google-services.json`, gitignored) and the push service accepting them; without
it KaPosts notifies through its in-app poller only. Registration sends `apns_topic` =
`kapost.kachat.app`, the app's package (the iPhone app sends `com.kachat.kaposts`), so the shared push
service knows which app the token is for. It also sends `android_channel_id`, the channel Settings >
Notifications > Sound & Vibration maps to (`NotificationHelper.channelFor`, one of the four
`kachat_messages_*`), so the KaPosts and room pushes the system draws by itself play and vibrate as
chosen (kachat-indexer `docs/ANDROID_NOTIFICATION_CHANNEL.md`). The channel is in
`PushRegistrationManager`'s snapshot and fingerprint, so changing Sound & Vibration re-registers. It watches no chat address (`watched_addresses`
empty, main address only), so the push service sends it no chat messages; any chat push that
comes anyway is ignored (`KaChatFirebaseMessagingService`). Only KaPosts and public chat pushes
are shown: KaPost has no notifications about domains. `.kachat` name pushes (`name_event`, routed
here by `primary_address`) are dropped, nothing runs `KachatNamesNotifier`, and the renewal
reminders an earlier build scheduled are cancelled at launch (`KachatNameReminders.cancelAll`). A
name push FCM draws by itself (one sent with a `notification` block) can only be stopped by the
push service skipping `name_event` for `kapost.kachat.app` (and the iPhone app's `com.kachat.kaposts`); a KaPosts push tap opens the Notifications tab, or the post it names on the Feed.
#kaspa and #kachat-bugs start with their bells on, once per account; an address receiving KAS
never notifies (`AddressActivityNotifier`).

Payment requests (`kachat://pay/...`, `https://kachat.app/pay/...`) are paid in KaPay, KaChat's
wallet app (KaChat-Wallet-Android, `kapay.kachat.app`): a post shows the link as a card
(`KaPostPaymentRequestCard`), and a tap, a pasted link or one opened from outside goes through
`PaymentRequestSheetHost`, which opens `kapay://pay/<same tail>` (`KaPayHandoff`). Your own request
and a chat-payment request (`chat=1`, an encrypted chat payment KaPay can't make) open here.
Without KaPay installed, the KaChat apps' install page opens (`KaPayHandoff.STORE_URL`,
`https://home.kachat.app/install/`, which links each app). KaChat follows the same rule. A KaPlay
challenge (`KaPlayChallengeViews.kt`) opens in KaPlay, and without it the same install page
(iOS 33095ac `KaPlayHandoff`).

Links to KaChat's own site (`home.kachat.app`, any page, `KaChatSiteLink`) preview by themselves: a
post draws the page's card (`LinkPreviewCard`) in place of the link's text, and a room loads it
without the tap other links wait for. Other web links in posts stay plain text.

Links: KaPosts opens `kaposts://` (same targets as `kachat://`), and `kachat://kapost/`,
`kachat://broadcast/` and `https://kachat.app/post|broadcast/`, which KaChat no longer handles.
For the https links to open KaPosts without a chooser, kachat.app's `assetlinks.json` needs
KaPosts' package and signing certificate.
