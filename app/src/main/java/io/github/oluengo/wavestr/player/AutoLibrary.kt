package io.github.oluengo.wavestr.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo para Android Auto (y otros navegadores multimedia): Favoritos (con carpetas), Streams que emiten,
 * Radios (populares y cerca de ti) y la última escuchada. Cuando el coche elige algo, aquí se resuelve la URL
 * y se avisa a la app ([PlayerConnection.adopt]) para que el resto (anterior/siguiente, widgets) cuadre.
 */
class AutoLibrary(private val c: AppContainer) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stations = ConcurrentHashMap<String, Station>()
    private val streams = ConcurrentHashMap<String, NowPlaying>()
    /** Lista en la que aparecía cada elemento (para anterior/siguiente). */
    private val stationLists = ConcurrentHashMap<String, List<Station>>()
    private val streamLists = ConcurrentHashMap<String, List<NowPlaying>>()
    private var searchResults: List<MediaItem> = emptyList()

    private fun str(id: Int) = c.appContext.getString(id)

    private fun folder(id: String, title: String, subtitle: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle)
                .setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
        ).build()

    private fun stationItem(st: Station, list: List<Station>): MediaItem {
        stations[st.id] = st
        stationLists[st.id] = list
        val art = c.artwork.cached(st)?.ifEmpty { null } ?: st.favicon
        return MediaItem.Builder()
            .setMediaId(ST + st.id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(st.name)
                    .setSubtitle(listOfNotNull(st.country, st.best.label).joinToString(" · "))
                    .setArtworkUri(art?.let(Uri::parse))
                    .setIsBrowsable(false).setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build()
            ).build()
    }

    private fun streamItem(np: NowPlaying, list: List<NowPlaying>): MediaItem {
        streams[np.id] = np
        streamLists[np.id] = list
        return MediaItem.Builder()
            .setMediaId(LIVE + np.id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(np.title).setSubtitle(np.subtitle)
                    .setArtworkUri(np.artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(false).setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build()
            ).build()
    }

    private suspend fun liveToNowPlaying(list: List<LiveStream>): List<NowPlaying> {
        val viewer = c.settings.current().pubkey
        val hosts = runCatching { c.nostr.profiles(list.map { it.hostPubkey }.distinct(), viewer) }.getOrDefault(emptyMap())
        return list.filter { it.streamingUrl != null }.map { s ->
            val h = hosts[s.hostPubkey]
            NowPlaying(
                id = s.address, url = s.streamingUrl!!, title = s.title.ifBlank { str(R.string.untitled_stream) },
                subtitle = h?.bestName ?: Nip19.shortNpub(s.hostPubkey), artworkUrl = s.image ?: h?.picture, hostPubkey = s.hostPubkey,
            )
        }
    }

    // ---------- Árbol ----------

    private suspend fun children(parentId: String): List<MediaItem> {
        c.library.ready()
        val lib = c.library.library.value
        return when {
            parentId == ROOT -> listOf(
                folder(FAVS, str(R.string.tab_favorites)),
                folder(STREAMS, str(R.string.tab_live)),
                folder(RADIOS, str(R.string.tab_radio)),
                folder(RECENT, str(R.string.auto_recent)),
            )
            parentId == FAVS || parentId.startsWith("$FAVS:") -> {
                val folderId = parentId.removePrefix(FAVS).removePrefix(":").ifEmpty { null }
                val subs = lib.subfolders(folderId).map { folder("$FAVS:${it.id}", it.name) }
                val items = lib.itemsIn(folderId)
                val favStations = items.mapNotNull { it.station }
                // Streamers favoritos: solo los que emiten ahora (si no, no hay nada que poner).
                val hosts = items.filter { it.kind == FavItem.Kind.STREAMER }.mapNotNull { it.hostPubkey }.toSet()
                val live = if (hosts.isEmpty()) emptyList()
                else liveToNowPlaying(runCatching { c.nostr.liveOf(hosts, c.settings.current().pubkey) }.getOrDefault(emptyList()))
                subs + favStations.map { stationItem(it, favStations) } + live.map { streamItem(it, live) }
            }
            parentId == STREAMS -> {
                val pk = c.settings.current().pubkey
                val list = runCatching { if (pk != null) c.nostr.liveFromFollows(pk) else c.nostr.liveGlobal(null) }.getOrDefault(emptyList())
                    .ifEmpty { runCatching { c.nostr.liveGlobal(pk) }.getOrDefault(emptyList()) }
                val nps = liveToNowPlaying(list.take(50))
                nps.map { streamItem(it, nps) }
            }
            parentId == RADIOS -> listOf(
                folder(RADIOS_POPULAR, str(R.string.radio_popular)),
                folder(RADIOS_NEAR, str(R.string.radio_near)),
            )
            parentId == RADIOS_POPULAR -> radioList { c.radio.top(60) }
            parentId == RADIOS_NEAR -> radioList { c.radio.byCountry(Locale.getDefault().country.ifEmpty { "ES" }) }
            parentId == RECENT -> listOfNotNull(c.player.lastStation()?.let { stationItem(it, listOf(it)) })
            else -> emptyList()
        }
    }

    private suspend fun radioList(load: suspend () -> List<Station>): List<MediaItem> {
        val list = runCatching { load() }.getOrDefault(emptyList()).filter { it.ok }.take(80)
        return list.map { stationItem(it, list) }
    }

    /** Del elemento que eligió el coche (o una búsqueda por voz) a algo que se pueda reproducir. */
    private suspend fun resolve(item: MediaItem): MediaItem? {
        val id = item.mediaId
        val query = item.requestMetadata.searchQuery
        when {
            id.startsWith(ST) -> {
                val st = stations[id.removePrefix(ST)] ?: c.library.library.value.favorites.firstOrNull { it.station?.id == id.removePrefix(ST) }?.station
                    ?: c.player.lastStation()?.takeIf { it.id == id.removePrefix(ST) } ?: return null
                val np = c.player.nowPlayingFor(st)
                c.player.adopt(np, stations = stationLists[st.id])
                return c.player.mediaItemFor(np)
            }
            id.startsWith(LIVE) -> {
                val np = streams[id.removePrefix(LIVE)] ?: return null
                c.player.adopt(np, streams = streamLists[np.id])
                return c.player.mediaItemFor(np)
            }
            !query.isNullOrBlank() -> {
                // «Pon … en Wavestr»: primero entre los favoritos, luego en el directorio.
                val favs = c.library.library.value.favorites.mapNotNull { it.station }
                val st = favs.firstOrNull { it.name.contains(query, ignoreCase = true) }
                    ?: runCatching { c.radio.search(query) }.getOrDefault(emptyList()).firstOrNull { it.ok } ?: return null
                val np = c.player.nowPlayingFor(st)
                c.player.adopt(np, stations = if (st in favs) favs else listOf(st))
                return c.player.mediaItemFor(np)
            }
            item.localConfiguration != null -> return item
            item.requestMetadata.mediaUri != null -> return item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
            else -> return null
        }
    }

    // ---------- Callback de la sesión ----------

    override fun onGetLibraryRoot(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Wavestr"), params))

    override fun onGetChildren(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val all = runCatching { children(parentId) }.getOrDefault(emptyList())
        val from = (page * pageSize).coerceAtMost(all.size)
        val to = if (pageSize <= 0) all.size else (from + pageSize).coerceAtMost(all.size)
        LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val st = stations[mediaId.removePrefix(ST)]
        val np = streams[mediaId.removePrefix(LIVE)]
        val item = when {
            st != null -> stationItem(st, stationLists[st.id] ?: listOf(st))
            np != null -> streamItem(np, streamLists[np.id] ?: listOf(np))
            else -> null
        }
        return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
        // La app (PlayerConnection) ya manda elementos completos; los del coche llegan solo con el id.
        mediaItems.mapNotNull { if (it.localConfiguration != null) it else resolve(it) }.toMutableList()
    }

    override fun onSearch(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        val list = runCatching { c.radio.search(query) }.getOrDefault(emptyList()).filter { it.ok }.take(40)
        searchResults = list.map { stationItem(it, list) }
        session.notifySearchResultChanged(browser, query, searchResults.size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
        page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(searchResults), params))

    fun release() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    companion object {
        const val ROOT = "root"
        const val FAVS = "favs"
        const val STREAMS = "streams"
        const val RADIOS = "radios"
        const val RADIOS_POPULAR = "radios:popular"
        const val RADIOS_NEAR = "radios:near"
        const val RECENT = "recent"
        const val ST = "st:"
        const val LIVE = "live:"
    }
}
