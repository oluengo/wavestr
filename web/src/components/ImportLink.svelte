<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { t } from '../lib/i18n'
  import { codeFrom, decode, LINK_BASE } from '../lib/share'
  import Modal from './Modal.svelte'
  import { incoming } from './incoming.svelte'

  let { onclose }: { onclose: () => void } = $props()
  let text = $state('')
  let bad = $state(false)
  async function go() {
    const c = codeFrom(text); const sh = c ? await decode(c) : null
    if (!sh) { bad = true; return }
    incoming.shared = sh; onclose()
  }
</script>

<Modal title={t('desk_import_link')} {onclose}>
  <p class="muted small">{t('desk_import_link_sub')}</p>
  <!-- svelte-ignore a11y_autofocus -->
  <input class="field" bind:value={text} placeholder={LINK_BASE + '#…'} oninput={() => (bad = false)} autofocus onkeydown={e => e.key === 'Enter' && go()} />
  {#if bad}<p class="small" style="color: var(--live)">{t('share_bad_link')}</p>{/if}
  {#snippet actions()}
    <button class="btn" onclick={onclose}>{t('cancel')}</button>
    <button class="btn primary" onclick={go} disabled={!text.trim()}>{t('desk_import_preview')}</button>
  {/snippet}
</Modal>
