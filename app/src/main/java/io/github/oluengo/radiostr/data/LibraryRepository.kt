package io.github.oluengo.radiostr.data

import android.content.Context
import io.github.oluengo.radiostr.nostr.NostrJson
import io.github.oluengo.radiostr.radio.Station
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

/** Guarda la biblioteca (favoritos, carpetas, importadas) en un JSON privado de la app. */
class LibraryRepository(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    init {
        scope.launch {
            _library.value = withContext(Dispatchers.IO) {
                runCatching { Library.fromJson(NostrJson.parseToJsonElement(file.readText()) as? JsonObject) }.getOrDefault(Library())
            }
            loaded.complete(Unit)
        }
    }

    fun update(change: (Library) -> Library) {
        scope.launch {
            loaded.await()
            mutex.withLock {
                val next = change(_library.value)
                if (next == _library.value) return@withLock
                _library.value = next
                withContext(Dispatchers.IO) {
                    // Escritura atómica: si se corta a medias, el archivo anterior sigue intacto.
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(next.toJson().toString())
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }

    fun toggleStation(s: Station) = update { it.toggle(FavItem.station(s)) }
    fun toggleStreamer(pubkey: String, name: String, image: String?) = update { it.toggle(FavItem.streamer(pubkey, name, image)) }
    fun newFolder(name: String, parentId: String?) = update { it.addFolder("f" + UUID.randomUUID().toString().take(8), name, parentId) }
}

fun FavItem.Companion.station(s: Station) = FavItem(
    key = s.id, kind = FavItem.Kind.STATION, title = s.name,
    subtitle = listOfNotNull(s.country, s.best.label).joinToString(" · ").ifEmpty { null },
    image = s.favicon, station = s, addedAt = System.currentTimeMillis(),
)

fun FavItem.Companion.streamer(pubkey: String, name: String, image: String?) = FavItem(
    key = "npub:$pubkey", kind = FavItem.Kind.STREAMER, title = name, image = image,
    hostPubkey = pubkey, addedAt = System.currentTimeMillis(),
)
