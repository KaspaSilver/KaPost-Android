# CLAUDE.md

Guidance for Claude Code in this repository.

## What KaPosts is

KaPosts is a standalone Android app for KaPosts (the on-chain social feed) and Public Chats, split
out of KaChat so KaChat can stay focused on 1:1 and group chats. It is the Android twin of
KaPosts-iPhone (`KaspaSilver/KaPosts-iPhone`) and follows it screen for screen. One account = one
Kaspa address, used to sign and pay for every post, reply, like, follow and room message.

The app is called **KaPost** wherever a person sees it: the launcher name (`app_name`, "KaPost
APK" for the GitHub flavour), notification titles, every on-screen string and the translations
(iOS KaPosts 4c0d5a8). The code, the package, the applicationId, the `kaposts://` scheme and log
tags keep "KaPosts". Write "KaPost" in any new user-facing text.

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
  profile editor, the Wallet's Domains tab and a name notification reach them. The Domains tab
  (`KaPostAddressDomainsTab`) only lists the names: registering, renewing, selling and editing
  them is KaChat Wallet's, which its button opens (`com.kachat.wallet`, listed in the manifest's
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
| Public Chats | `broadcasts` | `BroadcastListScreen` (`ui/screens/BroadcastScreens.kt`); every default room is listed, the customize button (top left) switches rooms off, or all language rooms at once |
| Profile | `profile` | `KaPostsScreen(rootMode = MY_PROFILE)` - your KaPosts profile; Edit Profile opens `edit_kachat_profile`, Wallet pushes `wallet` (`IdentityAddressDetailScreen`: balance, then History, UTXOs and .kachat names, which swipe; Receive and Send only on History) |
| Notifications | `notifications` | `KaPostsNotificationsTab`, with the unseen badge; a row opens its post on the Feed (a follow opens the follower's profile); the cog (top left) opens `settings_section/notifications` (post notifications only - no wallet ones) |
| Settings | `settings` | `SettingsScreen`: Customization, Security, Connection (its tabs swipe; the .kachat names indexer URL is on Domains), KaPost (`KaPostsSettingsOverlay`: Cool down before post, and a default tip in KAS or the app's currency), Notifications, Cache, Diagnostics, Open Source Licenses, View Seed Phrase, Log Out (deleting an account is on the opening screen's Saved Accounts) |

The dock stays in reach on every screen and steps aside for the keyboard. Pushed screens keep the
real one (`AboveDock` lays them out above it; the Wallet, a room and the tabs run on under it
through `LocalDockInset`). A thread is drawn inside the KaPosts screen, so the real dock floats
over it too. KaPosts' other full-screen screens are Dialog windows (profiles, the menu's screens,
Post Activity): they draw `KaPostsDockCopy` (`ui/screens/KaPostsDock.kt`) outside their slide, and
a tap closes every cover (`KaPostsDock.closeCovers`) and selects the tab. The composers and the
menu card have no dock. The connection dot sits top right on every screen, leaving the left to
Back.

Account setup is KaChat's, unchanged: `OnboardingScreen` (create / import a seed) -> `MainShell`.
There is no welcome guide: the shell lifts the onboarding sync hold itself.

## Identifiers

- applicationId `com.kachat.kaposts` (`.debug` suffix on debug builds), URL scheme `kaposts`
- Version: `app/build.gradle.kts` (`versionName`, `versionCode`, `kachatBuildNumber`)
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

Push needs a Firebase app for `com.kachat.kaposts` (`app/google-services.json`, gitignored) and the
push service accepting it; until then KaPosts notifies through its in-app poller only. Registration
sends `apns_topic` = the app id (`com.kachat.kaposts`, as iOS sends its bundle id) so the shared
push service knows which app the token is for. It watches no chat address (`watched_addresses`
empty, main address only), so the push service sends it no chat messages; any chat push that
comes anyway is ignored (`KaChatFirebaseMessagingService`). Only KaPosts, public chat and .kachat name pushes
are shown; a KaPosts push tap opens the Notifications tab, or the post it names on the Feed.
#kaspa and #kachat-bugs start with their bells on, once per account; an address receiving KAS
never notifies (`AddressActivityNotifier`).

Payment requests (`kachat://pay/...`, `https://kachat.app/pay/...`) are paid in KaPay, KaChat's
wallet app (KaChat-Wallet-Android, `com.kachat.wallet`): a post shows the link as a card
(`KaPostPaymentRequestCard`), and a tap, a pasted link or one opened from outside goes through
`PaymentRequestSheetHost`, which opens `kapay://pay/<same tail>` (`KaPayHandoff`). Your own request
and a chat-payment request (`chat=1`, an encrypted chat payment KaPay can't make) open here.
Without KaPay installed, the Play Store opens (`KaPayHandoff.STORE_URL`: a search for KaPay until
it is listed). KaChat follows the same rule.

Links: KaPosts opens `kaposts://` (same targets as `kachat://`), and `kachat://kapost/`,
`kachat://broadcast/` and `https://kachat.app/post|broadcast/`, which KaChat no longer handles.
For the https links to open KaPosts without a chooser, kachat.app's `assetlinks.json` needs
KaPosts' package and signing certificate.
