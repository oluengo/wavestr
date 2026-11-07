// Nostr para la web: los mismos datos y criterios que el núcleo Kotlin de Android y PC.
import { SimplePool } from 'nostr-tools/pool'
import type { Event, EventTemplate } from 'nostr-tools/pure'
import { verifyEvent } from 'nostr-tools/pure'
import * as nip19 from 'nostr-tools/nip19'

export const KIND = {
  PROFILE: 0, CONTACTS: 3, REACTION: 7, RELAY_LIST: 10002, ZAP_REQUEST: 9734, ZAP_RECEIPT: 9735,
  LIVE_EVENT: 30311, LIVE_CHAT: 1311, APP_DATA: 30078, CALENDAR_TIME: 31923,
} as const

export const BUILTIN_RELAYS = ['wss://relay.damus.io', 'wss://nos.lol', 'wss://relay.primal.net']
const INDEXERS = ['wss://purplepag.es']

export const pool = new SimplePool()

export function defaultRelays(): string[] {
  try {
    const r = JSON.parse(localStorage.getItem('wavestr.relays') || 'null')
    if (Array.isArray(r) && r.length) return r
  } catch {}
  return BUILTIN_RELAYS
}

export function setAppRelays(list: string[] | null) {
  if (list && list.length) localStorage.setItem('wavestr.relays', JSON.stringify(list))
  else localStorage.removeItem('wavestr.relays')
  relayCache.clear(); followCache.clear()
}

