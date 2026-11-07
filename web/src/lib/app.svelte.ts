// Estado de la app: sesión, favoritos, cartera, recordatorios y sincronización de favoritos por Nostr.
import { emptyLibrary, libraryFromJson, libraryToJson, lib, type Library } from './library'
import { canSign, loadSession, logout as signerLogout, nip44Decrypt, nip44Encrypt, sign, type Session } from './signer'
import { KIND, ownRelays, pool, publish, stream, unsigned, verify, type Scheduled } from './nostr'
import { parseNwc, type Nwc } from './zap'
import { t } from './i18n'

const load = <T>(k: string, d: T): T => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d } catch { return d } }
const save = (k: string, v: unknown) => { try { localStorage.setItem(k, JSON.stringify(v)) } catch {} }

export type Theme = 'system' | 'light' | 'dark'
export const ACCENTS = { violet: ['#6D28D9', '#A78BFA'], green: ['#0E7C6B', '#3FBFA6'], coral: ['#D9480F', '#FF8A5B'], blue: ['#1D4ED8', '#7EA6FF'], raspberry: ['#BE185D', '#F472B6'] } as const
export type Accent = keyof typeof ACCENTS

export interface Reminder { address: string; title: string; host: string; hostName: string; startsAt: number; leadMin: number }

class App {
  session = $state<Session | null>(loadSession())
  library = $state<Library>(libraryFromJson(load('wavestr.library', null)))
  theme = $state<Theme>(load('wavestr.theme', 'system'))
  accent = $state<Accent>(load('wavestr.accent', 'violet'))
  favSync = $state<boolean>(load('wavestr.favsync', false))
  syncStatus = $state<'off' | 'working' | 'done' | 'failed' | 'no_signer' | 'too_big'>('off')
  lastSync = $state<number>(load('wavestr.favsync.last', 0))
  nwc = $state<string | null>(load('wavestr.nwc', null))
  budget = $state<number>(load('wavestr.budget', 10_000))
  spent = $state<{ month: string; sats: number }>(load('wavestr.spent', { month: '', sats: 0 }))
  defaultZap = $state<number>(load('wavestr.defaultzap', 100))
  reminders = $state<Reminder[]>(load('wavestr.reminders', []))
  toast = $state<string | null>(null)
  /** Recordatorio pulsado: directo a abrir. */
  openStream = $state<string | null>(null)
  private syncTimer: number | null = null
  private toastTimer: number | null = null

  get pubkey() { return this.session?.pubkey ?? null }
  get canSign() { return canSign(this.session) }
  get wallet(): Nwc | null { return this.nwc ? parseNwc(this.nwc) : null }
  get spentThisMonth() { return this.spent.month === month() ? this.spent.sats : 0 }

  notify(msg: string) {
    this.toast = msg
    if (this.toastTimer) clearTimeout(this.toastTimer)
    this.toastTimer = window.setTimeout(() => (this.toast = null), 3500)
  }

  setSession(s: Session | null) { this.session = s; if (s && this.favSync && canSign(s)) this.syncNow() }
  logout() { signerLogout(); this.session = null; this.favSync = false; save('wavestr.favsync', false); this.syncStatus = 'off' }

  setTheme(t: Theme) { this.theme = t; save('wavestr.theme', t) }
  setAccent(a: Accent) { this.accent = a; save('wavestr.accent', a) }

  updateLibrary(f: (l: Library) => Library) {
    this.library = f(this.library)
    save('wavestr.library', libraryToJson(this.library))
    if (this.favSync && this.canSign) {
      if (this.syncTimer) clearTimeout(this.syncTimer)
      this.syncTimer = window.setTimeout(() => this.sync(false), 4000)
    }
  }

  // ---- Cartera ----
  setNwc(uri: string | null) { this.nwc = uri; save('wavestr.nwc', uri) }
  setBudget(n: number) { this.budget = n; save('wavestr.budget', n) }
  setDefaultZap(n: number) { this.defaultZap = n; save('wavestr.defaultzap', n) }
  addSpent(sats: number) { this.spent = { month: month(), sats: this.spentThisMonth + sats }; save('wavestr.spent', this.spent) }

