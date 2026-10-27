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

GitHub Actions compila el APK de depuración en cada push a `main`. Al subir una etiqueta `v*`, publica el APK firmado en Releases (necesita los secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`).

## Traducir

Los textos están en `app/src/main/res/values/strings.xml` (inglés), `values-es/` y `values-ca/`. Para añadir un idioma, sigue los pasos del [ADR 0004](docs/decisiones/0004-idiomas.md).

## Documentación

- [Plan técnico](docs/plan-tecnico.md)
- [Decisiones (ADR)](docs/decisiones/README.md)

## Licencia

[MIT](LICENSE)
