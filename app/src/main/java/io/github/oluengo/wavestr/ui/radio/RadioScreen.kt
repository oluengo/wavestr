package io.github.oluengo.wavestr.ui.radio

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.data.Library
import io.github.oluengo.wavestr.ui.common.EmptyNote
import io.github.oluengo.wavestr.ui.common.MeButton
import io.github.oluengo.wavestr.ui.common.ScreenHeader
import io.github.oluengo.wavestr.ui.common.SectionLabel
import io.github.oluengo.wavestr.ui.common.StationRow
import io.github.oluengo.wavestr.ui.common.countryName
import io.github.oluengo.wavestr.ui.theme.Rs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioScreen(c: AppContainer, onOpenPlayer: () -> Unit, onOpenMe: () -> Unit) {
    val vm: RadioViewModel = viewModel(factory = viewModelFactory { initializer { RadioViewModel(c) } })
    val st by vm.state.collectAsStateWithLifecycle()
    val lib by c.library.library.collectAsStateWithLifecycle()
    var importing by remember { mutableStateOf(false) }
    val searching = st.query.trim().length >= 2

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.tab_radio), stringResource(R.string.radio_sub)) { io.github.oluengo.wavestr.ui.common.SurpriseButton(c); MeButton(c, onOpenMe) }
        OutlinedTextField(
            value = st.query,
            onValueChange = vm::setQuery,
            placeholder = { Text(stringResource(R.string.radio_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (st.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                RadioViewModel.Tab.POPULAR to stringResource(R.string.radio_popular),
                RadioViewModel.Tab.NEAR to stringResource(R.string.radio_near),
                RadioViewModel.Tab.TAGS to stringResource(R.string.radio_tags),
                RadioViewModel.Tab.IMPORTED to stringResource(R.string.radio_imported) + if (lib.imported.isNotEmpty()) " · ${lib.imported.size}" else "",
            ).forEach { (tab, label) ->
                FilterChip(selected = !searching && st.tab == tab, onClick = { vm.setTab(tab) }, label = { Text(label) })
            }
        }

        val play: (io.github.oluengo.wavestr.radio.Station) -> Unit = { c.player.playStation(it); onOpenPlayer() }
        val toggle: (io.github.oluengo.wavestr.radio.Station) -> Unit = { c.library.toggleStation(it) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (!searching && st.tab == RadioViewModel.Tab.NEAR) item { CountryPicker(st.country, st.countries, vm::setCountry) }
            if (!searching && st.tab == RadioViewModel.Tab.TAGS) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    st.tags.forEach { (tag, n) ->
                        FilterChip(selected = st.tag == tag, onClick = { vm.setTag(if (st.tag == tag) null else tag) }, label = { Text("$tag · $n") })
                    }
                }
            }
            if (!searching && st.tab == RadioViewModel.Tab.IMPORTED) {
                item {
                    OutlinedButton(onClick = { vm.resetImport(); importing = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.import_button))
                    }
                }
                if (lib.imported.isEmpty()) item { EmptyNote(stringResource(R.string.import_empty)) }
                items(lib.imported, key = { it.id }) { s ->
                    StationRow(c, s, lib.isFavorite(s.id), onClick = { play(s) }, onToggleFav = { toggle(s) }, trailing = {
                        IconButton(onClick = { c.library.update { it.removeImported(s.id) } }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.import_remove), tint = Rs.colors.muted)
                        }
                    })
                    HorizontalDivider(color = Rs.colors.line)
                }
                return@LazyColumn
            }
            when {
                st.loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                st.failed -> item {
                    Column {
                        EmptyNote(stringResource(R.string.radio_error))
                        OutlinedButton(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }
                searching && st.stations.isEmpty() -> item {
                    Column {
                        EmptyNote(stringResource(R.string.radio_no_results, st.query.trim()))
                        OutlinedButton(onClick = { vm.resetImport(); importing = true }) { Text(stringResource(R.string.import_button)) }
                    }
                }
                st.tab == RadioViewModel.Tab.TAGS && st.tag == null && !searching -> item { EmptyNote(stringResource(R.string.radio_pick_tag)) }
                else -> {
                    item {
                        SectionLabel(
                            when {
                                searching -> stringResource(R.string.radio_results)
                                st.tab == RadioViewModel.Tab.POPULAR -> stringResource(R.string.radio_popular_label)
                                st.tab == RadioViewModel.Tab.NEAR -> countryName(st.country) ?: st.country
                                else -> st.tag.orEmpty()
                            } + " · ${st.stations.size}"
                        )
                    }
                    items(st.stations, key = { it.id }) { s ->
                        StationRow(c, s, lib.isFavorite(s.id), onClick = { play(s) }, onToggleFav = { toggle(s) })
                        HorizontalDivider(color = Rs.colors.line)
                    }
                    item { EmptyNote(stringResource(R.string.radio_source_note)) }
                }
            }
        }
    }

    if (importing) ImportSheet(c, vm, lib, onDone = { importing = false })
}

