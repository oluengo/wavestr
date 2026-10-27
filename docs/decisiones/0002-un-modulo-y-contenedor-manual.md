# 0002 · Un único módulo y contenedor de dependencias manual

*Estado: aceptada · 27 oct 2026*

## Decisión

Empezamos con `:app` y paquetes por capa (`nostr`, `player`, `data`, `ui`). Las dependencias se crean en `AppContainer`, sin Koin ni Hilt.

## Motivo

- Menos configuración de Gradle mientras la app es pequeña y se valida F0/F1.
- Un contenedor manual de ~20 líneas es más fácil de leer que un framework de DI.

## Qué la haría cambiar

Al llegar a F3 (radios + Room) o si los tiempos de compilación crecen: separar en `:core:*` y `:feature:*` como indica el plan.
