<script lang="ts">
  import { app, ACCENTS, type Accent, type Theme } from '../lib/app.svelte'
  import { t, fmt, lang, setLang, plural, LANGS, type Lang } from '../lib/i18n'
  import { bestName, BUILTIN_RELAYS, defaultRelays, normalizePubkey, normalizeRelay, npub, profiles, setAppRelays, type Profile } from '../lib/nostr'
  import { hasExtension, loginBunker, loginExtension, loginNpub, onBunkerAuth } from '../lib/signer'
  import { libraryFromJson, libraryToJson, libraryToM3u, lib } from '../lib/library'
  import { nwcTest, parseNwc } from '../lib/zap'
  import Art from './Art.svelte'
  import Modal from './Modal.svelte'

  let { install }: { install: (() => void) | null } = $props()
  let me = $state<Profile | null>(null)
  $effect(() => { const pk = app.pubkey; me = null; if (pk) profiles([pk], pk).then(p => (me = p[pk] ?? null)) })

  let npubText = $state(''); let npubBad = $state(false)
  let bunkerOpen = $state(false); let bunkerText = $state(''); let bunkerBusy = $state(false); let bunkerErr = $state<string | null>(null); let authUrl = $state<string | null>(null)
  let nwcText = $state(''); let nwcMsg = $state<string | null>(null); let testing = $state(false)
  let relays = $state(defaultRelays().join('\n'))
  let fileInput: HTMLInputElement
  onBunkerAuth(url => { authUrl = url; window.open(url, '_blank', 'noopener') })

  async function ext() { try { app.setSession(await loginExtension()) } catch { app.notify(t('web_no_extension')) } }
  function viaNpub() { const hex = normalizePubkey(npubText); if (!hex) { npubBad = true; return } app.setSession(loginNpub(hex)); npubText = '' }
  async function viaBunker() {
    bunkerBusy = true; bunkerErr = null
    try { app.setSession(await loginBunker(bunkerText)); bunkerOpen = false; bunkerText = '' }
    catch (e) { bunkerErr = (e as Error).message === 'bad-uri' ? t('bunker_bad_uri') : t('bunker_failed') }
    finally { bunkerBusy = false; authUrl = null }
  }
  async function connectNwc() {
    const c = parseNwc(nwcText); if (!c) { nwcMsg = t('wallet_invalid'); return }
    app.setNwc(nwcText.trim()); nwcText = ''; testing = true
    nwcMsg = t((await nwcTest(c)) ? 'wallet_connected_ok' : 'wallet_saved_untested'); testing = false
  }
  async function test() { const c = app.wallet; if (!c) return; testing = true; nwcMsg = t((await nwcTest(c)) ? 'wallet_test_ok' : 'wallet_test_fail'); testing = false }

  function download(name: string, text: string, type: string) {
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type })); a.download = name; a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 5000)
  }
  const exportJson = () => download('wavestr-favoritos.json', JSON.stringify({ app: 'wavestr', v: 1, exported: Date.now(), library: libraryToJson(app.library) }, null, 2), 'application/json')
  const exportM3u = () => download('wavestr.m3u', libraryToM3u(app.library), 'audio/x-mpegurl')
  async function importJson(e: Event) {
    const f = (e.target as HTMLInputElement).files?.[0]; if (!f) return
    try {
      const o = JSON.parse(await f.text())
      if (!o.library && !o.favorites && !o.folders) throw 0
      const l = libraryFromJson(o)
      app.updateLibrary(cur => lib.merge(cur, l))
      app.notify(t('backup_imported', l.favorites.length, l.folders.length, l.imported.length))
    } catch { app.notify(t('backup_invalid')) }
    fileInput.value = ''
  }
  function saveRelays() {
    const list = [...new Set(relays.split(/\s+/).map(normalizeRelay).filter(Boolean) as string[])]
    setAppRelays(list.length ? list : null); relays = defaultRelays().join('\n'); app.notify(t('web_relays_saved'))
  }
  const syncText = $derived(({ working: t('fav_sync_working'), done: app.lastSync ? t('desk_fav_sync_done', new Date(app.lastSync).toLocaleString()) : '', failed: t('desk_fav_sync_failed'), no_signer: t('desk_fav_sync_no_signer'), too_big: t('desk_fav_sync_too_big'), off: '' } as Record<string, string>)[app.syncStatus])
  const ACC: Accent[] = ['violet', 'green', 'coral', 'blue', 'raspberry']
</script>

<h1>{t('tab_settings')}</h1>

