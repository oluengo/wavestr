<script lang="ts">
  import type { Snippet } from 'svelte'
  let { title, onclose, children, actions, wide = false }: { title: string; onclose: () => void; children: Snippet; actions?: Snippet; wide?: boolean } = $props()
</script>
<svelte:window onkeydown={e => e.key === 'Escape' && onclose()} />
<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="scrim" onclick={onclose}>
  <div class="dialog card" class:wide onclick={e => e.stopPropagation()} role="dialog" tabindex="-1" aria-modal="true" aria-label={title}>
    <h2>{title}</h2>
    <div class="body">{@render children()}</div>
    {#if actions}<div class="actions">{@render actions()}</div>{/if}
  </div>
</div>
<style>
  .scrim { position: fixed; inset: 0; background: rgba(10, 10, 20, .45); display: grid; place-items: center; z-index: 60; padding: 16px; }
  .dialog { width: 100%; max-width: 440px; max-height: calc(100vh - 32px); display: flex; flex-direction: column; padding: 20px; gap: 12px; }
  .dialog.wide { max-width: 540px; }
  h2 { font-size: 18px; letter-spacing: -.01em; }
  .body { overflow: auto; display: flex; flex-direction: column; gap: 10px; }
  .actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
