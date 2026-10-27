# Radiostr · Plan técnico v0.4

*27 oct 2026 · v0.1 → v0.2: idiomas, decisiones abiertas cerradas, estado de las fases. Ver «Cambios» al final.*

Radio online (PLS/M3U) y directos de Nostr en una sola app Android. **Nostr first**: primero los directos de la gente que sigues; la radio llega después sobre el mismo reproductor.

---

## 1. Decisiones ya tomadas

| Tema | Decisión |
|---|---|
| Prioridad | Nostr first: directos NIP-53 → zaps → radios |
| Plataforma | Android nativo. La PWA queda como opcional (limitaciones de http/CORS y de segundo plano) |
| Reproducción | Selector Solo audio / Vídeo sin cortar el stream. Paso automático a audio con pantalla apagada |
| Android Auto | Solo audio. Sin vídeo ni zaps en el coche |
| Zaps | NIP-57 + NWC (NIP-47). Plan B: abrir la cartera externa con `lightning:`. **La app nunca custodia fondos** |
| Favoritos | Carpetas y subcarpetas sincronizadas como listas Nostr (NIP-51) |
| Calidad | Visible siempre. Seleccionable cuando hay varias variantes. Modo Auto según wifi/datos |
| Extras | Botón sorpresa (favoritas / del mundo), perfiles de streamer, ficha de emisora, tema claro/oscuro/auto |
| Idiomas | Català, castellano e inglés. Por defecto, el idioma del dispositivo; si no es ninguno de los tres, inglés. Selector en Ajustes |
| Distribución | GitHub Releases + Obtainium + Zapstore. Sin Google Play ni F-Droid por ahora. Rama `beta` → app aparte «Radiostr Beta» como en Usus |
| Acerca de | Como Usus: «Hecha con IA · probada por humanos», licencia MIT y garantías, enlaces a GitHub y apoyo con Bitcoin y Lightning (tocar para copiar) |
| Fuera | Top zappers |

---

## 2. Stack

- **Lenguaje y UI:** Kotlin, Jetpack Compose, Material 3 (tema claro/oscuro/auto)
- **Reproductor:** Media3 (ExoPlayer + HLS + `MediaLibraryService` para segundo plano y Android Auto)
- **Nostr:** cliente propio mínimo (OkHttp WebSocket + kotlinx-serialization). La criptografía se delega en Amber; `secp256k1-kmp` solo cuando haga falta (NWC, verificación de firmas). Ver ADR 0003
- **Firma:** Amber (NIP-55); NIP-46 (bunker) como alternativa. **No se guarda la nsec en la app**
- **Red:** OkHttp/Ktor para LNURL y Radio Browser
- **Datos locales:** DataStore (ajustes) desde F0; Room cuando lleguen radios y favoritos (F3–F4)
- **Imágenes:** Coil
- **Inyección de dependencias:** contenedor manual (`AppContainer`), sin framework. Ver ADR 0002
- **minSdk:** 26 · **targetSdk:** 35
- **Idiomas:** recursos `values/` (inglés, por defecto), `values-es/`, `values-ca/` + idioma por app (`locales_config` y AppCompat para Android < 13)

---

## 3. Arquitectura

Por ahora **un único módulo `:app`** con paquetes por capa; se separará en módulos cuando crezca (ADR 0002). La estructura objetivo sigue siendo esta:

```
:app                 → navegación, tema, pantallas
:feature:live        → directos, perfil del streamer, chat
:feature:radio       → directorio, ficha de emisora, importación PLS/M3U
:feature:favorites   → carpetas, mover, sorpresa
:feature:settings    → ajustes, cartera, Android Auto
:core:player         → PlaybackService (Media3), audio/vídeo, calidad
:core:nostr          → relays, firma, eventos, NWC, zaps
:core:radio          → cliente Radio Browser, parser PLS/M3U, ICY
:core:data           → Room, DataStore, repositorios
```

**Flujo:** relays / APIs → repositorios → ViewModels (`StateFlow`) → Compose.

### Reproductor (pieza central)

- **Un solo ExoPlayer** dentro de `PlaybackService` (`MediaLibraryService`). La UI se conecta con `MediaController`. El mismo servicio sirve al móvil, a la pantalla bloqueada y a Android Auto.
- **Audio / vídeo:** `setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, …)` y quitar o poner la superficie. Sin reiniciar el stream.
- **Detectar audio separado:** leer el master playlist HLS. Si hay `EXT-X-MEDIA:TYPE=AUDIO`, el modo audio ahorra datos; si no, solo batería. La UI lo avisa.
- **Pantalla apagada:** observar el ciclo de vida. Si el ajuste está activo, pasar a audio y restaurar al volver.
- **Calidad en directos:** variantes HLS mediante `TrackSelectionParameters` (1080p…360p).
- **Calidad en radios:** cada formato es una URL distinta, así que cambiar de formato implica cambiar de `MediaItem` (corte breve, aceptable).
- **Modo Auto:** `ConnectivityManager`. Con wifi, la mejor variante; con datos, la más ligera.
- **Metadatos ICY:** Media3 los expone (`IcyInfo`) → "sonando ahora" e historial.

