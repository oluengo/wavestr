# Radiostr

Radio online y directos de Nostr en una sola app Android.
*Online radio and Nostr live streams in one Android app. Català · Español · English.*

> Estado: **v0.1 en desarrollo**. F0 (base) y F1 (directos) escritos; aún sin release.

## Qué hace

- **Directos de Nostr (NIP-53)** de la gente que sigues, o de todo Nostr.
- **Solo audio o vídeo**, sin cortar el directo. Pasa a audio sola al apagar la pantalla y vuelve al vídeo cuando regresas.
- **Solo audio con datos móviles** (opcional) y calidad de vídeo seleccionable.
- **Perfil del streamer** con sus grabaciones.
- **Tres idiomas:** català, castellano e inglés. Usa el del dispositivo; si no es ninguno de ellos, inglés.
- **Tema** claro, oscuro o automático.
- Inicio de sesión con **Amber** (NIP-55) o con tu **npub** en solo lectura. La app nunca ve tu clave privada.

**Próximamente:** zaps con NWC, chat del directo, radios PLS/M3U con directorio mundial, favoritos en carpetas sincronizadas por Nostr, botón sorpresa y Android Auto. El orden está en el [plan técnico](docs/plan-tecnico.md).

## Demo de la interfaz

[`demo/index.html`](demo/index.html) es una maqueta interactiva de la app completa, con datos de ejemplo. Se abre en cualquier navegador.

## Compilar

Requisitos: JDK 17 y Android SDK 35.

```bash
./gradlew testDebugUnitTest      # tests
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
python3 scripts/check_strings.py # comprueba las traducciones
```

GitHub Actions ([`android.yml`](.github/workflows/android.yml)):

- **Push a `main`:** tests, traducciones y APK en «Artifacts».
- **Etiqueta `v*`** (igual que `radiostrVersion` en `app/build.gradle.kts`): Release con el APK firmado y las notas de [CHANGELOG.md](CHANGELOG.md). De ahí lo recogen Obtainium y Zapstore.
- **Versión de pruebas (beta):** cada push a la rama `beta` publica la pre-release **Radiostr Beta** (`X.Y.Z-beta.N`). Se instala como otra app (`io.github.oluengo.radiostr.beta`), junto a la normal y con sus propios datos. En Obtainium se añade aparte activando «incluir pre-releases». Cuando la beta está bien, se pasa a `main` y se publica la versión normal.

Huella SHA-256 del certificado de firma: `06:23:37:6B:0C:75:09:50:D7:E5:F1:4E:ED:06:C3:B3:C4:86:8B:B2:64:8B:8F:34:34:76:E2:DE:90:70:0F:89`.

Firma: secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` (mismos nombres que en Usus; la clave es propia de Radiostr, alias `radiostr`). Sin ellos se genera un APK de depuración y no se publica nada.

## Traducir

Los textos están en `app/src/main/res/values/strings.xml` (inglés), `values-es/` y `values-ca/`. Para añadir un idioma, sigue los pasos del [ADR 0004](docs/decisiones/0004-idiomas.md).

## Documentación

- [Plan técnico](docs/plan-tecnico.md)
- [Decisiones (ADR)](docs/decisiones/README.md)

## Licencia y apoyo

[MIT](LICENSE). Hecha con IA · probada por humanos.

Si te resulta útil, puedes apoyar el proyecto:

- **Bitcoin:** `bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa`
- **Lightning / zaps:** `oluengo@rizful.com`
