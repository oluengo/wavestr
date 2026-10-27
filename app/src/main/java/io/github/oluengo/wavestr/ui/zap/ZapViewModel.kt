package io.github.oluengo.wavestr.ui.zap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.crypto.Secp256k1
import io.github.oluengo.wavestr.data.LoginMethod
import io.github.oluengo.wavestr.data.WalletRepository
import io.github.oluengo.wavestr.nostr.Event
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Lnurl
import io.github.oluengo.wavestr.nostr.ZapClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Un zap a un directo: LNURL del streamer → petición de zap (firmada con Amber o, sin Amber, con una
 * clave temporal: zap anónimo) → factura (se comprueba el importe) → pago con NWC o con la app de cartera.
 */
class ZapViewModel(private val c: AppContainer) : ViewModel() {

    data class Target(val hostPubkey: String, val hostName: String, val address: String?, val title: String)

    sealed interface Step {
        data object Editing : Step
        data object Working : Step
        /** Hay que abrir Amber con este evento. */
        data class NeedsSignature(val unsigned: Event, val signerPackage: String?) : Step
        /** Sin NWC: se abre la app de cartera con la factura. */
        data class PayExternally(val invoice: String) : Step
        data class Done(val sats: Long) : Step
        data class Failed(val error: ZapFailure) : Step
    }

    sealed interface ZapFailure {
        data object NoAddress : ZapFailure
        data object AddressUnreachable : ZapFailure
        data class OutOfRange(val min: Long, val max: Long) : ZapFailure
        data class InvoiceFailed(val reason: String?) : ZapFailure
        data object InvoiceMismatch : ZapFailure
        data object SignatureCancelled : ZapFailure
        data class OverBudget(val left: Long) : ZapFailure
        data class Wallet(val message: String?) : ZapFailure
        data object Timeout : ZapFailure
    }

    private val _step = MutableStateFlow<Step>(Step.Editing)
    val step: StateFlow<Step> = _step.asStateFlow()

    private var target: Target? = null
    private var sats: Long = 0
    private var comment: String = ""
    private var info: Lnurl.PayInfo? = null
    private var lnurl: String = ""

    fun reset() { _step.value = Step.Editing }

    fun send(t: Target, amountSats: Long, note: String) {
        target = t; sats = amountSats; comment = note.trim().take(200)
        _step.value = Step.Working
        viewModelScope.launch {
            try {
                val settings = c.settings.current()
                val lud16 = c.nostr.profiles(listOf(t.hostPubkey), settings.pubkey)[t.hostPubkey]?.lud16
                val pay = c.zaps.payInfo(lud16)
                val msats = amountSats * 1000
                c.zaps.checkAmount(pay, msats)
                info = pay
                lnurl = Lnurl.encode(Lnurl.endpointFor(lud16!!)!!)
                if (!pay.allowsNostr) { finish(null); return@launch }
                val relays = c.nostr.relaysFor(settings.pubkey).take(5)
                val tags = Lnurl.zapRequestTags(t.hostPubkey, t.address, msats, lnurl, relays)
                val me = settings.pubkey
                if (me != null && settings.loginMethod == LoginMethod.AMBER) {
                    _step.value = Step.NeedsSignature(EventSigning.unsigned(me, Kinds.ZAP_REQUEST, tags, comment), settings.signerPackage)
                } else {
                    // Sin Amber: zap anónimo, firmado con una clave de un solo uso.
                    finish(EventSigning.sign(Kinds.ZAP_REQUEST, tags, comment, Secp256k1.randomSecret()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Resultado de Amber: evento firmado o null si se canceló. */
    fun onSigned(signed: Event?) {
        if (signed == null) { _step.value = Step.Failed(ZapFailure.SignatureCancelled); return }
        _step.value = Step.Working
        viewModelScope.launch {
            try { finish(signed) } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
        }
    }

    private suspend fun finish(zapRequest: Event?) {
        val pay = info ?: return
        val invoice = c.zaps.invoice(pay, sats * 1000, zapRequest, lnurl, comment)
        if (c.wallet.state.value.connection == null) { _step.value = Step.PayExternally(invoice); return }
        _step.value = when (val r = c.wallet.pay(invoice, sats)) {
            WalletRepository.PayResult.Paid -> Step.Done(sats)
            is WalletRepository.PayResult.OverBudget -> Step.Failed(ZapFailure.OverBudget(r.left))
            is WalletRepository.PayResult.WalletError -> Step.Failed(ZapFailure.Wallet(r.message ?: r.code))
            WalletRepository.PayResult.Timeout -> Step.Failed(ZapFailure.Timeout)
            WalletRepository.PayResult.NotConnected -> Step.PayExternally(invoice)
        }
    }

    private fun fail(e: Exception) {
        _step.value = Step.Failed(
            when (e) {
                ZapClient.ZapError.NoLightningAddress -> ZapFailure.NoAddress
                ZapClient.ZapError.AddressUnreachable -> ZapFailure.AddressUnreachable
                is ZapClient.ZapError.AmountOutOfRange -> ZapFailure.OutOfRange(e.minSats, e.maxSats)
                is ZapClient.ZapError.InvoiceFailed -> ZapFailure.InvoiceFailed(e.reason)
                ZapClient.ZapError.InvoiceMismatch -> ZapFailure.InvoiceMismatch
                else -> ZapFailure.AddressUnreachable
            }
        )
    }
}
