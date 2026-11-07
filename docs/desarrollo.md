# Desarrollo de Wavestr

## Compilar

Requisitos: JDK 17, Android SDK 35 (para `:app`) y VLC (para probar `:desktop`).

Módulos: `:core` (Nostr, criptografía, radio, favoritos; Kotlin/JVM sin Android), `:app` (Android) y `:desktop` (Compose Desktop + VLC). El escritorio usa los textos de `app/src/main/res/values*/strings.xml`.

```bash
./gradlew :core:test testDebugUnitTest  # tests
./gradlew assembleDebug                 # APK en app/build/outputs/apk/debug/
./gradlew :desktop:run                  # abre la app de escritorio
./gradlew :desktop:packageDeb           # .deb (en Linux) · :desktop:packageMsi en Windows
python3 scripts/check_strings.py        # comprueba las traducciones
cd web && npm install && npm run dev    # app web (TypeScript + Svelte) en local
```

La app web está en `web/` (Vite + Svelte + nostr-tools) y usa los mismos textos de `strings.xml`. Cada push a `main` que la toque la compila y la publica en Pages ([`pages.yml`](../.github/workflows/pages.yml)).

GitHub Actions ([`android.yml`](../.github/workflows/android.yml)):

- **Push a `main`:** tests, traducciones, APK (y, para pruebas, los instaladores de escritorio) en «Artifacts», una prueba de arranque del APK en un emulador Android y otra del escritorio (xvfb + VLC: radio y vídeo HLS). La beta y las versiones solo se publican si todo eso pasa.
- **Versión normal:** en GitHub, Actions → Build → *Run workflow* (rama `main`) → marcar «Publicar versión». Crea la etiqueta `vX.Y.Z` (la de `wavestrVersion`) y la Release con el APK firmado y las notas de [CHANGELOG.md](../CHANGELOG.md). De ahí lo recogen Obtainium y Zapstore. También sirve empujar una etiqueta `v*`.
- **Versión de pruebas (beta):** cada push a la rama `beta` publica la pre-release **Wavestr Beta** (`X.Y.Z-beta.N`). Se instala como otra app (`io.github.oluengo.wavestr.beta`), junto a la normal y con sus propios datos. En Obtainium se añade aparte activando «incluir pre-releases». Cuando la beta está bien, se pasa a `main` y se publica la versión normal.

Firma: secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` (mismos nombres que en Usus; la clave es propia del proyecto; su alias es `radiostr`, del nombre anterior, y no hace falta cambiarlo). Sin ellos se genera un APK de depuración y no se publica nada.

## Traducir

Los textos están en `app/src/main/res/values/strings.xml` (inglés), `values-es/` y `values-ca/`. Para añadir un idioma, sigue los pasos del [ADR 0004](decisiones/0004-idiomas.md).

## Documentación

- [Plan técnico](plan-tecnico.md)
- [Decisiones (ADR)](decisiones/README.md)
