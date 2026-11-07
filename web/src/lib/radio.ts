// Radio Browser y emisoras, con el mismo formato que el núcleo Kotlin (para compartir favoritos).

export interface Variant { url: string; codec?: string; bitrate: number; hls: boolean; uuid?: string }
export interface Station {
  id: string; name: string; variants: Variant[]; homepage?: string; favicon?: string; tags: string[]
  country?: string; cc?: string; lang?: string; votes: number; ok: boolean; imported: boolean
}

export const variantLabel = (v: Variant) => {
  const c = v.codec?.trim().toUpperCase()
  return [c && c !== 'UNKNOWN' ? c : null, v.bitrate > 0 ? `${v.bitrate} kbps` : null].filter(Boolean).join(' · ') || null
}

/** En una web https solo suenan las emisoras https (el navegador bloquea http o intenta pasarlo a https). */
export const webPlayable = (v: Variant) => v.url.startsWith('https://') || location.protocol === 'http:'

const FALLBACK = ['https://de1.api.radio-browser.info', 'https://fi1.api.radio-browser.info', 'https://de2.api.radio-browser.info']
let base: string | null = null

async function json(path: string): Promise<any> {
  const servers = base ? [base, ...FALLBACK.filter(s => s !== base)] : FALLBACK
  let last: unknown
  for (const s of servers.slice(0, 3)) {
    try {
      const r = await fetch(s + path, { headers: { Accept: 'application/json' } })
      if (!r.ok) throw new Error(String(r.status))
      base = s
      return await r.json()
    } catch (e) { last = e }
  }
  throw last
}

const normalize = (s: string) => s.toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '').replace(/[^a-z0-9]+/g, ' ').trim()
const host = (u?: string) => { try { return new URL(u!).host.replace(/^www\./, '').toLowerCase() } catch { return null } }

