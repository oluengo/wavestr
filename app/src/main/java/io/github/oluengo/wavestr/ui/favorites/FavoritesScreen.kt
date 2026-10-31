package io.github.oluengo.wavestr.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.Folder
import io.github.oluengo.wavestr.data.Library
import io.github.oluengo.wavestr.ui.common.Artwork
import io.github.oluengo.wavestr.ui.common.EmptyNote
import io.github.oluengo.wavestr.ui.common.MeButton
import io.github.oluengo.wavestr.ui.common.ScreenHeader
import io.github.oluengo.wavestr.ui.common.SectionLabel
import io.github.oluengo.wavestr.ui.common.initials
import io.github.oluengo.wavestr.ui.theme.Rs

private sealed interface Dialog {
    data object NewFolder : Dialog
    data class FolderOptions(val folder: Folder) : Dialog
    data class ConfirmDelete(val folder: Folder) : Dialog
    data class ItemOptions(val item: FavItem) : Dialog
    data class Share(val shared: io.github.oluengo.wavestr.data.Shared) : Dialog
}

@Composable
fun FavoritesScreen(c: AppContainer, onOpenPlayer: () -> Unit, onOpenProfile: (String) -> Unit, onOpenMe: () -> Unit) {
    val lib by c.library.library.collectAsStateWithLifecycle()
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    if (current != null && lib.folder(current) == null) current = null
    var dialog by androidx.compose.runtime.remember { mutableStateOf<Dialog?>(null) }
    val subs = lib.subfolders(current)
    val items = lib.itemsIn(current)
    // Estado de los streamers favoritos (emitiendo o no), cada minuto mientras la pantalla está abierta.
    val live by c.liveStatus.live.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.untitled_stream)
    val hosts = lib.favorites.mapNotNull { it.hostPubkey }.toSet()
    androidx.compose.runtime.LaunchedEffect(hosts) {
        while (true) {
            c.liveStatus.refresh(hosts, c.settings.current().pubkey)
            kotlinx.coroutines.delay(60_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.tab_favorites), stringResource(R.string.fav_sub)) {
            IconButton(onClick = { dialog = Dialog.NewFolder }) { Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.fav_new_folder)) }
            // Compartir lo que se ve: todos los favoritos en la raíz, o la carpeta abierta.
            val allName = stringResource(R.string.tab_favorites)
            IconButton(
                onClick = {
                    val sh = current?.let { io.github.oluengo.wavestr.data.Shared.ofFolder(lib, it) } ?: io.github.oluengo.wavestr.data.Shared.ofAll(lib, allName)
                    dialog = Dialog.Share(sh)
                },
                enabled = lib.favorites.isNotEmpty(),
            ) { Icon(Icons.Outlined.Share, stringResource(if (current == null) R.string.share_all else R.string.share_folder)) }
            io.github.oluengo.wavestr.ui.common.SurpriseButton(c)
            MeButton(c, onOpenMe)
        }
        // Migas de pan
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { current = null }) { Text(stringResource(R.string.tab_favorites), fontWeight = if (current == null) FontWeight.Bold else FontWeight.Normal) }
            lib.path(current).forEach { f ->
                Text("›", color = Rs.colors.muted)
                TextButton(onClick = { current = f.id }) { Text(f.name, fontWeight = if (current == f.id) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        SyncBanner(c, Modifier.padding(horizontal = 20.dp))

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (subs.isNotEmpty()) {
                item { SectionLabel(stringResource(if (current == null) R.string.fav_folders else R.string.fav_subfolders)) }
                items(subs, key = { "f:" + it.id }) { f ->
                    FolderRow(f, lib, onOpen = { current = f.id }, onMore = { dialog = Dialog.FolderOptions(f) })
                    HorizontalDivider(color = Rs.colors.line)
                }
            }
            item { SectionLabel(stringResource(if (current == null) R.string.fav_no_folder else R.string.fav_in_folder) + " · ${items.size}") }
            if (items.isEmpty()) item {
                EmptyNote(stringResource(if (lib.favorites.isEmpty()) R.string.fav_empty_all else R.string.fav_empty_folder))
            }
            items(items, key = { it.key }) { item ->
                ItemRow(c, item, live = item.hostPubkey?.let { h -> live?.let { h in it } }, onClick = {
                    when (item.kind) {
                        FavItem.Kind.STATION -> item.station?.let { c.player.playStation(it); onOpenPlayer() }
                        // Si está emitiendo, a reproducir directamente; si no, su perfil (con las grabaciones).
                        FavItem.Kind.STREAMER -> item.hostPubkey?.let { h ->
                            val np = live?.get(h)?.let { liveNowPlaying(it, item, untitled) }
                            if (np == null) onOpenProfile(h)
                            else {
                                // Anterior / siguiente: entre los favoritos que están emitiendo.
                                c.player.setStreamContext(lib.favorites.mapNotNull { f -> f.hostPubkey?.let { live?.get(it) }?.let { liveNowPlaying(it, f, untitled) } })
                                c.player.play(np); onOpenPlayer()
                            }
                        }
                    }
                }, onMore = { dialog = Dialog.ItemOptions(item) })
                HorizontalDivider(color = Rs.colors.line)
            }
            item { EmptyNote(stringResource(R.string.fav_sync_note)) }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.NewFolder -> NameDialog(
            title = stringResource(R.string.fav_new_folder),
            subtitle = stringResource(R.string.fav_inside, lib.path(current).lastOrNull()?.name ?: stringResource(R.string.tab_favorites)),
            initial = "",
            onDismiss = { dialog = null },
            onSave = { c.library.newFolder(it, current); dialog = null },
        )
        is Dialog.FolderOptions -> FolderOptionsDialog(
            d.folder,
            onRename = { name -> c.library.update { it.renameFolder(d.folder.id, name) }; dialog = null },
            onDelete = { dialog = Dialog.ConfirmDelete(d.folder) },
            onShare = { io.github.oluengo.wavestr.data.Shared.ofFolder(lib, d.folder.id)?.let { dialog = Dialog.Share(it) } },
            onDismiss = { dialog = null },
        )
        is Dialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.fav_delete_title, d.folder.name)) },
            text = { Text(stringResource(R.string.fav_delete_body, lib.folder(d.folder.parentId)?.name ?: stringResource(R.string.tab_favorites))) },
            confirmButton = {
                TextButton(onClick = { c.library.update { it.deleteFolder(d.folder.id) }; dialog = null }) {
                    Text(stringResource(R.string.delete), color = Rs.colors.live)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is Dialog.ItemOptions -> MoveDialog(
            lib, d.item,
            onMove = { folderId -> c.library.update { it.move(d.item.key, folderId) }; dialog = null },
            onRemove = { c.library.update { it.remove(d.item.key) }; dialog = null },
            onShare = { dialog = Dialog.Share(io.github.oluengo.wavestr.data.Shared.ofItem(d.item)) },
            onProfile = d.item.hostPubkey?.let { h -> { dialog = null; onOpenProfile(h) } },
            onDismiss = { dialog = null },
        )
        is Dialog.Share -> io.github.oluengo.wavestr.ui.common.ShareDialog(d.shared, onDismiss = { dialog = null })
    }
}

