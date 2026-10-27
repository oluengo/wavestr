# 0006 · Carpetas de favoritos privadas por defecto

*Estado: sustituida por la [0008](0008-favoritos-en-nostr.md) en el formato · 27 oct 2026*

## Decisión

Cada carpeta es una lista NIP-51 (kind 30003) con los elementos cifrados (NIP-44) en `content`. Se podrá marcar una carpeta como pública.

## Motivo

- Lo que escucha cada uno no debería ser público sin elegirlo.
- El cifrado lo hace Amber; los relays guardan la lista sin poder leerla.

## Qué la haría cambiar

Si compartir listas resulta la función estrella, cambiar el valor por defecto a pública.
