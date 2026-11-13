# 0006 · Favourite folders private by default

*Status: superseded by [0008](0008-favourites-on-nostr.md) in its format · 27 Oct 2026*

## Decision

Each folder is a NIP-51 list (kind 30003) with its items encrypted (NIP-44) in `content`. A folder could be marked public.

## Reason

- What each person listens to shouldn't be public unless they choose so.
- Amber does the encryption; relays store the list without being able to read it.

## What would change it

If sharing lists turns out to be the star feature, change the default to public.
