package io.github.oluengo.wavestr.desktop.ui

import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.streamer
import io.github.oluengo.wavestr.data.station
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.I18n
import io.github.oluengo.wavestr.desktop.VideoFrames
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile
import io.github.oluengo.wavestr.nostr.totalSats
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.NumberFormat

/**
 * Reproductor, igual para radio y streams: arriba, siempre a la vista, qué suena (y el vídeo, si lo hay);
 * en medio lo que se desplaza (carátula, detalles, chat o emisoras parecidas); abajo los controles y, en
 * los directos, la caja del chat. [onClose] null = fijo a la derecha (ventana ancha).
 */
@Composable
fun PlayerPanel(c: DesktopContainer, onClose: (() -> Unit)?) {
    val st by c.player.state.collectAsState()
    val s by c.settings.flow.collectAsState()
    val np = st.nowPlaying
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val chat = remember { ChatModel(c, scope) }
    val liveAddress = np?.takeIf { it.isLive && it.hostPubkey != null }?.id
    LaunchedEffect(liveAddress) { chat.watch(liveAddress, np?.hostPubkey) }
    var zap by remember { mutableStateOf<ZapTarget?>(null) }
    var share by remember { mutableStateOf<io.github.oluengo.wavestr.data.Shared?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    val canSign = s.pubkey != null && s.bunker

    Column(Modifier.fillMaxSize()) {
        NowPlayingBar(c, st, onClose, onShare = { share = it })
        if (np == null) {
            Text(t("nothing_playing"), color = Rs.colors.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        val showVideo = np.isLive && !st.audioOnly
        if (showVideo) Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            if (!fullscreen) VideoCanvas(c.player.video, Modifier.fillMaxSize())
            if (st.isBuffering && st.available) CircularProgressIndicator(Modifier.align(Alignment.Center))
            IconButton(
                onClick = { fullscreen = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
            ) { Icon(Icons.Outlined.Fullscreen, t("fullscreen")) }
        }

        val scroll = rememberScrollState()
        val chatState by chat.state.collectAsState()
        LaunchedEffect(chatState.lines.size) { if (scroll.maxValue - scroll.value < 400) scroll.animateScrollTo(scroll.maxValue) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            if (!showVideo) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                val size = if (liveAddress != null) 0.45f else 0.7f
                val station = np.station
                if (station != null) StationArtwork(c, station, Modifier.widthIn(max = 260.dp).fillMaxWidth(size).aspectRatio(1f), corner = 20.dp)
                else Artwork(np.artworkUrl, np.id, np.title, Modifier.widthIn(max = 260.dp).fillMaxWidth(size).aspectRatio(1f), corner = 20.dp)
                if (st.isBuffering && st.available) CircularProgressIndicator()
            }
            val margin = io.github.oluengo.wavestr.player.BufferPolicy.marginMs()
            if (st.isBuffering && margin >= 5_000) Text(
                t("buffer_loading", (margin * st.bufferPercent / 100_000).toInt(), (margin / 1000).toInt()),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            if (np.isLive) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                SegmentedButton(st.audioOnly, { c.player.setAudioOnly(true) }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) }) { Text(t("player_audio")) }
                SegmentedButton(!st.audioOnly, { c.player.setAudioOnly(false) }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) }) { Text(t("player_video")) }
            }
            np.station?.let { RadioQuality(c, it, np.variantIndex, np.url) }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(np.title, style = MaterialTheme.typography.titleLarge)
                st.icyTitle?.let { Text("♪ $it", fontWeight = FontWeight.SemiBold) }
                Text(np.subtitle, color = Rs.colors.muted)
            }
            if (st.error) {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t(if (np.isRadio) "radio_play_error" else "player_error"), color = Rs.colors.live)
                    val station = np.station
                    if (station != null && station.variants.size > 1) OutlinedButton(onClick = {
                        val cur = station.variants.indexOfFirst { it.url == np.url }
                        c.player.selectVariant((cur + 1) % station.variants.size)
                    }) { Text(t("radio_try_other")) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = Rs.colors.muted, modifier = Modifier.size(18.dp))
                Slider(st.volume.toFloat(), { c.player.setVolume(it.toInt()) }, valueRange = 0f..150f, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                SleepTimerChip(c.player.sleep, isNostr = np.hostPubkey != null)
                BoostChip(c, np.id)
            }
            if (liveAddress != null) {
                ChatHeader(chat, canSign)
                ChatLines(chat, canSign, onZap = { l -> l.pubkey?.let { pk -> zap = ZapTarget(pk, l.author, liveAddress, np.title, l.id) } })
            }
            np.station?.let { SimilarStations(c, it) }
        }

        HorizontalDivider(color = Rs.colors.line)
        Controls(c, st, onZap = np.hostPubkey?.takeIf { np.isLive }?.let { h -> { zap = ZapTarget(h, np.subtitle, liveAddress, np.title) } })
        if (liveAddress != null) ChatInput(c, chat)
    }

    if (fullscreen && np != null) FullscreenVideo(c, onExit = { fullscreen = false })
    zap?.let { z -> ZapDialog(c, z, onDismiss = { zap = null }, onZapped = { chat.refreshNow() }) }
    share?.let { ShareDialog(it, onDismiss = { share = null }) }
}