---

## 4. Nostr: eventos y NIPs

| Uso | Kind / NIP |
|---|---|
| Perfil, `lud16` | kind 0 · NIP-05 para verificar |
| Seguidos | kind 3 |
| Relays del usuario | kind 10002 (NIP-65, modelo outbox) |
| Directo | kind 30311 (NIP-53), `status=live` / `ended` → grabaciones |
| Chat del directo | kind 1311 |
| Zap request / recibo | kind 9734 / 9735 (NIP-57) |
| Cartera | NIP-47 (NWC) |
| Firma | NIP-55 (Amber) / NIP-46 |
| Carpetas de favoritos | kind 30003 (NIP-51, bookmark sets) |

### Carpetas en NIP-51

- Una lista por carpeta. La etiqueta `d` guarda la ruta (`trabajo`, `trabajo/lofi`) y `title` el nombre visible.
- Directos Nostr: etiqueta `a` (referencia al 30311 o al perfil del streamer).
- Radios: etiqueta `r` con la URL del stream y el `stationuuid` de Radio Browser como dato adicional.
- **Privadas por defecto:** elementos cifrados (NIP-44) en `content`. Lo que escucha cada uno no es público salvo que lo elija.

---

## 5. Radio

- **Directorio:** API pública de Radio Browser.
  - Búsqueda por nombre, país, idioma y etiqueta.
  - `order=random` para el botón sorpresa "Del mundo".
  - Avisar del uso (endpoint de *click*), como pide la API.
- **Variantes de calidad:** Radio Browser lista cada stream por separado. Hay que **agruparlos** por emisora (mismo nombre + web) con una heurística. Es el punto más delicado de esta fase.
- **Importación:** parser propio de PLS, M3U y M3U8. Las listas importadas van a una carpeta.
- **http sin cifrar:** permitido solo para streams de audio mediante `network_security_config`.
- **Enlaces caídos:** marcarlos localmente; opción de avisar al directorio.

---

## 6. Modelo de datos local (Room)

```
Station(id, name, country, lang, tags, homepage, source[PLS|M3U|M3U8|RB], rbUuid?)
StationVariant(id, stationId, url, codec, kbps)
LiveStreamCache(aTag, title, hostPubkey, status, streamingUrl, recordingUrl?, updatedAt)
Folder(id, name, parentId?, nostrD, isPrivate)
FavItem(id, folderId?, type[STATION|LIVE|PROFILE], ref)
QualityChoice(itemRef, mode[AUTO|FIXED], variantId?)
```

La fuente de verdad de los favoritos son las listas NIP-51. Room es la caché offline y la cola de cambios pendientes de publicar.

---

## 6b. Idiomas

- **Idiomas:** català (`ca`), castellano (`es`) e inglés (`en`).
- **Regla por defecto:** idioma del dispositivo si es uno de los tres; si no, inglés. Lo resuelve Android solo: `values/` es inglés y actúa de respaldo.
- **Selector en Ajustes:** Idioma del dispositivo / Català / Español / English. Usa el idioma por app de Android 13+ (`android:localeConfig`) y AppCompat (`setApplicationLocales` + `autoStoreLocales`) en versiones anteriores.
- **Control de calidad:** `scripts/check_strings.py` falla en CI si a `es` o `ca` le falta una cadena o tiene distintos marcadores (`%d`, `%s`).
- **Contenido:** los textos de la app se traducen; los títulos de directos, perfiles y emisoras se muestran tal cual llegan.
- **Demo HTML:** misma regla (navegador en ca/es → ese idioma; otro → inglés) y selector en Ajustes.

---

## 7. Fases

Cada fase termina con un APK usable.

**F0 · Base** ✅ *código escrito, pendiente de primera compilación en CI*
- Proyecto, módulos, tema claro/oscuro/auto, navegación.
- ~~Elegir la librería Nostr~~ → cliente propio (ADR 0003).
- Tres idiomas y selector.
- Login con Amber.
- *Hecho cuando:* abres la app, inicias sesión y ves tu npub y tus relays.

**F1 · Directos (MVP)** 🟡 *código escrito: directos de seguidos y global, reproductor con audio/vídeo, pantalla apagada, calidad de vídeo, perfil y grabaciones. Falta probar en móvil real*
- Leer seguidos y directos 30311 `live`.
- Reproductor con audio/vídeo, segundo plano y pantalla apagada.
- Perfil del streamer y grabaciones.
- *Hecho cuando:* escuchas un directo con la pantalla apagada y vuelves al vídeo sin corte.

**F2 · Chat y zaps** 🟡 *en la beta 0.3: zaps NIP-57 con NWC (NIP-47, secp256k1/Schnorr propios), límite mensual, plan B `lightning:`, chat y zaps en lectura. Falta escribir en el chat*
- Chat 1311.
- Zaps NIP-57: conectar NWC con límite mensual; plan B `lightning:`.
- Totales en sats.
- *Hecho cuando:* un zap aparece en el chat tras detectarse el recibo 9735.

