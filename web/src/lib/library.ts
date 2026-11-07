// Favoritos en carpetas: mismo JSON que Android y PC (así se sincronizan por Nostr y se importan copias).
import type { Station } from './radio'

export interface Folder { id: string; name: string; parent?: string }
export interface FavItem {
  key: string; kind: 'STATION' | 'STREAMER'; title: string; subtitle?: string; image?: string
  folder?: string; station?: Station; host?: string; added: number
}
export interface Library { folders: Folder[]; favorites: FavItem[]; imported: Station[] }

export const emptyLibrary = (): Library => ({ folders: [], favorites: [], imported: [] })

export const favStation = (s: Station): FavItem => ({ key: s.id, kind: 'STATION', title: s.name, subtitle: s.country, image: s.favicon, station: s, added: Date.now() })
export const favStreamer = (pk: string, name: string, image?: string): FavItem => ({ key: 'npub:' + pk, kind: 'STREAMER', title: name, image, host: pk, added: Date.now() })

export const lib = {
  isFavorite: (l: Library, key: string) => l.favorites.some(f => f.key === key),
  folder: (l: Library, id?: string | null) => l.folders.find(f => f.id === id),
  subfolders: (l: Library, parent?: string | null) => l.folders.filter(f => (f.parent ?? null) === (parent ?? null)).sort((a, b) => a.name.localeCompare(b.name)),
  itemsIn: (l: Library, folder?: string | null) => l.favorites.filter(f => (f.folder ?? null) === (folder ?? null)),
  path(l: Library, id?: string | null): Folder[] {
    const out: Folder[] = []; const seen = new Set<string>()
    let f = lib.folder(l, id)
    while (f && !seen.has(f.id)) { seen.add(f.id); out.unshift(f); f = lib.folder(l, f.parent) }
    return out
  },
  tree(l: Library, parent: string | null = null, depth = 0): [Folder, number][] {
    return lib.subfolders(l, parent).flatMap(f => [[f, depth] as [Folder, number], ...lib.tree(l, f.id, depth + 1)])
  },
  addFolder: (l: Library, id: string, name: string, parent?: string | null): Library =>
    ({ ...l, folders: [...l.folders, { id, name: name.trim().slice(0, 40), ...(parent && lib.folder(l, parent) ? { parent } : {}) }] }),
  renameFolder: (l: Library, id: string, name: string): Library => ({ ...l, folders: l.folders.map(f => (f.id === id ? { ...f, name: name.trim().slice(0, 40) } : f)) }),
  deleteFolder(l: Library, id: string): Library {
    const f = lib.folder(l, id); if (!f) return l
    return {
      ...l,
      folders: l.folders.filter(x => x.id !== id).map(x => (x.parent === id ? { ...x, parent: f.parent } : x)),
      favorites: l.favorites.map(x => (x.folder === id ? { ...x, folder: f.parent } : x)),
    }
  },
  add: (l: Library, i: FavItem): Library => (lib.isFavorite(l, i.key) ? l : { ...l, favorites: [...l.favorites, i] }),
  remove: (l: Library, key: string): Library => ({ ...l, favorites: l.favorites.filter(f => f.key !== key) }),
  toggle: (l: Library, i: FavItem): Library => (lib.isFavorite(l, i.key) ? lib.remove(l, i.key) : lib.add(l, i)),
  move: (l: Library, key: string, folder?: string | null): Library =>
    ({ ...l, favorites: l.favorites.map(f => (f.key === key ? { ...f, folder: folder && lib.folder(l, folder) ? folder : undefined } : f)) }),
  merge(a: Library, b: Library): Library {
    const fs = new Map([...a.folders, ...b.folders].map(f => [f.id, f]))
    const ids = new Set(fs.keys())
    return {
      folders: [...fs.values()].map(f => (f.parent && !ids.has(f.parent) ? { ...f, parent: undefined } : f)),
      favorites: [...new Map([...a.favorites, ...b.favorites].map(f => [f.key, f])).values()].map(f => (f.folder && !ids.has(f.folder) ? { ...f, folder: undefined } : f)),
      imported: [...new Map([...a.imported, ...b.imported].map(s => [s.id, s])).values()],
    }
  },
}

