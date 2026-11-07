// Zaps (NIP-57): LNURL del destinatario, petición de zap, factura y pago con NWC (NIP-47), WebLN o QR.
import { finalizeEvent, generateSecretKey, getPublicKey, type Event } from 'nostr-tools/pure'
import * as nip04 from 'nostr-tools/nip04'
import { bech32 } from '@scure/base'
import { KIND, bolt11Sats, now, pool } from './nostr'

export class ZapError extends Error { constructor(public code: string, public data: Record<string, number> = {}) { super(code) } }

export interface PayInfo { callback: string; min: number; max: number; allowsNostr: boolean; nostrPubkey?: string; commentAllowed: number }

export function lnurlEndpoint(lud16: string): string | null {
  const m = /^([a-z0-9._+-]+)@([a-z0-9.-]+\.[a-z]{2,})$/i.exec(lud16.trim())
  return m ? `https://${m[2]}/.well-known/lnurlp/${m[1]}` : null
}
export const encodeLnurl = (url: string) => bech32.encode('lnurl', bech32.toWords(new TextEncoder().encode(url)), 2000).toUpperCase()

export async function payInfo(lud16?: string): Promise<PayInfo> {
  const ep = lud16 && lnurlEndpoint(lud16)
  if (!ep) throw new ZapError('no_address')
  let o: any
  try { o = await (await fetch(ep)).json() } catch { throw new ZapError('unreachable') }
  if (o.status === 'ERROR' || !o.callback) throw new ZapError('unreachable')
  return { callback: o.callback, min: Number(o.minSendable) || 1000, max: Number(o.maxSendable) || 1e11, allowsNostr: !!o.allowsNostr && !!o.nostrPubkey, nostrPubkey: o.nostrPubkey, commentAllowed: Number(o.commentAllowed) || 0 }
}

export function zapRequestTags(recipient: string, address: string | undefined, msats: number, lnurl: string, relays: string[], eventId?: string): string[][] {
  const t = [['relays', ...relays.slice(0, 5)], ['amount', String(msats)], ['lnurl', lnurl], ['p', recipient]]
  if (address) t.push(['a', address])
  if (eventId) t.push(['e', eventId])
  return t
}

/** Petición anónima: firmada con una clave de un solo uso. */
export const anonymousRequest = (tags: string[][], content: string) =>
  finalizeEvent({ kind: KIND.ZAP_REQUEST, tags, content, created_at: now() }, generateSecretKey())

export async function invoice(info: PayInfo, msats: number, request: Event | null, lnurl: string, comment: string): Promise<string> {
  if (msats < info.min || msats > info.max) throw new ZapError('range', { min: Math.ceil(info.min / 1000), max: Math.floor(info.max / 1000) })
  const u = new URL(info.callback)
  u.searchParams.set('amount', String(msats))
  if (request) { u.searchParams.set('nostr', JSON.stringify(request)); u.searchParams.set('lnurl', lnurl) }
  else if (comment && info.commentAllowed > 0) u.searchParams.set('comment', comment.slice(0, info.commentAllowed))
  let o: any
  try { o = await (await fetch(u.toString())).json() } catch { throw new ZapError('invoice') }
  if (!o.pr) throw new ZapError('invoice')
  if (bolt11Sats(o.pr) !== Math.round(msats / 1000)) throw new ZapError('mismatch')
  return o.pr
}

// ---------- Cartera NWC ----------

export interface Nwc { pubkey: string; relays: string[]; secret: string; lud16?: string }

export function parseNwc(uri: string): Nwc | null {
  try {
    const u = uri.trim().replace(/^nostr\+walletconnect:\/\//i, 'https://').replace(/^nostrwalletconnect:\/\//i, 'https://')
    const p = new URL(u)
    const pubkey = p.hostname || p.pathname.replace(/^\/+/, '')
    const relays = p.searchParams.getAll('relay').filter(r => /^wss?:\/\//.test(r))
    const secret = p.searchParams.get('secret') || ''
    if (!/^[0-9a-f]{64}$/i.test(pubkey) || !/^[0-9a-f]{64}$/i.test(secret) || !relays.length) return null
    return { pubkey: pubkey.toLowerCase(), relays, secret: secret.toLowerCase(), lud16: p.searchParams.get('lud16') || undefined }
  } catch { return null }
}

const hexBytes = (h: string) => Uint8Array.from(h.match(/../g)!.map(b => parseInt(b, 16)))

async function nwcCall(c: Nwc, method: string, params: object, timeout = 45_000): Promise<any> {
  const sk = hexBytes(c.secret)
  const content = await nip04.encrypt(sk, c.pubkey, JSON.stringify({ method, params }))
  const req = finalizeEvent({ kind: 23194, created_at: now(), tags: [['p', c.pubkey]], content }, sk)
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { sub.close(); reject(new Error('timeout')) }, timeout)
    const sub = pool.subscribe(c.relays, { kinds: [23195], authors: [c.pubkey], '#e': [req.id] }, {
      onevent: async ev => {
        try {
          const r = JSON.parse(await nip04.decrypt(sk, c.pubkey, ev.content))
          clearTimeout(timer); sub.close(); resolve(r)
        } catch {}
      },
    })
    setTimeout(() => pool.publish(c.relays, req), 300)
  })
}

export async function nwcTest(c: Nwc): Promise<boolean> {
  try { const r = await nwcCall(c, 'get_info', {}, 20_000); return !r.error } catch { return false }
}

export async function nwcPay(c: Nwc, invoice: string): Promise<{ ok: true } | { ok: false; error: string }> {
  try {
    const r = await nwcCall(c, 'pay_invoice', { invoice })
    return r.error ? { ok: false, error: r.error.message || r.error.code || 'error' } : { ok: true }
  } catch (e) { return { ok: false, error: (e as Error).message } }
}

export const clientPubkey = (c: Nwc) => getPublicKey(hexBytes(c.secret))

/** A quién va el zap: al stream o a un mensaje concreto ([eventId]). */
export interface ZapTarget { pubkey: string; name: string; address?: string; title: string; eventId?: string }
