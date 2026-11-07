<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { player } from '../lib/player.svelte'
  import { t, fmt } from '../lib/i18n'
  import { favStation, favStreamer, lib } from '../lib/library'
  import { variantLabel, webPlayable } from '../lib/radio'
  import { ofItem, type Shared } from '../lib/share'
  import type { ZapTarget } from '../lib/zap'
  import { ChatModel, QUICK, type Line } from './Chat.svelte'
  import Art from './Art.svelte'
  import Icon from './Icon.svelte'
  import Menu from './Menu.svelte'
  import ShareDialog from './ShareDialog.svelte'
  import ZapDialog from './ZapDialog.svelte'

  let { onclose }: { onclose?: () => void } = $props()
  const chat = new ChatModel()
  const np = $derived(player.now)
  const liveAddress = $derived(np?.isLive && np.hostPubkey ? np.id : null)
  $effect(() => { chat.watch(liveAddress, np?.hostPubkey ?? null) })
  let zap = $state<ZapTarget | null>(null)
  let share = $state<Shared | null>(null)
  let text = $state('')
  let sending = $state(false)
  let failed = $state(false)
  let videoBox = $state<HTMLDivElement>()
  let scroller = $state<HTMLDivElement>()
  const showVideo = $derived(!!np?.isLive && !player.audioOnly)

  // El <video> del reproductor se mueve al hueco del vídeo cuando se ve.
  $effect(() => {
    if (showVideo && videoBox) { videoBox.appendChild(player.el); player.el.className = 'video' }
    else if (player.el.parentElement) player.el.remove()
  })
  // Si estás al final del chat, los mensajes nuevos se ven sin bajar.
  $effect(() => {
    chat.lines.length
    const s = scroller
    if (s && s.scrollHeight - s.scrollTop - s.clientHeight < 300) queueMicrotask(() => s.scrollTo({ top: s.scrollHeight, behavior: 'smooth' }))
  })

  const favKey = $derived(np?.station?.id ?? (np?.hostPubkey ? 'npub:' + np.hostPubkey : null))
  const isFav = $derived(!!favKey && lib.isFavorite(app.library, favKey))
  function toggleFav() {
    if (!np) return
    if (np.station) app.updateLibrary(l => lib.toggle(l, favStation(np.station!)))
    else if (np.hostPubkey) app.updateLibrary(l => lib.toggle(l, favStreamer(np.hostPubkey!, np.subtitle || np.title, np.artwork)))
  }
  function doShare() {
    if (!np) return
    const item = np.station ? favStation(np.station) : np.hostPubkey ? favStreamer(np.hostPubkey, np.subtitle || np.title, np.artwork) : null
    if (item) share = ofItem(item)
  }
  async function send() {
    const v = text.trim(); if (!v || sending) return
    sending = true; failed = false
    if (await chat.send(v)) text = ''; else failed = true
    sending = false
  }
  async function react(e: string, l: Line | null) { if (!(await chat.react(e, l))) app.notify(t('react_failed')) }
  function fullscreen() { (player.el.requestFullscreen?.() ?? (player.el as any).webkitEnterFullscreen?.())?.catch?.(() => {}) }
  const BOOST = [0, 3, 6, 9, 12]
  let boost = $state(0)
  $effect(() => { boost = player.boostFor(np?.id) })
  const SLEEP = [15, 30, 45, 60, 90]
  const sleepLeft = $derived(player.sleepAt ? Math.max(1, Math.round((player.sleepAt - Date.now()) / 60000)) : null)
</script>

