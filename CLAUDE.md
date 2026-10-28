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
| Profile | `profile` | `KaPostsScreen(rootMode = MY_PROFILE)` - your KaPosts profile; Edit Profile opens `edit_kachat_profile`, Wallet pushes `wallet` (`IdentityAddressDetailScreen`: balance, history, UTXOs, .kachat names) |
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
- The launcher icon is a vector drawn from the iOS icon design: `scripts/make_app_icon.py`

## Build

Use Android Studio's bundled JDK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleGithubDebug
```

Claude may build debug variants and run them on an emulator. Release signing (`keystore.properties`)
and store builds are the owner's.

## Protocol and servers

Same as KaChat: payloads under `kchat:1:`, the KaChat indexer for KaPosts and Public Chats, the
node pool for transactions. The protocol and server docs live in KaChat-iPhone
(`KAPOSTS_INDEXER.md`, `PUBLIC_CHATS_INDEXER.md`, `PUSH_EXTENSIONS.md`, `TRANSLATION_SERVICE.md`).

Push needs a Firebase app for `com.kachat.kaposts` (`app/google-services.json`, gitignored) and the
push service accepting it; until then KaPosts notifies through its in-app poller only. Registration
sends `apns_topic` = the app id (`com.kachat.kaposts`, as iOS sends its bundle id) so the shared
push service knows which app the token is for. Only KaPosts, public chat and .kachat name pushes
are shown; a KaPosts push tap opens the Notifications tab, or the post it names on the Feed.
#kaspa and #kachat-bugs start with their bells on, once per account; an address receiving KAS
never notifies (`AddressActivityNotifier`).

Links: KaPosts opens `kaposts://` (same targets as `kachat://`), and `kachat://kapost/`,
`kachat://broadcast/` and `https://kachat.app/post|broadcast/`, which KaChat no longer handles.
For the https links to open KaPosts without a chooser, kachat.app's `assetlinks.json` needs
KaPosts' package and signing certificate.
