# 0005 · Distribution: GitHub Releases, Obtainium and Zapstore

*Status: accepted · 27 Oct 2026*

## Decision

Signed APK published on GitHub Releases when tagging `v*`. Obtainium and Zapstore pick it up from there. No Google Play or F-Droid for now.

Test version as in Usus: the `beta` branch publishes the "Wavestr Beta" pre-release (`io.github.oluengo.wavestr.beta`, `X.Y.Z-beta.N`), which lives alongside the regular one.

## Reason

- Same approach as Usus.
- Zapstore suits a Nostr app.
- Android Auto works outside Play by turning on "Unknown sources" in its developer settings (a one-time step; explained in the app).

## What would change it

If frictionless Android Auto becomes important, Play is the way.
