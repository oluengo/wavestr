# 0003 · Cliente Nostr propio y firma con Amber

*Estado: aceptada · 27 oct 2026*

## Decisión

Cliente mínimo propio (OkHttp WebSocket + kotlinx-serialization) para leer de relays. Firma de eventos y cifrado NIP-44 delegados en Amber (NIP-55). La app no guarda la nsec.

## Motivo

- Para F1 solo hace falta leer (kinds 0, 3, 10002, 30311): REQ/EVENT/EOSE es poco código y fácil de testear.
- `rust-nostr` añade librerías nativas por ABI (más peso en el APK); Quartz arrastra mucho de Amethyst.
- Amber ya ofrece `sign_event`, `nip44_encrypt` y `nip44_decrypt`.

## Qué la haría cambiar

NWC (NIP-47) necesita firmar con la clave de la conexión: ahí se añadirá `secp256k1-kmp`. Si el cliente propio crece demasiado (outbox completo, NIP-42…), se reevalúa `rust-nostr`.