**F3 · Radios** 🟡 *en la beta 0.2: directorio, búsqueda, país, estilos, variantes de calidad, ICY e importación PLS/M3U*
- Radio Browser, importación PLS/M3U, ficha de emisora.
- Calidad seleccionable con modo Auto, metadatos ICY.
- *Hecho cuando:* cambias de formato una emisora con varias variantes y se recuerda la elección.

**F4 · Favoritos y sorpresa** 🟡 *carpetas locales en la beta 0.2; falta la sincronización NIP-51 y el botón sorpresa*
- Carpetas y subcarpetas, mover, renombrar, eliminar.
- Sincronización NIP-51 privada.
- Botón sorpresa y "Otra parecida".
- *Hecho cuando:* creas una carpeta en un móvil y aparece en otro.

**F5 · Android Auto**
- Árbol de navegación (En directo / Favoritos con carpetas / Radios), solo audio.
- *Hecho cuando:* funciona en el emulador DHU y en un coche con "Fuentes desconocidas" activado.

**F6 · Distribución**
- APK firmado en GitHub Releases (workflow `release.yml` al etiquetar `v*`), Obtainium y Zapstore.
- Sin F-Droid ni Google Play por ahora (ADR 0005).

---

## 8. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Directos con audio y vídeo mezclados | El modo audio no ahorra datos | Detectarlo y avisar en la UI |
| Relays lentos o caídos | Lista de directos incompleta | Outbox (NIP-65), caché, varios relays por defecto |
| Agrupar variantes de Radio Browser | Calidad mal agrupada | Heurística + corrección manual del usuario |
| URLs `http` y streams rotos | Fallos de reproducción | Cleartext solo para audio, marcar caídos, reintento |
| Fricción de Android Auto fuera de Play | Menos usuarios en el coche | Guía de un paso en la app; Play más adelante |
| Seguridad del enlace NWC | Gasto no autorizado | Cifrado en el dispositivo, límite mensual, revocable |

---

## 9. Decisiones (antes abiertas, cerradas el 27 oct)

| # | Tema | Decisión | ADR |
|---|---|---|---|
| 1 | Librería Nostr | Cliente propio mínimo + Amber para firmar y cifrar | 0003 |
| 2 | Carpetas privadas o públicas | Privadas por defecto (NIP-51 cifradas); se podrá publicar una carpeta | 0006 |
| 3 | PWA | Descartada por ahora; solo demos HTML de la UI | 0001 |
| 4 | Nombre | Radiostr. Pendiente: comprobar dominio y Zapstore antes de la primera release | — |
| 5 | Licencia | MIT | 0007 |
| 6 | Idiomas | ca/es/en, por defecto el del dispositivo o inglés | 0004 |
| 7 | Distribución | GitHub Releases + Obtainium + Zapstore | 0005 |

Todas se pueden revisar: cada ADR explica el motivo y qué haría cambiarla.

---

## 10. Repositorio

Propuesta: `oluengo/radiostr`

```
radiostr/
├── app/src/main/java/io/github/oluengo/radiostr/
│   ├── nostr/       ← eventos, NIP-19/53/65, relays, Amber
│   ├── player/      ← PlaybackService, PlayerConnection, HLS
│   ├── data/        ← ajustes e idioma
│   └── ui/          ← live, player, profile, settings, theme
├── app/src/main/res/values{,-es,-ca}/strings.xml
├── app/src/test/    ← tests JVM (NIP-19, NIP-53, HLS…)
├── demo/index.html  ← demo HTML de la UI
├── docs/
│   ├── plan-tecnico.md
│   └── decisiones/  ← una nota por decisión (ADR)
├── scripts/check_strings.py
└── .github/workflows/{android,release}.yml
```

---

## 11. Próximos pasos

1. Probar en móvil real la beta 0.3: zap con NWC (Alby Hub, Rizful…), zap con Amber, carátulas y exportar/importar.
2. Escribir en el chat (kind 1311 firmado con Amber).
3. F4: botón sorpresa y sincronización NIP-51.
4. F5: Android Auto.

---

## Cambios

- **v0.4 (27 oct):** F2 en marcha: NWC y zaps con criptografía propia en Kotlin en vez de `secp256k1-kmp` (sin dependencias nativas); carátulas desde la web de la emisora; copia de favoritos JSON/M3U; color de acento (§10, §11).
- **v0.3 (27 oct):** canal beta y sección «Acerca de» con apoyo al proyecto, como en Usus (§1).
- **v0.2 (27 oct):** idiomas ca/es/en (§1, §2, §6b); decisiones abiertas cerradas con ADR (§9); cliente Nostr propio y contenedor manual en vez de Koin (§2); un solo módulo para empezar (§3); Room aplazado a F3; distribución sin F-Droid ni Play (§7); estado de F0/F1; estructura real del repo (§10).
- **v0.1 (26 oct):** primera versión.
