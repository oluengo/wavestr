package io.github.oluengo.wavestr.nostr

import io.github.oluengo.wavestr.crypto.Nip04
import io.github.oluengo.wavestr.crypto.Secp256k1
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Conexión Nostr Wallet Connect (NIP-47). El secreto solo sirve para pedir pagos a esa cartera. */
data class NwcConnection(
    val walletPubkey: String,
    val relays: List<String>,
    val secret: String,
    val lud16: String? = null,
) {
    val clientPubkey: String get() = Secp256k1.pubkey(secret.hexToBytes()).toHex()

    /** Nombre amable para mostrar: el lud16 o el dominio del relay. */
    val label: String get() = lud16 ?: relays.firstOrNull()?.let { runCatching { URI(it).host }.getOrNull() } ?: walletPubkey.take(8)

    fun toUri(): String = buildString {
        append("nostr+walletconnect://").append(walletPubkey).append('?')
        append(relays.joinToString("&") { "relay=" + URLEncoder.encode(it, "UTF-8") })
        append("&secret=").append(secret)
        lud16?.let { append("&lud16=").append(URLEncoder.encode(it, "UTF-8")) }
    }

    companion object {
        private val HEX64 = Regex("^[0-9a-f]{64}$")

        fun parse(input: String): NwcConnection? = runCatching {
            val s = input.trim()
            val rest = when {
                s.startsWith("nostr+walletconnect://", true) -> s.substring("nostr+walletconnect://".length)
                s.startsWith("nostrwalletconnect://", true) -> s.substring("nostrwalletconnect://".length)
                s.startsWith("nostr+walletconnect:", true) -> s.substring("nostr+walletconnect:".length)
                else -> return null
            }
            val pub = rest.substringBefore('?').trim('/').lowercase()
            val params = rest.substringAfter('?', "").split('&').filter { '=' in it }
                .map { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            val relays = params.filter { it.first == "relay" }.map { it.second.trim().trimEnd('/') }.filter { it.startsWith("wss://") || it.startsWith("ws://") }
            val secret = params.firstOrNull { it.first == "secret" }?.second?.lowercase()
            if (!HEX64.matches(pub) || secret == null || !HEX64.matches(secret) || relays.isEmpty()) return null
            if (!Secp256k1.isValidSecret(secret.hexToBytes())) return null
            NwcConnection(pub, relays.distinct(), secret, params.firstOrNull { it.first == "lud16" }?.second)
        }.getOrNull()
    }
}

object Nwc {
    const val REQUEST = 23194
    const val RESPONSE = 23195

    data class Response(val resultType: String?, val errorCode: String?, val errorMessage: String?, val result: JsonObject?)

    fun request(conn: NwcConnection, method: String, params: JsonObject, createdAt: Long = System.currentTimeMillis() / 1000): Event {
        val body = buildJsonObject { put("method", method); put("params", params) }.toString()
        val sk = conn.secret.hexToBytes()
        val content = Nip04.encrypt(body, sk, conn.walletPubkey.hexToBytes())
        return EventSigning.sign(REQUEST, listOf(listOf("p", conn.walletPubkey)), content, sk, createdAt)
    }

    fun payInvoice(conn: NwcConnection, invoice: String) =
        request(conn, "pay_invoice", buildJsonObject { put("invoice", invoice) })

    fun getInfo(conn: NwcConnection) = request(conn, "get_info", buildJsonObject { })

    /** Filtro para esperar la respuesta a [req]. */
    fun responseFilter(conn: NwcConnection, req: Event) = Filter(
        kinds = listOf(RESPONSE), authors = listOf(conn.walletPubkey),
        tags = mapOf("e" to listOf(req.id)), since = req.createdAt - 60,
    )

    fun parseResponse(conn: NwcConnection, e: Event): Response? = runCatching {
        if (e.kind != RESPONSE || e.pubkey != conn.walletPubkey) return null
        val o = NostrJson.parseToJsonElement(Nip04.decrypt(e.content, conn.secret.hexToBytes(), conn.walletPubkey.hexToBytes())) as JsonObject
        val err = o["error"] as? JsonObject
        Response(
            resultType = (o["result_type"] as? JsonPrimitive)?.contentOrNull,
            errorCode = (err?.get("code") as? JsonPrimitive)?.contentOrNull,
            errorMessage = (err?.get("message") as? JsonPrimitive)?.contentOrNull,
            result = o["result"] as? JsonObject,
        )
    }.getOrNull()
}

/** LNURL-pay (LUD-06/16) y peticiones de zap (NIP-57). */
object Lnurl {
    data class PayInfo(
        val callback: String,
        val minMsat: Long,
        val maxMsat: Long,
        val allowsNostr: Boolean,
        val nostrPubkey: String?,
        val commentAllowed: Int,
    )

    private val ADDRESS = Regex("^([a-z0-9._+-]+)@([a-z0-9.-]+\\.[a-z]{2,})$", RegexOption.IGNORE_CASE)

    /** nombre@dominio → https://dominio/.well-known/lnurlp/nombre */
    fun endpointFor(lud16: String): String? {
        val m = ADDRESS.matchEntire(lud16.trim()) ?: return null
        return "https://${m.groupValues[2].lowercase()}/.well-known/lnurlp/${m.groupValues[1].lowercase()}"
    }

    fun encode(url: String): String = Bech32.encode("lnurl", url.toByteArray(Charsets.UTF_8))

    fun parsePayInfo(o: JsonObject): PayInfo? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun l(k: String) = (o[k] as? JsonPrimitive)?.longOrNull
        if (s("tag") != "payRequest") return null
        val cb = s("callback")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        return PayInfo(
            callback = cb, minMsat = l("minSendable") ?: 1000, maxMsat = l("maxSendable") ?: Long.MAX_VALUE,
            allowsNostr = (o["allowsNostr"] as? JsonPrimitive)?.booleanOrNull == true,
            nostrPubkey = s("nostrPubkey")?.lowercase()?.takeIf { it.length == 64 },
            commentAllowed = l("commentAllowed")?.toInt() ?: 0,
        )
    }

    fun callbackUrl(info: PayInfo, msats: Long, zapRequest: Event?, lnurl: String?, comment: String?): String = buildString {
        append(info.callback).append(if ('?' in info.callback) '&' else '?').append("amount=").append(msats)
        if (zapRequest != null) append("&nostr=").append(URLEncoder.encode(zapRequest.toJson().toString(), "UTF-8"))
        if (lnurl != null) append("&lnurl=").append(lnurl)
        if (zapRequest == null && !comment.isNullOrBlank() && info.commentAllowed > 0) {
            append("&comment=").append(URLEncoder.encode(comment.take(info.commentAllowed), "UTF-8"))
        }
    }

    /** Factura o motivo del error del servidor LNURL. */
    fun parseInvoice(o: JsonObject): Pair<String?, String?> {
        val pr = (o["pr"] as? JsonPrimitive)?.contentOrNull
        val reason = (o["reason"] as? JsonPrimitive)?.contentOrNull
        return pr to reason
    }

    /** Comprueba que la factura es por el importe pedido (no pagamos de más). */
    fun invoiceMatches(invoice: String, msats: Long): Boolean = Bolt11.amountSats(invoice)?.let { it * 1000 == msats } == true

    /** Petición de zap (kind 9734) sin firmar: la firma Amber o una clave temporal (zap anónimo). */
    fun zapRequestTags(recipient: String, address: String?, msats: Long, lnurl: String, relays: List<String>): List<List<String>> =
        buildList {
            add(listOf("relays") + relays.take(5))
            add(listOf("amount", msats.toString()))
            add(listOf("lnurl", lnurl))
            add(listOf("p", recipient))
            address?.let { add(listOf("a", it)) }
        }
}