@Composable
private fun CountryPicker(current: String, countries: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Box {
            OutlinedButton(onClick = { open = true }) { Text((countryName(current) ?: current) + " ▾") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                val names = countries.ifEmpty { listOf(current) }.map { it to (countryName(it) ?: it) }.sortedBy { it.second }
                names.forEach { (code, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(code) })
                }
            }
        }
        Text(stringResource(R.string.radio_country_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ImportSheet(c: AppContainer, vm: RadioViewModel, lib: Library, onDone: () -> Unit) {
    val st by vm.import.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(256 * 1024).toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text == null) vm.fileUnreadable() else vm.parseImport(text)
    }
    val addedMsg = stringResource(R.string.import_done)

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = st.text, onValueChange = vm::setImportText,
                placeholder = { Text(stringResource(R.string.import_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.parseImport() }, enabled = !st.busy) { Text(stringResource(R.string.import_read)) }
                OutlinedButton(onClick = {
                    picker.launch(arrayOf("audio/x-scpls", "audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream"))
                }) { Text(stringResource(R.string.import_file)) }
                if (st.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically))
            }
            st.error?.let { e ->
                Text(
                    stringResource(
                        when (e) {
                            RadioViewModel.ImportError.EMPTY_INPUT -> R.string.import_err_empty
                            RadioViewModel.ImportError.NOTHING_FOUND -> R.string.import_err_none
                            RadioViewModel.ImportError.HLS_CONTENT -> R.string.import_err_hls
                            RadioViewModel.ImportError.DOWNLOAD_FAILED -> R.string.import_err_download
                            RadioViewModel.ImportError.NOTHING_PICKED -> R.string.import_err_pick
                            RadioViewModel.ImportError.FILE_UNREADABLE -> R.string.import_err_file
                        }
                    ),
                    color = Rs.colors.live, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (st.items.isNotEmpty()) {
                SectionLabel(stringResource(R.string.import_found, st.kind?.name ?: "", st.items.size))
                st.items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in st.picked, onCheckedChange = { vm.togglePick(i) })
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Rs.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.url.startsWith("http://", true)) Text("http", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Rs.colors.muted)
                    }
                }
                DestinationPicker(lib, st.toFavorites, st.folderId, vm::setDestination)
                Button(onClick = {
                    val n = vm.confirmImport()
                    if (n != null) { Toast.makeText(ctx, String.format(addedMsg, n), Toast.LENGTH_SHORT).show(); onDone() }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(ctx.resources.getQuantityString(R.plurals.import_add, st.picked.size, st.picked.size))
                }
            }
            Text(stringResource(R.string.import_note), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DestinationPicker(lib: Library, toFav: Boolean, folderId: String?, onPick: (Boolean, String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val favLabel = stringResource(R.string.tab_favorites)
    val label = when {
        !toFav -> stringResource(R.string.import_dest_only)
        folderId == null -> favLabel
        else -> favLabel + " › " + (lib.path(folderId).joinToString(" › ") { it.name })
    }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.import_dest, label) + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.import_dest_only)) }, onClick = { open = false; onPick(false, null) })
            DropdownMenuItem(text = { Text(favLabel) }, onClick = { open = false; onPick(true, null) })
            lib.tree().forEach { (f, depth) ->
                DropdownMenuItem(text = { Text("   ".repeat(depth + 1) + f.name) }, onClick = { open = false; onPick(true, f.id) })
            }
        }
    }
}

/** readNBytes existe desde Java 11 / Android 13; esto funciona en todas. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n <= 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
