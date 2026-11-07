// Reproductor de la web: <audio>/<video> con hls.js para HLS, Media Session (pantalla de bloqueo,
// auriculares), anterior/siguiente, temporizador y refuerzo de volumen (cuando el stream lo permite).
import type Hls from 'hls.js'
// hls.js solo se descarga si hace falta (streams HLS en navegadores sin soporte nativo).
let HlsCtor: typeof Hls | null = null
const loadHls = async () => (HlsCtor ??= (await import('hls.js')).default)
import type { Station } from './radio'
import { radio, webPlayable } from './radio'

export interface NowPlaying {
  id: string; url: string; title: string; subtitle: string; artwork?: string
  station?: Station; variant?: number; hostPubkey?: string; isLive: boolean; hasVideo: boolean
}

export function stationNowPlaying(s: Station, variant?: number): NowPlaying | null {
  const vs = s.variants
  const idx = variant ?? Math.max(0, vs.findIndex(webPlayable))
  const v = vs[idx]
  if (!v) return null
  return { id: s.id, url: v.url, title: s.name, subtitle: [s.country, s.tags.slice(0, 3).join(', ')].filter(Boolean).join(' · '), artwork: s.favicon, station: s, variant: idx, isLive: false, hasVideo: false }
}

const corsCache = new Map<string, boolean>()
/** ¿El servidor permite leer el audio (CORS)? Hace falta para pasar del 100 % con Web Audio. */
async function corsOk(url: string): Promise<boolean> {
  if (corsCache.has(url)) return corsCache.get(url)!
  let ok = false
  try {
    const ctl = new AbortController()
    const t = setTimeout(() => ctl.abort(), 6000)
    const r = await fetch(url, { mode: 'cors', signal: ctl.signal })
    ok = r.ok; r.body?.cancel().catch(() => {}); clearTimeout(t)
  } catch { ok = false }
  corsCache.set(url, ok)
  return ok
}

class Player {
  now = $state<NowPlaying | null>(null)
  playing = $state(false)
  buffering = $state(false)
  error = $state(false)
  audioOnly = $state(true)
  volume = $state(Math.min(1, Number(localStorage.getItem('wavestr.volume') ?? 1)))
  sleepAt = $state<number | null>(null)
  /** El refuerzo no se puede aplicar a lo que suena (el servidor no lo permite). */
  boostBlocked = $state(false)
  /** Lista por la que se mueven anterior/siguiente (emisoras o streams). */
  context = $state<NowPlaying[]>([])
  /** Vídeo y audio normal. */
  el: HTMLVideoElement
  /** Audio con refuerzo (Web Audio): solo para emisoras que lo permiten. */
  private boostEl: HTMLAudioElement | null = null
  private media: HTMLMediaElement
  private hls: Hls | null = null
  private sleepTimer: number | null = null
  private gain: GainNode | null = null
  private ctx: AudioContext | null = null
  private seq = 0

  constructor() {
    this.el = document.createElement('video')
    this.el.playsInline = true
    this.el.preload = 'none'
    this.media = this.el
    this.wire(this.el)
    this.el.volume = this.volume
    const ms = navigator.mediaSession
    if (ms) {
      ms.setActionHandler('play', () => this.resume())
      ms.setActionHandler('pause', () => this.pause())
      ms.setActionHandler('stop', () => this.stop())
      ms.setActionHandler('previoustrack', () => this.skip(-1))
      ms.setActionHandler('nexttrack', () => this.skip(1))
    }
  }

  private wire(m: HTMLMediaElement) {
    const on = (ev: string, f: () => void) => m.addEventListener(ev, () => { if (this.media === m) f() })
    on('playing', () => { this.playing = true; this.buffering = false; this.error = false })
    on('pause', () => { this.playing = false })
    on('waiting', () => { this.buffering = true })
    on('canplay', () => { this.buffering = false })
    on('error', () => { if (m.getAttribute('src') || this.hls) { this.error = true; this.buffering = false; this.playing = false } })
  }

  private boostTarget(): HTMLAudioElement {
    if (!this.boostEl) {
      this.boostEl = document.createElement('audio')
      this.boostEl.crossOrigin = 'anonymous'
      this.boostEl.preload = 'none'
      this.wire(this.boostEl)
      this.ctx = new AudioContext()
      this.gain = this.ctx.createGain()
      this.ctx.createMediaElementSource(this.boostEl).connect(this.gain).connect(this.ctx.destination)
    }
    return this.boostEl
  }

