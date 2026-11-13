# Changelog

## Web (new)

- **Web app and PWA** at <https://oluengo.github.io/wavestr/app/>: radio (https stations only), streams with video, chat, reactions and zaps (NWC, WebLN or QR), favourites with folders and encrypted sync, sharing and import, upcoming with reminders, volume boost and sleep timer.
- **Sign in** with a NIP-07 extension, a NIP-46 bunker or a read-only npub.
- **Shared links:** the link page also offers "Open on the web".

## Desktop (beta)

- **Desktop downloads in the beta:** Linux (`.deb`, `.tar.gz`) and Windows (`.msi`). Needs VLC.
- **On par with Android:** sign in with a bunker (NIP-46), chat with reactions and zaps to messages and to the stream (NWC or QR), encrypted favourites on Nostr (the same as on the phone), upcoming with reminders (system notification), sharing (link, M3U, text) and importing links with a preview, per-station volume boost, unified player with pinned video and full screen.

## 0.11.1

- **Favourites:** the note no longer says Nostr sync is coming later; if you don't sync, it tells you where to turn it on.
- **Player:** a little more space between the status bar and the now-playing bar.
- **Sign in:** the read-only text mentions Amber and bunker for chatting, reacting and sending zaps.

## 0.11.0

- **Unified player (radio and streams):** what's playing is always at the top (thumbnail, title and song or host), with the video below if there is one; the scrolling part in the middle (artwork, details, chat or similar stations); the same controls for everything at the bottom and, in live streams, the chat box.
- **Upcoming:** scheduled streams (NIP-53) and timed calendar events (NIP-52) from people you follow and your favourite streamers, in Streams and on each profile. With the time in your zone and, if the organiser is in another, theirs too.
- **Reminders:** alert when it starts or 5, 15 or 60 minutes before; tapping the alert plays the stream (or opens the profile if it hasn't started yet). They survive phone restarts.
- **Streams with video:** the video stays pinned at the top while you read or type in the chat. **Full screen** in landscape.
- **Reactions and zaps from the chat:** tap a message to react (NIP-25) or zap its author; you can also react to the stream. Counts are shown on each message.
- **Sign in with a bunker (NIP-46):** nsecBunker, nsec.app and other remote signers with a `bunker://` link. Signs chat, reactions, zaps and encrypted favourites, like Amber.
- **Volume boost** per station (+3 to +12 dB) for those that play quietly; remembered for each one.
- **Chat:** when typing, the keyboard no longer covers the text box; the player shrinks and keeps it in view.
- **Favourites:** tapping a streamer who is live plays their stream right away (previous/next goes through the other live favourites). If they're not live, their profile opens; the profile is also in their ⋮ menu.
- **Share** a station or streamer (from the player or its menu in Favourites), a folder with its subfolders, or all favourites (share button in Favourites: shares what you're viewing). Three formats:
  - **Link:** opens Wavestr and shows a preview to choose what to add; without the app, a web page with the list, the M3U download and how to install it. Everything is inside the link: it goes through no server.
  - **M3U playlist** for VLC and other players (radios only).
  - **Text** with the station names and addresses.
- When importing, a folder arrives as a folder; "all favourites" go into a new "… (shared)" folder. What's already in your favourites isn't duplicated.

## 0.10.0

- **New icon:** the ring with the wave, refreshed, with an amber → coral → magenta gradient on a dark background; also in the Android 13+ themed icon and the widgets.
- **Android Auto:** Wavestr shows up in the car with Favourites (and their folders), Streams that are live, Radios (popular and near you) and the last one played; voice search for stations and previous/next. Outside Google Play you need to turn on "Unknown sources" in Android Auto's developer settings (steps in the README).
- **Stream chat:** you can now write (signed with Amber).
- **Widgets:** previous/next on the bar and the player.
- **Settings reorganised:** a main screen with categories (Account, Zaps and wallet, Playback, Appearance and language, Favourites and backups, Relays, About) and a summary of each.

## 0.9.0

- **Previous / next** also for Nostr streams (through the Streams list) and when reopening the app with a radio playing. The buttons are always visible; they're dimmed when there's nowhere to go.
- **Desktop:** the Linux and Windows version is removed from the downloads while it's being finished.
- **Relays:** the lists in Settings → Relays collapse and expand (closed by default, with the number of relays visible).
- **Previous / next station** (phone and desktop): if the radio is in favourites, it jumps between the favourites in its folder (or all of them); if not, through the list you picked it from (popular, near you, style, search, similar). On Android also from the notification, lock screen, headphones and car; on desktop, from the tray.
- **Buffer against dropouts** (Settings → Playback, phone and desktop): Auto, Live, 10, 20 or 30 s. More buffer means fewer dropouts with bad coverage, at the cost of being a few seconds behind live; the radio shows "Loading 12/20 s" while it fills. Auto: live on wifi and 20 s on mobile data. Default: 10 s.
- **Favourites:** Nostr streamers show whether they're live ("LIVE") or not ("Offline"), on phone and desktop. In the favourites widget, a red dot marks those who are live. Checked when opening Favourites and every minute.

## 0.8.0

- **Home screen widgets (Android):** Bar (4×1), Player (4×2), Favourites (4×2, with the folder you choose) and Square (2×2). Play/pause, surprise and favourites without opening the app; if nothing is playing, play starts the last station. They follow the phone's theme and your accent colour.

## 0.7.0

- **Sleep timer** (Android and desktop): 15, 30, 45, 60 or 90 minutes, with the volume fading in the last 30 seconds; "+10 min" and cancel. With a Nostr stream, also "when the stream ends".
- **"Directos" is renamed "Streams"** in Spanish and Catalan: radio is live too; streams are the Nostr ones.

## 0.6.0

- **Wavestr for Linux and Windows** (beta): `.deb`, `.tar.gz` and `.msi`. Adaptive window (phone layout, side navigation or three columns with the player pinned), tray icon to keep listening with the window closed, size and position remembered. Live streams, radios (with PLS/M3U import), favourites with folders, surprise, relays, backups to move favourites from the phone. Plays with VLC. Sign in with npub (read-only) for now.

## 0.5.0

- **Tablets and phone in landscape:** side navigation; on wide screens the player stays pinned on the right with the chat and zaps.
- **Similar stations** below the radio player, by style.
- **Surprise button** (the die, at the top of Live, Radios and Favourites): radio, stream or both; from your favourites or from everything. Remembers your choice.
- **Editable Wavestr relays** in Settings → Relays: add the ones you want (e.g. those of a streaming platform), remove them or go back to the defaults. Your NIP-65 list is shown but not touched.

## 0.4.0

- **Radiostr is renamed Wavestr** (the name was already taken). New logo: a ring crossed by a wave. It's a different Android app: install Wavestr and, if you had Radiostr, export your favourites first (Settings → Data and backups) and import them in Wavestr.
- **Favourites on Nostr:** after signing in with Amber, the app asks whether to store them on Nostr, encrypted so only you can read them. They sync automatically and can be restored on another phone with your account (Settings → Favourites on Nostr).
- **Violet** as the default accent colour (also in the icon).

## 0.3.0

- **Zaps with NWC:** connect your wallet (Nostr Wallet Connect) in Settings → Zaps and send zaps to live streams from the player. With Amber, the zap is signed by you; without a session, it's anonymous. With no wallet connected, your Lightning app opens or the invoice is copied.
- **Monthly zap limit** and default amount. The NWC connection is stored encrypted with the Android keystore and never goes into backups.
- **Stream chat and zaps** visible in the player (read-only for now), with the sats total.
- **Station artwork:** if the directory has no logo, it's looked up on the station's website (icon, apple-touch-icon, og:image) and saved.
- **Export and import favourites:** full JSON backup (folders included) or M3U playlist; on import, it's merged with what you already have.
- **Accent colour** to choose: green, violet, coral, blue or raspberry.

## 0.2.0

- **Your Nostr profile:** your avatar at the top of each tab; tap it for your name, NIP-05, follows, relays, npub and Lightning address. Wavestr reads it, it doesn't change it.
- **Online radio** with the free Radio Browser directory: popular, by country (the phone's by default), by style and search.
- Stations with several URLs are grouped and their **quality** is chosen in the player (Auto: the best on wifi, the lightest on mobile data).
- **Now-playing song** (ICY metadata) and a warning when a station doesn't respond, with the option to try another format.
- **Import PLS/M3U:** paste a URL, a link to the playlist or its contents, or pick a file.
- **Favourites in folders and subfolders:** radios and Nostr streamers; move, rename and delete (contents move up to the parent folder). Stored on the phone.

## 0.1.0

- Nostr live streams (NIP-53) from people you follow or from all of Nostr.
- Audio-only or video without interrupting the stream; automatic audio with the screen off and on mobile data.
- Selectable video quality.
- Streamer profile with their recordings.
- Sign in with Amber or with your npub (read-only).
- Catalan, Spanish and English; light, dark or automatic theme.
- About: license, project links and ways to support it.
