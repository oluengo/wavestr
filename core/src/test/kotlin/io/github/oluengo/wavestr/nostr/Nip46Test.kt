package io.github.oluengo.wavestr.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip46Test {
    private val pk = "fa984bd7dbb282f07e16e7ae87b26a2a7b9b90b7246a44771f0cf5ae58018f52"

    @Test fun parseBunkerUri() {
        val u = BunkerUri.parse("bunker://$pk?relay=wss%3A%2F%2Frelay.nsec.app&relay=nos.lol&secret=abc123")!!
        assertEquals(pk, u.signerPubkey)
        assertEquals(listOf("wss://relay.nsec.app", "wss://nos.lol"), u.relays)
        assertEquals("abc123", u.secret)
        assertEquals(u, BunkerUri.parse(u.toUri()))
    }

    @Test fun rejectsBad() {
        assertNull(BunkerUri.parse("bunker://nope?relay=wss://x.y"))
        assertNull(BunkerUri.parse("bunker://$pk"))
        assertNull(BunkerUri.parse("nostrconnect://$pk?relay=wss://x.y"))
    }
}

class ReactionTest {
    private val addr = "30311:abc:d1"
    private fun ev(content: String, tags: List<List<String>>) = Event("i", "pk", 1, Kinds.REACTION, tags, content)

    @Test fun parse() {
        assertEquals("🔥", Reaction.from(ev("🔥", Reaction.tags(addr, "p1", "m1")), addr)!!.emoji)
        assertEquals("m1", Reaction.from(ev("+", Reaction.tags(addr, "p1", "m1")), addr)!!.eventId)
        assertEquals("❤️", Reaction.from(ev("+", Reaction.tags(addr, "p1", null)), addr)!!.emoji)
        assertNull(Reaction.from(ev("-", Reaction.tags(addr, "p1", null)), addr))
        assertNull(Reaction.from(ev("+", listOf(listOf("p", "x"))), addr))
        assertEquals(listOf("k", "1311"), Reaction.tags(addr, "p1", "m1").last())
    }
}
