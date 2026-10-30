package io.github.oluengo.wavestr.data

import io.github.oluengo.wavestr.radio.Station
import io.github.oluengo.wavestr.radio.StreamVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTest {
    private fun st(id: String) = Station(id, "Radio $id", listOf(StreamVariant("https://x/$id", "AAC", 128)), favicon = "https://x/$id.png", tags = listOf("rock"))
    private val pk = "a".repeat(64)

    private val lib = Library()
        .addFolder("f1", "Rock", null)
        .addFolder("f2", "Clásico", "f1")
        .addFolder("f3", "Otra", null)
        .add(FavItem.station(st("a")).copy(folderId = "f1"))
        .add(FavItem.station(st("b")).copy(folderId = "f2"))
        .add(FavItem.station(st("c")).copy(folderId = "f3"))
        .add(FavItem.station(st("d")))
        .add(FavItem.streamer(pk, "Streamer", null).copy(folderId = "f1"))

    @Test fun roundTripFolder() {
        val sh = Shared.ofFolder(lib, "f1")!!
        assertEquals(listOf("f1", "f2"), sh.folders.map { it.id })
        assertEquals(setOf("a", "b", "npub:$pk"), sh.items.map { it.key }.toSet())
        val link = sh.link()
        assertTrue(link.startsWith(Shared.LINK_BASE + "#"))
        val back = Shared.decode(Shared.codeFrom(link)!!)!!
        assertEquals(Shared.Kind.FOLDER, back.kind)
        assertEquals("Rock", back.name)
        assertEquals(sh.items.map { it.key to it.folderId }.toSet(), back.items.map { it.key to it.folderId }.toSet())
        assertEquals("https://x/a", back.items.first { it.key == "a" }.station!!.best.url)
        assertEquals(1, back.streamers)
    }

    @Test fun importFolderCreatesOnlyNeededFolders() {
        val sh = Shared.decode(Shared.ofFolder(lib, "f1")!!.toCode())!!
        var n = 0
        val out = sh.importInto(Library(), setOf("b"), "x") { "n${n++}" }
        // «b» está en Rock/Clásico → se crean las dos carpetas.
        assertEquals(listOf("Rock", "Clásico"), out.folders.map { it.name })
        assertEquals(out.folders[0].id, out.folders[1].parentId)
        assertEquals(out.folders[1].id, out.favorites.single().folderId)
    }

    @Test fun importAllGoesIntoWrapperAndSkipsExisting() {
        val sh = Shared.decode(Shared.ofAll(lib, "Favoritos").toCode())!!
        val mine = Library().add(FavItem.station(st("d")))
        var n = 0
        val out = sh.importInto(mine, sh.items.map { it.key }.toSet(), "Compartido") { "n${n++}" }
        val wrapper = out.folders.first { it.name == "Compartido" }
        assertNull(wrapper.parentId)
        assertEquals(5, out.favorites.size)
        assertNull("ya era favorito: no se mueve", out.favorites.first { it.key == "d" }.folderId)
        assertEquals(wrapper.id, out.folder(out.favorites.first { it.key == "a" }.folderId)!!.parentId)
    }

    @Test fun textAndM3u() {
        val sh = Shared.ofFolder(lib, "f1")!!
        val text = sh.toText()
        assertTrue(text.contains("Radio a — https://x/a"))
        assertTrue(text.contains("Rock / Clásico"))
        assertTrue(text.contains("https://njump.me/npub1"))
        val m3u = sh.toM3u()
        assertTrue(m3u.contains("group-title=\"Rock / Clásico\""))
        assertEquals(2, m3u.lines().count { it.startsWith("#EXTINF") })
        assertEquals("Radio d\nhttps://x/d", Shared.ofItem(lib.favorites.first { it.key == "d" }).toText())
    }

    @Test fun badInput() {
        assertNull(Shared.decode("no-es-un-codigo"))
        assertNull(Shared.codeFrom("https://example.com/#abc"))
        assertNotNull(Shared.codeFrom("wavestr://s#abc"))
    }
}