  async play(np: NowPlaying) {
    const my = ++this.seq
    this.stopMedia()
    this.now = np; this.error = false; this.buffering = true; this.boostBlocked = false
    if (!np.hasVideo) this.audioOnly = true
    const url = np.url.startsWith('http://') && location.protocol === 'https:' ? 'https://' + np.url.slice(7) : np.url
    const db = this.boostFor(np.id)
    let target: HTMLMediaElement = this.el
    if (db && this.audioOnly) {
      if (await corsOk(url)) target = this.boostTarget(); else this.boostBlocked = true
      if (my !== this.seq) return
    }
    this.media = target
    target.volume = this.volume
    const isHls = /\.m3u8(\?|$)/i.test(url) || !!np.station?.variants[np.variant ?? 0]?.hls
    const H = isHls && !target.canPlayType('application/vnd.apple.mpegurl') ? await loadHls() : null
    if (my !== this.seq) return
    if (H && H.isSupported()) {
      this.hls = new H({ maxBufferLength: 30 })
      this.hls.on(H.Events.ERROR, (_, d) => { if (d.fatal) { this.error = true; this.buffering = false } })
      this.hls.loadSource(url); this.hls.attachMedia(target)
    } else target.src = url
    if (target === this.boostEl) { this.gain!.gain.value = Math.pow(10, db / 20); this.ctx!.resume() }
    target.play().catch(() => { this.buffering = false })
    if (np.station) radio.countClick(np.station.variants[np.variant ?? 0]?.uuid)
    if (navigator.mediaSession) {
      navigator.mediaSession.metadata = new MediaMetadata({ title: np.title, artist: np.subtitle, artwork: np.artwork ? [{ src: np.artwork, sizes: '512x512' }] : [] })
    }
    localStorage.setItem('wavestr.last', JSON.stringify(np))
  }

  playStation(s: Station, variant?: number) { const np = stationNowPlaying(s, variant); if (np) this.play(np) }
  setContext(list: NowPlaying[]) { this.context = list }
  canSkip() { return this.context.length > 1 }
  skip(dir: number) {
    const list = this.context; if (list.length < 2 || !this.now) return
    const i = list.findIndex(n => n.id === this.now!.id)
    this.play(list[((i < 0 ? 0 : i + dir) + list.length) % list.length])
  }
  setAudioOnly(v: boolean) { if (this.audioOnly !== v) { this.audioOnly = v; if (this.now) this.play(this.now) } }

  resume() {
    if (!this.now) return
    if (!this.media.getAttribute('src') && !this.hls) this.play(this.now)
    else { this.ctx?.resume(); this.media.play().catch(() => {}) }
  }
  pause() { this.media.pause() }
  toggle() { if (this.playing) this.pause(); else this.resume() }
  stop() { this.seq++; this.stopMedia(); this.now = null; this.cancelSleep() }

  private stopMedia() {
    this.hls?.destroy(); this.hls = null
    for (const m of [this.el, this.boostEl]) if (m) { m.pause(); m.removeAttribute('src'); m.load() }
    this.playing = false
  }

  setVolume(v: number) {
    this.volume = v; localStorage.setItem('wavestr.volume', String(v))
    this.media.volume = v
  }

  /** Refuerzo por emisora (dB), para las que suenan flojo. */
  boostFor(id?: string) { return Number(localStorage.getItem('wavestr.boost.' + id) || 0) }
  setBoost(id: string, db: number) {
    if (db) localStorage.setItem('wavestr.boost.' + id, String(db)); else localStorage.removeItem('wavestr.boost.' + id)
    if (this.now?.id === id) {
      if (this.media === this.boostEl && db && this.gain) this.gain.gain.value = Math.pow(10, db / 20)
      else this.play(this.now)
    }
  }

  // ---- Temporizador ----
  sleepIn(min: number) {
    this.cancelSleep()
    this.sleepAt = Date.now() + min * 60_000
    this.sleepTimer = window.setTimeout(() => this.fadeOut(), min * 60_000)
  }
  cancelSleep() { if (this.sleepTimer) clearTimeout(this.sleepTimer); this.sleepTimer = null; this.sleepAt = null }
  private fadeOut() {
    const m = this.media; const start = m.volume; let i = 0
    const t = window.setInterval(() => {
      i++; m.volume = Math.max(0, start * (1 - i / 20))
      if (i >= 20) { clearInterval(t); this.pause(); m.volume = this.volume; this.sleepAt = null }
    }, 1000)
  }

  lastPlayed(): NowPlaying | null { try { return JSON.parse(localStorage.getItem('wavestr.last') || 'null') } catch { return null } }
}

export const player = new Player()
