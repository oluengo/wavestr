# 0001 · Android nativo; PWA descartada por ahora

*Estado: aceptada · 27 oct 2026*

## Decisión

Kotlin + Jetpack Compose + Media3. Sin PWA de producción; la web se usa solo para demos de la UI.

## Motivo

- El modo «solo audio con la pantalla apagada» necesita un servicio en primer plano (`MediaSessionService`); en el navegador Android pausa o recorta el vídeo en segundo plano.
- Android Auto exige `MediaLibraryService`.
- Muchas radios emiten por `http://` y sin CORS: una PWA en https no puede reproducirlas.

## Qué la haría cambiar

Si una versión web aporta usuarios que la app no alcanza, se haría una PWA ligera de solo directos Nostr.
