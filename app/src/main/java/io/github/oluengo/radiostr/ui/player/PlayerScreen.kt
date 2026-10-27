package io.github.oluengo.radiostr.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.player.Hls
import io.github.oluengo.radiostr.player.PlayerUiState
import io.github.oluengo.radiostr.ui.common.Artwork
import io.github.oluengo.radiostr.ui.common.LiveBadge
import io.github.oluengo.radiostr.ui.theme.Rs

@Composable
fun PlayerScreen(c: AppContainer, onClose: () -> Unit, onOpenProfile: (String) -> Unit) {
    val st by c.player.state.collectAsStateWithLifecycle()
    val lib by c.library.library.collectAsStateWithLifecycle()
    val np = st.nowPlaying
    val activity: LiveActivityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.viewmodel.viewModelFactory { androidx.lifecycle.viewmodel.initializer { LiveActivityViewModel(c) } }
    )
    val liveAddress = np?.takeIf { it.isLive && it.hostPubkey != null }?.id
    androidx.compose.runtime.LaunchedEffect(liveAddress) { activity.watch(liveAddress) }
    var zapOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.ExpandMore, stringResource(R.string.minimize)) }
            Spacer(Modifier.weight(1f))
            if (np?.isLive == true) LiveBadge()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { c.player.stop(); onClose() }, enabled = np != null) {
                Icon(Icons.Outlined.Close, stringResource(R.string.stop))
            }
        }

        if (np == null) {
            Text(stringResource(R.string.nothing_playing), color = Rs.colors.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }

        // Escenario: vídeo o carátula
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            if (!st.audioOnly) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .background(Color.Black, RoundedCornerShape(18.dp)),
                ) { VideoSurface(c) }
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val station = np.station
                    if (station != null) io.github.oluengo.radiostr.ui.common.StationArtwork(c, station, Modifier.fillMaxWidth(0.58f).aspectRatio(1f), corner = 22.dp)
                    else Artwork(np.artworkUrl, np.id, Modifier.fillMaxWidth(0.58f).aspectRatio(1f), corner = 22.dp)
                }
            }
            if (st.isBuffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // Selector audio / vídeo (la radio es solo audio)
        if (!np.isRadio) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            SegmentedButton(
                selected = st.audioOnly,
                onClick = { c.player.setWantsVideo(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) },
            ) { Text(stringResource(R.string.player_audio)) }
            SegmentedButton(
                selected = !st.audioOnly,
                onClick = { c.player.setWantsVideo(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = np.hasVideo,
                icon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) },
            ) { Text(stringResource(R.string.player_video)) }
        }

        if (np.isRadio) Box(Modifier.size(16.dp))
        Text(hintFor(st), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))

        if (!st.audioOnly && st.videoHeights.size > 1) QualityChip(c, st)
        np.station?.let { RadioQuality(c, it, np.variantIndex, np.url) }

        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(np.title, style = MaterialTheme.typography.titleLarge)
            st.icyTitle?.let { Text("♪ $it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
            val host = np.hostPubkey
            Text(
                if (host != null) "${np.subtitle} · " + stringResource(R.string.view_profile) else np.subtitle,
                color = if (host != null) MaterialTheme.colorScheme.primary else Rs.colors.muted,
                fontWeight = if (host != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = if (host != null) Modifier.clickable { onOpenProfile(host) } else Modifier,
            )
        }

        if (st.error) {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(if (np.isRadio) R.string.radio_play_error else R.string.player_error), color = Rs.colors.live)
                val station = np.station
                if (station != null && station.variants.size > 1) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        val cur = station.variants.indexOfFirst { it.url == np.url }
                        c.player.selectVariant((cur + 1) % station.variants.size)
                    }) { Text(stringResource(R.string.radio_try_other)) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val station = np.station
            val host = np.hostPubkey
            val favKey = station?.id ?: host?.let { "npub:$it" }
            val isFav = favKey != null && lib.isFavorite(favKey)
            if (np.isLive && host != null) {
                IconButton(
                    onClick = { zapOpen = true }, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Rs.colors.zapSoft, contentColor = Rs.colors.zap),
                ) { Icon(Icons.Filled.Bolt, stringResource(R.string.zap_button)) }
            } else Box(Modifier.size(48.dp))
            FilledIconButton(
                onClick = c.player::togglePlay,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Icon(
                    if (st.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (st.isPlaying) R.string.pause else R.string.play),
                    Modifier.size(30.dp),
                )
            }
            IconButton(onClick = {
                when {
                    station != null -> c.library.toggleStation(station)
                    host != null -> c.library.toggleStreamer(host, np.subtitle, np.artworkUrl)
                }
            }, enabled = favKey != null, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                    tint = if (isFav) Rs.colors.live else Rs.colors.muted,
                )
            }
        }

        if (liveAddress != null) LiveChat(activity)
    }

    val host = np?.hostPubkey
    if (zapOpen && np != null && host != null) {
        io.github.oluengo.radiostr.ui.zap.ZapSheet(
            c,
            io.github.oluengo.radiostr.ui.zap.ZapViewModel.Target(host, np.subtitle, liveAddress, np.title),
            onDismiss = { zapOpen = false },
            onZapped = { activity.refreshNow() },
        )
    }
}

