package io.github.oluengo.wavestr.nostr

/** Mensaje del chat de un directo (NIP-53, kind 1311). */
data class ChatMessage(
    val id: String,
    val pubkey: String,
    val content: String,
    val createdAt: Long,
    val streamAddress: String,
) {
    companion object {
        fun from(e: Event): ChatMessage? {
            if (e.kind != Kinds.LIVE_CHAT) return null
            val a = e.tagValues("a").firstOrNull { it.startsWith("${Kinds.LIVE_EVENT}:") } ?: return null
            if (e.content.isBlank()) return null
            return ChatMessage(e.id, e.pubkey, e.content.trim(), e.createdAt, a)
        }
    }
}

/** Recibo de zap (NIP-57, kind 9735) ya interpretado. */
data class ZapReceipt(
    val id: String,
    /** Quien paga: autor de la zap request incluida en "description". */
    val senderPubkey: String?,
    val recipientPubkey: String?,
    val amountSats: Long,
    val comment: String,
    val streamAddress: String?,
    val createdAt: Long,
) {
    companion object {
        fun from(e: Event): ZapReceipt? {
            if (e.kind != Kinds.ZAP_RECEIPT) return null
            val request = e.tag("description")?.let(Event::fromJson)
            // Importe: preferimos la factura (lo realmente pagado); si no, el "amount" de la petición (msats).
            val sats = e.tag("bolt11")?.let(Bolt11::amountSats)
                ?: request?.tag("amount")?.toLongOrNull()?.div(1000)
                ?: return null
            if (sats <= 0) return null
            return ZapReceipt(
                id = e.id,
                senderPubkey = request?.pubkey ?: e.tag("P"),
                recipientPubkey = e.tag("p"),
                amountSats = sats,
                comment = request?.content?.trim().orEmpty(),
                streamAddress = e.tagValues("a").firstOrNull { it.startsWith("${Kinds.LIVE_EVENT}:") }
                    ?: request?.tagValues("a")?.firstOrNull { it.startsWith("${Kinds.LIVE_EVENT}:") },
                createdAt = e.createdAt,
            )
        }
    }
}

/** Lectura del importe de una factura Lightning (BOLT 11) sin dependencias. */
object Bolt11 {
    private val HRP = Regex("^ln(?:bc|tb|bcrt|tbs)(\\d+)([munp]?)1", RegexOption.IGNORE_CASE)

    /** Satoshis de la factura, o null si no lleva importe o no se entiende. */
    fun amountSats(invoice: String): Long? {
        val m = HRP.find(invoice.trim().removePrefix("lightning:")) ?: return null
        val n = m.groupValues[1].toBigDecimalOrNull() ?: return null
        val btc = when (m.groupValues[2].lowercase()) {
            "m" -> n.movePointLeft(3)
            "u" -> n.movePointLeft(6)
            "n" -> n.movePointLeft(9)
            "p" -> n.movePointLeft(12)
            else -> n
        }
        return btc.movePointRight(8).setScale(0, java.math.RoundingMode.DOWN).toLong()
    }
}

/** Total de sats de una lista de recibos, sin contar duplicados. */
fun List<ZapReceipt>.totalSats(): Long = distinctBy { it.id }.sumOf { it.amountSats }
