<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { player, stationNowPlaying, type NowPlaying } from '../lib/player.svelte'
  import { t, plural } from '../lib/i18n'
  import { lib, newId, favStation, type FavItem, type Folder } from '../lib/library'
  import { liveOf, profiles, type LiveStream } from '../lib/nostr'
  import { liveNow } from '../lib/live'
  import { parsePlaylist, webPlayable } from '../lib/radio'
  import { ofAll, ofFolder, ofItem, type Shared } from '../lib/share'
  import Art from './Art.svelte'
  import Icon from './Icon.svelte'
  import Menu from './Menu.svelte'
  import Modal from './Modal.svelte'
  import ShareDialog from './ShareDialog.svelte'
  import ImportLink from './ImportLink.svelte'

  let { onplay }: { onplay: () => void } = $props()
  let current = $state<string | null>(null)
  let live = $state(new Map<string, LiveStream>())
  let naming = $state<{ folder?: Folder } | null>(null)
  let name = $state('')
  let moving = $state<FavItem | null>(null)
  let deleting = $state<Folder | null>(null)
  let sharing = $state<Shared | null>(null)
  let importing = $state(false)
  let fileInput: HTMLInputElement

  const L = $derived(app.library)
  const folders = $derived(lib.subfolders(L, current))
  const items = $derived(lib.itemsIn(L, current))
  const path = $derived(lib.path(L, current))

  $effect(() => {
    const hosts = app.library.favorites.map(f => f.host).filter(Boolean) as string[]
    const pk = app.pubkey
    liveOf(hosts, pk).then(m => (live = m)).catch(() => {})
  })

  function play(i: FavItem) {
    if (i.station) {
      if (!i.station.variants.some(webPlayable)) { app.notify(t('web_http_only')); return }
      player.setContext(items.map(x => x.station && stationNowPlaying(x.station)).filter(Boolean) as NowPlaying[])
      player.playStation(i.station); onplay()
    } else if (i.host) {
      const s = live.get(i.host)
      if (!s) { app.notify(t('fav_offline')); return }
      player.setContext(L.favorites.map(f => f.host && live.get(f.host)).filter(Boolean).map(x => liveNow(x as LiveStream)))
      profiles([i.host]).then(p => { player.play(liveNow(s, p[i.host!])); onplay() })
    }
  }

  function saveName() {
    const n = name.trim(); if (!n) return
    if (naming?.folder) app.updateLibrary(l => lib.renameFolder(l, naming!.folder!.id, n))
    else app.updateLibrary(l => lib.addFolder(l, newId(), n, current))
    naming = null; name = ''
  }

  async function importFile(e: Event) {
    const f = (e.target as HTMLInputElement).files?.[0]; if (!f) return
    const text = await f.text()
    const list = parsePlaylist(text, f.name)
    if (!list.length) { app.notify(t('import_err_none')); return }
    app.updateLibrary(l => {
      let out = { ...l, imported: [...l.imported, ...list.filter(s => !l.imported.some(x => x.id === s.id))] }
      for (const s of list) out = lib.add(out, { ...favStation(s), folder: current ?? undefined })
      return out
    })
    app.notify(plural('import_done', list.length, list.length))
    fileInput.value = ''
  }
</script>

