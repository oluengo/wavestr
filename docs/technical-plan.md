# Wavestr · Technical plan v0.6

*27 Oct 2026 · v0.1 → v0.2: languages, open decisions closed, phase status. See "Changes" at the end.*

Online radio (PLS/M3U) and Nostr streams (formerly "live") in one app, for Android, Linux and Windows. **Nostr first**: first the live streams of the people you follow; radio comes later on the same player.

---

## 1. Decisions already taken

| Topic | Decision |
|---|---|
| Priority | Nostr first: NIP-53 live streams → zaps → radios |
| Platform | Native Android. The PWA stays optional (http/CORS and background limitations) |
| Playback | Audio only / Video selector without cutting the stream. Automatic switch to audio with the screen off |
| Android Auto | Audio only. No video or zaps in the car |
| Zaps | NIP-57 + NWC (NIP-47). Plan B: open the external wallet with `lightning:`. **The app never holds funds** |
| Favourites | Folders and subfolders synced as Nostr lists (NIP-51) |
| Quality | Always visible. Selectable when there are several variants. Auto mode by wifi/data |
| Extras | Surprise button (favourites / worldwide), streamer profiles, station card, light/dark/auto theme |
| Languages | Catalan, Spanish and English. By default, the device language; if it's none of the three, English. Selector in Settings |
| Distribution | GitHub Releases + Obtainium + Zapstore. No Google Play or F-Droid for now. `beta` branch → separate "Wavestr Beta" app as in Usus |
| About | As in Usus: "Built with AI · tested by humans", MIT license and warranty, links to GitHub and support with Bitcoin and Lightning (tap to copy) |
| Out | Top zappers |

---

## 2. Stack

- **Language and UI:** Kotlin, Jetpack Compose, Material 3 (light/dark/auto theme)
- **Player:** Media3 (ExoPlayer + HLS + `MediaLibraryService` for background and Android Auto)
- **Nostr:** minimal own client (OkHttp WebSocket + kotlinx-serialization). Cryptography is delegated to Amber; `secp256k1-kmp` only when needed (NWC, signature verification). See ADR 0003
- **Signing:** Amber (NIP-55); NIP-46 (bunker) as an alternative. **The nsec is never stored in the app**
- **Network:** OkHttp/Ktor for LNURL and Radio Browser
- **Local data:** DataStore (settings) from F0; Room when radios and favourites arrive (F3–F4)
- **Images:** Coil
- **Dependency injection:** manual container (`AppContainer`), no framework. See ADR 0002
- **minSdk:** 26 · **targetSdk:** 35
- **Languages:** resources `values/` (English, default), `values-es/`, `values-ca/` + per-app language (`locales_config` and AppCompat for Android < 13)

---

## 3. Architecture

**Today:** `:core` (shared logic in Kotlin/JVM), `:app` (Android) and `:desktop` (Linux and Windows, Compose Desktop + VLC). See ADR 0009. The long-term target structure:

```
:app                 → navigation, theme, screens
:feature:live        → live streams, streamer profile, chat
:feature:radio       → directory, station card, PLS/M3U import
:feature:favorites   → folders, moving, surprise
:feature:settings    → settings, wallet, Android Auto
:core:player         → PlaybackService (Media3), audio/video, quality
:core:nostr          → relays, signing, events, NWC, zaps
:core:radio          → Radio Browser client, PLS/M3U parser, ICY
:core:data           → Room, DataStore, repositories
```

**Flow:** relays / APIs → repositories → ViewModels (`StateFlow`) → Compose.

### Player (the central piece)

- **A single ExoPlayer** inside `PlaybackService` (`MediaLibraryService`). The UI connects with `MediaController`. The same service serves the phone, the lock screen and Android Auto.
- **Audio / video:** `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, …)` and removing or setting the surface. Without restarting the stream.
- **Detecting separate audio:** read the HLS master playlist. If there's `EXT-X-MEDIA:TYPE=AUDIO`, audio mode saves data; if not, only battery. The UI says so.
- **Screen off:** observe the lifecycle. If the setting is on, switch to audio and restore on return.
- **Live stream quality:** HLS variants through `TrackSelectionParameters` (1080p…360p).
- **Radio quality:** each format is a different URL, so changing format means changing `MediaItem` (a brief cut, acceptable).
- **Auto mode:** `ConnectivityManager`. On wifi, the best variant; on data, the lightest.
- **ICY metadata:** Media3 exposes it (`IcyInfo`) → "now playing" and history.

