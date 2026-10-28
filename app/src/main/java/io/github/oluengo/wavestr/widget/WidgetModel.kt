package io.github.oluengo.wavestr.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.data.Accent
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.player.SleepTimer
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Un favorito tal como lo pinta el widget. */
data class FavTile(val key: String, val title: String, val folderId: String?, val art: Bitmap?)

/** Todo lo que necesitan los widgets, ya calculado (y con las carátulas como Bitmap). */
data class WidgetModel(
    val idle: Boolean = true,
    val title: String = "",
    val subtitle: String = "",
    val song: String? = null,
    val isNostr: Boolean = false,
    val isLive: Boolean = false,
    val playing: Boolean = false,
    val art: Bitmap? = null,
    /** Hora (ms) a la que se apaga, o -1 = al terminar el stream; null = sin temporizador. */
    val sleepAt: Long? = null,
    val favorites: List<FavTile> = emptyList(),
    val accent: Accent = Accent.VIOLET,
)

/**
 * Mantiene el [WidgetModel] al día con el reproductor, los favoritos y los ajustes, y avisa a los
 * widgets que haya en la pantalla de inicio para que se vuelvan a pintar.
 */
@OptIn(FlowPreview::class)
class WidgetSync(private val app: Application, private val c: AppContainer) {
    private val _model = MutableStateFlow(WidgetModel())
    val model: StateFlow<WidgetModel> = _model.asStateFlow()
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val widgets: List<GlanceAppWidget> = listOf(BarWidget(), PlayerWidget(), FavoritesWidget(), SquareWidget())

    fun start() {
        c.appScope.launch {
            combine(c.player.state, c.library.library, c.settings.flow, c.player.sleep.mode) { ps, lib, s, sleep ->
                Snapshot(ps.nowPlaying, ps.isPlaying, ps.icyTitle, lib.favorites, s.accent, sleep)
            }.distinctUntilChanged().debounce(300).collectLatest { snap ->
                try {
                    _model.value = build(snap)
                    if (anyPlaced()) widgets.forEach { runCatching { it.updateAll(app) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    private data class Snapshot(
        val np: io.github.oluengo.wavestr.player.NowPlaying?, val playing: Boolean, val icy: String?,
        val favs: List<FavItem>, val accent: Accent, val sleep: SleepTimer.Mode?,
    )

    private suspend fun anyPlaced(): Boolean = runCatching {
        val m = GlanceAppWidgetManager(app)
        widgets.any { m.getGlanceIds(it.javaClass).isNotEmpty() }
    }.getOrDefault(false)

    private fun stationArt(st: Station): String? = c.artwork.cached(st)?.ifEmpty { null } ?: st.favicon

    private suspend fun build(s: Snapshot): WidgetModel {
        val favs = s.favs.take(30).map { f ->
            val url = f.station?.let(::stationArt) ?: f.image
            FavTile(f.key, f.title, f.folderId, url?.let { load(it) })
        }
        val sleepAt = when (val m = s.sleep) {
            is SleepTimer.Mode.At -> m.endsAt
            SleepTimer.Mode.StreamEnd -> -1L
            null -> null
        }
        val np = s.np
        if (np == null) {
            val last = c.player.lastStation()
            return WidgetModel(
                idle = true, title = last?.name.orEmpty(), subtitle = "",
                art = last?.let(::stationArt)?.let { load(it) }, favorites = favs, accent = s.accent,
            )
        }
        val artUrl = np.station?.let(::stationArt) ?: np.artworkUrl
        return WidgetModel(
            idle = false, title = np.title, subtitle = np.subtitle, song = s.icy,
            isNostr = np.hostPubkey != null, isLive = np.isLive && np.station == null, playing = s.playing,
            art = artUrl?.let { load(it) }, sleepAt = sleepAt, favorites = favs, accent = s.accent,
        )
    }

    /** Carátula a 192 px (los widgets tienen un límite de memoria para imágenes). */
    private suspend fun load(url: String): Bitmap? {
        bitmaps[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(app).data(url).size(192).allowHardware(false).build()
                (app.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(192, 192)
            }.getOrNull()
        }?.also { if (bitmaps.size > 60) bitmaps.clear(); bitmaps[url] = it }
    }
}

val Context.widgetSync: WidgetSync get() = (applicationContext as io.github.oluengo.wavestr.WavestrApp).container.widgets
