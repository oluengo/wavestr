<script lang="ts">
  // Carátula con respaldo: iniciales sobre un color estable según el id.
  let { src, id, title, size = 48, round = false }: { src?: string; id: string; title: string; size?: number; round?: boolean } = $props()
  let failed = $state(false)
  $effect(() => { src; failed = false })
  const hue = $derived([...id].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7) % 360)
  const initials = $derived(title.split(/\s+/).filter(Boolean).slice(0, 2).map(w => [...w][0]).join('').toUpperCase() || '?')
</script>
<div class="art" style:width="{size}px" style:height="{size}px" style:border-radius={round ? '50%' : Math.round(size * 0.22) + 'px'}
  style:background={!src || failed ? `linear-gradient(135deg, hsl(${hue} 60% 55%), hsl(${(hue + 40) % 360} 65% 45%))` : 'var(--surface2)'}>
  {#if src && !failed}
    <img {src} alt="" loading="lazy" referrerpolicy="no-referrer" onerror={() => (failed = true)} />
  {:else}
    <span style:font-size="{Math.max(11, size * 0.34)}px">{initials}</span>
  {/if}
</div>
<style>
  .art { flex: none; overflow: hidden; display: grid; place-items: center; color: #fff; font-weight: 700; }
  img { width: 100%; height: 100%; object-fit: cover; }
</style>
