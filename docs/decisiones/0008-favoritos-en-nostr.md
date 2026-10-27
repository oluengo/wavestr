# 0008 · Favoritos en Nostr: un solo evento cifrado (NIP-78)

*Estado: aceptada · 27 oct 2026 · sustituye a la 0006 en el formato; mantiene «privado por defecto»*

## Decisión

Toda la biblioteca (carpetas, subcarpetas, radios, streamers e importadas) se guarda en **un evento reemplazable kind 30078** (NIP-78, datos de app) con `d = radiostr/favorites`, cifrado con **NIP-44 para uno mismo** por Amber.

- Es opcional: tras entrar con Amber, la app **pregunta** una vez; se cambia en Ajustes.
- Sincroniza sola (al abrir y 4 s después de cada cambio) si Amber dio permiso «siempre»; si no, queda pendiente y se sincroniza a mano.
- Si solo cambió un lado, gana ese lado (también los borrados). Si cambiaron los dos, se combinan sin perder nada.

## Motivo

- Las listas NIP-51 no tienen carpetas anidadas ni un sitio natural para una radio (URL de stream, variantes, logo).
- Un solo evento: una firma y un cifrado por cambio, en vez de uno por carpeta.
- Nadie más necesita leer estos datos: NIP-78 es justo para eso.

## Límites

- NIP-44 cifra hasta 64 KB: unos 150–200 favoritos de radio. Si se supera, se avisa.
- Con sesión de solo lectura (npub) no se puede cifrar: no hay sincronización.

## Qué la haría cambiar

Si se quiere compartir carpetas con otros clientes, publicar además las carpetas marcadas como públicas como NIP-51 (kind 30003).