<header class="head row">
  <div class="grow">
    <h1>{t('tab_favorites')}</h1>
    <p class="muted small">{t('fav_sub')}</p>
  </div>
  <button class="icon-btn" title={t('fav_new_folder')} onclick={() => { naming = {}; name = '' }}><Icon name="newFolder" /></button>
  <button class="icon-btn" title={t(current ? 'share_folder' : 'share_all')} disabled={!L.favorites.length}
    onclick={() => (sharing = current ? ofFolder(L, current) : ofAll(L, t('tab_favorites')))}><Icon name="share" /></button>
  <Menu>
    {#snippet trigger(toggle)}<button class="icon-btn" onclick={toggle}><Icon name="more" /></button>{/snippet}
    {#snippet children(close)}
      <button class="item" onclick={() => { importing = true; close() }}><Icon name="link" size={18} />{t('desk_import_link')}</button>
      <button class="item" onclick={() => { fileInput.click(); close() }}><Icon name="upload" size={18} />{t('web_import_playlist')}</button>
    {/snippet}
  </Menu>
  <input type="file" accept=".m3u,.m3u8,.pls,audio/x-mpegurl,audio/x-scpls" bind:this={fileInput} onchange={importFile} hidden />
</header>

{#if path.length}
  <nav class="crumbs row">
    <button class="btn text" onclick={() => (current = null)}>{t('tab_favorites')}</button>
    {#each path as f}<span class="muted">›</span><button class="btn text" onclick={() => (current = f.id)}>{f.name}</button>{/each}
  </nav>
{/if}

{#if !L.favorites.length && !L.folders.length}
  <p class="muted pad">{t('web_fav_empty')}</p>
{/if}

{#if folders.length}
  <div class="section-label">{t(current ? 'fav_subfolders' : 'fav_folders')}</div>
  <div class="folders">
    {#each folders as f (f.id)}
      {@const n = lib.itemsIn(L, f.id).length}
      <div class="folder card row">
        <button class="row grow left" onclick={() => (current = f.id)}>
          <span class="fic"><Icon name="folder" /></span>
          <div class="grow"><div class="bold ellipsis">{f.name}</div><div class="muted tiny">{n ? plural('fav_count', n, n) : t('fav_empty_short')}</div></div>
        </button>
        <Menu>
          {#snippet trigger(toggle)}<button class="icon-btn" onclick={toggle}><Icon name="more" size={20} /></button>{/snippet}
          {#snippet children(close)}
            <button class="item" onclick={() => { naming = { folder: f }; name = f.name; close() }}>{t('web_rename')}</button>
            <button class="item" onclick={() => { sharing = ofFolder(L, f.id); close() }}>{t('share_folder')}</button>
            <button class="item" style="color: var(--live)" onclick={() => { deleting = f; close() }}>{t('fav_delete_folder')}</button>
          {/snippet}
        </Menu>
      </div>
    {/each}
  </div>
{/if}

{#if items.length}
  <div class="section-label">{t(current ? 'fav_in_folder' : 'fav_no_folder')}</div>
  <div class="card list">
    {#each items as i (i.key)}
      {@const isLive = i.host ? live.has(i.host) : false}
      <div class="row it" class:on={player.now?.id === i.key || (!!i.host && player.now?.hostPubkey === i.host)}>
        <button class="row grow left" onclick={() => play(i)}>
          <Art src={i.station?.favicon || i.image} id={i.key} title={i.title} size={44} round={!!i.host} />
          <div class="grow">
            <div class="row" style="gap:6px">{#if isLive}<span class="badge-live">{t('live_badge')}</span>{/if}<span class="bold ellipsis">{i.title}</span></div>
            <div class="muted small ellipsis">{i.host ? (isLive ? live.get(i.host)?.title || t('fav_kind_streamer') : t('fav_offline')) : (i.station?.country || t('fav_kind_station'))}</div>
          </div>
        </button>
        <Menu>
          {#snippet trigger(toggle)}<button class="icon-btn" onclick={toggle}><Icon name="more" size={20} /></button>{/snippet}
          {#snippet children(close)}
            <button class="item" onclick={() => { moving = i; close() }}>{t('fav_move_to')}</button>
            <button class="item" onclick={() => { sharing = ofItem(i); close() }}>{t('share')}</button>
            <button class="item" style="color: var(--live)" onclick={() => { app.updateLibrary(l => lib.remove(l, i.key)); close() }}>{t('fav_remove')}</button>
          {/snippet}
        </Menu>
      </div>
    {/each}
  </div>
{:else if current}
  <p class="muted pad">{t('fav_empty_folder')}</p>
{/if}
{#if !app.favSync}<p class="muted tiny pad">{t('web_fav_sync_note')}</p>{/if}

{#if naming}
  <Modal title={naming.folder ? t('web_rename') : t('fav_new_folder')} onclose={() => (naming = null)}>
    <!-- svelte-ignore a11y_autofocus -->
    <input class="field" bind:value={name} placeholder={t('fav_name_hint')} autofocus onkeydown={e => e.key === 'Enter' && saveName()} />
    {#snippet actions()}
      <button class="btn" onclick={() => (naming = null)}>{t('cancel')}</button>
      <button class="btn primary" onclick={saveName} disabled={!name.trim()}>{t('save')}</button>
    {/snippet}
  </Modal>
{/if}

{#if moving}
  <Modal title={t('fav_move_to')} onclose={() => (moving = null)}>
    <div class="list">
      <button class="item-row" onclick={() => { app.updateLibrary(l => lib.move(l, moving!.key, null)); moving = null }}>{t('fav_no_folder')}</button>
      {#each lib.tree(L) as [f, depth]}
        <button class="item-row" style:padding-left="{12 + depth * 18}px" class:bold={moving.folder === f.id}
          onclick={() => { app.updateLibrary(l => lib.move(l, moving!.key, f.id)); moving = null }}>📁 {f.name}</button>
      {/each}
    </div>
  </Modal>
{/if}

{#if deleting}
  <Modal title={t('fav_delete_title', deleting.name)} onclose={() => (deleting = null)}>
    <p>{t('fav_delete_body', lib.folder(L, deleting.parent)?.name || t('tab_favorites'))}</p>
    {#snippet actions()}
      <button class="btn" onclick={() => (deleting = null)}>{t('cancel')}</button>
      <button class="btn danger" onclick={() => { const id = deleting!.id; if (current === id) current = deleting!.parent ?? null; app.updateLibrary(l => lib.deleteFolder(l, id)); deleting = null }}>{t('fav_delete_folder')}</button>
    {/snippet}
  </Modal>
{/if}

{#if sharing}<ShareDialog shared={sharing} onclose={() => (sharing = null)} />{/if}
{#if importing}<ImportLink onclose={() => (importing = false)} />{/if}

<style>
  .head { margin-bottom: 8px; align-items: flex-start; }
  h1 { font-size: 24px; letter-spacing: -.02em; }
  .crumbs { flex-wrap: wrap; gap: 2px; margin-bottom: 4px; }
  .pad { padding: 14px 4px; }
  .folders { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
  .folder { padding: 6px 4px 6px 12px; }
  .fic { color: var(--accent); display: grid; place-items: center; width: 36px; height: 36px; background: var(--accent-soft); border-radius: 10px; flex: none; }
  .left { text-align: left; gap: 12px; padding: 4px 0; }
  .it { padding: 6px 6px 6px 10px; }
  .it.on { background: var(--accent-soft); }
  .item-row { text-align: left; padding: 10px 12px; border-radius: 8px; }
  .item-row:hover { background: var(--surface2); }
</style>