/** Barra de arriba: miniatura, título y canción o anfitrión, compartir y parar. */
@Composable
private fun NowPlayingBar(c: DesktopContainer, st: io.github.oluengo.wavestr.desktop.PlayerState, onClose: (() -> Unit)?, onShare: (io.github.oluengo.wavestr.data.Shared) -> Unit) {
    val np = st.nowPlaying
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.ExpandMore, t("minimize")) } else Box(Modifier.size(12.dp))
        if (np != null) {
            val station = np.station
            if (station != null) StationArtwork(c, station, Modifier.size(36.dp), corner = 8.dp) else Artwork(np.artworkUrl, np.id, np.title, Modifier.size(36.dp), corner = 8.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (np.isLive) LiveBadge()
                    Text(np.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text(st.icyTitle?.let { "♪ $it" } ?: np.subtitle, style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            val item = np.station?.let { FavItem.station(it) }
                ?: np.hostPubkey?.let { h -> FavItem.streamer(h, np.subtitle.ifBlank { np.title }, np.artworkUrl) }
            if (item != null) IconButton(onClick = { onShare(io.github.oluengo.wavestr.data.Shared.ofItem(item)) }) { Icon(Icons.Outlined.Share, t("share")) }
        } else Spacer(Modifier.weight(1f))
        IconButton(onClick = { c.player.stop(); onClose?.invoke() }, enabled = np != null) { Icon(Icons.Outlined.Close, t("stop")) }
    }
}

/** Zap (en directos), favorito, anterior, play/pausa y siguiente: iguales para radio y streams. */
@Composable
private fun Controls(c: DesktopContainer, st: io.github.oluengo.wavestr.desktop.PlayerState, onZap: (() -> Unit)?) {
    val np = st.nowPlaying ?: return
    val lib by c.library.library.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onZap != null) IconButton(onClick = onZap, colors = IconButtonDefaults.iconButtonColors(containerColor = Rs.colors.zapSoft, contentColor = Rs.colors.zap)) {
            Icon(Icons.Filled.Bolt, t("zap_button"))
        } else Box(Modifier.size(48.dp))
        val canSkip = c.player.canSkip()
        IconButton(onClick = { c.player.skip(-1) }, enabled = canSkip) { Icon(Icons.Outlined.SkipPrevious, t("skip_previous"), Modifier.size(28.dp)) }
        FilledIconButton(
            onClick = c.player::togglePlay, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
        ) { Icon(if (st.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, t(if (st.isPlaying) "pause" else "play"), Modifier.size(28.dp)) }
        IconButton(onClick = { c.player.skip(+1) }, enabled = canSkip) { Icon(Icons.Outlined.SkipNext, t("skip_next"), Modifier.size(28.dp)) }
        val station = np.station
        val host = np.hostPubkey
        val favKey = station?.id ?: host?.let { "npub:$it" }
        FavButton(favKey != null && lib.isFavorite(favKey)) {
            when {
                station != null -> c.library.toggleStation(station)
                host != null -> c.library.toggleStreamer(host, np.subtitle, np.artworkUrl)
            }
        }
    }
}

/** Refuerzo por emisora o stream (+3 a +12 dB), para las que suenan flojo. */
@Composable
private fun BoostChip(c: DesktopContainer, id: String) {
    var db by remember(id) { mutableStateOf(c.player.boostFor(id)) }
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(if (db == 0) t("boost") else t("boost_on", db)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp)) })
        DropdownMenu(open, { open = false }) {
            Text(t("boost_explain"), style = MaterialTheme.typography.bodySmall, color = Rs.colors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 260.dp))
            listOf(0, 3, 6, 9, 12).forEach { v ->
                DropdownMenuItem(text = { Text(if (v == 0) t("boost_off") else "+$v dB", fontWeight = if (v == db) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; db = v; c.player.setBoost(id, v) })
            }
        }
    }
}

