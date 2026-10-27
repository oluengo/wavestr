# 0004 · Idiomas: català, castellano e inglés

*Estado: aceptada · 27 oct 2026*

## Decisión

La app y la demo están en ca/es/en. Por defecto, el idioma del dispositivo; si no es ninguno de los tres, inglés. Selector en Ajustes.

## Motivo

- `values/` es inglés y actúa de respaldo automático para cualquier otro idioma.
- Idioma por app: `android:localeConfig` en Android 13+ y AppCompat (`setApplicationLocales`, `autoStoreLocales`) en versiones anteriores.
- `scripts/check_strings.py` en CI impide publicar con traducciones incompletas.

## Qué la haría cambiar

Añadir un idioma = una carpeta `values-xx/` + entrada en `locales_config.xml` + diccionario en la demo.
