package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.SurpriseKind
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Pick {
    val title: String
    data class Radio(val station: Station) : Pick { override val title get() = station.name }
    data class Live(val stream: LiveStream) : Pick { override val title get() = stream.title.ifBlank { t("untitled_stream") } }
}

/** Igual que en Android: radio, directo o ambos; de tus favoritos o de todo. */
private suspend fun pick(c: DesktopContainer, kind: SurpriseKind, fromFavorites: Boolean): Pair<List<Pick>, Pick>? {
    val viewer = c.settings.current.pubkey
    val lib = c.library.library.value
    val r: List<Pick> = if (kind == SurpriseKind.LIVE) emptyList() else
        (if (fromFavorites) lib.favorites.mapNotNull { it.station } else runCatching { c.radio.random(40) }.getOrDefault(emptyList()))
            .filter { it.ok }.map { Pick.Radio(it) }
    val l: List<Pick> = if (kind == SurpriseKind.RADIO) emptyList() else runCatching {
        if (fromFavorites) c.nostr.liveOf(lib.favorites.filter { it.kind == FavItem.Kind.STREAMER }.mapNotNull { it.hostPubkey }.toSet(), viewer)
        else c.nostr.liveGlobal(viewer)
    }.getOrDefault(emptyList()).filter { it.streamingUrl != null }.map { Pick.Live(it) }
    val group = listOf(r, l).filter { it.isNotEmpty() }.randomOrNull() ?: return null
    return (r + l) to group.random()
}

@Composable
fun SurpriseButton(c: DesktopContainer) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Casino, t("surprise")) }
    if (open) SurpriseDialog(c) { open = false }
}

@Composable
private fun SurpriseDialog(c: DesktopContainer, onDismiss: () -> Unit) {
    val s by c.settings.flow.collectAsState()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("surprise")) },
        text = {
            Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val kinds = listOf(SurpriseKind.RADIO to "surprise_radio", SurpriseKind.LIVE to "surprise_live", SurpriseKind.BOTH to "surprise_both")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, (k, label) ->
                        SegmentedButton(s.surpriseKind == k, { empty = false; c.settings.setSurprise(k, s.surpriseFromFavorites) }, SegmentedButtonDefaults.itemShape(i, kinds.size)) { Text(t(label)) }
                    }
                }
                val froms = listOf(true to "surprise_from_favs", false to "surprise_from_all")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    froms.forEachIndexed { i, (fav, label) ->
                        SegmentedButton(s.surpriseFromFavorites == fav, { empty = false; c.settings.setSurprise(s.surpriseKind, fav) }, SegmentedButtonDefaults.itemShape(i, froms.size)) { Text(t(label)) }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                    when {
                        spinning != null -> AnimatedContent(spinning, label = "spin") { name ->
                            Text(name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        loading -> CircularProgressIndicator()
                        empty -> Text(t(if (s.surpriseFromFavorites) "surprise_empty_favs" else "surprise_empty_all"), color = Rs.colors.muted, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    loading = true; empty = false
                    scope.launch {
                        try {
                            val result = pick(c, s.surpriseKind, s.surpriseFromFavorites)
                            loading = false
                            if (result == null) { empty = true; return@launch }
                            val (pool, chosen) = result
                            repeat(9) { i -> spinning = pool.random().title; delay(70L + i * 22) }
                            spinning = chosen.title
                            delay(550)
                            when (chosen) {
                                is Pick.Radio -> c.player.playStation(chosen.station)
                                is Pick.Live -> {
                                    val host = runCatching { c.nostr.profiles(listOf(chosen.stream.hostPubkey), c.settings.current.pubkey)[chosen.stream.hostPubkey] }.getOrNull()
                                    c.player.play(liveNowPlaying(chosen.stream, host))
                                }
                            }
                            onDismiss()
                            nav.openPlayer()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            loading = false; spinning = null; empty = true
                        }
                    }
                },
                enabled = !loading && spinning == null,
            ) { Text(t("surprise_go")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("close")) } },
    )
}