  // ---- Recordatorios (con la web abierta) ----
  setReminder(s: Scheduled, hostName: string, leadMin: number) {
    this.reminders = [...this.reminders.filter(r => r.address !== s.address), { address: s.address, title: s.title, host: s.hostPubkey, hostName, startsAt: s.startsAt, leadMin }]
    save('wavestr.reminders', this.reminders)
    if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission()
  }
  removeReminder(address: string) { this.reminders = this.reminders.filter(r => r.address !== address); save('wavestr.reminders', this.reminders) }
  refreshReminders(list: Scheduled[]) {
    let changed = false
    const next = this.reminders.map(r => { const s = list.find(x => x.address === r.address); if (s && (s.startsAt !== r.startsAt || s.title !== r.title)) { changed = true; return { ...r, startsAt: s.startsAt, title: s.title } } return r })
    if (changed) { this.reminders = next; save('wavestr.reminders', next) }
  }
  checkReminders() {
    const nowMs = Date.now()
    const due = this.reminders.filter(r => (r.startsAt - r.leadMin * 60) * 1000 <= nowMs)
    if (!due.length) return
    for (const r of due.filter(r => nowMs - (r.startsAt - r.leadMin * 60) * 1000 < 30 * 60_000)) {
      const text = r.leadMin === 0 ? t('reminder_now', r.hostName) : t('reminder_soon', r.leadMin, r.hostName)
      if ('Notification' in window && Notification.permission === 'granted') {
        const n = new Notification(r.title, { body: text, icon: './icon-192.png', tag: r.address })
        n.onclick = () => { window.focus(); this.openStream = r.address; n.close() }
      } else this.notify(r.title + ' · ' + text)
    }
    this.reminders = this.reminders.filter(r => !due.includes(r)); save('wavestr.reminders', this.reminders)
  }

  // ---- Favoritos en Nostr (mismo evento que Android y PC) ----
  setFavSync(v: boolean) { this.favSync = v; save('wavestr.favsync', v); if (v) this.syncNow(); else this.syncStatus = 'off' }
  syncNow() { this.sync(true) }

  private async sync(pull: boolean) {
    const s = this.session; const me = s?.pubkey
    if (!s || !me || !this.favSync || !canSign(s)) return
    this.syncStatus = 'working'
    try {
      const relays = await ownRelays(me)
      if (pull) {
        const evs = (await pool.querySync(relays, { kinds: [KIND.APP_DATA], authors: [me], '#d': [D_TAG], limit: 1 }, { maxWait: 5000 }))
          .filter(e => e.pubkey === me && verify(e)).sort((a, b) => b.created_at - a.created_at)
        const remote = evs[0]
        const remoteAt = load<number>('wavestr.favsync.remote', 0)
        if (remote && remote.created_at > remoteAt) {
          const plain = await nip44Decrypt(s, me, remote.content)
          if (plain == null) { this.syncStatus = 'no_signer'; return }
          const remoteLib = libraryFromJson(JSON.parse(plain))
          const lastHash = load<string | null>('wavestr.favsync.hash', null)
          const local = this.library
          const next = !lastHash || (await hash(local)) !== lastHash ? lib.merge(remoteLib, local) : remoteLib
          this.library = next; save('wavestr.library', libraryToJson(next))
          save('wavestr.favsync.remote', remote.created_at)
          if ((await hash(next)) === (await hash(remoteLib))) { save('wavestr.favsync.hash', await hash(next)); this.done(); return }
        }
      }
      const h = await hash(this.library)
      if (h === load('wavestr.favsync.hash', null)) { this.done(); return }
      const json = JSON.stringify(libraryToJson(this.library))
      if (new TextEncoder().encode(json).length > 64_000) { this.syncStatus = 'too_big'; return }
      const enc = await nip44Encrypt(s, me, json)
      if (!enc) { this.syncStatus = 'no_signer'; return }
      const ev = await sign(s, unsigned(me, KIND.APP_DATA, [['d', D_TAG]], enc))
      if (!ev) { this.syncStatus = 'no_signer'; return }
      if (!(await publish(relays, ev))) { this.syncStatus = 'failed'; return }
      save('wavestr.favsync.hash', h); save('wavestr.favsync.remote', ev.created_at)
      this.done()
    } catch { this.syncStatus = 'failed' }
  }
  private done() { this.syncStatus = 'done'; this.lastSync = Date.now(); save('wavestr.favsync.last', this.lastSync) }

  async streamFor(address: string) { return stream(address, this.pubkey) }
}

const D_TAG = 'wavestr/favorites'
const month = () => new Date().toISOString().slice(0, 7)
async function hash(l: Library): Promise<string> {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(libraryToJson(l))))
  return [...new Uint8Array(d)].map(b => b.toString(16).padStart(2, '0')).join('')
}

export const app = new App()
export { emptyLibrary }
