package io.github.oluengo.wavestr.ui.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.Shared
import io.github.oluengo.wavestr.nostr.EventSigning
import io.github.oluengo.wavestr.nostr.Kinds
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.nostr.canSign
import io.github.oluengo.wavestr.nostr.noteTags
import io.github.oluengo.wavestr.ui.theme.Rs
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Qué se comparte, en una línea: «Rock · 12 emisoras y 2 streamers». */
@Composable
fun sharedSummary(sh: Shared): String {
    val parts = listOfNotNull(
        sh.stations.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.share_stations, it, it) },
        sh.streamers.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.share_streamers, it, it) },
    )
    return parts.joinToString(stringResource(R.string.share_and))
}

/** Elegir cómo compartir: en Nostr (si hay cuenta), enlace, lista M3U o texto. */
@Composable
fun ShareDialog(c: AppContainer, shared: Shared, onDismiss: () -> Unit, liveAddress: String? = null) {
    val ctx = LocalContext.current
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    var composing by remember { mutableStateOf(false) }
    if (composing) { NostrPostDialog(c, shared, liveAddress, onDismiss); return }
    if (shared.items.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.share_title, shared.name)) },
            text = { Text(stringResource(R.string.share_empty)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title, shared.name)) },
        text = {
            Column {
                Text(sharedSummary(shared), color = Rs.colors.muted, modifier = Modifier.padding(bottom = 8.dp))
                // Con cuenta de Nostr: escribir la nota directamente, sin copiar y pegar.
                settings?.takeIf { it.pubkey != null }?.let { s ->
                    val canSign = s.loginMethod.canSign
                    ShareOption(
                        Icons.Outlined.Edit, R.string.share_nostr,
                        stringResource(if (canSign) R.string.share_nostr_sub else R.string.share_nostr_needs_signer),
                        enabled = canSign,
                    ) { composing = true }
                }
                ShareOption(Icons.Outlined.Link, R.string.share_link, stringResource(R.string.share_link_sub)) {
                    sendText(ctx, shared.name, ctx.getString(R.string.share_link_text, shared.name, shared.link())); onDismiss()
                }
                ShareOption(
                    Icons.AutoMirrored.Outlined.QueueMusic, R.string.share_m3u,
                    stringResource(if (shared.stations == 0) R.string.share_m3u_none else R.string.share_m3u_sub),
                    enabled = shared.stations > 0,
                ) { sendM3u(ctx, shared); onDismiss() }
                // Texto: para un stream o una emisora sueltos, el mismo texto que en Nostr (WAV-001/002),
                // con el streamer como nombre y enlace a njump; para listas, la lista de siempre.
                val streamIntro = stringResource(R.string.share_stream_intro)
                val radioText = shared.single?.takeIf { it.station != null }?.let { stringResource(R.string.share_radio_text, it.title, shared.link()) }
                ShareOption(Icons.AutoMirrored.Outlined.Notes, R.string.share_text, stringResource(R.string.share_text_sub)) {
                    val text = shared.streamText(streamIntro, liveAddress, nostr = false) ?: radioText ?: shared.toText()
                    sendText(ctx, shared.name, text); onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ShareOption(icon: ImageVector, title: Int, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.primary else Rs.colors.muted
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Rs.colors.muted)
            Text(sub, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Nota de Nostr (kind 1) ya escrita en el idioma de la app, con el enlace y, si es un streamer, su
 * mención; se puede editar antes de publicar. Se firma con Amber o bunker y se publica en tus relays.
 */
@Composable
private fun NostrPostDialog(c: AppContainer, shared: Shared, liveAddress: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    // Textos de WAV-001 (stream) y WAV-002 (radio); los hashtags son los mismos en todos los idiomas.
    val single = shared.items.singleOrNull()?.takeIf { shared.kind == Shared.Kind.ITEM }
    val host = single?.hostPubkey
    val initial = when {
        // Stream: enlace a su página web (y de ahí al evento de Nostr) o, si no hay evento, al enlace
        // de compartir; dos saltos de línea y la mención (npub), sin repetir el nombre.
        host != null -> shared.streamText(stringResource(R.string.share_stream_intro), liveAddress, nostr = true).orEmpty()
        single?.station != null -> stringResource(R.string.share_radio_text, single?.title.orEmpty(), shared.link())
        single != null -> stringResource(R.string.share_nostr_text, single.title, shared.link()) + "\n\n#wavestr"
        else -> stringResource(R.string.share_nostr_text_list, shared.name, shared.link()) + "\n\n#wavestr"
    }
    var text by remember { mutableStateOf(initial) }
    var sending by remember { mutableStateOf(false) }
    val done = stringResource(R.string.share_nostr_done)
    val failed = stringResource(R.string.share_nostr_failed)
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.share_nostr)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(2000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                enabled = !sending,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !sending, onClick = {
                val me = settings?.pubkey ?: return@TextButton
                val content = text.trim()
                sending = true
                scope.launch {
                    val signed = c.signer.sign(EventSigning.unsigned(me, Kinds.TEXT_NOTE, noteTags(content), content))
                    if (signed == null) { sending = false; return@launch }
                    val relays = runCatching { c.nostr.ownRelays(me) }.getOrNull().orEmpty().ifEmpty { RelayPool.DEFAULT }
                    val ok = runCatching { c.relays.publish(relays, signed) > 0 }.getOrDefault(false)
                    sending = false
                    Toast.makeText(ctx, if (ok) done else failed, Toast.LENGTH_LONG).show()
                    if (ok) onDismiss()
                }
            }) { Text(stringResource(if (sending) R.string.share_nostr_sending else R.string.share_nostr_post)) }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun sendText(ctx: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, null))
}

private fun sendM3u(ctx: Context, shared: Shared) {
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val safe = shared.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "wavestr" }.take(40)
        val file = File(dir, "$safe.m3u").apply { writeText(shared.toM3u()) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("audio/x-mpegurl")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, shared.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { Toast.makeText(ctx, R.string.backup_write_error, Toast.LENGTH_LONG).show() }
}

/**
 * Vista previa de lo recibido por enlace: se marca qué añadir (lo que ya es favorito sale atenuado) y se
 * importa a Favoritos. Una carpeta llega como carpeta; «todos los favoritos», dentro de una carpeta nueva.
 */
@Composable
fun IncomingShareHost(c: AppContainer) {
    val shared by c.incomingShare.collectAsStateWithLifecycle()
    val sh = shared ?: return
    val ctx = LocalContext.current
    val lib by c.library.library.collectAsStateWithLifecycle()
    val already = remember(sh) { sh.items.filter { lib.isFavorite(it.key) }.map { it.key }.toSet() }
    var selected by remember(sh) { mutableStateOf(sh.items.map { it.key }.toSet() - already) }
    val src = remember(sh) { sh.asLibrary() }
    val wrapper = stringResource(R.string.share_wrapper, sh.name.ifBlank { stringResource(R.string.tab_favorites) })
    // Elementos en orden: sueltos y luego por carpeta; la ruta de la carpeta, como cabecera de su primer elemento.
    val rows: List<Pair<String?, FavItem>> = remember(sh) {
        val out = ArrayList<Pair<String?, FavItem>>()
        src.itemsIn(null).forEach { out += null to it }
        fun walk(parent: String?) {
            src.subfolders(parent).forEach { f ->
                val path = src.path(f.id).joinToString(" / ") { p -> p.name }
                src.itemsIn(f.id).forEachIndexed { i, it -> out += (if (i == 0) path else null) to it }
                walk(f.id)
            }
        }
        walk(null)
        out
    }
    fun close() { c.incomingShare.value = null }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.share_received, sh.name.ifBlank { stringResource(R.string.tab_favorites) })) },
        text = {
            Column {
                Text(sharedSummary(sh), color = Rs.colors.muted)
                if (sh.kind == Shared.Kind.ALL) Text(stringResource(R.string.share_into_wrapper, wrapper), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
                if (rows.size > 1) Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { selected = sh.items.map { it.key }.toSet() - already }) { Text(stringResource(R.string.share_select_all)) }
                    TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.share_select_none)) }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(rows, key = { it.second.key }) { (header, item) ->
                        ShareItemRow(c, header, item, item.key in already, item.key in selected) {
                            selected = if (item.key in selected) selected - item.key else selected + item.key
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                val n = selected.size
                c.library.update { sh.importInto(it, selected, wrapper) { "f" + UUID.randomUUID().toString().take(8) } }
                Toast.makeText(ctx, ctx.resources.getQuantityString(R.plurals.share_added, n, n), Toast.LENGTH_LONG).show()
                close()
            }) { Text(stringResource(R.string.share_add, selected.size)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ShareItemRow(c: AppContainer, header: String?, item: FavItem, isFav: Boolean, checked: Boolean, onToggle: () -> Unit) {
    Column {
        if (header != null) {
            Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(header, style = MaterialTheme.typography.labelMedium, color = Rs.colors.muted, modifier = Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !isFav, onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(checked = checked || isFav, onCheckedChange = { onToggle() }, enabled = !isFav)
            val st = item.station
            if (st != null) StationArtwork(c, st, Modifier.size(36.dp), corner = 8.dp)
            else Box(Modifier.size(36.dp)) { Artwork(item.image, item.key, Modifier.size(36.dp), corner = 18.dp) }
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isFav) Rs.colors.muted else Color.Unspecified)
                Text(
                    stringResource(when {
                        isFav -> R.string.share_already
                        item.kind == FavItem.Kind.STREAMER -> R.string.fav_kind_streamer
                        else -> R.string.fav_kind_station
                    }),
                    color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
