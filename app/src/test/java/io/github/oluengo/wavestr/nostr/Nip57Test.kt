package io.github.oluengo.wavestr.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip57Test {
    private val stream = "30311:${"a".repeat(64)}:abc"

    @Test fun bolt11Amounts() {
        assertEquals(250_000L, Bolt11.amountSats("lnbc2500u1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypq"))
        assertEquals(1L, Bolt11.amountSats("lnbc10n1pj9x7qz8f6ycs3h"))
        assertEquals(100L, Bolt11.amountSats("LNBC1U1ABC"))
        assertEquals(100_000L, Bolt11.amountSats("lightning:lnbc1m1xyz"))
        assertNull("sin importe", Bolt11.amountSats("lnbc1pvjluezpp5qqqsyq"))
        assertNull(Bolt11.amountSats("not an invoice"))
    }

    @Test fun zapReceiptUsesRequestForSenderAndComment() {
        val request = Event(
            "req", "c".repeat(64), 10, Kinds.ZAP_REQUEST,
            listOf(listOf("amount", "21000"), listOf("a", stream), listOf("p", "b".repeat(64))),
            "gracias por el directo",
        )
        val receipt = Event(
            "rcpt", "d".repeat(64), 11, Kinds.ZAP_RECEIPT,
            listOf(
                listOf("p", "b".repeat(64)),
                listOf("bolt11", "lnbc210n1pxyz"),
                listOf("description", request.toJson().toString()),
            ),
        )
        val z = ZapReceipt.from(receipt)!!
        assertEquals(21L, z.amountSats)
        assertEquals("c".repeat(64), z.senderPubkey)
        assertEquals("gracias por el directo", z.comment)
        assertEquals(stream, z.streamAddress)
        assertEquals(42L, listOf(z, z.copy(id = "other"), z).totalSats())
    }

    @Test fun zapReceiptFallsBackToRequestAmount() {
        val request = Event("req", "c".repeat(64), 10, Kinds.ZAP_REQUEST, listOf(listOf("amount", "5000000")))
        val receipt = Event("r", "d".repeat(64), 11, Kinds.ZAP_RECEIPT, listOf(listOf("description", request.toJson().toString())))
        assertEquals(5_000L, ZapReceipt.from(receipt)!!.amountSats)
        assertNull(ZapReceipt.from(receipt.copy(tags = emptyList())))
    }

    @Test fun chatMessages() {
        val e = Event("m", "c".repeat(64), 5, Kinds.LIVE_CHAT, listOf(listOf("a", stream, "wss://r")), "  hola  ")
        assertEquals("hola", ChatMessage.from(e)!!.content)
        assertNull(ChatMessage.from(e.copy(content = " ")))
        assertNull(ChatMessage.from(e.copy(tags = emptyList())))
    }
}
