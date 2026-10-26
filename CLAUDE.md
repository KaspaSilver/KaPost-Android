# CLAUDE.md

Guidance for Claude Code in this repository.

## What KaPosts is

KaPosts is a standalone Android app for KaPosts (the on-chain social feed) and Public Chats, split
out of KaChat so KaChat can stay focused on 1:1 and group chats. It is the Android twin of
KaPosts-iPhone (`KaspaSilver/KaPosts-iPhone`) and follows it screen for screen. One account = one
Kaspa address, used to sign and pay for every post, reply, like, follow and room message.

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
  profile editor, the Wallet's Domains tab and a name notification reach them.
- Chats live in KaChat: a profile's "Chat in KaChat" opens `kachat://profile/<address>` (or its
  kachat.app page).

## Dock

`app/src/main/java/com/kachat/app/ui/KaChatApp.kt` - `KAPOSTS_DOCK`, five fixed tabs:

| Tab | Route | Screen |
|---|---|---|
| Feed | `kaposts` | `KaPostsScreen` (`ui/screens/KaPostsScreens.kt`), the ☰ menu beside the title |
| Public Chats | `broadcasts` | `BroadcastListScreen` (`ui/screens/BroadcastScreens.kt`); rooms hide the dock |
| Profile | `profile` | `KaPostsScreen(rootMode = MY_PROFILE)` - your KaPosts profile; Edit Profile opens `edit_kachat_profile` |
| Wallet | `wallet` | `IdentityAddressDetailScreen(title = "Wallet")` - the one address's balance, history, UTXOs, domains |
| Settings | `settings` | `SettingsScreen`: Customization, Security, Connection, Notifications, KaPosts, Public Chats, Cache, Diagnostics, Open Source Licenses, View Seed Phrase, Delete Account |

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
push service accepting it; until then KaPosts notifies through its in-app poller only. Only KaPosts,
public chat and .kachat name pushes are shown.

Links: KaPosts opens `kaposts://` (same targets as `kachat://`), and `kachat://kapost/`,
`kachat://broadcast/` and `https://kachat.app/post|broadcast/`, which KaChat no longer handles.
For the https links to open KaPosts without a chooser, kachat.app's `assetlinks.json` needs
KaPosts' package and signing certificate.