<section class="player">
  <!-- Arriba: qué suena, siempre a la vista -->
  <div class="bar row">
    {#if onclose}<button class="icon-btn" onclick={onclose} title={t('minimize')}><Icon name="down" /></button>{/if}
    {#if np}
      {#if !showVideo}<Art src={np.artwork} id={np.id} title={np.title} size={36} />{/if}
      <div class="grow">
        <div class="row" style="gap:6px">{#if np.isLive}<span class="badge-live">{t('live_badge')}</span>{/if}<span class="bold ellipsis">{np.title}</span></div>
        <div class="muted small ellipsis">{np.subtitle}</div>
      </div>
      <button class="icon-btn" onclick={doShare} title={t('share')}><Icon name="share" /></button>
      <button class="icon-btn" onclick={() => { player.stop(); onclose?.() }} title={t('stop')}><Icon name="close" /></button>
    {:else}
      <div class="grow muted">{t('nothing_playing')}</div>
    {/if}
  </div>

  {#if np}
    {#if showVideo}
      <div class="stage" bind:this={videoBox}>
        {#if player.buffering}<div class="spin"></div>{/if}
        <button class="fs" onclick={fullscreen} title={t('fullscreen')}><Icon name="fullscreen" /></button>
      </div>
    {/if}

    <!-- En medio: lo que se desplaza -->
    <div class="scroll" bind:this={scroller}>
      {#if !showVideo}
        <div class="art"><Art src={np.artwork} id={np.id} title={np.title} size={liveAddress ? 150 : 220} />{#if player.buffering}<div class="spin"></div>{/if}</div>
      {/if}
      {#if np.isLive}
        <div class="row seg">
          <button class="chip" class:on={player.audioOnly} onclick={() => player.setAudioOnly(true)}><Icon name="headphones" size={18} />{t('player_audio')}</button>
          <button class="chip" class:on={!player.audioOnly} onclick={() => player.setAudioOnly(false)}><Icon name="video" size={18} />{t('player_video')}</button>
        </div>
      {/if}
      {#if np.station && np.station.variants.length > 1}
        <div class="row seg wrap">
          {#each np.station.variants as v, i}
            {#if webPlayable(v)}<button class="chip" class:on={np.variant === i} onclick={() => player.playStation(np.station!, i)}>{variantLabel(v) || t('radio_format_unknown')}</button>{/if}
          {/each}
        </div>
      {/if}
      <div class="info">
        <h2>{np.title}</h2>
        <p class="muted">{np.subtitle}</p>
        {#if player.error}<p class="small" style="color: var(--live)">{t(np.station ? 'radio_play_error' : 'player_error')}</p>{/if}
      </div>
      <div class="row vol">
        <Icon name="volume" size={18} />
        <input type="range" min="0" max="1" step="0.01" value={player.volume} oninput={e => player.setVolume(Number((e.target as HTMLInputElement).value))} class="grow" />
      </div>
      <div class="row chips">
        <Menu align="left">
          {#snippet trigger(toggle)}<button class="chip" class:on={!!player.sleepAt} onclick={toggle}><Icon name="timer" size={16} />{sleepLeft ? t('sleep_in', t('sleep_minutes', sleepLeft)) : t('sleep_timer')}</button>{/snippet}
          {#snippet children(close)}
            {#each SLEEP as m}<button class="item" onclick={() => { player.sleepIn(m); close() }}>{t('sleep_minutes', m)}</button>{/each}
            {#if player.sleepAt}<button class="item" style="color: var(--live)" onclick={() => { player.cancelSleep(); close() }}>{t('sleep_cancel')}</button>{/if}
          {/snippet}
        </Menu>
        {#if !showVideo}
          <Menu align="left">
            {#snippet trigger(toggle)}<button class="chip" class:on={boost > 0} onclick={toggle}><Icon name="volume" size={16} />{boost ? t('boost_on', boost) : t('boost')}</button>{/snippet}
            {#snippet children(close)}
              <div class="label">{t('boost_explain')}</div>
              {#each BOOST as b}<button class="item" class:bold={b === boost} onclick={() => { boost = b; player.setBoost(np.id, b); close() }}>{b ? `+${b} dB` : t('boost_off')}</button>{/each}
            {/snippet}
          </Menu>
        {/if}
      </div>
      {#if player.boostBlocked}<p class="muted tiny pad">{t('web_boost_blocked')}</p>{/if}

      {#if liveAddress}
        <div class="chat-head row">
          <span class="muted tiny grow">{t('live_chat_title')}{chat.total ? ` · ⚡ ${fmt(chat.total)} sats` : ''}</span>
          {#each chat.streamReactions.slice(0, 4) as [e, n]}<span class="tiny" class:bold={chat.mineStream.has(e)}>{e} {n}</span>{/each}
          {#if app.canSign}
            <Menu>
              {#snippet trigger(toggle)}<button class="icon-btn" onclick={toggle} title={t('react')}><Icon name="react" size={20} /></button>{/snippet}
              {#snippet children(close)}<div class="row emo">{#each QUICK as e}<button onclick={() => { react(e, null); close() }}>{e}</button>{/each}</div>{/snippet}
            </Menu>
          {/if}
        </div>
        <div class="lines">
          {#if chat.loaded && !chat.lines.length}<p class="muted small">{t('live_chat_empty')}</p>{/if}
          {#each chat.lines as l (l.id ?? l.time + l.author)}
            <div class="line" class:zap={l.sats}>
              {#if l.id}
                <Menu align="left">
                  {#snippet trigger(toggle)}
                    <button class="msg" onclick={toggle}>
                      <span class="who">{l.author}</span> {l.text}
                    </button>
                  {/snippet}
                  {#snippet children(close)}
                    {#if app.canSign}<div class="row emo">{#each QUICK as e}<button onclick={() => { react(e, l); close() }}>{e}</button>{/each}</div>{:else}<div class="label">{t('react_needs_signer')}</div>{/if}
                    {#if l.pubkey}<button class="item" onclick={() => { zap = { pubkey: l.pubkey!, name: l.author, address: liveAddress!, title: np.title, eventId: l.id }; close() }}>⚡ {t('zap_message', l.author)}</button>{/if}
                  {/snippet}
                </Menu>
              {:else}
                <span class="who">{l.author}</span> <span class="sats">⚡ {fmt(l.sats || 0)}</span> {l.text}
              {/if}
              {#if l.reactions.length || l.zapSats}
                <div class="pills">
                  {#if l.zapSats}<span class="pill z">⚡ {fmt(l.zapSats)}</span>{/if}
                  {#each l.reactions as [e, n]}<span class="pill" class:mine={l.mine.has(e)}>{e} {n}</span>{/each}
                </div>
              {/if}
            </div>
          {/each}
        </div>
      {/if}
    </div>

    <!-- Abajo: los mismos controles para radio y streams -->
    <div class="controls row">
      {#if np.isLive && np.hostPubkey}
        <button class="icon-btn zapb" onclick={() => (zap = { pubkey: np.hostPubkey!, name: np.subtitle, address: liveAddress ?? undefined, title: np.title })} title={t('zap_button')}><Icon name="bolt" /></button>
      {:else}<span class="icon-btn"></span>{/if}
      <button class="icon-btn" onclick={() => player.skip(-1)} disabled={!player.canSkip()} title={t('skip_previous')}><Icon name="prev" size={28} /></button>
      <button class="playb" onclick={() => player.toggle()} title={t(player.playing ? 'pause' : 'play')}><Icon name={player.playing ? 'pause' : 'play'} size={28} /></button>
      <button class="icon-btn" onclick={() => player.skip(1)} disabled={!player.canSkip()} title={t('skip_next')}><Icon name="next" size={28} /></button>
      <button class="icon-btn" onclick={toggleFav} disabled={!favKey} title={t(isFav ? 'fav_remove' : 'fav_add')} style:color={isFav ? 'var(--live)' : 'var(--muted)'}><Icon name={isFav ? 'heartFill' : 'heart'} /></button>
    </div>
    {#if liveAddress}
      {#if app.canSign}
        <div class="input row">
          <textarea rows="1" class="field grow" placeholder={t('chat_hint')} bind:value={text} maxlength="500"
            onkeydown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }}></textarea>
          <button class="icon-btn sendb" onclick={send} disabled={!text.trim() || sending} title={t('chat_send')}><Icon name="send" /></button>
        </div>
        {#if failed}<p class="tiny pad" style="color: var(--live)">{t('chat_failed')}</p>{/if}
      {:else}
        <p class="muted small pad">{t('web_chat_needs_signer')}</p>
      {/if}
    {/if}
  {/if}
</section>

{#if zap}<ZapDialog target={zap} onclose={() => (zap = null)} onzapped={() => chat.refresh()} />{/if}
{#if share}<ShareDialog shared={share} onclose={() => (share = null)} />{/if}

<style>
  .player { display: flex; flex-direction: column; height: 100%; background: var(--surface); }
  .bar { padding: 10px 8px 6px; gap: 8px; }
  .stage { position: relative; margin: 0 8px; aspect-ratio: 16/9; background: #000; border-radius: 12px; overflow: hidden; flex: none; }
  .stage :global(.video) { width: 100%; height: 100%; object-fit: contain; display: block; }
  .fs { position: absolute; right: 6px; bottom: 6px; background: rgba(0,0,0,.45); color: #fff; width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; }
  .scroll { flex: 1; overflow: auto; min-height: 0; }
  .art { display: grid; place-items: center; padding: 10px 0 4px; position: relative; }
  .art :global(.art) { box-shadow: var(--shadow); }
  .spin { position: absolute; left: 50%; top: 50%; width: 34px; height: 34px; margin: -17px; border: 3px solid rgba(255,255,255,.4); border-top-color: var(--accent); border-radius: 50%; animation: s 1s linear infinite; }
  @keyframes s { to { transform: rotate(360deg) } }
  .seg { padding: 10px 16px 0; }
  .wrap { flex-wrap: wrap; }
  .info { padding: 12px 18px 6px; }
  .info h2 { font-size: 20px; letter-spacing: -.01em; }
  .vol { padding: 4px 22px; color: var(--muted); }
  .vol input { accent-color: var(--accent); }
  .chips { padding: 6px 16px 10px; justify-content: center; flex-wrap: wrap; }
  .pad { padding: 0 18px 8px; }
  .chat-head { padding: 8px 18px 0; border-top: 1px solid var(--line); margin: 0 0 4px; gap: 6px; }
  .lines { padding: 0 18px 10px; display: flex; flex-direction: column; gap: 3px; font-size: 14px; }
  .line { padding: 2px 0; }
  .line.zap { background: var(--zap-soft); border-radius: 8px; padding: 4px 8px; }
  .msg { text-align: left; padding: 0; }
  .who { color: var(--accent); font-weight: 650; }
  .sats { color: var(--zap); font-family: ui-monospace, monospace; }
  .pills { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 2px; }
  .pill { font-size: 12px; padding: 1px 7px; border-radius: 10px; background: var(--surface2); }
  .pill.mine { background: var(--accent-soft); }
  .pill.z { background: var(--zap-soft); color: var(--zap); }
  .emo { gap: 2px; padding: 4px; }
  .emo button { font-size: 22px; width: 40px; height: 40px; border-radius: 8px; }
  .emo button:hover { background: var(--surface2); }
  .controls { justify-content: center; gap: 10px; padding: 8px 0; border-top: 1px solid var(--line); flex: none; }
  .zapb { background: var(--zap-soft); color: var(--zap); }
  .playb { width: 56px; height: 56px; border-radius: 50%; background: var(--ink); color: var(--surface); display: grid; place-items: center; }
  .input { padding: 6px 12px 10px; gap: 8px; flex: none; }
  .input textarea { resize: none; max-height: 90px; }
  .sendb { background: var(--accent); color: #fff; }
</style>
