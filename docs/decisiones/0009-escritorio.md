# 0009 · Escritorio con Compose Desktop y VLC; lógica en `:core`

*Estado: aceptada · 27 oct 2026 · actualiza la 0001 (Android nativo) y la 0002 (un módulo)*

## Decisión

- La lógica sin Android (Nostr, criptografía, NWC, radio, favoritos) pasa a un módulo **`:core`** en Kotlin/JVM, que usan Android y escritorio.
- El escritorio (**`:desktop`**) es una app **Compose Desktop** para Linux y Windows, con su propia capa de pantallas (mismo diseño, mismos colores) y los **textos de los `strings.xml` de Android**, leídos en tiempo de ejecución.
- Reproduce con **libVLC (vlcj)**; el vídeo se pinta en Compose fotograma a fotograma, sin ventana aparte.
- Instaladores con jpackage: `.deb` y `.tar.gz` (Linux), `.msi` (Windows), en la misma release o pre-release que el APK.

## Motivo

- Compartir las pantallas de verdad (Compose Multiplatform en Android) obliga a rehacer recursos, navegación, reproductor, Amber, intents y servicios a la vez: mucho riesgo para la app que ya funciona. Con `:core` se comparte lo difícil (protocolos y cripto) y Android no cambia.
- VLC reproduce todo lo que llega de Radio Browser y de Nostr (MP3, AAC, Ogg, HLS, vídeo) y da la canción que suena (ICY). Las alternativas en la JVM (JavaFX Media, GStreamer) cubren menos formatos.

## Límites

- Hace falta tener VLC instalado; la app avisa si no lo encuentra.
- En el escritorio no hay Amber: de momento, sesión con npub en solo lectura (sin zaps ni favoritos en Nostr) hasta añadir NIP-46.
- Hay dos capas de pantallas que mantener.

## Qué la haría cambiar

Si las dos interfaces divergen demasiado, migrar `:app` a Compose Multiplatform y compartir también las pantallas.
