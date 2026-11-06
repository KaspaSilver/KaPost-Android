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
  `<queries>`). `.kachat` is the
  only name service KaPosts reads: KNS (.kas), dotk (.k) and Kaspa Names (.kaspa) are switched
  off at their entry points (`KnsService.IS_ENABLED`, `NameServicesClient.OTHER_SERVICES_ENABLED`),
  so the Domains tab, address fields, mentions and search see .kachat names only.
- Chats live in KaChat: a profile's Chat button opens `kachat://profile/<address>?chat=1` (or its
  kachat.app page).

## Dock

`app/src/main/java/com/kachat/app/ui/KaChatApp.kt` - `KAPOSTS_DOCK`, five fixed tabs:

| Tab | Route | Screen |
|---|---|---|
| Feed | `kaposts` | `KaPostsScreen` (`ui/screens/KaPostsScreens.kt`); the ☰ slides out a card (`KaPostsFeedDrawer`); Following, Feed and Popular swipe sideways |
| Public Chats | `broadcasts` | `BroadcastListScreen` (`ui/screens/BroadcastScreens.kt`); every default room is listed, the customize button (top left) switches rooms off, or all language rooms at once; Select (top right) picks rooms for Read, Unread, Mute and Delete; rows swipe (`SwipeActionsRow`; left: Delete, Mute, Pin; right: read state), and a pinned room moves into the circles row above the list, saved per account (`KaPostRoomPins`); the tab's badge is the unread count across the rooms (`KaPostsDock.unreadPublicChats`) |
| Profile | `profile` | `KaPostsScreen(rootMode = MY_PROFILE)` - your KaPosts profile; Edit Profile opens `edit_kachat_profile`, Wallet pushes `wallet` (`KaPostWalletScreen`, iOS e5a9b11 `KaPostWalletView`: the total with Chatting and Spending, Send, Receive and Request, the backup hygiene card, then the chatting address's history) |
| Notifications | `notifications` | `KaPostsNotificationsTab`, with the unseen badge; a row opens its post on the Feed (a follow opens the follower's profile); the cog (top left) opens `settings_section/notifications` (post notifications only - no wallet ones) |
| Settings | `settings` | `SettingsScreen`, alphabetical (iOS e5a9b11): About (version, Terms of Use, Open Source Licenses), Account Name, Back Up Account (Complete Your Backup, View 12 or 24 Words, Back Up to NFC Card), Connection (its tabs swipe; the .kachat names indexer URL is on Domains), Customization, Diagnostics, KaPost (`KaPostsSettingsOverlay`: Cool down before post, and a default tip in KAS or the app's currency; instant tips are capped at 10 KAS and ask for the phone's lock once when turned on), Notifications, Security, Storage; then Delete Account (`DeleteAccountSheet`, the warning the opening screen's Delete also shows; both ask for the phone's lock first; `WalletViewModel.deleteCurrentAccount`), Log Out |

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
the Delete Account warning leads with Back Up Account; deleting is never blocked. Imported and older accounts are never owed one. An import that answers Yes to the
passphrase question checks the first 50 addresses, picks the first with funds and lets you pick
another (Import waits until that search ends); one without a passphrase opens address #0.
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

## NFC card backup (as iOS 9653c04)

`services/NfcCard.kt`, `ui/screens/NfcCardScreens.kt`. The card commands and backup format are
`docs/PROTOCOL.md` in KaChat-NFC, so a card works across apps; `family` uses iOS's names, and iOS's
payload version 2 (the doc still shows version 1). In every build, Play too (iOS e5a9b11).
Settings > Back Up Account > Back Up to NFC Card only exists for an account with a BIP39 passphrase,
which is never written to the card. Import Account > Restore from NFC Card. Android has no system
scan sheet, so the app draws `NfcScanSheet` while reader mode runs.

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
2. **From**: `SendFromStep` ("Where are you sending from?"): Your Address, or Choose an Address
   with your addresses listed right there (`OwnAddressList`, the same cards `OwnAddressPicker`'s
   sheet shows). Skipped when the From is fixed (an address's own Send, a post's tip).
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
sending Kaspa") and sends at 30% of the screen (120dp at least). KaPost sends only from the chatting
address (iOS e5a9b11): Send, tips and Request offer no other address, and the From step is skipped.
The fee total includes change too small to keep (`SendFeeModel` folded change).

Send safety (from KaPay): a node's answer must carry the locally computed transaction id, and a send
the network never answered throws `SendOutcomeUnknownException` ("This may have gone through...").
That shows "Not confirmed" with only Done on the swipe screen, and posts, replies, room messages and
instant tips marked `UNCONFIRMED` offer no Retry, which could post and pay twice.

Steps slide sideways (`SendStepTransition`). Where they are used:
- `SpendingAddressSendFlow` (`Screens.kt`): the Wallet's Send, a room sender's Send, paying a
  chat-payment request (other requests go to KaPay), Compound UTXOs. A pre-filled recipient or a
  request opens on From (a request's amount is fixed: no keypad), Compound on the review.
- `KaPostTipSheet` (`KaPostsScreens.kt`): no To (the poster is the recipient); From only for a
  profile's tip (a post's tip comes from your address), then the amount; the review also has the
  tip's encrypted memo and the small-amount warning.
- Not ported: the .kachat sheets (`KachatTxSheet`, `KachatClaimSheet` in
  `KachatNamesLiveScreens.kt`) keep their one-page form; nothing in KaPost opens them (the Domains
  tab only lists names), as in iOS KaPosts. Request (`CreatePaymentRequestSheet`, on the Wallet)
  is amount then review, always to the chatting address.

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
service knows which app the token is for. It watches no chat address (`watched_addresses`
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
Without KaPay installed, the Play Store opens (`KaPayHandoff.STORE_URL`: a search for KaPay until
it is listed). KaChat follows the same rule.

Links to KaChat's own site (`home.kachat.app`, any page, `KaChatSiteLink`) preview by themselves: a
post draws the page's card (`LinkPreviewCard`) in place of the link's text, and a room loads it
without the tap other links wait for. Other web links in posts stay plain text.

Links: KaPosts opens `kaposts://` (same targets as `kachat://`), and `kachat://kapost/`,
`kachat://broadcast/` and `https://kachat.app/post|broadcast/`, which KaChat no longer handles.
For the https links to open KaPosts without a chooser, kachat.app's `assetlinks.json` needs
KaPosts' package and signing certificate.
