package io.github.oluengo.wavestr.desktop

import androidx.compose.runtime.mutableLongStateOf
import io.github.oluengo.wavestr.radio.Station
import io.github.oluengo.wavestr.radio.Stations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/** Lo que suena: un directo de Nostr o una emisora (con la variante elegida). */
data class NowPlaying(
    val id: String,
    val url: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val hostPubkey: String?,
    val isLive: Boolean = true,
    val station: Station? = null,
    val variantIndex: Int? = null,
) {
    val isRadio get() = station != null
}

data class PlayerState(
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: Boolean = false,
    val icyTitle: String? = null,
    val audioOnly: Boolean = false,
    /** false si no se encuentra VLC en el equipo. */
    val available: Boolean = true,
    val volume: Int = 90,
)

/**
 * Reproductor con libVLC (vlcj): radio (MP3/AAC/Ogg/FLAC, PLS/M3U ya resueltos), HLS y vídeo.
 * El vídeo se pinta en Compose fotograma a fotograma ([VideoFrames]); sin ventana aparte de VLC.
 */
class VlcPlayer(private val scope: CoroutineScope, private val onStationPlay: (String?) -> Unit) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    val video = VideoFrames()
    private val variantChoice = HashMap<String, Int?>()

    private val factory: MediaPlayerFactory? = runCatching {
        if (NativeDiscovery().discover()) MediaPlayerFactory("--no-video-title-show", "--quiet") else null
    }.getOrNull()
    private val mp: EmbeddedMediaPlayer? = runCatching { factory?.mediaPlayers()?.newEmbeddedMediaPlayer() }.getOrNull()

    init {
        _state.update { it.copy(available = mp != null) }
        mp?.let { p ->
            p.videoSurface().set(factory!!.videoSurfaces().newVideoSurface(video, video, true))
            p.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) = _state.update { it.copy(isPlaying = true, isBuffering = false, error = false) }
                override fun paused(mediaPlayer: MediaPlayer) = _state.update { it.copy(isPlaying = false) }
                override fun stopped(mediaPlayer: MediaPlayer) = _state.update { it.copy(isPlaying = false, isBuffering = false) }
                override fun finished(mediaPlayer: MediaPlayer) = _state.update { it.copy(isPlaying = false, isBuffering = false) }
                override fun error(mediaPlayer: MediaPlayer) = _state.update { it.copy(isPlaying = false, isBuffering = false, error = true) }
                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = _state.update { it.copy(isBuffering = newCache < 100f) }
            })
            p.events().addMediaEventListener(object : MediaEventAdapter() {
                override fun mediaMetaChanged(media: Media, metaType: Meta) {
                    if (metaType != Meta.NOW_PLAYING) return
                    val now = runCatching { media.meta().get(Meta.NOW_PLAYING) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    _state.update { it.copy(icyTitle = now) }
                }
            })
            p.audio().setVolume(_state.value.volume)
            // Respaldo del título ICY: no todas las emisoras disparan el evento de metadatos; se consulta cada 4 s.
            scope.launch(Dispatchers.IO) {
                while (true) {
                    delay(4_000)
                    if (!_state.value.isPlaying) continue
                    val now = runCatching { p.media().meta().get(Meta.NOW_PLAYING) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    if (now != null && now != _state.value.icyTitle) _state.update { it.copy(icyTitle = now) }
                }
            }
        }
    }

    fun play(item: NowPlaying) {
        val p = mp ?: return
        video.clear()
        _state.update { it.copy(nowPlaying = item, error = false, icyTitle = null, isBuffering = true, audioOnly = it.audioOnly && !item.isRadio) }
        // Las llamadas a libVLC, fuera del hilo de la UI y de sus propios eventos.
        scope.launch(Dispatchers.IO) { p.media().play(item.url, ":network-caching=1500", ":http-reconnect") }
    }

    /** Emisora con la variante elegida o, en Auto, la de más calidad (en el ordenador no hay datos móviles). */
    fun playStation(st: Station, chosen: Int? = variantChoice[st.id]) {
        val v = Stations.pick(st, chosen, false)
        play(
            NowPlaying(
                id = st.id, url = v.url, title = st.name,
                subtitle = listOfNotNull(st.country, v.label).joinToString(" · "),
                artworkUrl = st.favicon, hostPubkey = null, isLive = false, station = st, variantIndex = chosen,
            )
        )
        onStationPlay(v.uuid)
    }

    fun selectVariant(index: Int?) {
        val st = _state.value.nowPlaying?.station ?: return
        variantChoice[st.id] = index
        playStation(st, index)
    }

    fun togglePlay() {
        val p = mp ?: return
        val np = _state.value.nowPlaying ?: return
        scope.launch(Dispatchers.IO) {
            when {
                p.status().isPlaying -> p.controls().pause()
                // Un directo pausado se retoma desde el punto en vivo, no desde donde se dejó.
                np.isLive || _state.value.error -> p.media().play(np.url, ":network-caching=1500")
                else -> p.controls().play()
            }
        }
    }

    fun stop() {
        val p = mp ?: return
        scope.launch(Dispatchers.IO) { p.controls().stop() }
        video.clear()
        _state.update { it.copy(nowPlaying = null, isPlaying = false, isBuffering = false, icyTitle = null, error = false) }
    }

    /** En el ordenador no hace falta ahorrar: «solo audio» oculta el vídeo sin cortar el directo. */
    fun setAudioOnly(v: Boolean) = _state.update { it.copy(audioOnly = v) }

    fun setVolume(v: Int) {
        val vol = v.coerceIn(0, 150)
        _state.update { it.copy(volume = vol) }
        mp?.let { p -> scope.launch(Dispatchers.IO) { p.audio().setVolume(vol) } }
    }

    fun release() {
        runCatching { mp?.controls()?.stop(); mp?.release(); factory?.release() }
    }
}

/**
 * Recibe los fotogramas de VLC (RV32 = BGRA) en un búfer propio; solo el hilo de la UI toca el Bitmap de Skia
 * ([current]), así nunca se pinta un bitmap a medio escribir. [frame] cambia con cada fotograma.
 */
class VideoFrames : BufferFormatCallback, RenderCallback {
    val frame = mutableLongStateOf(0L)
    private val lock = Any()
    private var back = ByteArray(0)
    private var fresh = false
    @Volatile var width = 0
        private set
    @Volatile var height = 0
        private set
    private var ui: Bitmap? = null

    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
        synchronized(lock) {
            width = sourceWidth; height = sourceHeight
            back = ByteArray(sourceWidth * sourceHeight * 4)
            fresh = false
        }
        return RV32BufferFormat(sourceWidth, sourceHeight)
    }

    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}

    override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
        synchronized(lock) {
            val src = nativeBuffers[0]
            src.rewind()
            if (back.isEmpty() || src.remaining() < back.size) return
            src.get(back, 0, back.size)
            fresh = true
        }
        frame.longValue++
    }

    /** Llamar solo desde la UI: devuelve el último fotograma (o null si no hay vídeo). */
    fun current(): Bitmap? = synchronized(lock) {
        if (width == 0 || height == 0 || back.isEmpty()) return null
        val b = ui?.takeIf { it.width == width && it.height == height }
            ?: Bitmap().apply { allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)) }.also { ui?.close(); ui = it }
        if (fresh) { b.installPixels(back); fresh = false }
        b
    }

    fun clear() {
        synchronized(lock) { width = 0; height = 0; back = ByteArray(0); fresh = false }
        frame.longValue++
    }
}
