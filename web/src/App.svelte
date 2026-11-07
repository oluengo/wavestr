<script lang="ts">
  import { onMount } from 'svelte'
  import { app, ACCENTS } from './lib/app.svelte'
  import { player } from './lib/player.svelte'
  import { t } from './lib/i18n'
  import { codeFrom, decode } from './lib/share'
  import { liveGlobal, profiles, isLiveNow } from './lib/nostr'
  import { liveNow } from './lib/live'
  import { radio, webPlayable } from './lib/radio'
  import Icon from './components/Icon.svelte'
  import Art from './components/Art.svelte'
  import LiveTab from './components/LiveTab.svelte'
  import RadioTab from './components/RadioTab.svelte'
  import FavoritesTab from './components/FavoritesTab.svelte'
  import SettingsTab from './components/SettingsTab.svelte'
  import Player from './components/Player.svelte'
  import IncomingShare from './components/IncomingShare.svelte'
  import { incoming } from './components/incoming.svelte'

  type Tab = 'live' | 'radio' | 'favs' | 'settings'
  const TABS: [Tab, string, string][] = [['live', 'live', 'tab_live'], ['radio', 'radio', 'tab_radio'], ['favs', 'heart', 'tab_favorites'], ['settings', 'settings', 'tab_settings']]
  let tab = $state<Tab>((localStorage.getItem('wavestr.tab') as Tab) || 'live')
  let wide = $state(window.innerWidth >= 1000)
  let sheet = $state(false)
  let installEvent = $state<any>(null)
  let surprising = $state(false)
  $effect(() => { localStorage.setItem('wavestr.tab', tab) })

  // Tema y acento
  $effect(() => {
    const root = document.documentElement
    if (app.theme === 'system') root.removeAttribute('data-theme'); else root.dataset.theme = app.theme
    const dark = app.theme === 'dark' || (app.theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    root.style.setProperty('--accent', ACCENTS[app.accent][dark ? 1 : 0])
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', dark ? '#0f0e15' : '#f4f5f7')
  })

  async function checkHash() {
    const h = location.hash
    if (!h.startsWith('#import=')) return
    const sh = await decode(codeFrom(h) || '')
    history.replaceState(null, '', location.pathname)
    if (sh) { incoming.shared = sh; tab = 'favs' } else app.notify(t('share_bad_link'))
  }

  onMount(() => {
    const r = () => (wide = window.innerWidth >= 1000)
    window.addEventListener('resize', r)
    window.addEventListener('hashchange', checkHash)
    window.addEventListener('beforeinstallprompt', (e: Event) => { e.preventDefault(); installEvent = e })
    checkHash()
    app.checkReminders()
    const iv = setInterval(() => app.checkReminders(), 20_000)
    if (app.favSync && app.canSign) app.syncNow()
    const last = player.lastPlayed(); if (last && !player.now) player.now = last
    if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('./sw.js').catch(() => {})
    return () => { window.removeEventListener('resize', r); clearInterval(iv) }
  })

  // Recordatorio pulsado: si ya está en directo, a sonar.
  $effect(() => {
    const a = app.openStream; if (!a) return
    app.openStream = null
    app.streamFor(a).then(async s => {
      if (s && isLiveNow(s)) { const p = await profiles([s.hostPubkey], app.pubkey); player.play(liveNow(s, p[s.hostPubkey])); openPlayer() }
      else app.notify(t('desk_reminder_not_live'))
    })
  })

  function openPlayer() { if (!wide) sheet = true }

  async function surprise() {
    surprising = true
    try {
      if (Math.random() < 0.5) {
        const list = (await liveGlobal(app.pubkey)).filter(s => s.streamingUrl)
        if (list.length) { const s = list[Math.floor(Math.random() * Math.min(list.length, 20))]; const p = await profiles([s.hostPubkey]); player.play(liveNow(s, p[s.hostPubkey])); openPlayer(); return }
      }
      const st = (await radio.random(40)).filter(s => s.variants.some(webPlayable))
      if (st.length) { player.playStation(st[Math.floor(Math.random() * st.length)]); openPlayer() } else app.notify(t('surprise_empty_all'))
    } catch { app.notify(t('surprise_empty_all')) } finally { surprising = false }
  }
  const install = $derived(installEvent ? () => { installEvent.prompt(); installEvent = null } : null)
</script>

<div class="shell" class:wide>
  {#if wide}
    <nav class="side">
      <div class="brand row"><img src="./icon.svg" alt="" width="32" height="32" /><b>Wavestr</b></div>
      {#each TABS as [id, icon, label]}
        <button class="nav row" class:on={tab === id} onclick={() => (tab = id)}><Icon name={icon} />{t(label)}</button>
      {/each}
      <button class="nav row" onclick={surprise} disabled={surprising}><Icon name="dice" />{t('surprise')}</button>
    </nav>
  {:else}
    <header class="top row">
      <img src="./icon.svg" alt="" width="28" height="28" /><b class="grow">Wavestr</b>
      <button class="icon-btn" onclick={surprise} disabled={surprising} title={t('surprise')}><Icon name="dice" /></button>
    </header>
  {/if}

  <main class="content" class:padded-bottom={!wide}>
    {#if tab === 'live'}<LiveTab onplay={openPlayer} onlogin={() => (tab = 'settings')} />
    {:else if tab === 'radio'}<RadioTab onplay={openPlayer} />
    {:else if tab === 'favs'}<FavoritesTab onplay={openPlayer} />
    {:else}<SettingsTab {install} />{/if}
  </main>

  {#if wide && player.now}
    <aside class="panel"><Player /></aside>
  {/if}
</div>

{#if !wide}
  {#if player.now && !sheet}
    <button class="mini row" onclick={() => (sheet = true)}>
      <Art src={player.now.artwork} id={player.now.id} title={player.now.title} size={40} />
      <div class="grow" style="text-align:left">
        <div class="bold ellipsis">{player.now.title}</div>
        <div class="muted small ellipsis">{player.now.subtitle}</div>
      </div>
      <span class="icon-btn" role="button" tabindex="0" onclick={e => { e.stopPropagation(); player.toggle() }} onkeydown={() => {}}><Icon name={player.playing ? 'pause' : 'play'} /></span>
    </button>
  {/if}
  <nav class="bottom row">
    {#each TABS as [id, icon, label]}
      <button class="tab" class:on={tab === id} onclick={() => { tab = id; sheet = false }}><Icon name={icon} /><span>{t(label)}</span></button>
    {/each}
  </nav>
  {#if sheet && player.now}<div class="sheet"><Player onclose={() => (sheet = false)} /></div>{/if}
{/if}

{#if incoming.shared}<IncomingShare />{/if}
{#if app.toast}<div class="toast">{app.toast}</div>{/if}

<style>
  .shell { min-height: 100vh; }
  .shell.wide { display: grid; grid-template-columns: 220px 1fr auto; }
  .side { position: sticky; top: 0; height: 100vh; padding: 18px 12px; display: flex; flex-direction: column; gap: 4px; border-right: 1px solid var(--line); background: var(--surface); }
  .brand { padding: 4px 10px 18px; font-size: 19px; letter-spacing: -.02em; }
  .brand img { border-radius: 8px; }
  .nav { padding: 10px 12px; border-radius: 12px; font-weight: 600; color: var(--muted); gap: 12px; }
  .nav.on { background: var(--accent-soft); color: var(--accent); }
  .nav:hover:not(.on) { background: var(--surface2); }
  .content { padding: 20px 16px 32px; max-width: 980px; width: 100%; margin: 0 auto; }
  .content.padded-bottom { padding-bottom: 150px; }
  .panel { position: sticky; top: 0; height: 100vh; width: 400px; border-left: 1px solid var(--line); }
  .top { position: sticky; top: 0; z-index: 10; padding: 10px 12px 10px 16px; background: color-mix(in srgb, var(--bg) 92%, transparent); backdrop-filter: blur(8px); gap: 10px; font-size: 18px; }
  .top img { border-radius: 7px; }
  .bottom { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; background: var(--surface); border-top: 1px solid var(--line); padding-bottom: env(safe-area-inset-bottom); }
  .tab { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 0 6px; font-size: 11.5px; font-weight: 600; color: var(--muted); }
  .tab.on { color: var(--accent); }
  .mini { position: fixed; left: 8px; right: 8px; bottom: calc(62px + env(safe-area-inset-bottom)); z-index: 21; background: var(--surface); border: 1px solid var(--line); border-radius: 14px; padding: 8px; gap: 10px; box-shadow: 0 6px 24px rgba(0,0,0,.12); }
  .sheet { position: fixed; inset: 0; z-index: 30; background: var(--surface); padding-top: env(safe-area-inset-top); }
</style>
