<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { player } from '../lib/player.svelte'
  import { t, fmt } from '../lib/i18n'
  import { bestName, liveFromFollows, liveGlobal, profiles, upcoming, follows, type LiveStream, type Profile, type Scheduled } from '../lib/nostr'
  import Art from './Art.svelte'
  import Icon from './Icon.svelte'
  import Upcoming from './Upcoming.svelte'
  import { liveNow } from '../lib/live'
  import { untrack } from 'svelte'

  let { onplay, onlogin }: { onplay: () => void; onlogin: () => void } = $props()
  let scope = $state<'following' | 'global'>(localStorage.getItem('wavestr.scope') === 'global' ? 'global' : 'following')
  let items = $state<{ s: LiveStream; host?: Profile }[]>([])
  let loading = $state(true)
  let failed = $state(false)
  let up = $state<{ s: Scheduled; host?: Profile }[]>([])
  let reload = $state(0)

  $effect(() => {
    const pk = app.pubkey; const sc = scope; const r = reload
    untrack(() => run(pk, sc, r))
  })
  function run(pk: string | null, sc: 'following' | 'global', reload: number) {
    localStorage.setItem('wavestr.scope', sc)
    loading = true; failed = false
    ;(async () => {
      try {
        const list = sc === 'following' ? (pk ? await liveFromFollows(pk, reload > 0) : []) : await liveGlobal(pk)
        const p = await profiles(list.map(s => s.hostPubkey), pk)
        items = list.map(s => ({ s, host: p[s.hostPubkey] }))
      } catch { failed = true } finally { loading = false }
    })()
    ;(async () => {
      try {
        const hosts = [...app.library.favorites.map(f => f.host).filter(Boolean) as string[], ...(pk ? await follows(pk) : [])]
        const list = await upcoming(hosts, pk)
        app.refreshReminders(list)
        const p = await profiles(list.map(s => s.hostPubkey), pk)
        up = list.slice(0, 12).map(s => ({ s, host: p[s.hostPubkey] }))
      } catch {}
    })()
  }

  function play(i: { s: LiveStream; host?: Profile }) {
    player.setContext(items.filter(x => x.s.streamingUrl).map(x => liveNow(x.s, x.host)))
    player.play(liveNow(i.s, i.host)); onplay()
  }
</script>

<header class="head">
  <div class="grow">
    <h1>{t('live_title')}</h1>
    <p class="muted small">{t(scope === 'following' ? 'live_sub_following' : 'live_sub_global')}</p>
  </div>
  <button class="icon-btn" onclick={() => reload++} title={t('refresh')}><Icon name="refresh" /></button>
</header>
<div class="row seg">
  <button class="chip" class:on={scope === 'following'} onclick={() => (scope = 'following')}>{t('chip_following')}</button>
  <button class="chip" class:on={scope === 'global'} onclick={() => (scope = 'global')}>{t('chip_global')}</button>
</div>

{#if scope === 'following' && !app.pubkey}
  <div class="card note"><p>{t('web_login_prompt')}</p><div class="row"><button class="btn primary" onclick={onlogin}>{t('settings_account')}</button><button class="btn" onclick={() => (scope = 'global')}>{t('chip_global')}</button></div></div>
{:else if loading && !items.length}
  <p class="muted pad">{t('web_loading')}</p>
{:else if failed}
  <p class="muted pad">{t('live_error')}</p>
{:else if !items.length}
  <p class="muted pad">{t(scope === 'following' ? 'web_live_empty_following' : 'web_live_empty_global')}</p>
{:else}
  <div class="grid">
    {#each items as i (i.s.address)}
      <button class="live card" class:on={player.now?.id === i.s.address} onclick={() => play(i)}>
        <div class="thumb">
          {#if i.s.image}<img src={i.s.image} alt="" loading="lazy" referrerpolicy="no-referrer" />{:else}<Art src={i.host?.picture} id={i.s.address} title={i.s.title} size={64} round />{/if}
          <span class="badge-live">{t('live_badge')}</span>
          {#if i.s.participants}<span class="viewers">{fmt(i.s.participants)}</span>{/if}
        </div>
        <div class="row meta">
          <Art src={i.host?.picture} id={i.s.hostPubkey} title={bestName(i.host, i.s.hostPubkey)} size={32} round />
          <div class="grow">
            <div class="bold ellipsis">{i.s.title || t('untitled_stream')}</div>
            <div class="muted small ellipsis">{bestName(i.host, i.s.hostPubkey)}</div>
          </div>
        </div>
      </button>
    {/each}
  </div>
{/if}

{#if up.length}
  <div class="section-label">{t('upcoming')}</div>
  <div class="card list">
    {#each up as u (u.s.address)}<Upcoming s={u.s} host={u.host} />{/each}
  </div>
{/if}

<style>
  .head { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 10px; }
  h1 { font-size: 24px; letter-spacing: -.02em; }
  .seg { margin-bottom: 14px; }
  .pad { padding: 20px 4px; }
  .note { padding: 16px; display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 12px; }
  .live { text-align: left; overflow: hidden; padding: 0; }
  .live.on { outline: 2px solid var(--accent); }
  .thumb { position: relative; aspect-ratio: 16/9; background: var(--surface2); display: grid; place-items: center; }
  .thumb img { width: 100%; height: 100%; object-fit: cover; position: absolute; inset: 0; }
  .thumb .badge-live { position: absolute; left: 8px; top: 8px; }
  .viewers { position: absolute; right: 8px; bottom: 8px; background: rgba(0,0,0,.6); color: #fff; font-size: 12px; padding: 2px 7px; border-radius: 6px; }
  .meta { padding: 10px 12px; }
</style>
