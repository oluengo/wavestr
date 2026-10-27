package io.github.oluengo.radiostr.data

import io.github.oluengo.radiostr.nostr.NostrJson
import io.github.oluengo.radiostr.radio.Station
import io.github.oluengo.radiostr.radio.StreamVariant
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val st = Station("rb:1", "Radio Clásica", listOf(StreamVariant("https://x/192", "AAC", 192), StreamVariant("https://x/64", "AAC", 64)), tags = listOf("classical"))
    private fun fav(key: String, folder: String? = null) = FavItem(key, FavItem.Kind.STATION, key, station = st.copy(id = key), folderId = folder)

    @Test fun foldersAndMoves() {
        var lib = Library()
            .addFolder("f1", "Para trabajar", null)
            .addFolder("f2", "Lo-fi", "f1")
            .addFolder("f3", "Huérfana", "no-existe")
            .add(fav("a")).add(fav("b")).add(fav("a"))
        assertEquals(2, lib.favorites.size)
        assertNull("padre inexistente → raíz", lib.folder("f3")!!.parentId)
        lib = lib.move("a", "f2").move("b", "nope")
        assertEquals("f2", lib.favorites.first { it.key == "a" }.folderId)
        assertNull(lib.favorites.first { it.key == "b" }.folderId)
        assertEquals(listOf("f1", "f2"), lib.path("f2").map { it.id })
        assertEquals(listOf("f3" to 0, "f1" to 0, "f2" to 1), lib.tree().map { it.first.id to it.second })
        assertEquals(1 to 0, lib.countIn("f2"))
    }

    @Test fun deletingAFolderKeepsItsContent() {
        val lib = Library().addFolder("f1", "A", null).addFolder("f2", "B", "f1").addFolder("f3", "C", "f2")
            .add(fav("x", "f2")).deleteFolder("f2")
        assertEquals("f1", lib.favorites.single().folderId)
        assertEquals("f1", lib.folder("f3")!!.parentId)
        assertEquals(2, lib.folders.size)
    }

    @Test fun toggleAndImported() {
        var lib = Library().toggle(fav("a"))
        assertTrue(lib.isFavorite("a"))
        lib = lib.toggle(fav("a"))
        assertFalse(lib.isFavorite("a"))
        val imp = st.copy(id = "imp:1", imported = true)
        lib = lib.addImported(listOf(imp, imp)).add(fav("imp:1"))
        assertEquals(1, lib.imported.size)
        lib = lib.removeImported("imp:1")
        assertTrue(lib.imported.isEmpty())
        assertFalse(lib.isFavorite("imp:1"))
    }

    @Test fun jsonRoundTripAndTolerance() {
        val lib = Library().addFolder("f1", "Noches de jazz", null).addFolder("f2", "Sub", "f1")
            .add(fav("rb:1", "f2"))
            .add(FavItem("npub:abc", FavItem.Kind.STREAMER, "Luis", "@luis", null, "f1", hostPubkey = "abc", addedAt = 5))
            .addImported(listOf(st.copy(id = "imp:9", imported = true)))
        val back = Library.fromJson(NostrJson.parseToJsonElement(lib.toJson().toString()) as JsonObject)
        assertEquals(lib, back)

        val broken = """{"v":1,"folders":[{"id":"f1","name":"A","parent":"zz"},{"name":"sin id"}],
            "favorites":[{"key":"k","kind":"STATION","title":"sin emisora"},{"key":"s","kind":"STREAMER","title":"S","folder":"nope","host":"h"},{"kind":"RARO"}],
            "imported":[{"id":"x"}]}"""
        val lib2 = Library.fromJson(NostrJson.parseToJsonElement(broken) as JsonObject)
        assertEquals(1, lib2.folders.size)
        assertNull(lib2.folders.single().parentId)
        assertEquals(listOf("s"), lib2.favorites.map { it.key })
        assertNull(lib2.favorites.single().folderId)
        assertTrue(lib2.imported.isEmpty())
        assertEquals(Library(), Library.fromJson(null))
    }
}
