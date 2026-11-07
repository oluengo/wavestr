<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { t, plural } from '../lib/i18n'
  import { lib, newId, type FavItem } from '../lib/library'
  import { asLibrary, importInto, stations, streamers } from '../lib/share'
  import Art from './Art.svelte'
  import Modal from './Modal.svelte'
  import { incoming } from './incoming.svelte'

  const sh = $derived(incoming.shared!)
  const already = $derived(new Set(sh.items.filter(i => lib.isFavorite(app.library, i.key)).map(i => i.key)))
  let selected = $state(new Set<string>())
  $effect(() => { selected = new Set(sh.items.map(i => i.key).filter(k => !lib.isFavorite(app.library, k))) })
  const name = $derived(sh.name || t('tab_favorites'))
  const rows = $derived.by(() => {
    const src = asLibrary(sh); const out: [string | null, FavItem][] = []
    lib.itemsIn(src, null).forEach(i => out.push([null, i]))
    const walk = (p: string | null) => lib.subfolders(src, p).forEach(f => {
      const path = lib.path(src, f.id).map(x => x.name).join(' / ')
      lib.itemsIn(src, f.id).forEach((i, n) => out.push([n === 0 ? path : null, i])); walk(f.id)
    })
    walk(null); return out
  })
  function toggle(k: string) { const s = new Set(selected); s.has(k) ? s.delete(k) : s.add(k); selected = s }
  function add() {
    const n = selected.size
    app.updateLibrary(l => importInto(sh, l, selected, t('share_wrapper', name), newId))
    app.notify(plural('share_added', n, n)); incoming.shared = null
  }
  const summary = $derived([stations(sh) && plural('share_stations', stations(sh), stations(sh)), streamers(sh) && plural('share_streamers', streamers(sh), streamers(sh))].filter(Boolean).join(t('share_and')))
</script>

<Modal title={t('share_received', name)} onclose={() => (incoming.shared = null)} wide>
  <p class="muted">{summary}</p>
  {#if sh.kind === 'a'}<p class="muted small">{t('share_into_wrapper', t('share_wrapper', name))}</p>{/if}
  {#if rows.length > 1}
    <div class="row"><button class="btn text" onclick={() => (selected = new Set(sh.items.map(i => i.key).filter(k => !already.has(k))))}>{t('share_select_all')}</button>
      <button class="btn text" onclick={() => (selected = new Set())}>{t('share_select_none')}</button></div>
  {/if}
  <div class="rows">
    {#each rows as [header, i] (i.key)}
      {#if header}<div class="hdr muted small">📁 {header}</div>{/if}
      <label class="row r" class:dim={already.has(i.key)}>
        <input type="checkbox" checked={selected.has(i.key) || already.has(i.key)} disabled={already.has(i.key)} onchange={() => toggle(i.key)} />
        <Art src={i.station?.favicon || i.image} id={i.key} title={i.title} size={34} round={!!i.host} />
        <div class="grow"><div class="ellipsis">{i.title}</div><div class="muted tiny">{t(already.has(i.key) ? 'share_already' : i.host ? 'fav_kind_streamer' : 'fav_kind_station')}</div></div>
      </label>
    {/each}
  </div>
  {#snippet actions()}
    <button class="btn" onclick={() => (incoming.shared = null)}>{t('cancel')}</button>
    <button class="btn primary" onclick={add} disabled={!selected.size}>{t('share_add', selected.size)}</button>
  {/snippet}
</Modal>

<style>
  .rows { max-height: 50vh; overflow: auto; }
  .hdr { padding: 10px 0 2px; font-weight: 600; }
  .r { padding: 5px 0; gap: 10px; cursor: pointer; }
  .dim { opacity: .55; }
</style>