@Composable
private fun FolderRow(f: Folder, lib: Library, onOpen: () -> Unit, onMore: () -> Unit) {
    val (n, k) = lib.countIn(f.id)
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(52.dp).background(Rs.colors.accentSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = listOfNotNull(
                n.takeIf { it > 0 }?.let { androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.fav_count, it, it) },
                k.takeIf { it > 0 }?.let { androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.subfolder_count, it, it) },
            )
            Text(parts.joinToString(" · ").ifEmpty { stringResource(R.string.fav_empty_short) }, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.fav_folder_options, f.name), tint = Rs.colors.muted) }
    }
}

@Composable
/** [live]: solo streamers; true = emitiendo, false = sin emitir, null = aún no se sabe. */
private fun ItemRow(c: AppContainer, item: FavItem, live: Boolean?, onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val station = item.station
        if (station != null) io.github.oluengo.wavestr.ui.common.StationArtwork(c, station, Modifier.size(52.dp))
        else Box(Modifier.size(52.dp)) {
            Artwork(item.image, item.key, Modifier.size(52.dp), corner = 26.dp)
            if (item.image == null) Text(initials(item.title), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.subtitle ?: stringResource(if (item.kind == FavItem.Kind.STREAMER) R.string.fav_kind_streamer else R.string.fav_kind_station),
                color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            item.kind != FavItem.Kind.STREAMER -> Text("RADIO", fontSize = 10.sp, color = Rs.colors.muted)
            live == true -> io.github.oluengo.wavestr.ui.common.LiveBadge()
            live == false -> Text(
                stringResource(R.string.fav_offline), fontSize = 11.sp, color = Rs.colors.muted,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
            else -> Text("NOSTR", fontSize = 10.sp, color = Rs.colors.muted)
        }
        IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.fav_item_options, item.title), tint = Rs.colors.muted) }
    }
}

@Composable
private fun NameDialog(title: String, subtitle: String?, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subtitle?.let { Text(it, color = Rs.colors.muted) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40); error = false },
                    placeholder = { Text(stringResource(R.string.fav_name_hint)) }, singleLine = true, isError = error,
                    supportingText = { if (error) Text(stringResource(R.string.fav_name_required)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isBlank()) error = true else onSave(name.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderOptionsDialog(f: Folder, onRename: (String) -> Unit, onDelete: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(f.id) { mutableStateOf(f.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.fav_folder_name)) })
                TextButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.share_folder), Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.fav_delete_folder), color = Rs.colors.live) }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(name.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveDialog(lib: Library, item: FavItem, onMove: (String?) -> Unit, onRemove: () -> Unit, onShare: () -> Unit, onProfile: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fav_move_to)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, color = Rs.colors.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Text(stringResource(R.string.share), Modifier.padding(start = 8.dp))
                    }
                }
                if (onProfile != null) TextButton(onClick = onProfile) { Text(stringResource(R.string.view_profile)) }
                DestRow(stringResource(R.string.fav_no_folder), 0, item.folderId == null) { onMove(null) }
                lib.tree().forEach { (f, depth) -> DestRow(f.name, depth + 1, item.folderId == f.id) { onMove(f.id) } }
                TextButton(onClick = onRemove, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.fav_remove), color = Rs.colors.live) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DestRow(label: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** El directo de un streamer favorito, listo para el reproductor. */
private fun liveNowPlaying(s: io.github.oluengo.wavestr.nostr.LiveStream, fav: FavItem, untitled: String): io.github.oluengo.wavestr.player.NowPlaying? {
    val url = s.streamingUrl ?: return null
    return io.github.oluengo.wavestr.player.NowPlaying(
        id = s.address, url = url, title = s.title.ifBlank { untitled }, subtitle = fav.title,
        artworkUrl = s.image ?: fav.image, hostPubkey = s.hostPubkey,
    )
}
