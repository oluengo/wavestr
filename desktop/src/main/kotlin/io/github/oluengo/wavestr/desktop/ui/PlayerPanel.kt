package io.github.oluengo.wavestr.desktop.ui

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

/** Reproductor: a pantalla completa (estrecho) o fijo a la derecha ([onClose] null). */
@Composable
fun PlayerPanel(c: DesktopContainer, onClose: (() -> Unit)?) {
    val st by c.player.state.collectAsState()
    val lib by c.library.library.collectAsState()
    val np = st.nowPlaying

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.ExpandMore, t("minimize")) } else Box(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
            if (np?.isLive == true) LiveBadge()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.player.stop(); onClose?.invoke() }, enabled = np != null) { Icon(Icons.Outlined.Close, t("stop")) }
        }
        if (np == null) {
            Text(t("nothing_playing"), color = Rs.colors.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        val showVideo = np.isLive && !st.audioOnly
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showVideo) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                    VideoCanvas(c.player.video, Modifier.fillMaxSize())
                }
            } else {
                val station = np.station
                if (station != null) StationArtwork(c, station, Modifier.widthIn(max = 260.dp).fillMaxWidth(0.7f).aspectRatio(1f), corner = 20.dp)
                else Artwork(np.artworkUrl, np.id, np.title, Modifier.widthIn(max = 260.dp).fillMaxWidth(0.7f).aspectRatio(1f), corner = 20.dp)
            }
            if (st.isBuffering && st.available) CircularProgressIndicator()
        }
        val margin = io.github.oluengo.wavestr.player.BufferPolicy.marginMs()
        if (st.isBuffering && margin >= 5_000) Text(
            t("buffer_loading", (margin * st.bufferPercent / 100_000).toInt(), (margin / 1000).toInt()),
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )

        if (np.isLive) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            SegmentedButton(st.audioOnly, { c.player.setAudioOnly(true) }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) }) { Text(t("player_audio")) }
            SegmentedButton(!st.audioOnly, { c.player.setAudioOnly(false) }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) }) { Text(t("player_video")) }
        }
        np.station?.let { RadioQuality(c, it, np.variantIndex, np.url) }

        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val station = np.station
            val host = np.hostPubkey
            val favKey = station?.id ?: host?.let { "npub:$it" }
            FavButton(favKey != null && lib.isFavorite(favKey)) {
                when {
                    station != null -> c.library.toggleStation(station)
                    host != null -> c.library.toggleStreamer(host, np.subtitle, np.artworkUrl)
                }
            }
            val canSkip = c.player.canSkip()
            IconButton(onClick = { c.player.skip(-1) }, enabled = canSkip) { Icon(Icons.Outlined.SkipPrevious, t("skip_previous"), Modifier.size(28.dp)) }
            FilledIconButton(
                onClick = c.player::togglePlay, modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(if (st.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, t(if (st.isPlaying) "pause" else "play"), Modifier.size(28.dp))
            }
            IconButton(onClick = { c.player.skip(+1) }, enabled = canSkip) { Icon(Icons.Outlined.SkipNext, t("skip_next"), Modifier.size(28.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = Rs.colors.muted, modifier = Modifier.size(18.dp))
            Slider(st.volume.toFloat(), { c.player.setVolume(it.toInt()) }, valueRange = 0f..150f, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            SleepTimerChip(c.player.sleep, isNostr = np.hostPubkey != null)
        }

        if (np.isLive && np.hostPubkey != null) LiveChat(c, np.id)
        np.station?.let { SimilarStations(c, it) }
    }
}

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

private data class ChatLine(val time: Long, val author: String, val text: String, val sats: Long?)

/** Chat y zaps del directo (lectura), cada 15 s. */
@Composable
private fun LiveChat(c: DesktopContainer, address: String) {
    var lines by remember(address) { mutableStateOf<List<ChatLine>>(emptyList()) }
    var total by remember(address) { mutableStateOf(0L) }
    var loaded by remember(address) { mutableStateOf(false) }
    val names = remember(address) { HashMap<String, Profile>() }
    val fmt = remember { NumberFormat.getIntegerInstance(I18n.locale) }
    LaunchedEffect(address) {
        val since = System.currentTimeMillis() / 1000 - 12 * 3600
        while (isActive) {
            try {
                val viewer = c.settings.current.pubkey
                val (chat, zaps) = c.nostr.activity(address, since, viewer)
                val who = (chat.map { it.pubkey } + zaps.mapNotNull { it.senderPubkey }).distinct().filter { it !in names }
                if (who.isNotEmpty()) names.putAll(c.nostr.profiles(who, viewer))
                fun name(pk: String?) = pk?.let { names[it]?.bestName ?: Nip19.shortNpub(it) } ?: "?"
                lines = (chat.map { ChatLine(it.createdAt, name(it.pubkey), it.content, null) } +
                    zaps.map { ChatLine(it.createdAt, name(it.senderPubkey), it.comment, it.amountSats) }).sortedBy { it.time }.takeLast(60)
                total = zaps.totalSats()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            loaded = true
            delay(15_000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = Rs.colors.line)
        Text(t("live_chat_title") + if (total > 0) " · ⚡ " + fmt.format(total) + " sats" else "", color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall)
        if (loaded && lines.isEmpty()) Text(t("live_chat_empty"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        lines.forEach { l ->
            val zap = l.sats != null
            Row(
                if (zap) Modifier.fillMaxWidth().background(Rs.colors.zapSoft, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(l.author, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                if (zap) Text("⚡ " + fmt.format(l.sats), color = Rs.colors.zap, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                if (l.text.isNotBlank()) Text(l.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Text(t("desk_zaps_soon"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
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
