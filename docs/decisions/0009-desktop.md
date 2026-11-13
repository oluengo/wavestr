# 0009 · Desktop with Compose Desktop and VLC; logic in `:core`

*Status: accepted · 27 Oct 2026 · updates 0001 (native Android) and 0002 (single module)*

## Decision

- Logic without Android (Nostr, cryptography, NWC, radio, favourites) moves to a **`:core`** Kotlin/JVM module, used by Android and desktop.
- The desktop app (**`:desktop`**) is a **Compose Desktop** app for Linux and Windows, with its own screen layer (same design, same colours) and **the texts from Android's `strings.xml`**, read at runtime.
- Playback with **libVLC (vlcj)**; video is painted in Compose frame by frame, with no separate window.
- Installers with jpackage: `.deb` and `.tar.gz` (Linux), `.msi` (Windows), in the same release or pre-release as the APK.

## Reason

- Truly sharing the screens (Compose Multiplatform on Android) means redoing resources, navigation, player, Amber, intents and services all at once: a lot of risk for an app that already works. With `:core` the hard part (protocols and crypto) is shared and Android doesn't change.
- VLC plays everything that comes from Radio Browser and Nostr (MP3, AAC, Ogg, HLS, video) and provides the now-playing song (ICY). The JVM alternatives (JavaFX Media, GStreamer) cover fewer formats.

## Limits

- VLC must be installed; the app warns if it can't find it.
- There's no Amber on desktop: for now, a read-only npub session (no zaps or favourites on Nostr) until NIP-46 is added.
- There are two screen layers to maintain.

## What would change it

If the two interfaces diverge too much, migrate `:app` to Compose Multiplatform and share the screens too.
