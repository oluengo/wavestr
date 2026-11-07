// Sesión y firma en el navegador: extensión NIP-07 (Alby, nos2x…), bunker NIP-46 o npub en solo lectura.
import { BunkerSigner, parseBunkerInput } from 'nostr-tools/nip46'
import { generateSecretKey } from 'nostr-tools/pure'
import { bytesToHex, hexToBytes } from 'nostr-tools/utils'
import type { Event, EventTemplate } from 'nostr-tools/pure'
import { pool } from './nostr'

export type Method = 'nip07' | 'bunker' | 'npub'
export interface Session { pubkey: string; method: Method; bunker?: string; clientKey?: string }

declare global {
  interface Window {
    nostr?: {
      getPublicKey(): Promise<string>
      signEvent(e: EventTemplate): Promise<Event>
      nip44?: { encrypt(pk: string, pt: string): Promise<string>; decrypt(pk: string, ct: string): Promise<string> }
    }
    webln?: { enable(): Promise<void>; sendPayment(invoice: string): Promise<{ preimage: string }> }
  }
}

const KEY = 'wavestr.session'
let bunker: BunkerSigner | null = null
let authHandler: (url: string) => void = url => window.open(url, '_blank', 'noopener')
export const onBunkerAuth = (f: (url: string) => void) => { authHandler = f }

export function loadSession(): Session | null {
  try { return JSON.parse(localStorage.getItem(KEY) || 'null') } catch { return null }
}
function saveSession(s: Session | null) {
  if (s) localStorage.setItem(KEY, JSON.stringify(s)); else localStorage.removeItem(KEY)
}

export const hasExtension = () => typeof window.nostr?.signEvent === 'function'
export const canSign = (s: Session | null) => !!s && s.method !== 'npub'

export async function loginExtension(): Promise<Session> {
  if (!window.nostr) throw new Error('no-extension')
  const pk = await window.nostr.getPublicKey()
  const s: Session = { pubkey: pk, method: 'nip07' }
  saveSession(s); return s
}

export function loginNpub(hex: string): Session {
  const s: Session = { pubkey: hex, method: 'npub' }
  saveSession(s); return s
}

export async function loginBunker(input: string): Promise<Session> {
  const bp = await parseBunkerInput(input.trim())
  if (!bp || !bp.relays?.length) throw new Error('bad-uri')
  const sk = generateSecretKey()
  const b = BunkerSigner.fromBunker(sk, bp, { pool, onauth: url => authHandler(url) })
  await withTimeout(b.connect(), 120_000)
  const pk = await withTimeout(b.getPublicKey(), 60_000)
  bunker = b
  const uri = `bunker://${bp.pubkey}?` + bp.relays.map(r => 'relay=' + encodeURIComponent(r)).join('&')
  const s: Session = { pubkey: pk, method: 'bunker', bunker: uri, clientKey: bytesToHex(sk) }
  saveSession(s); return s
}

export function logout() {
  bunker?.close().catch(() => {})
  bunker = null
  saveSession(null)
}

async function bunkerFor(s: Session): Promise<BunkerSigner | null> {
  if (bunker) return bunker
  if (!s.bunker || !s.clientKey) return null
  const bp = await parseBunkerInput(s.bunker)
  if (!bp) return null
  bunker = BunkerSigner.fromBunker(hexToBytes(s.clientKey), bp, { pool, onauth: url => authHandler(url) })
  return bunker
}

function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return Promise.race([p, new Promise<T>((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))])
}

/** Firma con lo que haya: extensión o bunker. null si no se puede o se rechaza. */
export async function sign(s: Session | null, t: EventTemplate & { pubkey?: string }): Promise<Event | null> {
  if (!s || s.method === 'npub') return null
  const tpl: EventTemplate = { kind: t.kind, tags: t.tags, content: t.content, created_at: t.created_at }
  try {
    if (s.method === 'nip07') {
      const e = await window.nostr!.signEvent(tpl)
      return e.pubkey === s.pubkey ? e : null
    }
    const b = await bunkerFor(s)
    if (!b) return null
    const e = await withTimeout(b.signEvent(tpl), 60_000)
    return e.pubkey === s.pubkey ? e : null
  } catch { return null }
}

export async function nip44Encrypt(s: Session | null, peer: string, plain: string): Promise<string | null> {
  if (!s || s.method === 'npub') return null
  try {
    if (s.method === 'nip07') return window.nostr?.nip44 ? await window.nostr.nip44.encrypt(peer, plain) : null
    const b = await bunkerFor(s); return b ? await withTimeout(b.nip44Encrypt(peer, plain), 60_000) : null
  } catch { return null }
}

export async function nip44Decrypt(s: Session | null, peer: string, cipher: string): Promise<string | null> {
  if (!s || s.method === 'npub') return null
  try {
    if (s.method === 'nip07') return window.nostr?.nip44 ? await window.nostr.nip44.decrypt(peer, cipher) : null
    const b = await bunkerFor(s); return b ? await withTimeout(b.nip44Decrypt(peer, cipher), 60_000) : null
  } catch { return null }
}