/** Vídeo a pantalla completa en su propia ventana. Esc, doble clic o el botón salen. */
@Composable
private fun FullscreenVideo(c: DesktopContainer, onExit: () -> Unit) {
    val st by c.player.state.collectAsState()
    val state = androidx.compose.ui.window.rememberWindowState(placement = androidx.compose.ui.window.WindowPlacement.Fullscreen)
    androidx.compose.ui.window.Window(
        onCloseRequest = onExit, state = state, title = st.nowPlaying?.title ?: "", undecorated = true,
        onPreviewKeyEvent = { e ->
            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) when (e.key) {
                androidx.compose.ui.input.key.Key.Escape -> { onExit(); true }
                androidx.compose.ui.input.key.Key.Spacebar -> { c.player.togglePlay(); true }
                else -> false
            } else false
        },
    ) {
        var controls by remember { mutableStateOf(true) }
        LaunchedEffect(controls) { if (controls) { delay(3_000); controls = false } }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .androidx_pointer { controls = true }
                .combinedClickableCompat(onClick = { controls = !controls }, onDoubleClick = onExit),
        ) {
            VideoCanvas(c.player.video, Modifier.fillMaxSize())
            if (controls) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
                Text(st.nowPlaying?.title ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.TopStart).padding(20.dp))
                IconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Outlined.FullscreenExit, t("fullscreen_exit"), tint = Color.White) }
                FilledIconButton(onClick = c.player::togglePlay, modifier = Modifier.align(Alignment.Center).size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Color.Black)) {
                    Icon(if (st.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, t(if (st.isPlaying) "pause" else "play"), Modifier.size(36.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.androidx_pointer(onMove: () -> Unit) =
    this.then(Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) { onMove() })

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onDoubleClick: () -> Unit) =
    this.then(Modifier.combinedClickable(
        interactionSource = null, indication = null, onClick = onClick, onDoubleClick = onDoubleClick,
    ))

/** Pinta el último fotograma de VLC encajado (sin deformar) en el espacio disponible. */
@Composable
private fun VideoCanvas(frames: VideoFrames, modifier: Modifier) {
    val tick by frames.frame
    Canvas(modifier) {
        tick
        val bmp = frames.current() ?: return@Canvas
        val img = bmp.asComposeImageBitmap()
        val scale = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * scale).toInt(); val h = (img.height * scale).toInt()
        drawImage(
            img, dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w, h),
        )
    }
}

@Composable
private fun RadioQuality(c: DesktopContainer, station: Station, chosen: Int?, currentUrl: String) {
    var open by remember { mutableStateOf(false) }
    val current = station.variants.firstOrNull { it.url == currentUrl } ?: station.best
    val unknown = t("radio_format_unknown")
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t("quality_audio"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (station.variants.size == 1) {
            Text(t("radio_single_format", current.label ?: unknown), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        } else Box {
            AssistChip(onClick = { open = true }, label = { Text((if (chosen == null) t("quality_auto") + " · " else "") + (current.label ?: unknown) + " ▾") })
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem(text = { Text(t("quality_auto")) }, onClick = { open = false; c.player.selectVariant(null) })
                station.variants.forEachIndexed { i, v -> DropdownMenuItem(text = { Text(v.label ?: unknown) }, onClick = { open = false; c.player.selectVariant(i) }) }
            }
        }
    }
}

@Composable
private fun SimilarStations(c: DesktopContainer, current: Station) {
    val lib by c.library.library.collectAsState()
    var similar by remember(current.id) { mutableStateOf<List<Station>>(emptyList()) }
    LaunchedEffect(current.id) {
        similar = runCatching {
            current.tags.take(2).flatMap { c.radio.byTag(it) }
                .filter { it.id != current.id && it.ok && it.name != current.name }
                .distinctBy { it.id }.shuffled().take(5)
        }.getOrDefault(emptyList())
    }
    if (similar.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Rs.colors.line)
        Text(t("similar_stations"), color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        similar.forEach { s ->
            StationRow(c, s, lib.isFavorite(s.id), false, onClick = { c.player.playStation(s, from = listOf(current) + similar) }, onToggleFav = { c.library.toggleStation(s) })
        }
    }
}
