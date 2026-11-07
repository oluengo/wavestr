<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { t, plural } from '../lib/i18n'
  import { libraryToM3u } from '../lib/library'
  import { asLibrary, link, stations, streamers, toText, type Shared } from '../lib/share'
  import Icon from './Icon.svelte'
  import Modal from './Modal.svelte'

  let { shared, onclose }: { shared: Shared; onclose: () => void } = $props()
  const summary = $derived([stations(shared) && plural('share_stations', stations(shared), stations(shared)), streamers(shared) && plural('share_streamers', streamers(shared), streamers(shared))].filter(Boolean).join(t('share_and')))

  async function out(text: string, title: string) {
    if (navigator.share) { try { await navigator.share({ title, text }); return } catch (e) { if ((e as Error).name === 'AbortError') return } }
    await navigator.clipboard.writeText(text); app.notify(t('desk_share_copied'))
  }
  async function shareLink() { await out(t('share_link_text', shared.name, await link(shared)), shared.name); onclose() }
  async function shareText() { await out(toText(shared), shared.name); onclose() }
  function m3u() {
    const blob = new Blob([libraryToM3u(asLibrary(shared))], { type: 'audio/x-mpegurl' })
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob)
    a.download = (shared.name.replace(/[^\p{L}\p{N} _-]/gu, '').trim() || 'wavestr').slice(0, 40) + '.m3u'; a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 5000); onclose()
  }
</script>

<Modal title={t('share_title', shared.name)} {onclose}>
  {#if !shared.items.length}
    <p>{t('share_empty')}</p>
  {:else}
    <p class="muted">{summary}</p>
    <button class="opt row" onclick={shareLink}><Icon name="link" /><div class="grow"><div class="bold">{t('share_link')}</div><div class="muted small">{t('web_share_link_sub')}</div></div></button>
    <button class="opt row" onclick={m3u} disabled={!stations(shared)}><Icon name="download" /><div class="grow"><div class="bold">{t('share_m3u')}</div><div class="muted small">{t(stations(shared) ? 'share_m3u_sub' : 'share_m3u_none')}</div></div></button>
    <button class="opt row" onclick={shareText}><Icon name="share" /><div class="grow"><div class="bold">{t('share_text')}</div><div class="muted small">{t('share_text_sub')}</div></div></button>
  {/if}
  {#snippet actions()}<button class="btn" onclick={onclose}>{t('close')}</button>{/snippet}
</Modal>

<style>
  .opt { text-align: left; padding: 10px 8px; border-radius: 10px; color: var(--accent); gap: 14px; }
  .opt:hover:not(:disabled) { background: var(--surface2); }
  .opt .grow { color: var(--ink); }
</style>
