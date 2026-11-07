<script lang="ts">
  import { app } from '../lib/app.svelte'
  import { t, fmt } from '../lib/i18n'
  import { KIND, profiles, relaysFor, unsigned } from '../lib/nostr'
  import { sign } from '../lib/signer'
  import { type ZapTarget as Target, anonymousRequest, encodeLnurl, invoice as getInvoice, lnurlEndpoint, nwcPay, payInfo, zapRequestTags, ZapError } from '../lib/zap'
  import Modal from './Modal.svelte'
  import qrcode from 'qrcode-generator'

  let { target, onclose, onzapped = () => {} }: { target: Target; onclose: () => void; onzapped?: () => void } = $props()
  const AMOUNTS = [21, 100, 500, 1000, 5000]
  let amount = $state(app.defaultZap)
  let other = $state('')
  let comment = $state('')
  let working = $state(false)
  let error = $state<string | null>(null)
  let done = $state<number | null>(null)
  let invoice = $state<string | null>(null)
  const sats = $derived(Number(other) > 0 ? Number(other) : amount)
  const wallet = $derived(app.wallet)
  const left = $derived(Math.max(0, app.budget - app.spentThisMonth))

  function qrSvg(text: string) { const q = qrcode(0, 'M'); q.addData(text); q.make(); return q.createSvgTag({ cellSize: 5, margin: 2, scalable: true }) }

  async function send() {
    working = true; error = null
    try {
      const me = app.pubkey
      const lud16 = (await profiles([target.pubkey], me))[target.pubkey]?.lud16
      const info = await payInfo(lud16)
      const msats = sats * 1000
      const lnurl = encodeLnurl(lnurlEndpoint(lud16!)!)
      const note = comment.trim().slice(0, 200)
      let req = null
      if (info.allowsNostr) {
        const tags = zapRequestTags(target.pubkey, target.address, msats, lnurl, (await relaysFor(me)).slice(0, 5), target.eventId)
        if (me && app.canSign) {
          req = await sign(app.session, unsigned(me, KIND.ZAP_REQUEST, tags, note))
          if (!req) { error = t('desk_zap_err_signature'); return }
        } else req = anonymousRequest(tags, note)
      }
      const inv = await getInvoice(info, msats, req, lnurl, note)
      if (wallet) {
        if (sats > left) { error = t('zap_err_budget', left); return }
        const r = await nwcPay(wallet, inv)
        if (r.ok) { app.addSpent(sats); done = sats; onzapped() } else error = t('zap_err_wallet') + ' (' + r.error + ')'
      } else if (window.webln) {
        try { await window.webln.enable(); await window.webln.sendPayment(inv); done = sats; onzapped() } catch { invoice = inv }
      } else invoice = inv
    } catch (e) {
      const z = e as ZapError
      error = z.code === 'no_address' ? t('zap_err_no_address') : z.code === 'range' ? t('zap_err_range', z.data.min, z.data.max)
        : z.code === 'invoice' ? t('zap_err_invoice') : z.code === 'mismatch' ? t('zap_err_mismatch') : t('zap_err_unreachable')
    } finally { working = false }
  }
</script>

<Modal title={t('zap_title', target.name)} {onclose}>
  {#if done}
    <p class="bold">{t('zap_sent', fmt(done), target.name)}</p>
    <p class="muted small">{t('zap_sent_note')}</p>
  {:else if invoice}
    <p>{t('desk_zap_pay_qr')}</p>
    <a class="qr" href={'lightning:' + invoice}>{@html qrSvg('lightning:' + invoice.toUpperCase())}</a>
    <div class="row">
      <button class="btn" onclick={() => { navigator.clipboard.writeText(invoice!); app.notify(t('copied')) }}>{t('zap_copy_invoice')}</button>
      <a class="btn" href={'lightning:' + invoice}>{t('zap_open_wallet')}</a>
    </div>
  {:else}
    <div class="row wrap">
      {#each AMOUNTS as a}<button class="chip" class:on={!other && amount === a} onclick={() => { amount = a; other = '' }}>⚡ {fmt(a)}</button>{/each}
    </div>
    <input class="field" inputmode="numeric" placeholder={t('zap_other')} bind:value={other} oninput={() => (other = other.replace(/\D/g, '').slice(0, 8))} />
    <input class="field" placeholder={t('zap_comment')} bind:value={comment} maxlength="200" />
    <p class="muted small">{wallet ? t('zap_via_nwc', wallet.lud16 || 'NWC', fmt(left), fmt(app.budget)) : window.webln ? t('web_zap_via_webln') : t('desk_zap_via_qr')}</p>
    {#if !app.canSign}<p class="muted small">{t('desk_zap_anonymous')}</p>{/if}
    {#if error}<p class="small" style="color: var(--live)">{error}</p>{/if}
  {/if}
  {#snippet actions()}
    {#if done || invoice}<button class="btn" onclick={onclose}>{t('close')}</button>
    {:else}
      <button class="btn" onclick={onclose} disabled={working}>{t('cancel')}</button>
      <button class="btn primary" onclick={send} disabled={working || !(sats > 0)}>{working ? '…' : t('zap_send', fmt(sats))}</button>
    {/if}
  {/snippet}
</Modal>

<style>
  .wrap { flex-wrap: wrap; }
  .qr { display: block; width: 240px; max-width: 100%; margin: 4px auto; background: #fff; border-radius: 10px; padding: 6px; }
  .qr :global(svg) { width: 100%; height: auto; display: block; }
</style>
