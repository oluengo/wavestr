package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.Folder
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.t
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(c: DesktopContainer) {
    val lib by c.library.library.collectAsState()
    val ps by c.player.state.collectAsState()
    val s by c.settings.flow.collectAsState()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Folder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Folder?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    if (current != null && lib.folder(current) == null) current = null

    fun open(item: FavItem) {
        val st = item.station
        if (st != null) { c.player.playStation(st); nav.openPlayer(); return }
        val host = item.hostPubkey ?: return
        note = null
        scope.launch {
            val live = runCatching { c.nostr.liveOf(setOf(host), s.pubkey) }.getOrDefault(emptyList()).firstOrNull()
            if (live == null) { note = t("desk_streamer_offline", item.title); return@launch }
            val profile = runCatching { c.nostr.profiles(listOf(host), s.pubkey)[host] }.getOrNull()
            c.player.play(liveNowPlaying(live, profile)); nav.openPlayer()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(t("tab_favorites"), t("fav_sub")) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.CreateNewFolder, t("fav_new_folder")) }
            HeaderActions(c)
        }
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { current = null }) { Text(t("tab_favorites"), fontWeight = if (current == null) FontWeight.Bold else FontWeight.Normal) }
            lib.path(current).forEach { f ->
                Text("›", color = Rs.colors.muted)
                TextButton(onClick = { current = f.id }) { Text(f.name, fontWeight = if (current == f.id) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        note?.let { Text(it, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp)) }

        val folders = lib.subfolders(current)
        val items = lib.itemsIn(current)
        LazyVerticalGrid(
            GridCells.Adaptive(330.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (folders.isEmpty() && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyNote(t(if (current == null) "fav_empty_all" else "fav_empty_folder"))
            }
            items(folders, key = { "f:" + it.id }) { f ->
                FolderCard(f, lib.countIn(f.id).first, onOpen = { current = f.id }, onRename = { naming = f }, onDelete = { deleting = f })
            }
            items(items, key = { it.key }) { item ->
                FavCard(
                    c, item, selected = ps.nowPlaying?.id == (item.station?.id ?: "x"),
                    destinations = listOf<Folder?>(null) + lib.tree().map { it.first },
                    onClick = { open(item) },
                    onMove = { dest -> c.library.update { it.move(item.key, dest) } },
                    onRemove = { c.library.update { it.remove(item.key) } },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(t("desk_favs_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (creating) NameDialog(t("fav_new_folder"), "") { name -> creating = false; if (name != null) c.library.newFolder(name, current) }
    naming?.let { f -> NameDialog(t("fav_folder_name"), f.name) { name -> naming = null; if (name != null) c.library.update { it.renameFolder(f.id, name) } } }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(t("fav_delete_title", f.name)) },
            text = { Text(t("fav_delete_body", lib.folder(f.parentId)?.name ?: t("tab_favorites"))) },
            confirmButton = { TextButton(onClick = { c.library.update { it.deleteFolder(f.id) }; deleting = null }) { Text(t("delete"), color = Rs.colors.live) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("cancel")) } },
        )
    }
}

@Composable
private fun FolderCard(f: Folder, count: Int, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Rs.colors.line, RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Rs.colors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 0) t("fav_empty_short") else count.toString(), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, t("fav_folder_options", f.name)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(t("fav_folder_name")) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(t("fav_delete_folder"), color = Rs.colors.live) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun FavCard(
    c: DesktopContainer, item: FavItem, selected: Boolean, destinations: List<Folder?>,
    onClick: () -> Unit, onMove: (String?) -> Unit, onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Rs.colors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val st = item.station
        if (st != null) StationArtwork(c, st, Modifier.size(48.dp), corner = 10.dp)
        else Artwork(item.image, item.key, item.title, Modifier.size(48.dp), corner = 24.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.subtitle ?: t(if (st != null) "fav_kind_station" else "fav_kind_streamer"),
                color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, t("fav_item_options", item.title)) }
            DropdownMenu(menu, { menu = false }) {
                Text(t("fav_move_to"), color = Rs.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                destinations.forEach { d ->
                    DropdownMenuItem(
                        text = { Text(d?.name ?: t("fav_no_folder"), fontWeight = if (d?.id == item.folderId) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; onMove(d?.id) },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(t("fav_remove"), color = Rs.colors.live) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it; error = false }, singleLine = true, isError = error,
                placeholder = { Text(t("fav_name_hint")) },
                supportingText = { if (error) Text(t("fav_name_required")) },
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isBlank()) error = true else onDone(name.trim()) }) { Text(t("save")) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(t("cancel")) } },
    )
}
