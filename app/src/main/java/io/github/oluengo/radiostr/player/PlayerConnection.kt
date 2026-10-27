package io.github.oluengo.radiostr.player

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.oluengo.radiostr.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Lo que suena ahora (directo Nostr; en F3 también emisoras de radio). */
data class NowPlaying(
    val id: String,
    val url: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val hostPubkey: String?,
    val hasVideo: Boolean = true,
    val isLive: Boolean = true,
)

data class PlayerUiState(
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** Lo que el usuario eligió en el selector. */
    val userWantsVideo: Boolean = true,
    /** Lo que de verdad se está haciendo (puede forzarse a audio). */
    val audioOnly: Boolean = false,
    val forcedByCellular: Boolean = false,
    val audioLayout: Hls.AudioLayout = Hls.AudioLayout.UNKNOWN,
    /** Alturas de vídeo disponibles (1080, 720…), de mayor a menor. */
    val videoHeights: List<Int> = emptyList(),
    /** null = Auto. */
    val selectedHeight: Int? = null,
    val currentHeight: Int? = null,
    val error: Boolean = false,
)

/**
 * Puente entre la UI y PlaybackService. Gestiona el cambio audio/vídeo sin cortar el stream,
 * el paso a audio con la pantalla apagada y la calidad de vídeo.
 */
class PlayerConnection(
    private val context: Context,
    private val settings: SettingsRepository,
    private val hls: HlsInspector,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controllerFuture: Deferred<MediaController>? = null
    var controller: MediaController? = null
        private set

    /** true mientras la app está en segundo plano o con la pantalla apagada. */
    private var inBackground = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                inBackground = true
                scope.launch { if (settings.current().audioOnScreenOff) applyVideo() }
            }

            override fun onStart(owner: LifecycleOwner) {
                inBackground = false
                applyVideo()
            }
        })
    }

    private suspend fun ensureController(): MediaController {
        controller?.let { return it }
        val f = controllerFuture ?: scope.async {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also { c ->
                c.addListener(listener)
                controller = c
                syncFromController()
            }
        }.also { controllerFuture = it }
        return f.await()
    }

    /** Reconecta al abrir la app si ya había algo sonando en el servicio. */
    fun connect() {
        scope.launch { runCatching { ensureController() } }
    }

    fun play(item: NowPlaying) {
        scope.launch {
            val c = ensureController()
            val s = settings.current()
            val cellular = s.audioOnCellular && isCellular()
            _state.update {
                it.copy(
                    nowPlaying = item, error = false, forcedByCellular = cellular,
                    audioLayout = Hls.AudioLayout.UNKNOWN, videoHeights = emptyList(),
                    selectedHeight = null, currentHeight = null,
                )
            }
            val media = MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(item.url)
                .apply { if (item.url.contains(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.subtitle)
                        .apply { item.artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                        .build()
                )
                .build()
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            c.setMediaItem(media)
            applyVideoNow()
            c.prepare()
            c.play()
            val layout = hls.audioLayout(item.url)
            _state.update { if (it.nowPlaying?.id == item.id) it.copy(audioLayout = layout) else it }
        }
    }

    fun setWantsVideo(wants: Boolean) {
        _state.update { it.copy(userWantsVideo = wants, forcedByCellular = if (wants) false else it.forcedByCellular) }
        applyVideo()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            // En directo, volver al borde en vez de reanudar con retraso.
            if (c.isCurrentMediaItemLive) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun stop() {
        controller?.run { stop(); clearMediaItems() }
        _state.update { PlayerUiState(userWantsVideo = it.userWantsVideo) }
    }

    /** height = null → Auto. */
    fun selectHeight(height: Int?) {
        val c = controller ?: return
        val builder = c.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (height != null) {
            c.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { g ->
                val idx = (0 until g.length).firstOrNull { g.getTrackFormat(it).height == height }
                if (idx != null) builder.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, idx))
            }
        }
        c.trackSelectionParameters = builder.build()
        _state.update { it.copy(selectedHeight = height) }
    }

    /** Decide si se decodifica vídeo según la elección, la red y si la pantalla está apagada. */
    private fun applyVideo() {
        scope.launch { applyVideoNow() }
    }

    private suspend fun applyVideoNow() {
        run {
            val c = controller ?: return
            val st = _state.value
            val s = settings.current()
            val audioOnly = !st.userWantsVideo || st.forcedByCellular ||
                (inBackground && s.audioOnScreenOff) || st.nowPlaying?.hasVideo == false
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
                .build()
            _state.update { it.copy(audioOnly = audioOnly) }
        }
    }

    private fun isCellular(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun syncFromController() {
        val c = controller ?: return
        _state.update {
            it.copy(
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                nowPlaying = it.nowPlaying ?: c.currentMediaItem?.let { m ->
                    m.localConfiguration?.uri?.toString()?.let { url ->
                        NowPlaying(
                            id = m.mediaId, url = url,
                            title = m.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = m.mediaMetadata.artist?.toString().orEmpty(),
                            artworkUrl = m.mediaMetadata.artworkUri?.toString(),
                            hostPubkey = null,
                        )
                    }
                },
            )
        }
        updateTracks(c.currentTracks)
    }

    private fun updateTracks(tracks: Tracks) {
        val heights = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
            .filter { it > 0 }.distinct().sortedDescending()
        val current = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).height } }
        _state.update { it.copy(videoHeights = heights, currentHeight = current?.takeIf { h -> h > 0 }) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onTracksChanged(tracks: Tracks) = updateTracks(tracks)

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = true, isPlaying = false) }
        }
    }
}
