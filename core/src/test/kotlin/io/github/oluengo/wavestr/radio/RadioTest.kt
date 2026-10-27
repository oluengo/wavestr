package io.github.oluengo.wavestr.radio

import io.github.oluengo.wavestr.nostr.NostrJson
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTest {
    @Test fun parsesPls() {
        val r = Playlists.parse(
            """
            [playlist]
            NumberOfEntries=3
            File2=http://example.net:8000/jazz
            Title2=Jazz Nocturno
            File1=https://example.net/clasica-128.mp3
            Title1=Clásica 24h
            File3=https://example.net/clasica-128.mp3
            Version=2
            """.trimIndent()
        ) as Playlists.Result.Found
        assertEquals(Playlists.Kind.PLS, r.kind)
        assertEquals(listOf("Clásica 24h", "Jazz Nocturno"), r.items.map { it.title })
        assertEquals(2, r.items.size)
    }

    @Test fun parsesExtendedM3u() {
        val r = Playlists.parse(
            "#EXTM3U\r\n#EXTINF:-1 tvg-logo=\"logo.png\" group-title=\"Pop\",Lo-fi Barcelona\r\nhttps://example.net/lofi.aac\r\n#EXTINF:-1,\r\nhttps://radio.example.org/live\r\n# comentario\r\nnot a url\r\n"
        ) as Playlists.Result.Found
        assertEquals(Playlists.Kind.M3U, r.kind)
        assertEquals(listOf("Lo-fi Barcelona", "radio.example.org"), r.items.map { it.title })
    }

    @Test fun singleUrlsAndEdgeCases() {
        val s = Playlists.parse("  https://stream.example.com/radio.mp3 ") as Playlists.Result.Found
        assertEquals(Playlists.Kind.URL, s.kind)
        assertEquals("stream.example.com", s.items.single().title)
        assertTrue(Playlists.parse("https://example.com/list.pls") is Playlists.Result.NeedsDownload)
        assertTrue(Playlists.parse("https://example.com/list.m3u?x=1") is Playlists.Result.NeedsDownload)
        assertTrue("una URL .m3u8 es un stream HLS", Playlists.parse("https://example.com/live/master.m3u8") is Playlists.Result.Found)
        assertTrue(Playlists.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8") is Playlists.Result.HlsContent)
        assertTrue(Playlists.parse("hola") is Playlists.Result.Empty)
        assertTrue(Playlists.parse("") is Playlists.Result.Empty)
    }

    @Test fun importedStationsHaveStableIds() {
        val a = Playlists.toStation(Playlists.Item("X", "https://a.example/x.mp3"), Playlists.Kind.M3U)
        val b = Playlists.toStation(Playlists.Item("Otro nombre", "https://a.example/x.mp3"), Playlists.Kind.PLS)
        assertEquals(a.id, b.id)
        assertEquals("MP3", a.best.codec)
        assertTrue(a.imported)
    }

    private val json = """
        [
         {"stationuuid":"u1","name":"Radio Clásica","url":"http://x/c64","url_resolved":"https://x/c64.aac","homepage":"https://www.rtve.es/radioclasica","favicon":"https://x/f.png","tags":"Classical,cultura, classical","country":"Spain","countrycode":"es","language":"spanish","codec":"AAC","bitrate":64,"hls":0,"lastcheckok":1,"votes":100},
         {"stationuuid":"u2","name":"Radio  CLÁSICA","url_resolved":"https://x/c192.aac","homepage":"https://rtve.es/radioclasica","codec":"AAC","bitrate":192,"hls":0,"lastcheckok":0,"votes":50,"countrycode":"ES","tags":""},
         {"stationuuid":"u3","name":"Jazz FM","url_resolved":"https://j/stream","homepage":"","codec":"MP3","bitrate":128,"hls":0,"lastcheckok":0,"votes":5,"countrycode":"GB"},
         {"stationuuid":"u4","name":"Sin URL","url_resolved":"ftp://bad","codec":"MP3"},
         {"name":"Sin id","url_resolved":"https://ok"}
        ]
    """.trimIndent()

    @Test fun groupsDirectoryVariants() {
        val raw = RadioBrowserJson.parseStations(NostrJson.parseToJsonElement(json) as JsonArray)
        assertEquals(3, raw.size)
        assertEquals(listOf("classical", "cultura"), raw[0].tags)
        val st = Stations.group(raw)
        assertEquals(2, st.size)
        val clasica = st[0]
        assertEquals("rb:u1", clasica.id)
        assertEquals(listOf(192, 64), clasica.variants.map { it.bitrate })
        assertEquals("AAC · 192 kbps", clasica.best.label)
        assertTrue("alguna variante responde", clasica.ok)
        assertEquals(150, clasica.votes)
        assertFalse(st[1].ok)
        assertEquals(64, Stations.pick(clasica, null, cellular = true).bitrate)
        assertEquals(192, Stations.pick(clasica, null, cellular = false).bitrate)
        assertEquals(64, Stations.pick(clasica, 1, cellular = false).bitrate)
        assertNull(StreamVariant("u", "unknown", 0).label)
    }
}
