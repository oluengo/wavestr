# 0008 · Favourites on Nostr: a single encrypted event (NIP-78)

*Status: accepted · 27 Oct 2026 · supersedes 0006 in its format; keeps "private by default"*

## Decision

The whole library (folders, subfolders, radios, streamers and imported items) is stored in **one replaceable kind 30078 event** (NIP-78, app data) with `d = wavestr/favorites`, encrypted with **NIP-44 to oneself** by Amber.

- It's optional: after signing in with Amber, the app **asks** once; it can be changed in Settings.
- It syncs automatically (on opening and 4 s after each change) if Amber granted "always" permission; otherwise it stays pending and is synced by hand.
- If only one side changed, that side wins (deletions too). If both changed, they are merged without losing anything.

## Reason

- NIP-51 lists have no nested folders and no natural place for a radio (stream URL, variants, logo).
- A single event: one signature and one encryption per change, instead of one per folder.
- Nobody else needs to read this data: NIP-78 is exactly for that.

## Limits

- NIP-44 encrypts up to 64 KB: about 150–200 radio favourites. A warning is shown if it's exceeded.
- With a read-only session (npub) nothing can be encrypted: no sync.

## What would change it

If folders should be shared with other clients, also publish the folders marked public as NIP-51 (kind 30003).
