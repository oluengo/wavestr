package io.github.oluengo.wavestr.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrTest {
    // Vector de ejemplo de NIP-19
    private val hex = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"

    @Test fun npubRoundTrip() {
        assertEquals(hex, Nip19.npubToHex(npub))
        assertEquals(npub, Nip19.hexToNpub(hex))
    }

    @Test fun normalizeAcceptsNpubHexAndUri() {
        assertEquals(hex, Nip19.normalizePubkey(npub))
        assertEquals(hex, Nip19.normalizePubkey("nostr:$npub"))
        assertEquals(hex, Nip19.normalizePubkey(hex.uppercase()))
        assertNull(Nip19.normalizePubkey("npub1notvalid"))
        assertNull(Nip19.normalizePubkey(npub.dropLast(1) + "q"))
    }

    private fun liveEvent(status: String, createdAt: Long, host: String? = "b".repeat(64)) = Event(
        id = "1", pubkey = "a".repeat(64), createdAt = createdAt, kind = Kinds.LIVE_EVENT,
        tags = listOfNotNull(
            listOf("d", "abc"),
            listOf("title", "Tertulia"),
            listOf("status", status),
            listOf("streaming", "https://example.com/live.m3u8"),
            listOf("current_participants", "42"),
            listOf("t", "Bitcoin"),
            host?.let { listOf("p", it, "wss://relay", "Host") },
        ),
    )

    @Test fun parsesNip53LiveEvent() {
        val s = LiveStream.from(liveEvent("live", 1_000))!!
        assertEquals("b".repeat(64), s.hostPubkey)
        assertEquals("Tertulia", s.title)
        assertEquals(42, s.participants)
        assertEquals(listOf("bitcoin"), s.hashtags)
        assertEquals("30311:${"a".repeat(64)}:abc", s.address)
        assertTrue(s.isLiveNow(nowSec = 1_000 + 600))
        assertFalse("sin actualizar en más de 1 h", s.isLiveNow(nowSec = 1_000 + 7_200))
    }

    @Test fun hostFallsBackToAuthor() {
        val s = LiveStream.from(liveEvent("ended", 1_000, host = null))!!
        assertEquals("a".repeat(64), s.hostPubkey)
        assertFalse(s.isLiveNow(1_000))
    }

    @Test fun keepsLatestReplaceable() {
        val old = liveEvent("live", 1_000)
        val new = old.copy(id = "2", createdAt = 2_000, tags = old.tags.map { if (it[0] == "status") listOf("status", "ended") else it })
        val latest = listOf(old, new).latestByAddress()
        assertEquals(1, latest.size)
        assertEquals("2", latest.first().id)
    }

    @Test fun eventJsonRoundTrip() {
        val e = liveEvent("live", 1_234)
        val back = Event.fromJson(e.toJson().toString())
        assertEquals(e, back)
        assertNull(Event.fromJson("""{"id":"x"}"""))
    }

    @Test fun profileParsing() {
        val e = Event("1", hex, 1, Kinds.METADATA, emptyList(),
            """{"name":"luis","display_name":"Luis Díaz","picture":"https://x/p.jpg","lud16":"luis@getalby.com","nip05":"luis@x.es"}""")
        val p = Profile.from(e)!!
        assertEquals("Luis Díaz", p.bestName)
        assertEquals("luis@getalby.com", p.lud16)
        assertNotNull(p.picture)
        assertNull(Profile.from(e.copy(content = "not json")))
    }

    @Test fun followsAndRelays() {
        val contacts = Event("1", hex, 1, Kinds.CONTACTS, listOf(listOf("p", "C".repeat(64)), listOf("p", "short"), listOf("p", "c".repeat(64))))
        assertEquals(listOf("c".repeat(64)), contacts.followedPubkeys())
        val relays = Event("1", hex, 1, Kinds.RELAY_LIST, listOf(
            listOf("r", "wss://a.example/"), listOf("r", "wss://b.example", "write"), listOf("r", "wss://c.example", "read"), listOf("r", "http://bad"),
        ))
        assertEquals(listOf("wss://a.example", "wss://c.example"), relays.readRelays())
    }

    @Test fun filterJson() {
        val f = Filter(kinds = listOf(30311), tags = mapOf("p" to listOf("abc")), since = 10, limit = 5)
        assertEquals("""{"kinds":[30311],"#p":["abc"],"since":10,"limit":5}""", f.toJson().toString())
    }
}
