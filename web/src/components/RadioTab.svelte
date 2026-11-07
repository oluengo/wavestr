<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { player, stationNowPlaying } from '../lib/player.svelte'
  import { t } from '../lib/i18n'
  import { radio, guessCountry, webPlayable, type Station } from '../lib/radio'
  import { favStation, lib } from '../lib/library'
  import Art from './Art.svelte'
  import Icon from './Icon.svelte'
  import { untrack } from 'svelte'

  let { onplay }: { onplay: () => void } = $props()
  type Mode = 'popular' | 'near' | 'tags' | 'search'
  let mode = $state<Mode>('popular')
  let q = $state('')
  let tag = $state<string | null>(null)
  let tags = $state<[string, number][]>([])
  let country = $state(localStorage.getItem('wavestr.country') || guessCountry())
  let countries = $state<{ code: string; name: string }[]>([])
  let list = $state<Station[]>([])
  let loading = $state(false)
  let failed = $state(false)
  let timer: number | undefined

  async function load(f: () => Promise<Station[]>) {
    loading = true; failed = false
    try { list = await f() } catch { failed = true; list = [] } finally { loading = false }
  }
  $effect(() => {
    const m = mode, c = country, tg = tag
    untrack(() => {
      if (m === 'popular') load(() => radio.top())
      else if (m === 'near') { localStorage.setItem('wavestr.country', c); load(() => radio.byCountry(c)) }
      else if (m === 'tags') { if (tg) load(() => radio.byTag(tg)); else list = [] }
    })
  })
  $effect(() => {
    const m = mode
    untrack(() => {
      if (m === 'tags' && !tags.length) radio.tags().then(v => (tags = v)).catch(() => {})
      if (m === 'near' && !countries.length) radio.countries().then(c => (countries = c.sort((a, b) => a.name.localeCompare(b.name)))).catch(() => {})
    })
  })

  function search() {
    clearTimeout(timer)
    const v = q.trim()
    if (v.length < 2) { if (mode === 'search') mode = 'popular'; return }
    timer = window.setTimeout(() => { mode = 'search'; load(() => radio.search(v)) }, 350)
  }

  function play(s: Station) {
    const ctx = list.map(x => stationNowPlaying(x)).filter(Boolean) as any[]
    player.setContext(ctx); player.playStation(s); onplay()
  }
  const usable = (s: Station) => s.variants.some(webPlayable)
</script>

<header class="head">
  <h1>{t('tab_radio')}</h1>
  <p class="muted small">{t('web_radio_sub')}</p>
</header>
<div class="search row">
  <Icon name="search" size={20} />
  <input class="grow" placeholder={t('radio_search_hint')} bind:value={q} oninput={search} />
  {#if q}<button class="icon-btn" onclick={() => { q = ''; mode = 'popular' }}><Icon name="close" size={18} /></button>{/if}
</div>
<div class="row chips">
  <button class="chip" class:on={mode === 'popular'} onclick={() => { mode = 'popular'; q = '' }}>{t('radio_popular')}</button>
  <button class="chip" class:on={mode === 'near'} onclick={() => { mode = 'near'; q = '' }}>{t('radio_near')}</button>
  <button class="chip" class:on={mode === 'tags'} onclick={() => { mode = 'tags'; q = '' }}>{t('radio_tags')}</button>
</div>

{#if mode === 'near'}
  <div class="row sub">
    <select class="field" bind:value={country}>
      {#each countries.length ? countries : [{ code: country, name: country }] as c}<option value={c.code}>{c.name}</option>{/each}
    </select>
  </div>
{:else if mode === 'tags'}
  <div class="tags">
    {#each tags.slice(0, 40) as [name]}<button class="chip" class:on={tag === name} onclick={() => (tag = name)}>{name}</button>{/each}
  </div>
  {#if !tag}<p class="muted pad">{t('radio_pick_tag')}</p>{/if}
{/if}

{#if loading}
  <p class="muted pad">{t('web_loading')}</p>
{:else if failed}
  <p class="muted pad">{t('radio_error')}</p>
{:else if mode === 'search' && !list.length}
  <p class="muted pad">{t('radio_no_results', q)}</p>
{:else if list.length}
  <div class="card list">
    {#each list as s (s.id)}
      {@const fav = lib.isFavorite(app.library, s.id)}
      <div class="row st" class:on={player.now?.id === s.id} class:off={!usable(s)}>
        <button class="row grow left" onclick={() => usable(s) && play(s)} disabled={!usable(s)}>
          <Art src={s.favicon} id={s.id} title={s.name} size={44} />
          <div class="grow">
            <div class="bold ellipsis">{s.name}</div>
            <div class="muted small ellipsis">{usable(s) ? [s.country, s.tags.slice(0, 3).join(', ')].filter(Boolean).join(' · ') : t('web_http_only')}</div>
          </div>
        </button>
        <button class="icon-btn" onclick={() => app.updateLibrary(l => lib.toggle(l, favStation(s)))} title={t(fav ? 'fav_remove' : 'fav_add')} style:color={fav ? 'var(--live)' : 'var(--muted)'}>
          <Icon name={fav ? 'heartFill' : 'heart'} />
        </button>
      </div>
    {/each}
  </div>
  <p class="muted tiny pad">{t('radio_source_note')} {t('web_http_note')}</p>
{/if}

<style>
  .head { margin-bottom: 12px; }
  h1 { font-size: 24px; letter-spacing: -.02em; }
  .search { background: var(--surface); border: 1px solid var(--line); border-radius: 14px; padding: 4px 6px 4px 12px; color: var(--muted); }
  .search input { border: 0; outline: 0; background: none; padding: 8px 0; color: var(--ink); }
  .chips { margin: 12px 0; flex-wrap: wrap; }
  .sub { margin-bottom: 12px; }
  .sub .field { max-width: 320px; }
  .tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
  .pad { padding: 16px 4px; }
  .st { padding: 6px 6px 6px 10px; }
  .st.on { background: var(--accent-soft); }
  .st.off { opacity: .55; }
  .left { text-align: left; padding: 4px 0; gap: 12px; }
</style>