// ---------- JSON (formato del núcleo) ----------

export function stationJson(s: Station) {
  return {
    id: s.id, name: s.name,
    variants: s.variants.map(v => ({ url: v.url, ...(v.codec ? { codec: v.codec } : {}), bitrate: v.bitrate, hls: v.hls, ...(v.uuid ? { uuid: v.uuid } : {}) })),
    ...(s.homepage ? { homepage: s.homepage } : {}), ...(s.favicon ? { favicon: s.favicon } : {}), tags: s.tags,
    ...(s.country ? { country: s.country } : {}), ...(s.cc ? { cc: s.cc } : {}), ...(s.lang ? { lang: s.lang } : {}),
    votes: s.votes, ok: s.ok, imported: s.imported,
  }
}

export function stationFrom(o: any): Station | undefined {
  try {
    const variants = (o.variants as any[]).map(v => ({ url: String(v.url), codec: v.codec || undefined, bitrate: Number(v.bitrate) || 0, hls: !!v.hls, uuid: v.uuid || undefined }))
    if (!o.id || !o.name || !variants.length) return undefined
    return { id: o.id, name: o.name, variants, homepage: o.homepage, favicon: o.favicon, tags: Array.isArray(o.tags) ? o.tags.map(String) : [],
      country: o.country, cc: o.cc, lang: o.lang, votes: Number(o.votes) || 0, ok: o.ok !== false, imported: !!o.imported }
  } catch { return undefined }
}

export function libraryToJson(l: Library) {
  return {
    v: 1,
    folders: l.folders.map(f => ({ id: f.id, name: f.name, ...(f.parent ? { parent: f.parent } : {}) })),
    favorites: l.favorites.map(i => ({
      key: i.key, kind: i.kind, title: i.title, ...(i.subtitle ? { subtitle: i.subtitle } : {}), ...(i.image ? { image: i.image } : {}),
      ...(i.folder ? { folder: i.folder } : {}), ...(i.host ? { host: i.host } : {}), added: i.added, ...(i.station ? { station: stationJson(i.station) } : {}),
    })),
    imported: l.imported.map(stationJson),
  }
}

export function libraryFromJson(o: any): Library {
  if (!o) return emptyLibrary()
  const src = o.library && typeof o.library === 'object' ? o.library : o
  const folders: Folder[] = (src.folders || []).filter((f: any) => f?.id && f?.name).map((f: any) => ({ id: f.id, name: f.name, ...(f.parent ? { parent: f.parent } : {}) }))
  const ids = new Set(folders.map(f => f.id))
  const favorites: FavItem[] = (src.favorites || []).map((f: any) => {
    if (!f?.key || (f.kind !== 'STATION' && f.kind !== 'STREAMER')) return null
    return { key: f.key, kind: f.kind, title: f.title || '', subtitle: f.subtitle, image: f.image, folder: ids.has(f.folder) ? f.folder : undefined,
      station: f.station ? stationFrom(f.station) : undefined, host: f.host, added: Number(f.added) || 0 }
  }).filter(Boolean)
  const imported = (src.imported || []).map(stationFrom).filter(Boolean) as Station[]
  return { folders, favorites, imported: [...new Map(imported.map(s => [s.id, s])).values()] }
}

/** Lista M3U de las emisoras, con carpetas como group-title. */
export function libraryToM3u(l: Library): string {
  const clean = (s: string) => s.replace(/"/g, "'").replace(/[\n,]/g, ' ').trim()
  let s = '#EXTM3U\n'
  for (const i of l.favorites) {
    if (!i.station) continue
    const g = i.folder ? lib.path(l, i.folder).map(f => f.name).join(' / ') : ''
    s += '#EXTINF:-1' + (g ? ` group-title="${clean(g)}"` : '') + (i.station.favicon ? ` tvg-logo="${i.station.favicon}"` : '') + ',' + clean(i.title) + '\n' + i.station.variants[0].url + '\n'
  }
  return s
}

export const newId = () => 'f' + Math.random().toString(36).slice(2, 10)