function parse(arr: any[]): any[] {
  return arr.map(o => {
    const url = (o.url_resolved || o.url || '').trim()
    if (!/^https?:\/\//i.test(url) || !o.stationuuid || !o.name?.trim()) return null
    return {
      uuid: o.stationuuid, name: o.name.trim(), url, homepage: o.homepage?.trim() || undefined,
      favicon: o.favicon?.startsWith('http') ? o.favicon : undefined,
      tags: [...new Set(String(o.tags || '').split(',').map((s: string) => s.trim().toLowerCase()).filter(Boolean))].slice(0, 8),
      country: o.country || undefined, cc: (o.countrycode || '').toUpperCase() || undefined, lang: o.language || undefined,
      codec: o.codec || undefined, bitrate: Number(o.bitrate) || 0, hls: o.hls === 1 || o.hls === '1' || o.hls === true,
      ok: o.lastcheckok === undefined ? true : o.lastcheckok === 1 || o.lastcheckok === '1', votes: Number(o.votes) || 0,
    }
  }).filter(Boolean)
}

/** Una ficha por URL → una emisora por nombre + web (o país), con las URLs como variantes de calidad. */
export function group(raw: any[]): Station[] {
  const groups = new Map<string, any[]>()
  for (const r of raw) {
    const k = normalize(r.name) + '|' + (host(r.homepage) || r.cc || '')
    if (!groups.has(k)) groups.set(k, [])
    groups.get(k)!.push(r)
  }
  return [...groups.values()].map(list => {
    const main = list.reduce((a, b) => (b.votes > a.votes ? b : a))
    const seen = new Set<string>()
    const variants: Variant[] = list.filter(r => { const u = r.url.replace(/\/$/, ''); return !seen.has(u) && (seen.add(u), true) })
      .map(r => ({ url: r.url, codec: r.codec, bitrate: r.bitrate, hls: r.hls, uuid: r.uuid }))
      .sort((a, b) => b.bitrate - a.bitrate || a.url.localeCompare(b.url))
    return {
      id: 'rb:' + main.uuid, name: main.name, variants, homepage: main.homepage, favicon: list.find(r => r.favicon)?.favicon,
      tags: [...new Set(list.flatMap(r => r.tags))].slice(0, 8), country: main.country, cc: main.cc, lang: main.lang,
      votes: list.reduce((n, r) => n + r.votes, 0), ok: list.some(r => r.ok), imported: false,
    }
  })
}

const stations = async (path: string) => group(parse(await json(path)))
const enc = encodeURIComponent

export const radio = {
  top: (limit = 80) => stations(`/json/stations/search?order=votes&reverse=true&hidebroken=true&limit=${limit}`),
  random: (limit = 40) => stations(`/json/stations/search?order=random&hidebroken=true&limit=${limit}`),
  byCountry: (cc: string) => stations(`/json/stations/search?countrycode=${enc(cc)}&order=votes&reverse=true&hidebroken=true&limit=120`),
  byTag: (tag: string) => stations(`/json/stations/search?tag=${enc(tag)}&tagExact=true&order=votes&reverse=true&hidebroken=true&limit=120`),
  async search(q: string) {
    const byName = parse(await json(`/json/stations/search?name=${enc(q)}&order=votes&reverse=true&limit=80`))
    const byTag = byName.length < 20 ? parse(await json(`/json/stations/search?tag=${enc(q.toLowerCase())}&order=votes&reverse=true&hidebroken=true&limit=40`)) : []
    return group([...byName, ...byTag.filter(t => !byName.some(n => n.uuid === t.uuid))])
  },
  async tags(): Promise<[string, number][]> {
    const arr = await json('/json/tags?order=stationcount&reverse=true&hidebroken=true&limit=80')
    return arr.map((o: any) => [String(o.name || '').trim().toLowerCase(), Number(o.stationcount) || 0]).filter(([n]: [string]) => n.length >= 2 && n.length <= 30)
  },
  async countries(min = 15): Promise<{ code: string; name: string }[]> {
    const arr = await json('/json/countries?order=stationcount&reverse=true&hidebroken=true')
    return arr.filter((o: any) => (o.stationcount || 0) >= min && o.iso_3166_1).map((o: any) => ({ code: String(o.iso_3166_1).toUpperCase(), name: o.name }))
  },
  countClick(uuid?: string) { if (uuid) json(`/json/url/${enc(uuid)}`).catch(() => {}) },
}

/** País por defecto a partir del idioma del navegador (es-ES → ES). */
export function guessCountry(): string {
  const parts = (navigator.language || '').split('-')
  return (parts[1] || (parts[0] === 'ca' ? 'ES' : parts[0] === 'en' ? 'GB' : parts[0] || 'ES')).toUpperCase()
}

/** Lista M3U o PLS → emisoras importadas (con todas las URLs). */
export function parsePlaylist(text: string, fileName = ''): Station[] {
  const out: Station[] = []
  const add = (name: string, url: string, logo?: string, group?: string) => {
    if (!/^https?:\/\//i.test(url)) return
    out.push({ id: 'imp:' + hash(url), name: name || url, variants: [{ url, bitrate: 0, hls: /\.m3u8(\?|$)/i.test(url) }], favicon: logo, tags: group ? [group.toLowerCase()] : [], votes: 0, ok: true, imported: true })
  }
  if (/^\[playlist\]/im.test(text) || fileName.toLowerCase().endsWith('.pls')) {
    const m = new Map<string, { f?: string; t?: string }>()
    for (const line of text.split(/\r?\n/)) {
      const r = /^(File|Title)(\d+)=(.*)$/i.exec(line.trim()); if (!r) continue
      const e = m.get(r[2]) || {}; if (r[1].toLowerCase() === 'file') e.f = r[3].trim(); else e.t = r[3].trim(); m.set(r[2], e)
    }
    for (const e of m.values()) if (e.f) add(e.t || '', e.f)
    return out
  }
  let name = '', logo: string | undefined, group: string | undefined
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim()
    if (line.startsWith('#EXTINF')) {
      name = line.slice(line.lastIndexOf(',') + 1).trim()
      logo = /tvg-logo="([^"]+)"/.exec(line)?.[1]; group = /group-title="([^"]+)"/.exec(line)?.[1]
    } else if (line && !line.startsWith('#')) { add(name, line, logo, group); name = ''; logo = undefined; group = undefined }
  }
  return out
}

function hash(s: string) { let h = 0; for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0; return (h >>> 0).toString(16) }
