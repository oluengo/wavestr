<script lang="ts">
  import type { Snippet } from 'svelte'
  // Menú desplegable sencillo, anclado a su botón.
  let { trigger, children, align = 'right' }: { trigger: Snippet<[() => void]>; children: Snippet<[() => void]>; align?: 'left' | 'right' } = $props()
  let open = $state(false)
  const toggle = () => (open = !open)
  const close = () => (open = false)
</script>
<div class="wrap">
  {@render trigger(toggle)}
  {#if open}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="catch" onclick={close}></div>
    <div class="menu card" class:left={align === 'left'}>{@render children(close)}</div>
  {/if}
</div>
<style>
  .wrap { position: relative; display: inline-flex; }
  .catch { position: fixed; inset: 0; z-index: 40; }
  .menu { position: absolute; top: calc(100% + 4px); right: 0; z-index: 41; min-width: 200px; max-width: 300px; padding: 6px; display: flex; flex-direction: column; }
  .menu.left { right: auto; left: 0; }
  .menu :global(.item) { text-align: left; padding: 9px 12px; border-radius: 9px; display: flex; gap: 10px; align-items: center; }
  .menu :global(.item:hover) { background: var(--surface2); }
  .menu :global(.label) { padding: 6px 12px; font-size: 12px; color: var(--muted); }
</style>
