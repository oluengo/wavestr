// Chat, zaps y reacciones del directo que suena (igual que en Android y PC), cada 15 s.
import { app } from '../lib/app.svelte'
import { activity, bestName, profiles, publish, reactionTags, relaysFor, KIND, unsigned, type Event, type Profile, type Reaction } from '../lib/nostr'
import { sign } from '../lib/signer'

export interface Line { id?: string; pubkey?: string; time: number; author: string; text: string; sats?: number; zapSats: number; reactions: [string, number][]; mine: Set<string> }

export class ChatModel {
  address = $state<string | null>(null)
  host = $state<string | null>(null)
  lines = $state<Line[]>([])
  total = $state(0)
  streamReactions = $state<[string, number][]>([])
  mineStream = $state(new Set<string>())
  loaded = $state(false)
  private timer: number | null = null
  private names = new Map<string, Profile>()
  private local: Reaction[] = []

  watch(address: string | null, host: string | null) {
    if (address === this.address) return
    if (this.timer) clearInterval(this.timer)
    this.address = address; this.host = host; this.lines = []; this.total = 0; this.streamReactions = []; this.loaded = false; this.local = []
    if (!address) return
    this.refresh()
    this.timer = window.setInterval(() => this.refresh(), 15_000)
  }

  async refresh() {
    const address = this.address; if (!address) return
    try {
      const viewer = app.pubkey
      const act = await activity(address, Math.floor(Date.now() / 1000) - 12 * 3600, viewer)
      const who = [...new Set([...act.chat.map(m => m.pubkey), ...act.zaps.map(z => z.senderPubkey).filter(Boolean) as string[]])].filter(p => !this.names.has(p))
      if (who.length) for (const [k, v] of Object.entries(await profiles(who, viewer))) this.names.set(k, v)
      const name = (pk?: string) => (pk ? bestName(this.names.get(pk), pk) : '?')
      const seen = new Set<string>()
      const reactions = [...act.reactions, ...this.local].filter(r => { const k = r.pubkey + r.eventId + r.emoji; return !seen.has(k) && (seen.add(k), true) })
      const counts = (l: Reaction[]): [string, number][] => { const m = new Map<string, number>(); l.forEach(r => m.set(r.emoji, (m.get(r.emoji) || 0) + 1)); return [...m].sort((a, b) => b[1] - a[1]) }
      const ids = new Set(act.chat.map(m => m.id))
      const lines: Line[] = [
        ...act.chat.map(m => {
          const r = reactions.filter(x => x.eventId === m.id)
          return { id: m.id, pubkey: m.pubkey, time: m.createdAt, author: name(m.pubkey), text: m.content,
            zapSats: act.zaps.filter(z => z.eventId === m.id).reduce((n, z) => n + z.amountSats, 0), reactions: counts(r), mine: new Set(r.filter(x => x.pubkey === viewer).map(x => x.emoji)) }
        }),
        ...act.zaps.filter(z => !z.eventId || !ids.has(z.eventId)).map(z => ({ pubkey: z.senderPubkey, time: z.createdAt, author: name(z.senderPubkey), text: z.comment, sats: z.amountSats, zapSats: 0, reactions: [] as [string, number][], mine: new Set<string>() })),
      ].sort((a, b) => a.time - b.time).slice(-80)
      if (this.address !== address) return
      const sr = reactions.filter(r => !r.eventId)
      this.lines = lines; this.total = act.zaps.reduce((n, z) => n + z.amountSats, 0)
      this.streamReactions = counts(sr); this.mineStream = new Set(sr.filter(r => r.pubkey === viewer).map(r => r.emoji)); this.loaded = true
    } catch { this.loaded = true }
  }

  async send(text: string): Promise<boolean> {
    const me = app.pubkey; const a = this.address
    if (!me || !a) return false
    const ev = await sign(app.session, unsigned(me, KIND.LIVE_CHAT, [['a', a, '', 'root']], text))
    if (!ev || !(await this.pub(ev))) return false
    this.lines = [...this.lines, { id: ev.id, pubkey: me, time: ev.created_at, author: bestName((await profiles([me], me))[me], me), text, zapSats: 0, reactions: [], mine: new Set() }].slice(-80)
    return true
  }

  async react(emoji: string, line: Line | null): Promise<boolean> {
    const me = app.pubkey; const a = this.address
    const target = line?.pubkey ?? this.host
    if (!me || !a || !target) return false
    const ev = await sign(app.session, unsigned(me, KIND.REACTION, reactionTags(a, target, line?.id), emoji))
    if (!ev || !(await this.pub(ev))) return false
    this.local.push({ id: ev.id, pubkey: me, emoji, eventId: line?.id, createdAt: ev.created_at })
    this.refresh(); return true
  }

  private async pub(ev: Event) { return (await publish(await relaysFor(ev.pubkey), ev)) > 0 }
}

export const QUICK = ['❤️', '🔥', '😂', '🤙', '👏', '🫂']