export function normalizeRelay(s: string): string | null {
  let u = s.trim()
  if (!u) return null
  if (!/^wss?:\/\//i.test(u)) u = 'wss://' + u
  try { const p = new URL(u); return (p.protocol + '//' + p.host + p.pathname).replace(/\/$/, '') } catch { return null }
}

export const now = () => Math.floor(Date.now() / 1000)
const tag = (e: Event, n: string) => e.tags.find(t => t[0] === n)?.[1]
const tagValues = (e: Event, n: string) => e.tags.filter(t => t[0] === n && t[1] != null).map(t => t[1])
export const addressOf = (e: Event) => `${e.kind}:${e.pubkey}:${tag(e, 'd') ?? ''}`

export function npub(hex: string) { try { return nip19.npubEncode(hex) } catch { return hex } }
export function shortNpub(hex: string) { const n = npub(hex); return n.slice(0, 10) + '…' + n.slice(-4) }
/** npub, nprofile o hex → hex (o null). */
export function normalizePubkey(s: string): string | null {
  const v = s.trim().replace(/^nostr:/, '')
  if (/^[0-9a-f]{64}$/i.test(v)) return v.toLowerCase()
  try {
    const d = nip19.decode(v)
    if (d.type === 'npub') return d.data as string
    if (d.type === 'nprofile') return (d.data as { pubkey: string }).pubkey
  } catch {}
  return null
}

async function query(relays: string[], filters: any[], maxWait = 4500): Promise<Event[]> {
  const rs = [...new Set(relays)].slice(0, 10)
  const res = await Promise.all(filters.map(f => pool.querySync(rs, f, { maxWait }).catch(() => [] as Event[])))
  const seen = new Set<string>()
  return res.flat().filter(e => !seen.has(e.id) && (seen.add(e.id), true))
}

function latestByAddress(evs: Event[]): Event[] {
  const m = new Map<string, Event>()
  for (const e of evs) { const a = addressOf(e); const o = m.get(a); if (!o || o.created_at < e.created_at) m.set(a, e) }
  return [...m.values()]
}

export async function publish(relays: string[], ev: Event): Promise<number> {
  const rs = [...new Set(relays)].slice(0, 10)
  const res = await Promise.allSettled(pool.publish(rs, ev))
  return res.filter(r => r.status === 'fulfilled').length
}

// ---------- Modelos ----------

export interface Profile { pubkey: string; name?: string; displayName?: string; picture?: string; banner?: string; about?: string; lud16?: string; nip05?: string }
export const bestName = (p: Profile | undefined, pk: string) => p?.displayName?.trim() || p?.name?.trim() || shortNpub(pk)

export interface LiveStream {
  address: string; eventPubkey: string; hostPubkey: string; title: string; summary?: string; image?: string
  streamingUrl?: string; recordingUrl?: string; status: 'planned' | 'live' | 'ended'; startsAt?: number; endsAt?: number
  participants?: number; hashtags: string[]; updatedAt: number
}

export function liveFrom(e: Event): LiveStream | null {
  if (e.kind !== KIND.LIVE_EVENT || tag(e, 'd') == null) return null
  const host = e.tags.find(t => t[0] === 'p' && (t[3] || '').toLowerCase() === 'host')?.[1] || e.pubkey
  const st = (tag(e, 'status') || '').toLowerCase()
  const http = (v?: string) => (v && v.startsWith('http') ? v : undefined)
  return {
    address: addressOf(e), eventPubkey: e.pubkey, hostPubkey: host.toLowerCase(),
    title: tag(e, 'title')?.trim() || '', summary: tag(e, 'summary')?.trim() || undefined, image: http(tag(e, 'image')),
    streamingUrl: tagValues(e, 'streaming').find(v => v.startsWith('http')), recordingUrl: tagValues(e, 'recording').find(v => v.startsWith('http')),
    status: st === 'live' ? 'live' : st === 'ended' ? 'ended' : 'planned',
    startsAt: Number(tag(e, 'starts')) || undefined, endsAt: Number(tag(e, 'ends')) || undefined,
    participants: Number(tag(e, 'current_participants')) || undefined,
    hashtags: [...new Set(tagValues(e, 't').map(s => s.toLowerCase()))], updatedAt: e.created_at,
  }
}
export const isLiveNow = (s: LiveStream) => s.status === 'live' && !!s.streamingUrl && now() - s.updatedAt <= 3600

export interface Scheduled { address: string; hostPubkey: string; title: string; summary?: string; image?: string; startsAt: number; endsAt?: number; tzid?: string; fromCalendar: boolean }

export function scheduledFrom(e: Event): Scheduled | null {
  if (e.kind === KIND.LIVE_EVENT) {
    const s = liveFrom(e)
    if (!s || s.status !== 'planned' || !s.startsAt) return null
    return { address: s.address, hostPubkey: s.hostPubkey, title: s.title, summary: s.summary, image: s.image, startsAt: s.startsAt, endsAt: s.endsAt, fromCalendar: false }
  }
  if (e.kind === KIND.CALENDAR_TIME) {
    const start = Number(tag(e, 'start')); const title = (tag(e, 'title') || tag(e, 'name') || '').trim()
    if (!start || !title) return null
    const host = e.tags.find(t => t[0] === 'p' && (t[3] || '').toLowerCase() === 'host')?.[1]?.toLowerCase() || e.pubkey
    const img = tag(e, 'image')
    return { address: addressOf(e), hostPubkey: host, title, summary: tag(e, 'summary') || e.content?.slice(0, 300) || undefined,
      image: img?.startsWith('http') ? img : undefined, startsAt: start, endsAt: Number(tag(e, 'end')) || undefined, tzid: tag(e, 'start_tzid') || undefined, fromCalendar: true }
  }
  return null
}

export interface ChatMessage { id: string; pubkey: string; content: string; createdAt: number }
export interface ZapReceipt { id: string; senderPubkey?: string; amountSats: number; comment: string; createdAt: number; eventId?: string; address?: string }
export interface Reaction { id: string; pubkey: string; emoji: string; eventId?: string; createdAt: number }

function bolt11Sats(inv: string): number | null {
  const m = /^ln(?:bc|tb|bcrt|tbs)(\d+)([munp]?)1/i.exec(inv.trim())
  if (!m) return null
  const n = Number(m[1]); const mul = { '': 1e8, m: 1e5, u: 100, n: 0.1, p: 0.0001 }[m[2].toLowerCase() as '' | 'm' | 'u' | 'n' | 'p']
  return Math.round(n * mul)
}
export { bolt11Sats }

function zapFrom(e: Event): ZapReceipt | null {
  if (e.kind !== KIND.ZAP_RECEIPT) return null
  let req: Event | null = null
  try { req = JSON.parse(tag(e, 'description') || 'null') } catch {}
  const sats = bolt11Sats(tag(e, 'bolt11') || '') ?? (req ? Math.round(Number(req.tags.find(t => t[0] === 'amount')?.[1] || 0) / 1000) : 0)
  if (!sats) return null
  return {
    id: e.id, senderPubkey: req?.pubkey || tag(e, 'P'), amountSats: sats, comment: req?.content || '', createdAt: e.created_at,
    eventId: tag(e, 'e') || (req ? req.tags.find(t => t[0] === 'e')?.[1] : undefined),
    address: tag(e, 'a') || (req ? req.tags.find(t => t[0] === 'a')?.[1] : undefined),
  }
}

function reactionFrom(e: Event, address: string): Reaction | null {
  if (e.kind !== KIND.REACTION) return null
  const target = tagValues(e, 'e').at(-1)
  if (!target && !tagValues(e, 'a').includes(address)) return null
  const c = e.content.trim()
  if (c === '-') return null
  const emoji = c === '' || c === '+' || (c.startsWith(':') && c.endsWith(':')) ? '❤️' : [...c].slice(0, 4).join('')
  return { id: e.id, pubkey: e.pubkey, emoji, eventId: target, createdAt: e.created_at }
}

export function reactionTags(address: string, targetPubkey: string, eventId?: string): string[][] {
  const t: string[][] = []
  if (eventId) t.push(['e', eventId])
  t.push(['p', targetPubkey], ['a', address], ['k', String(eventId ? KIND.LIVE_CHAT : KIND.LIVE_EVENT)])
  return t
}

// ---------- Consultas (con caché) ----------

const relayCache = new Map<string, string[]>()
const followCache = new Map<string, string[]>()
const profileCache = new Map<string, Profile>()

function readRelays(e: Event | undefined, mode: 'read' | 'write'): string[] {
  if (!e) return []
  return e.tags.filter(t => t[0] === 'r' && t[1] && (!t[2] || t[2] === mode)).map(t => normalizeRelay(t[1])).filter(Boolean) as string[]
}

export async function relaysFor(pubkey?: string | null): Promise<string[]> {
  if (!pubkey) return defaultRelays()
  const c = relayCache.get(pubkey); if (c) return c
  const ev = (await query([...INDEXERS, ...defaultRelays()], [{ kinds: [KIND.RELAY_LIST], authors: [pubkey], limit: 1 }])).sort((a, b) => b.created_at - a.created_at)[0]
  const list = [...new Set([...readRelays(ev, 'read').slice(0, 6), ...defaultRelays()])]
  relayCache.set(pubkey, list)
  return list
}

export async function ownRelays(pubkey: string): Promise<string[]> {
  const ev = (await query([...INDEXERS, ...defaultRelays()], [{ kinds: [KIND.RELAY_LIST], authors: [pubkey], limit: 1 }])).sort((a, b) => b.created_at - a.created_at)[0]
  return [...new Set([...readRelays(ev, 'write').slice(0, 5), ...defaultRelays()])]
}

export async function follows(pubkey: string, refresh = false): Promise<string[]> {
  if (!refresh && followCache.has(pubkey)) return followCache.get(pubkey)!
  const ev = (await query([...INDEXERS, ...(await relaysFor(pubkey))], [{ kinds: [KIND.CONTACTS], authors: [pubkey], limit: 1 }])).sort((a, b) => b.created_at - a.created_at)[0]
  const list = ev ? [...new Set(ev.tags.filter(t => t[0] === 'p' && /^[0-9a-f]{64}$/.test(t[1] || '')).map(t => t[1]))] : []
  followCache.set(pubkey, list)
  return list
}

export async function profiles(pks: string[], viewer?: string | null): Promise<Record<string, Profile>> {
  const want = [...new Set(pks)].filter(p => !profileCache.has(p))
  for (let i = 0; i < want.length; i += 150) {
    const chunk = want.slice(i, i + 150)
    const evs = await query([...INDEXERS, ...(await relaysFor(viewer))], [{ kinds: [KIND.PROFILE], authors: chunk }])
    const latest = new Map<string, Event>()
    for (const e of evs) { const o = latest.get(e.pubkey); if (!o || o.created_at < e.created_at) latest.set(e.pubkey, e) }
    for (const [pk, e] of latest) {
      try {
        const j = JSON.parse(e.content)
        const http = (v: unknown) => (typeof v === 'string' && v.startsWith('http') ? v : undefined)
        profileCache.set(pk, { pubkey: pk, name: j.name, displayName: j.display_name || j.displayName, picture: http(j.picture), banner: http(j.banner), about: j.about, lud16: j.lud16 || undefined, nip05: j.nip05 })
      } catch {}
    }
  }
  const out: Record<string, Profile> = {}
  for (const p of pks) { const v = profileCache.get(p); if (v) out[p] = v }
  return out
}

export async function liveFromFollows(pubkey: string, refresh = false): Promise<LiveStream[]> {
  const fl = await follows(pubkey, refresh)
  if (!fl.length) return []
  const since = now() - 24 * 3600
  const filters: any[] = []
  for (let i = 0; i < fl.length; i += 300) {
    const c = fl.slice(i, i + 300)
    filters.push({ kinds: [KIND.LIVE_EVENT], authors: c, since }, { kinds: [KIND.LIVE_EVENT], '#p': c, since })
  }
  const set = new Set(fl)
  return latestByAddress(await query(await relaysFor(pubkey), filters)).map(liveFrom)
    .filter((s): s is LiveStream => !!s && isLiveNow(s) && (set.has(s.hostPubkey) || set.has(s.eventPubkey)))
    .sort((a, b) => (b.participants || 0) - (a.participants || 0))
}

export async function liveGlobal(pubkey?: string | null): Promise<LiveStream[]> {
  return latestByAddress(await query(await relaysFor(pubkey), [{ kinds: [KIND.LIVE_EVENT], since: now() - 6 * 3600, limit: 300 }]))
    .map(liveFrom).filter((s): s is LiveStream => !!s && isLiveNow(s)).sort((a, b) => (b.participants || 0) - (a.participants || 0))
}

export async function upcoming(hosts: string[], viewer?: string | null, days = 14): Promise<Scheduled[]> {
  const list = [...new Set(hosts)]
  if (!list.length) return []
  const t = now(); const since = t - 60 * 24 * 3600
  const filters: any[] = []
  for (let i = 0; i < list.length; i += 300) {
    const c = list.slice(i, i + 300)
    filters.push({ kinds: [KIND.LIVE_EVENT, KIND.CALENDAR_TIME], authors: c, since, limit: 300 }, { kinds: [KIND.LIVE_EVENT, KIND.CALENDAR_TIME], '#p': c, since, limit: 300 })
  }
  const set = new Set(list); const seen = new Set<string>()
  return latestByAddress(await query(await relaysFor(viewer), filters)).map(scheduledFrom)
    .filter((s): s is Scheduled => !!s && set.has(s.hostPubkey) && s.startsAt > t - 900 && s.startsAt < t + days * 86400 && !seen.has(s.address) && (seen.add(s.address), true))
    .sort((a, b) => a.startsAt - b.startsAt)
}

export async function stream(address: string, viewer?: string | null): Promise<LiveStream | null> {
  const [k, pk, d] = address.split(':')
  if (Number(k) !== KIND.LIVE_EVENT || !pk) return null
  const evs = await query(await relaysFor(viewer), [{ kinds: [KIND.LIVE_EVENT], authors: [pk], '#d': [d ?? ''], limit: 5 }])
  const e = evs.sort((a, b) => b.created_at - a.created_at)[0]
  return e ? liveFrom(e) : null
}

export interface Activity { chat: ChatMessage[]; zaps: ZapReceipt[]; reactions: Reaction[] }

/** Chat, zaps y reacciones (al stream y a los mensajes) de un directo desde [since]. */
export async function activity(address: string, since: number, viewer?: string | null): Promise<Activity> {
  const relays = await relaysFor(viewer)
  const evs = await query(relays, [{ kinds: [KIND.LIVE_CHAT, KIND.ZAP_RECEIPT, KIND.REACTION], '#a': [address], since, limit: 500 }], 5000)
  const chat = evs.filter(e => e.kind === KIND.LIVE_CHAT && e.tags.some(t => t[0] === 'a' && t[1] === address))
    .map(e => ({ id: e.id, pubkey: e.pubkey, content: e.content, createdAt: e.created_at })).sort((a, b) => a.createdAt - b.createdAt)
  const ids = chat.slice(-60).map(m => m.id)
  const extra = ids.length ? await query(relays, [{ kinds: [KIND.REACTION, KIND.ZAP_RECEIPT], '#e': ids, since }], 4000) : []
  const all = [...evs, ...extra.filter(e => !evs.some(x => x.id === e.id))]
  const known = new Set(ids)
  const zaps = all.map(zapFrom).filter((z): z is ZapReceipt => !!z && (z.address === address || (!!z.eventId && known.has(z.eventId)))).sort((a, b) => a.createdAt - b.createdAt)
  const seen = new Set<string>()
  const reactions = all.map(e => reactionFrom(e, address)).filter((r): r is Reaction => !!r)
    .map(r => (r.eventId && !known.has(r.eventId) ? { ...r, eventId: undefined } : r))
    .filter(r => { const k = r.pubkey + '|' + (r.eventId || '') + '|' + r.emoji; return !seen.has(k) && (seen.add(k), true) })
  return { chat, zaps, reactions }
}

export function unsigned(pubkey: string, kind: number, tags: string[][], content: string): EventTemplate & { pubkey: string } {
  return { pubkey, kind, tags, content, created_at: now() }
}

export const verify = (e: Event) => { try { return verifyEvent(e) } catch { return false } }
export type { Event }

/** Directos en marcha de unos anfitriones (para los streamers favoritos). */
export async function liveOf(hosts: string[], viewer?: string | null): Promise<Map<string, LiveStream>> {
  const out = new Map<string, LiveStream>()
  const list = [...new Set(hosts)]
  if (!list.length) return out
  const since = now() - 24 * 3600
  const evs = await query(await relaysFor(viewer), [{ kinds: [KIND.LIVE_EVENT], authors: list, since }, { kinds: [KIND.LIVE_EVENT], '#p': list, since }])
  for (const s of latestByAddress(evs).map(liveFrom)) if (s && isLiveNow(s) && list.includes(s.hostPubkey)) out.set(s.hostPubkey, s)
  return out
}