---

## 4. Nostr: events and NIPs

| Use | Kind / NIP |
|---|---|
| Profile, `lud16` | kind 0 · NIP-05 to verify |
| Follows | kind 3 |
| User relays | kind 10002 (NIP-65, outbox model) |
| Live stream | kind 30311 (NIP-53), `status=live` / `ended` → recordings |
| Live chat | kind 1311 |
| Zap request / receipt | kind 9734 / 9735 (NIP-57) |
| Wallet | NIP-47 (NWC) |
| Signing | NIP-55 (Amber) / NIP-46 |
| Favourite folders | kind 30003 (NIP-51, bookmark sets) |

### Folders in NIP-51

- One list per folder. The `d` tag stores the path (`work`, `work/lofi`) and `title` the visible name.
- Nostr live streams: `a` tag (reference to the 30311 or to the streamer's profile).
- Radios: `r` tag with the stream URL and the Radio Browser `stationuuid` as extra data.
- **Private by default:** items encrypted (NIP-44) in `content`. What each person listens to isn't public unless they choose so.

---

## 5. Radio

- **Directory:** Radio Browser public API.
  - Search by name, country, language and tag.
  - `order=random` for the "Worldwide" surprise button.
  - Report usage (*click* endpoint), as the API asks.
- **Quality variants:** Radio Browser lists each stream separately. They must be **grouped** by station (same name + website) with a heuristic. It's the most delicate point of this phase.
- **Import:** own PLS, M3U and M3U8 parser. Imported lists go into a folder.
- **Unencrypted http:** allowed only for audio streams through `network_security_config`.
- **Broken links:** mark them locally; option to report them to the directory.

---

## 6. Local data model (Room)

```
Station(id, name, country, lang, tags, homepage, source[PLS|M3U|M3U8|RB], rbUuid?)
StationVariant(id, stationId, url, codec, kbps)
LiveStreamCache(aTag, title, hostPubkey, status, streamingUrl, recordingUrl?, updatedAt)
Folder(id, name, parentId?, nostrD, isPrivate)
FavItem(id, folderId?, type[STATION|LIVE|PROFILE], ref)
QualityChoice(itemRef, mode[AUTO|FIXED], variantId?)
```

The source of truth for favourites is the NIP-51 lists. Room is the offline cache and the queue of changes waiting to be published.

---

## 6b. Languages

- **Languages:** Catalan (`ca`), Spanish (`es`) and English (`en`).
- **Default rule:** the device language if it's one of the three; otherwise English. Android resolves it on its own: `values/` is English and acts as the fallback.
- **Selector in Settings:** Device language / Català / Español / English. Uses Android 13+ per-app language (`android:localeConfig`) and AppCompat (`setApplicationLocales` + `autoStoreLocales`) on earlier versions.
- **Quality control:** `scripts/check_strings.py` fails in CI if `es` or `ca` is missing a string or has different placeholders (`%d`, `%s`).
- **Content:** the app's texts are translated; titles of live streams, profiles and stations are shown as they arrive.
- **HTML demo:** same rule (browser in ca/es → that language; anything else → English) and a selector in Settings.

---

## 7. Phases

Each phase ends with a usable APK.

**F0 · Base** ✅ *code written, pending first build in CI*
- Project, modules, light/dark/auto theme, navigation.
- ~~Choose the Nostr library~~ → own client (ADR 0003).
- Three languages and selector.
- Sign-in with Amber.
- *Done when:* you open the app, sign in and see your npub and your relays.

**F1 · Live streams (MVP)** 🟡 *code written: follows and global live streams, player with audio/video, screen off, video quality, profile and recordings. Still to test on a real phone*
- Read follows and `live` 30311 streams.
- Player with audio/video, background and screen off.
- Streamer profile and recordings.
- *Done when:* you listen to a live stream with the screen off and go back to video without a cut.

**F2 · Chat and zaps** 🟡 *in beta 0.3: NIP-57 zaps with NWC (NIP-47, own secp256k1/Schnorr), monthly limit, `lightning:` plan B, read-only chat and zaps. Writing in the chat is missing*
- 1311 chat.
- NIP-57 zaps: connect NWC with a monthly limit; `lightning:` plan B.
- Totals in sats.
- *Done when:* a zap shows up in the chat after the 9735 receipt is detected.

**F3 · Radios** 🟡 *in beta 0.2: directory, search, country, styles, quality variants, ICY and PLS/M3U import*
- Radio Browser, PLS/M3U import, station card.
- Selectable quality with Auto mode, ICY metadata.
- *Done when:* you change the format of a station with several variants and the choice is remembered.

**F4 · Favourites and surprise** 🟡 *local folders in beta 0.2; NIP-51 sync and the surprise button are missing*
- Folders and subfolders, move, rename, delete.
- Private NIP-51 sync.
- Surprise button and "Another similar one".
- *Done when:* you create a folder on one phone and it shows up on another.

**F5 · Android Auto**
- Navigation tree (Live / Favourites with folders / Radios), audio only.
- *Done when:* it works in the DHU emulator and in a car with "Unknown sources" turned on.

**F6 · Distribution**
- Signed APK on GitHub Releases (`release.yml` workflow when tagging `v*`), Obtainium and Zapstore.
- No F-Droid or Google Play for now (ADR 0005).

---

## 8. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Live streams with muxed audio and video | Audio mode doesn't save data | Detect it and say so in the UI |
| Slow or down relays | Incomplete live stream list | Outbox (NIP-65), cache, several default relays |
| Grouping Radio Browser variants | Badly grouped quality | Heuristic + manual correction by the user |
| `http` URLs and broken streams | Playback failures | Cleartext only for audio, mark broken ones, retry |
| Android Auto friction outside Play | Fewer users in the car | One-step guide in the app; Play later |
| NWC link security | Unauthorised spending | On-device encryption, monthly limit, revocable |

---

## 9. Decisions (previously open, closed on 27 Oct)

| # | Topic | Decision | ADR |
|---|---|---|---|
| 1 | Nostr library | Minimal own client + Amber to sign and encrypt | 0003 |
| 2 | Private or public folders | Private by default (encrypted NIP-51); a folder can be published | 0006 |
| 3 | PWA | Ruled out for now; only HTML demos of the UI | 0001 |
| 4 | Name | Wavestr. Pending: check domain and Zapstore before the first release | — |
| 5 | License | MIT | 0007 |
| 6 | Languages | ca/es/en, default the device's or English | 0004 |
| 7 | Distribution | GitHub Releases + Obtainium + Zapstore | 0005 |

All can be revisited: each ADR explains the reason and what would change it.

---

## 10. Repository

Proposal: `oluengo/wavestr`

```
wavestr/
├── app/src/main/java/io/github/oluengo/wavestr/
│   ├── nostr/       ← events, NIP-19/53/65, relays, Amber
│   ├── player/      ← PlaybackService, PlayerConnection, HLS
│   ├── data/        ← settings and language
│   └── ui/          ← live, player, profile, settings, theme
├── app/src/main/res/values{,-es,-ca}/strings.xml
├── app/src/test/    ← JVM tests (NIP-19, NIP-53, HLS…)
├── demo/index.html  ← HTML demo of the UI
├── docs/
│   ├── technical-plan.md
│   └── decisions/   ← one note per decision (ADR)
├── scripts/check_strings.py
└── .github/workflows/{android,release}.yml
```

---

## 11. Next steps

1. Test beta 0.3 on a real phone: zap with NWC (Alby Hub, Rizful…), zap with Amber, artwork and export/import.
2. Write in the chat (kind 1311 signed with Amber).
3. F4: surprise button and NIP-51 sync.
4. F5: Android Auto.

---

## Changes

- **v0.6 (27 Oct):** desktop version (Linux and Windows) and `:core` module (ADR 0009); adaptive layout on Android; surprise button; editable relays.
- **v0.5 (27 Oct):** the project goes from Radiostr to **Wavestr** (the name was taken), with a new logo (ring and wave) and violet as accent. Favourites on Nostr with encrypted NIP-78 (ADR 0008).
- **v0.4 (27 Oct):** F2 under way: NWC and zaps with own Kotlin cryptography instead of `secp256k1-kmp` (no native dependencies); artwork from the station's website; JSON/M3U favourites backup; accent colour (§10, §11).
- **v0.3 (27 Oct):** beta channel and "About" section with project support, as in Usus (§1).
- **v0.2 (27 Oct):** ca/es/en languages (§1, §2, §6b); open decisions closed with ADRs (§9); own Nostr client and manual container instead of Koin (§2); a single module to start (§3); Room postponed to F3; distribution without F-Droid or Play (§7); F0/F1 status; actual repo structure (§10).
- **v0.1 (26 Oct):** first version.
