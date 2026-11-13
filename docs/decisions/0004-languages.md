# 0004 · Languages: Catalan, Spanish and English

*Status: accepted · 27 Oct 2026*

## Decision

The app and the demo are in ca/es/en. By default, the device language; if it's none of the three, English. Selector in Settings.

## Reason

- `values/` is English and acts as the automatic fallback for any other language.
- Per-app language: `android:localeConfig` on Android 13+ and AppCompat (`setApplicationLocales`, `autoStoreLocales`) on earlier versions.
- `scripts/check_strings.py` in CI prevents publishing with incomplete translations.

## What would change it

Adding a language = a `values-xx/` folder + an entry in `locales_config.xml` + a dictionary in the demo.
