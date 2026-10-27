package io.github.oluengo.wavestr.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.AppSettings
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.SurpriseKind
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.player.NowPlaying
import io.github.oluengo.wavestr.radio.Station
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Abre el reproductor a pantalla completa (lo da la navegación raíz). */
val LocalOpenPlayer = staticCompositionLocalOf<() -> Unit> { {} }

/** Elige algo al azar: radio, directo o ambos; de tus favoritos o de todo. */
class SurprisePicker(private val c: AppContainer) {
    sealed interface Pick {
        val title: String
        data class Radio(val station: Station) : Pick { override val title get() = station.name }
        data class Live(val stream: LiveStream) : Pick { override val title get() = stream.title }
    }

    /** Todos los candidatos (para la animación) y el elegido; null si no hay ninguno. */
    suspend fun pick(kind: SurpriseKind, fromFavorites: Boolean): Pair<List<Pick>, Pick>? {
        val viewer = c.settings.current().pubkey
        val lib = c.library.library.value
        suspend fun radios(): List<Pick> =
            (if (fromFavorites) lib.favorites.mapNotNull { it.station } else runCatching { c.radio.random(40) }.getOrDefault(emptyList()))
                .filter { it.ok }.map { Pick.Radio(it) }
        suspend fun lives(): List<Pick> = runCatching {
            if (fromFavorites) c.nostr.liveOf(lib.favorites.filter { it.kind == FavItem.Kind.STREAMER }.mapNotNull { it.hostPubkey }.toSet(), viewer)
            else c.nostr.liveGlobal(viewer)
        }.getOrDefault(emptyList()).filter { it.streamingUrl != null }.map { Pick.Live(it) }

        val r = if (kind != SurpriseKind.LIVE) radios() else emptyList()
        val l = if (kind != SurpriseKind.RADIO) lives() else emptyList()
        // Con «ambos», primero se echa a suertes el tipo; si no, una sola radio favorita taparía los directos.
        val group = listOf(r, l).filter { it.isNotEmpty() }.randomOrNull() ?: return null
        return (r + l) to group.random()
    }

    suspend fun play(p: Pick, untitled: String) {
        when (p) {
            is Pick.Radio -> c.player.playStation(p.station)
            is Pick.Live -> {
                val s = p.stream
                val host = runCatching { c.nostr.profiles(listOf(s.hostPubkey), c.settings.current().pubkey)[s.hostPubkey] }.getOrNull()
                c.player.play(
                    NowPlaying(
                        id = s.address, url = s.streamingUrl!!, title = s.title.ifBlank { untitled },
                        subtitle = host?.bestName ?: Nip19.shortNpub(s.hostPubkey),
                        artworkUrl = s.image ?: host?.picture, hostPubkey = s.hostPubkey,
                    )
                )
            }
        }
    }
}

/** Botón del dado en la cabecera de Directos, Radios y Favoritos. */
@Composable
fun SurpriseButton(c: AppContainer) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Casino, stringResource(R.string.surprise)) }
    if (open) SurpriseSheet(c) { open = false }
}

@Composable
private fun SurpriseSheet(c: AppContainer, onDismiss: () -> Unit) {
    val s by c.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val openPlayer = LocalOpenPlayer.current
    val untitled = stringResource(R.string.untitled_stream)
    var spinning by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.surprise), style = MaterialTheme.typography.titleLarge)
            val kinds = listOf(SurpriseKind.RADIO to R.string.surprise_radio, SurpriseKind.LIVE to R.string.surprise_live, SurpriseKind.BOTH to R.string.surprise_both)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = s.surpriseKind == k, shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                        onClick = { empty = false; scope.launch { c.settings.setSurprise(k, s.surpriseFromFavorites) } },
                    ) { Text(stringResource(label)) }
                }
            }
            val froms = listOf(true to R.string.surprise_from_favs, false to R.string.surprise_from_all)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                froms.forEachIndexed { i, (fav, label) ->
                    SegmentedButton(
                        selected = s.surpriseFromFavorites == fav, shape = SegmentedButtonDefaults.itemShape(i, froms.size),
                        onClick = { empty = false; scope.launch { c.settings.setSurprise(s.surpriseKind, fav) } },
                    ) { Text(stringResource(label)) }
                }
            }

            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                when {
                    spinning != null -> AnimatedContent(spinning, label = "spin") { name ->
                        Text(
                            name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    loading -> CircularProgressIndicator()
                    empty -> Text(
                        stringResource(if (s.surpriseFromFavorites) R.string.surprise_empty_favs else R.string.surprise_empty_all),
                        color = Rs.colors.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = {
                    loading = true; empty = false
                    scope.launch {
                        try {
                            val result = c.surprise.pick(s.surpriseKind, s.surpriseFromFavorites)
                            loading = false
                            if (result == null) { empty = true; return@launch }
                            val (pool, chosen) = result
                            // Ruleta corta: unos nombres al azar y se para en el elegido.
                            repeat(9) { i -> spinning = pool.random().title.ifBlank { untitled }; delay(70L + i * 22) }
                            spinning = chosen.title.ifBlank { untitled }
                            delay(550)
                            c.surprise.play(chosen, untitled)
                            onDismiss()
                            openPlayer()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            loading = false; spinning = null; empty = true
                        }
                    }
                },
                enabled = !loading && spinning == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.surprise_go)) }
        }
    }
}