<div class="section-label">{t('settings_account')}</div>
<div class="card box">
  {#if !app.session}
    <p class="bold">{t('account_none')}</p>
    <div class="col">
      <button class="btn primary" onclick={ext} disabled={!hasExtension()}>{t('web_login_extension')}</button>
      {#if !hasExtension()}<p class="muted tiny">{t('web_no_extension')}</p>{/if}
      <button class="btn" onclick={() => (bunkerOpen = true)}>{t('login_bunker')}</button>
    </div>
    <hr />
    <p class="muted small">{t('web_npub_explain')}</p>
    <div class="row">
      <input class="field grow" placeholder="npub1…" bind:value={npubText} oninput={() => (npubBad = false)} onkeydown={e => e.key === 'Enter' && viaNpub()} />
      <button class="btn" onclick={viaNpub}>{t('login_npub')}</button>
    </div>
    {#if npubBad}<p class="tiny" style="color: var(--live)">{t('npub_invalid')}</p>{/if}
  {:else}
    <div class="row">
      <Art src={me?.picture} id={app.session.pubkey} title={bestName(me ?? undefined, app.session.pubkey)} size={48} round />
      <div class="grow">
        <div class="bold">{bestName(me ?? undefined, app.session.pubkey)}</div>
        <div class="muted small">{t({ nip07: 'web_account_extension', bunker: 'account_bunker', npub: 'account_readonly' }[app.session.method])}</div>
      </div>
      <button class="btn" onclick={() => app.logout()}>{t('logout')}</button>
    </div>
    <p class="muted tiny mono">{npub(app.session.pubkey)}</p>
  {/if}
</div>

<div class="section-label">{t('wallet_title')}</div>
<div class="card box">
  {#if app.wallet}
    <div class="row">
      <div class="grow"><div class="bold">{app.wallet.lud16 || 'NWC'}</div><div class="muted small">{t('wallet_connected_sub', fmt(app.spentThisMonth), fmt(app.budget))}</div></div>
      <button class="btn text" onclick={test} disabled={testing}>{t('wallet_test')}</button>
      <button class="btn text" style="color: var(--live)" onclick={() => { app.setNwc(null); nwcMsg = null }}>{t('wallet_disconnect')}</button>
    </div>
    <p class="bold small">{t('wallet_budget')}</p>
    <div class="row wrap">{#each [1000, 5000, 10000, 50000, 100000] as b}<button class="chip" class:on={app.budget === b} onclick={() => app.setBudget(b)}>{fmt(b)}</button>{/each}</div>
    <p class="muted tiny">{t('wallet_budget_sub')}</p>
    <p class="bold small">{t('wallet_default_zap')}</p>
    <div class="row wrap">{#each [21, 100, 500, 1000, 5000] as a}<button class="chip" class:on={app.defaultZap === a} onclick={() => app.setDefaultZap(a)}>{fmt(a)}</button>{/each}</div>
  {:else}
    <p class="bold">{t('wallet_none')}</p>
    <p class="muted small">{t('web_wallet_none_sub')}</p>
    <div class="row">
      <input class="field grow" placeholder="nostr+walletconnect://…" bind:value={nwcText} />
      <button class="btn" onclick={connectNwc} disabled={!nwcText.trim() || testing}>{t('wallet_connect_short')}</button>
    </div>
    <p class="muted tiny">{t('web_wallet_security')}</p>
  {/if}
  {#if nwcMsg}<p class="small">{nwcMsg}</p>{/if}
</div>

<div class="section-label">{t('fav_sync_title')}</div>
<div class="card box">
  <div class="row">
    <div class="grow"><div class="bold">{t('fav_sync_toggle')}</div><div class="muted small">{t(app.canSign ? 'desk_fav_sync_sub' : 'web_fav_sync_needs_signer')}</div></div>
    <button class="switch" class:on={app.favSync && app.canSign} disabled={!app.canSign} onclick={() => app.setFavSync(!app.favSync)} aria-label={t('fav_sync_toggle')}></button>
  </div>
  {#if app.favSync && app.canSign}
    <div class="row"><span class="muted small grow">{syncText}</span><button class="btn text" onclick={() => app.syncNow()} disabled={app.syncStatus === 'working'}>{t('fav_sync_now')}</button></div>
  {/if}
</div>

<div class="section-label">{t('settings_appearance_lang')}</div>
<div class="card box">
  <p class="bold small">{t('settings_theme')}</p>
  <div class="row wrap">{#each ['system', 'light', 'dark'] as th}<button class="chip" class:on={app.theme === th} onclick={() => app.setTheme(th as Theme)}>{t('theme_' + th)}</button>{/each}</div>
  <p class="bold small">{t('settings_accent')}</p>
  <div class="row wrap">
    {#each ACC as a}<button class="dot" class:on={app.accent === a} style:background={ACCENTS[a][0]} onclick={() => app.setAccent(a)} title={t('accent_' + a)} aria-label={t('accent_' + a)}></button>{/each}
  </div>
  <p class="bold small">{t('settings_language')}</p>
  <div class="row wrap">
    {#each LANGS as l}<button class="chip" class:on={lang === l} onclick={() => setLang(l as Lang)}>{({ es: 'Español', en: 'English', ca: 'Català' })[l]}</button>{/each}
  </div>
</div>

<div class="section-label">{t('backup_title')}</div>
<div class="card box">
  <button class="opt" onclick={exportJson}><div class="bold">{t('backup_export')}</div><div class="muted small">{t('backup_export_sub')}</div></button>
  <button class="opt" onclick={() => fileInput.click()}><div class="bold">{t('backup_import')}</div><div class="muted small">{t('backup_import_sub')}</div></button>
  <button class="opt" onclick={exportM3u}><div class="bold">{t('backup_m3u')}</div><div class="muted small">{t('backup_m3u_sub')}</div></button>
  <input type="file" accept=".json,application/json" bind:this={fileInput} onchange={importJson} hidden />
  <p class="muted tiny">{t('web_backup_note', app.library.favorites.length, app.library.folders.length, app.library.imported.length)}</p>
</div>

<div class="section-label">{t('relays_app')}</div>
<div class="card box">
  <p class="muted small">{t('relays_app_sub')}</p>
  <textarea class="field mono" rows="4" bind:value={relays}></textarea>
  <div class="row"><button class="btn" onclick={saveRelays}>{t('save')}</button>
    <button class="btn text" onclick={() => { setAppRelays(null); relays = BUILTIN_RELAYS.join('\n') }}>{t('relays_restore')}</button></div>
</div>

{#if install}
  <div class="section-label">{t('web_install_title')}</div>
  <div class="card box"><p class="muted small">{t('web_install_sub')}</p><button class="btn primary" onclick={install}>{t('web_install')}</button></div>
{/if}

<div class="section-label">{t('settings_about')}</div>
<div class="card box">
  <p class="bold">Wavestr · web</p>
  <p class="muted small">{t('web_about')}</p>
  <div class="row wrap">
    <a class="btn" href="https://github.com/oluengo/wavestr#readme" target="_blank" rel="noopener">{t('about_source')}</a>
    <a class="btn" href="https://github.com/oluengo/wavestr/releases" target="_blank" rel="noopener">{t('web_get_apps')}</a>
    <a class="btn" href="https://github.com/oluengo/wavestr/issues" target="_blank" rel="noopener">{t('about_bug')}</a>
  </div>
  <hr />
  <p class="bold">{t('support_title')}</p>
  <p class="muted small">{t('support_body')}</p>
  {#each [['₿ Bitcoin', 'bc1pnvdct6hfnh67p7dcg2e5mdveyz8jlxuzv4yflkxqccc72qkuc8ls8raspa', ''], ['⚡ Lightning / zaps', 'oluengo@rizful.com', 'lightning:oluengo@rizful.com']] as [label, addr, href]}
    <button class="donate" onclick={() => { navigator.clipboard.writeText(addr); app.notify(t('copied')) }}>
      <span class="bold">{label}</span>
      <code>{addr}</code>
    </button>
    {#if href}<a class="btn" href={href}>{t('zap_open_wallet')}</a>{/if}
  {/each}
  <p class="muted tiny">{t('about_ai')}</p>
</div>

{#if bunkerOpen}
  <Modal title={t('login_bunker')} onclose={() => !bunkerBusy && (bunkerOpen = false)}>
    <p class="small">{t('bunker_explain')}</p>
    <input class="field" placeholder="bunker://…" bind:value={bunkerText} disabled={bunkerBusy} />
    {#if bunkerBusy}<p class="muted small">{t('bunker_connecting')}</p>{/if}
    {#if authUrl}<a class="small" href={authUrl} target="_blank" rel="noopener">{t('web_bunker_approve')}</a>{/if}
    {#if bunkerErr}<p class="small" style="color: var(--live)">{bunkerErr}</p>{/if}
    {#snippet actions()}
      <button class="btn" onclick={() => (bunkerOpen = false)} disabled={bunkerBusy}>{t('cancel')}</button>
      <button class="btn primary" onclick={viaBunker} disabled={bunkerBusy || !bunkerText.trim()}>{t('bunker_connect')}</button>
    {/snippet}
  </Modal>
{/if}

<style>
  h1 { font-size: 24px; letter-spacing: -.02em; }
  .box { padding: 16px; display: flex; flex-direction: column; gap: 10px; }
  hr { border: 0; border-top: 1px solid var(--line); }
  .wrap { flex-wrap: wrap; }
  .mono { font-family: ui-monospace, monospace; font-size: 12px; word-break: break-all; }
  .dot { width: 30px; height: 30px; border-radius: 50%; border: 3px solid var(--surface); box-shadow: 0 0 0 1px var(--line); }
  .dot.on { box-shadow: 0 0 0 2px var(--ink); }
  .opt { text-align: left; padding: 8px; border-radius: 10px; }
  .opt:hover { background: var(--surface2); }
  .donate { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; text-align: left; padding: 8px 10px; border-radius: 10px; width: 100%; }
  .donate:hover { background: var(--surface2); }
  .donate code { font-size: 12px; color: var(--muted); word-break: break-all; }
</style>
