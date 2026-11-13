# Wavestr development

## Building

Requirements: JDK 17, Android SDK 35 (for `:app`) and VLC (to try `:desktop`).

Modules: `:core` (Nostr, cryptography, radio, favourites; Kotlin/JVM without Android), `:app` (Android) and `:desktop` (Compose Desktop + VLC). The desktop app uses the texts in `app/src/main/res/values*/strings.xml`.

```bash
./gradlew :core:test testDebugUnitTest  # tests
./gradlew assembleDebug                 # APK in app/build/outputs/apk/debug/
./gradlew :desktop:run                  # opens the desktop app
./gradlew :desktop:packageDeb           # .deb (on Linux) · :desktop:packageMsi on Windows
python3 scripts/check_strings.py        # checks the translations
cd web && npm install && npm run dev    # web app (TypeScript + Svelte) locally
```

The web app lives in `web/` (Vite + Svelte + nostr-tools) and uses the same texts from `strings.xml`. Every push to `main` that touches it builds it and publishes it on Pages ([`pages.yml`](../.github/workflows/pages.yml)).

GitHub Actions ([`android.yml`](../.github/workflows/android.yml)):

- **Push to `main`:** tests, translations, APK (and, for testing, the desktop installers) in "Artifacts", an APK startup test on an Android emulator and another for the desktop app (xvfb + VLC: radio and HLS video). The beta and releases are only published if all of that passes.
- **Regular release:** on GitHub, Actions → Build → *Run workflow* (branch `main`) → tick "Publish release". It creates the tag `vX.Y.Z` (from `wavestrVersion`) and the Release with the signed APK and the notes from [CHANGELOG.md](../CHANGELOG.md). Obtainium and Zapstore pick it up from there. Pushing a `v*` tag also works.
- **Test version (beta):** every push to the `beta` branch publishes the **Wavestr Beta** pre-release (`X.Y.Z-beta.N`). It installs as a separate app (`io.github.oluengo.wavestr.beta`), next to the regular one and with its own data. In Obtainium it's added separately with "include pre-releases" on. When the beta is good, it's merged into `main` and the regular version is published.

Signing: secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` (same names as in Usus; the key belongs to this project; its alias is `radiostr`, from the previous name, and doesn't need changing). Without them a debug APK is built and nothing is published.

## Translating

The texts are in `app/src/main/res/values/strings.xml` (English), `values-es/` and `values-ca/`. To add a language, follow the steps in [ADR 0004](decisions/0004-languages.md).

## Documentation

- [Technical plan](technical-plan.md)
- [Decisions (ADR)](decisions/README.md)
