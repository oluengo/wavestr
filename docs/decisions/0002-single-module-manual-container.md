# 0002 · A single module and a manual dependency container

*Status: accepted · 27 Oct 2026*

## Decision

We start with `:app` and packages by layer (`nostr`, `player`, `data`, `ui`). Dependencies are created in `AppContainer`, without Koin or Hilt.

## Reason

- Less Gradle configuration while the app is small and F0/F1 are being validated.
- A ~20-line manual container is easier to read than a DI framework.

## What would change it

On reaching F3 (radios + Room) or if build times grow: split into `:core:*` and `:feature:*` as the plan says.
