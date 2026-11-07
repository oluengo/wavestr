<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { t, locale } from '../lib/i18n'
  import { bestName, type Profile, type Scheduled } from '../lib/nostr'
  import Art from './Art.svelte'
  import Icon from './Icon.svelte'
  import Menu from './Menu.svelte'

  let { s, host }: { s: Scheduled; host?: Profile } = $props()
  const LEADS = [0, 5, 15, 60]
  const current = $derived(app.reminders.find(r => r.address === s.address))
  const name = $derived(bestName(host, s.hostPubkey))

  /** «Hoy, 21:00» en tu zona y, si el organizador está en otra, también la suya. */
  function when(): string {
    const at = new Date(s.startsAt * 1000)
    const time = (tz?: string) => new Intl.DateTimeFormat(locale(), { hour: '2-digit', minute: '2-digit', timeZone: tz }).format(at)
    const day = (d: Date) => d.toDateString()
    const today = new Date(); const tomorrow = new Date(Date.now() + 86400000)
    const d = day(at) === day(today) ? t('time_today') : day(at) === day(tomorrow) ? t('time_tomorrow')
      : new Intl.DateTimeFormat(locale(), { weekday: 'short', day: 'numeric', month: 'short' }).format(at)
    let out = `${d}, ${time()}`
    if (s.tzid) {
      try {
        const mine = Intl.DateTimeFormat().resolvedOptions().timeZone
        if (time(s.tzid) !== time(mine) || new Intl.DateTimeFormat('en', { timeZone: s.tzid, day: 'numeric' }).format(at) !== new Intl.DateTimeFormat('en', { day: 'numeric' }).format(at))
          out += ' · ' + t('time_their_zone', time(s.tzid), s.tzid.split('/').pop()!.replace(/_/g, ' '))
      } catch {}
    }
    return out
  }
  const lead = (m: number) => (m === 0 ? t('reminder_at_start') : t('reminder_before', m >= 60 ? t('lead_hours', m / 60) : t('lead_minutes', m)))
</script>

<div class="row up">
  <Art src={s.image || host?.picture} id={s.address} title={s.title} size={48} />
  <div class="grow">
    <div class="bold ellipsis">{s.title}</div>
    <div class="muted small ellipsis">{name}</div>
    <div class="small when">{when()}</div>
  </div>
  <Menu>
    {#snippet trigger(toggle)}
      <button class="icon-btn" onclick={toggle} title={t(current ? 'reminder_edit' : 'reminder_add')} style:color={current ? 'var(--accent)' : 'var(--muted)'}>
        <Icon name={current ? 'bellOn' : 'bell'} />
      </button>
    {/snippet}
    {#snippet children(close)}
      <div class="label">{t('reminder_title')}</div>
      {#each LEADS as m}
        <button class="item" class:bold={current?.leadMin === m} onclick={() => { app.setReminder(s, name, m); app.notify(t('reminder_saved')); close() }}>{lead(m)}</button>
      {/each}
      {#if current}<button class="item" style="color: var(--live)" onclick={() => { app.removeReminder(s.address); close() }}>{t('reminder_remove')}</button>{/if}
      <div class="label">{t('web_reminder_note')}</div>
    {/snippet}
  </Menu>
</div>

<style>
  .up { padding: 10px 12px; }
  .when { color: var(--accent); font-weight: 600; }
</style>
