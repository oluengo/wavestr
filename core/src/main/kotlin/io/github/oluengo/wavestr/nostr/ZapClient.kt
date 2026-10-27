package io.github.oluengo.wavestr.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Parte HTTP de un zap: datos LNURL del destinatario y factura del servidor. */
class ZapClient(private val http: OkHttpClient) {

    sealed class ZapError : Exception() {
        data object NoLightningAddress : ZapError()
        data object AddressUnreachable : ZapError()
        data class AmountOutOfRange(val minSats: Long, val maxSats: Long) : ZapError()
        data class InvoiceFailed(val reason: String?) : ZapError()
        data object InvoiceMismatch : ZapError()
    }

    private fun getJson(url: String): JsonObject {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            return NostrJson.parseToJsonElement(body) as JsonObject
        }
    }

    suspend fun payInfo(lud16: String?): Lnurl.PayInfo = withContext(Dispatchers.IO) {
        val endpoint = lud16?.let(Lnurl::endpointFor) ?: throw ZapError.NoLightningAddress
        runCatching { Lnurl.parsePayInfo(getJson(endpoint)) }.getOrNull() ?: throw ZapError.AddressUnreachable
    }

    fun checkAmount(info: Lnurl.PayInfo, msats: Long) {
        if (msats < info.minMsat || msats > info.maxMsat) throw ZapError.AmountOutOfRange(info.minMsat / 1000, info.maxMsat / 1000)
    }

    /** Pide la factura y comprueba que es por el importe pedido. */
    suspend fun invoice(info: Lnurl.PayInfo, msats: Long, zapRequest: Event?, lnurl: String, comment: String?): String =
        withContext(Dispatchers.IO) {
            val o = runCatching { getJson(Lnurl.callbackUrl(info, msats, zapRequest, lnurl, comment)) }.getOrNull()
                ?: throw ZapError.InvoiceFailed(null)
            val (pr, reason) = Lnurl.parseInvoice(o)
            pr ?: throw ZapError.InvoiceFailed(reason)
            if (!Lnurl.invoiceMatches(pr, msats)) throw ZapError.InvoiceMismatch
            pr
        }
}
