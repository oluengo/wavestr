# 0003 · Own Nostr client and signing with Amber

*Status: accepted · 27 Oct 2026*

## Decision

A minimal own client (OkHttp WebSocket + kotlinx-serialization) to read from relays. Event signing and NIP-44 encryption delegated to Amber (NIP-55). The app doesn't store the nsec.

## Reason

- F1 only needs reading (kinds 0, 3, 10002, 30311): REQ/EVENT/EOSE is little code and easy to test.
- `rust-nostr` adds native libraries per ABI (a heavier APK); Quartz pulls in a lot of Amethyst.
- Amber already offers `sign_event`, `nip44_encrypt` and `nip44_decrypt`.

## What would change it

NWC (NIP-47) needs signing with the connection's key: `secp256k1-kmp` will be added for that. If the own client grows too much (full outbox, NIP-42…), `rust-nostr` will be reconsidered.
