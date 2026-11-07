import strings from '../strings.json'

type Dict = Record<string, string | Record<string, string>>
const all = strings as Record<string, Dict>

export const LANGS = ['es', 'en', 'ca'] as const
export type Lang = (typeof LANGS)[number]

function detect(): Lang {
  const saved = localStorage.getItem('wavestr.lang')
  if (saved && (LANGS as readonly string[]).includes(saved)) return saved as Lang
  const nav = (navigator.language || 'en').slice(0, 2)
  return ((LANGS as readonly string[]).includes(nav) ? nav : 'en') as Lang
}

export let lang: Lang = detect()
export const locale = () => ({ es: 'es-ES', en: 'en-GB', ca: 'ca-ES' })[lang]

export function setLang(l: Lang | null) {
  if (l) localStorage.setItem('wavestr.lang', l)
  else localStorage.removeItem('wavestr.lang')
  location.reload()
}

/** Sustituye %1$s, %2$d… y %s / %d en orden, como en Android. */
function format(s: string, args: unknown[]): string {
  let i = 0
  return s.replace(/%(?:(\d+)\$)?([sd])/g, (_, n) => {
    const v = n ? args[Number(n) - 1] : args[i++]
    return v === undefined ? '' : String(v)
  })
}

export function t(key: string, ...args: unknown[]): string {
  const v = all[lang]?.[key] ?? all.en?.[key]
  if (typeof v !== 'string') return key
  return format(v, args)
}

export function plural(key: string, n: number, ...args: unknown[]): string {
  const v = all[lang]?.[key] ?? all.en?.[key]
  if (!v || typeof v === 'string') return typeof v === 'string' ? format(v, args) : key
  const q = new Intl.PluralRules(locale()).select(n)
  return format(v[q] ?? v.other ?? Object.values(v)[0], args)
}

export const fmt = (n: number) => new Intl.NumberFormat(locale()).format(n)