/** Calidad de una emisora: las variantes que emite (cada una es una URL) o «único formato». */
@Composable
private fun RadioQuality(c: AppContainer, station: io.github.oluengo.radiostr.radio.Station, chosen: Int?, currentUrl: String) {
    var open by remember { mutableStateOf(false) }
    val current = station.variants.firstOrNull { it.url == currentUrl } ?: station.best
    val unknown = stringResource(R.string.radio_format_unknown)
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.quality_audio), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (station.variants.size == 1) {
            Text(stringResource(R.string.radio_single_format, current.label ?: unknown), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        } else Box {
            AssistChip(onClick = { open = true }, label = {
                Text((if (chosen == null) stringResource(R.string.quality_auto) + " · " else "") + (current.label ?: unknown) + " ▾")
            })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.radio_quality_auto)) }, onClick = { open = false; c.player.selectVariant(null) })
                station.variants.forEachIndexed { i, v ->
                    val mbH = if (v.bitrate > 0) " · ≈ " + (v.bitrate * 0.45).toInt() + " MB/h" else ""
                    DropdownMenuItem(text = { Text((v.label ?: unknown) + mbH) }, onClick = { open = false; c.player.selectVariant(i) })
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(c: AppContainer) {
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = false; player = c.player.controller } },
        update = { it.player = c.player.controller },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun hintFor(st: PlayerUiState): String = stringResource(
    when {
        st.nowPlaying?.isRadio == true -> if (st.nowPlaying?.url?.startsWith("http://", true) == true) R.string.hint_radio_http else R.string.hint_radio
        st.forcedByCellular && st.audioOnly -> R.string.hint_cellular
        !st.audioOnly -> R.string.hint_video
        st.audioLayout == Hls.AudioLayout.SEPARATE -> R.string.hint_audio_separate
        st.audioLayout == Hls.AudioLayout.MUXED -> R.string.hint_audio_muxed
        else -> R.string.hint_audio_unknown
    }
)

@Composable
private fun QualityChip(c: AppContainer, st: PlayerUiState) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.quality_video), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box {
            val label = when (val h = st.selectedHeight) {
                null -> stringResource(R.string.quality_auto) + (st.currentHeight?.let { " · ${it}p" } ?: "")
                else -> "${h}p"
            }
            AssistChip(onClick = { open = true }, label = { Text("$label ▾") })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.quality_auto_desc)) },
                    onClick = { c.player.selectHeight(null); open = false },
                )
                st.videoHeights.forEach { h ->
                    DropdownMenuItem(text = { Text("${h}p") }, onClick = { c.player.selectHeight(h); open = false })
                }
            }
        }
    }
}
