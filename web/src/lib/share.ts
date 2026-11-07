// Enlaces para compartir: mismo código que Android y PC (JSON compacto, deflate sin cabecera, base64url).
import { lib, type FavItem, type Folder, type Library } from './library'
import type { Station } from './radio'
import { npub } from './nostr'

export const LINK_BASE = 'https://oluengo.github.io/wavestr/s/'
export type Kind = 'i' | 'f' | 'a'
export interface Shared { kind: Kind; name: string; folders: Folder[]; items: FavItem[] }

export const stations = (s: Shared) => s.items.filter(i => i.kind === 'STATION').length
export const streamers = (s: Shared) => s.items.filter(i => i.kind === 'STREAMER').length
export const asLibrary = (s: Shared): Library => ({ folders: s.folders, favorites: s.items, imported: [] })

export const ofItem = (i: FavItem): Shared => ({ kind: 'i', name: i.title, folders: [], items: [{ ...i, folder: undefined }] })
export function ofFolder(l: Library, id: string): Shared | null {
  const root = lib.folder(l, id); if (!root) return null
  const tree = [{ ...root, parent: undefined }, ...lib.tree(l, id).map(([f]) => f)]
  const ids = new Set(tree.map(f => f.id))
  return { kind: 'f', name: root.name, folders: tree, items: l.favorites.filter(f => f.folder && ids.has(f.folder)) }
}
export const ofAll = (l: Library, name: string): Shared => ({ kind: 'a', name, folders: l.folders, items: l.favorites })

