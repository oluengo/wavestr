# 0001 · Native Android; PWA ruled out for now

*Status: accepted · 27 Oct 2026*

## Decision

Kotlin + Jetpack Compose + Media3. No production PWA; the web is only used for UI demos.

## Reason

- "Audio only with the screen off" needs a foreground service (`MediaSessionService`); in the Android browser, video is paused or cut off in the background.
- Android Auto requires `MediaLibraryService`.
- Many radios stream over `http://` and without CORS: a PWA on https can't play them.

## What would change it

If a web version brings users the app doesn't reach, a lightweight PWA with only Nostr live streams would be built.
