package io.github.oluengo.wavestr.radio

import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationSkipTest {
    private fun st(id: String, ok: Boolean = true) = Station(id, id.uppercase(), listOf(StreamVariant("https://$id.example/s", "MP3", 128)), ok = ok)
    private fun fav(s: Station, folder: String? = null) = FavItem.station(s).copy(folderId = folder)

    @Test
    fun favoritesInSameFolder() {
        val a = st("a"); val b = st("b"); val c = st("c"); val d = st("d")
        val favs = listOf(fav(a, "f1"), fav(b, "f2"), fav(c, "f1"), fav(d, "f1"))
        val list = StationSkip.list(c, favs, emptyList())
        assertEquals(listOf("a", "c", "d"), list.map { it.id })
        assertEquals("d", StationSkip.neighbor(c, list, +1)!!.id)
        assertEquals("a", StationSkip.neighbor(d, list, +1)!!.id)
        assertEquals("d", StationSkip.neighbor(a, list, -1)!!.id)
    }

    @Test
    fun aloneInFolderUsesAllFavorites() {
        val a = st("a"); val b = st("b")
        val favs = listOf(fav(a, "f1"), fav(b, "f2"))
        assertEquals(listOf("a", "b"), StationSkip.list(b, favs, emptyList()).map { it.id })
    }

    @Test
    fun notFavoriteUsesContextSkippingDown() {
        val a = st("a"); val b = st("b", ok = false); val c = st("c")
        val list = StationSkip.list(a, emptyList(), listOf(a, b, c))
        assertEquals(listOf("a", "c"), list.map { it.id })
        assertEquals(emptyList<Station>(), StationSkip.list(st("x"), emptyList(), listOf(a, c)))
        assertNull(StationSkip.neighbor(a, listOf(a), +1))
    }
}