function compact(s: Station) {
  return {
    id: s.id, n: s.name,
    v: s.variants.map(v => ({ u: v.url, ...(v.codec ? { c: v.codec } : {}), ...(v.bitrate > 0 ? { b: v.bitrate } : {}), ...(v.hls ? { h: true } : {}), ...(v.uuid ? { id: v.uuid } : {}) })),
    ...(s.favicon ? { fv: s.favicon } : {}), ...(s.homepage ? { hp: s.homepage } : {}), ...(s.tags.length ? { t: s.tags.slice(0, 6) } : {}),
    ...(s.country ? { co: s.country } : {}), ...(s.cc ? { cc: s.cc } : {}), ...(s.lang ? { l: s.lang } : {}), ...(s.imported ? { im: true } : {}),
  }
}
function expand(o: any): Station | undefined {
  try {
    const variants = (o.v as any[]).filter(v => /^https?:\/\//i.test(v.u)).map(v => ({ url: v.u, codec: v.c, bitrate: Number(v.b) || 0, hls: !!v.h, uuid: v.id }))
    if (!variants.length || !o.id || !o.n) return undefined
    return { id: o.id, name: o.n, variants, favicon: o.fv, homepage: o.hp, tags: o.t || [], country: o.co, cc: o.cc, lang: o.l, votes: 0, ok: true, imported: !!o.im }
  } catch { return undefined }
}

const b64url = (b: Uint8Array) => btoa(String.fromCharCode(...b)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const unb64url = (s: string) => { const t = s.replace(/-/g, '+').replace(/_/g, '/'); return Uint8Array.from(atob(t + '==='.slice((t.length + 3) % 4)), c => c.charCodeAt(0)) }

async function pipe(data: Uint8Array, stream: CompressionStream | DecompressionStream): Promise<Uint8Array> {
  return new Uint8Array(await new Response(new Blob([data as BlobPart]).stream().pipeThrough(stream)).arrayBuffer())
}

export async function toCode(s: Shared): Promise<string> {
  const json = {
    v: 1, k: s.kind, n: s.name,
    ...(s.folders.length ? { f: s.folders.map(f => ({ i: f.id, n: f.name, ...(f.parent ? { p: f.parent } : {}) })) } : {}),
    x: s.items.map(i => ({ t: i.title, ...(i.folder ? { f: i.folder } : {}), ...(i.station ? { s: compact(i.station) } : { ...(i.host ? { h: i.host } : {}), ...(i.image ? { i: i.image } : {}) }) })),
  }
  const bytes = await pipe(new TextEncoder().encode(JSON.stringify(json)), new CompressionStream('deflate-raw'))
  // btoa con muchos bytes: por trozos.
  let bin = ''; for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export const link = async (s: Shared) => LINK_BASE + '#' + (await toCode(s))

export function codeFrom(uri: string): string | null {
  const u = uri.trim()
  if (!/^https:\/\/oluengo\.github\.io\/wavestr\/(s|app)/i.test(u) && !/^wavestr:\/\//i.test(u) && !/^#?import=/.test(u)) return null
  const h = u.includes('#') ? u.slice(u.indexOf('#') + 1) : u
  const c = h.replace(/^import=/, '')
  return c || null
}

export async function decode(code: string): Promise<Shared | null> {
  try {
    const o = JSON.parse(new TextDecoder().decode(await pipe(unb64url(code.trim()), new DecompressionStream('deflate-raw'))))
    const kind: Kind = ['i', 'f', 'a'].includes(o.k) ? o.k : 'a'
    const folders: Folder[] = (o.f || []).filter((f: any) => f.i && f.n).map((f: any) => ({ id: f.i, name: String(f.n).slice(0, 40), ...(f.p ? { parent: f.p } : {}) }))
    const ids = new Set(folders.map(f => f.id))
    const seen = new Set<string>()
    const items: FavItem[] = (o.x || []).slice(0, 2000).map((x: any) => {
      const folder = ids.has(x.f) ? x.f : undefined
      const st = x.s ? expand(x.s) : undefined
      if (st) return { key: st.id, kind: 'STATION', title: x.t || st.name, subtitle: st.country, image: st.favicon, station: st, folder, added: 0 }
      if (x.h && /^[0-9a-f]{64}$/i.test(x.h)) return { key: 'npub:' + x.h.toLowerCase(), kind: 'STREAMER', title: x.t || x.h.slice(0, 8), image: x.i, host: x.h.toLowerCase(), folder, added: 0 }
      return null
    }).filter((i: FavItem | null) => i && !seen.has(i.key) && (seen.add(i.key), true))
    if (!items.length) return null
    return { kind, name: o.n || '', folders: folders.map(f => (f.parent && !ids.has(f.parent) ? { ...f, parent: undefined } : f)), items }
  } catch { return null }
}

export function toText(s: Shared): string {
  const url = (i: FavItem) => i.station?.variants[0].url || (i.host ? 'https://njump.me/' + npub(i.host) : '')
  if (s.kind === 'i') { const i = s.items[0]; return [i.title, url(i)].filter(Boolean).join('\n') }
  const l = asLibrary(s); const out: string[] = [s.name]
  lib.itemsIn(l, null).forEach(i => out.push(`• ${i.title} — ${url(i)}`))
  const walk = (p: string | null) => lib.subfolders(l, p).forEach(f => {
    const inside = lib.itemsIn(l, f.id)
    if (inside.length) { out.push('', lib.path(l, f.id).map(x => x.name).join(' / ')); inside.forEach(i => out.push(`• ${i.title} — ${url(i)}`)) }
    walk(f.id)
  })
  walk(null)
  return out.join('\n')
}

/** Añade lo elegido: carpeta → carpeta nueva; todo → dentro de [wrapper]; suelto → raíz. Sin duplicar. */
export function importInto(s: Shared, l: Library, keys: Set<string>, wrapper: string, newId: () => string): Library {
  const chosen = s.items.filter(i => keys.has(i.key) && !lib.isFavorite(l, i.key))
  if (!chosen.length) return l
  const src = asLibrary(s); const ids = new Map<string, string>()
  let out = l
  let wrap: string | undefined
  if (s.kind === 'a') { wrap = newId(); out = lib.addFolder(out, wrap, wrapper, null) }
  const needed = new Set(chosen.flatMap(i => (i.folder ? lib.path(src, i.folder).map(f => f.id) : [])))
  const create = (parent: string | null, np?: string) => lib.subfolders(src, parent).filter(f => needed.has(f.id)).forEach(f => {
    const id = newId(); ids.set(f.id, id); out = lib.addFolder(out, id, f.name, np ?? null); create(f.id, id)
  })
  create(null, wrap)
  const now = Date.now()
  for (const i of chosen) out = lib.add(out, { ...i, folder: (i.folder && ids.get(i.folder)) || wrap, added: now })
  return out
}

export { b64url }
