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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.oluengo.wavestr.ui.theme.Rs
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

/** Elegir cómo compartir: enlace, lista M3U o texto. */
@Composable
fun ShareDialog(shared: Shared, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
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
                ShareOption(Icons.Outlined.Link, R.string.share_link, stringResource(R.string.share_link_sub)) {
                    sendText(ctx, shared.name, ctx.getString(R.string.share_link_text, shared.name, shared.link())); onDismiss()
                }
                ShareOption(
                    Icons.AutoMirrored.Outlined.QueueMusic, R.string.share_m3u,
                    stringResource(if (shared.stations == 0) R.string.share_m3u_none else R.string.share_m3u_sub),
                    enabled = shared.stations > 0,
                ) { sendM3u(ctx, shared); onDismiss() }
                ShareOption(Icons.AutoMirrored.Outlined.Notes, R.string.share_text, stringResource(R.string.share_text_sub)) {
                    sendText(ctx, shared.name, shared.toText()); onDismiss()
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
