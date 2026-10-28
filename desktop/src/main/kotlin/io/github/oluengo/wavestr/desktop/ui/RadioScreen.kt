package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.radio.Playlists
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.util.Locale

private enum class RadioMode { POPULAR, NEAR, TAGS, SEARCH, IMPORTED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioScreen(c: DesktopContainer) {
    val lib by c.library.library.collectAsState()
    val ps by c.player.state.collectAsState()
    val nav = LocalNav.current
    var mode by remember { mutableStateOf(RadioMode.POPULAR) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val country = remember { Locale.getDefault().country.takeIf { it.length == 2 } ?: "ES" }

    LaunchedEffect(mode, searched, tag) {
        failed = false
        if (mode == RadioMode.IMPORTED) return@LaunchedEffect
        if (mode == RadioMode.TAGS && tags.isEmpty()) tags = runCatching { c.radio.tags().map { it.first }.take(40) }.getOrDefault(emptyList())
        if (mode == RadioMode.TAGS && tag == null) { stations = emptyList(); return@LaunchedEffect }
        loading = true
        try {
            stations = when (mode) {
                RadioMode.POPULAR -> c.radio.top()
                RadioMode.NEAR -> c.radio.byCountry(country)
                RadioMode.TAGS -> c.radio.byTag(tag!!)
                RadioMode.SEARCH -> if (searched.isBlank()) emptyList() else c.radio.search(searched)
                RadioMode.IMPORTED -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true; stations = emptyList()
        }
        loading = false
    }

    val list = if (mode == RadioMode.IMPORTED) lib.imported else stations
    fun play(st: Station) { c.player.playStation(st, from = list); nav.openPlayer() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(t("tab_radio"), t("radio_sub")) { HeaderActions(c) }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(t("radio_search_hint")) }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, t("clear")) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { searched = query.trim(); mode = RadioMode.SEARCH }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == RadioMode.POPULAR, { mode = RadioMode.POPULAR }, { Text(t("radio_popular")) })
            FilterChip(mode == RadioMode.NEAR, { mode = RadioMode.NEAR }, { Text(t("radio_near") + " · " + (countryName(country) ?: country)) })
            FilterChip(mode == RadioMode.TAGS, { mode = RadioMode.TAGS }, { Text(t("radio_tags")) })
            if (searched.isNotBlank()) FilterChip(mode == RadioMode.SEARCH, { mode = RadioMode.SEARCH }, { Text("«$searched»") })
            FilterChip(mode == RadioMode.IMPORTED, { mode = RadioMode.IMPORTED }, { Text(t("radio_imported")) })
            TextButton(onClick = { importing = true }) { Text(t("import_button")) }
        }
        if (mode == RadioMode.TAGS) {
            FlowRow(Modifier.padding(horizontal = 20.dp).heightIn(max = 120.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tg -> FilterChip(tag == tg, { tag = tg }, { Text(tg) }) }
            }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(330.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                failed -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyNote(t("radio_error")) }
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyNote(
                        when (mode) {
                            RadioMode.TAGS -> t("radio_pick_tag")
                            RadioMode.IMPORTED -> t("import_empty")
                            RadioMode.SEARCH -> t("radio_no_results", searched)
                            else -> t("radio_error")
                        }
                    )
                }
                else -> items(list, key = { it.id }) { st ->
                    StationRow(c, st, lib.isFavorite(st.id), ps.nowPlaying?.id == st.id, onClick = { play(st) }, onToggleFav = { c.library.toggleStation(st) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(t("radio_source_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (importing) ImportDialog(c, onDone = { n -> importing = false; if (n > 0) mode = RadioMode.IMPORTED }, onDismiss = { importing = false })
}

/** Importar PLS/M3U: pegar enlace o contenido, o elegir un archivo. */
@Composable
private fun ImportDialog(c: DesktopContainer, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<Station>>(emptyList()) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun read(input: String) {
        busy = true; error = null
        scope.launch {
            try {
                var r = Playlists.parse(input)
                if (r is Playlists.Result.NeedsDownload) r = Playlists.parse(c.radio.fetchText(r.url))
                when (r) {
                    is Playlists.Result.Found -> { found = r.items.map { Playlists.toStation(it, r.kind) }; picked = found.map { it.id }.toSet() }
                    Playlists.Result.HlsContent -> error = t("import_err_hls")
                    Playlists.Result.Empty -> error = t(if (input.isBlank()) "import_err_empty" else "import_err_none")
                    is Playlists.Result.NeedsDownload -> error = t("import_err_download")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = t("import_err_download")
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("import_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it; error = null }, placeholder = { Text(t("import_hint")) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { read(text) }, enabled = !busy) { Text(t("import_read")) }
                    OutlinedButton(onClick = {
                        val fd = FileDialog(null as Frame?, t("import_file"), FileDialog.LOAD)
                        fd.isVisible = true
                        val f = fd.files.firstOrNull()
                        if (f != null) scope.launch {
                            val content = runCatching { withContext(Dispatchers.IO) { f.readText() } }.getOrNull()
                            if (content == null) error = t("import_err_file") else read(content)
                        }
                    }, enabled = !busy) { Text(t("import_file")) }
                    if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                }
                error?.let { Text(it, color = Rs.colors.live, style = MaterialTheme.typography.bodySmall) }
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    found.forEach { st ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(st.id in picked, { on -> picked = if (on) picked + st.id else picked - st.id })
                            Column {
                                Text(st.name)
                                Text(st.best.url, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
                Text(t("import_note"), color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = found.filter { it.id in picked }
                if (chosen.isEmpty()) { error = t("import_err_pick"); return@TextButton }
                c.library.update { it.addImported(chosen) }
                onDone(chosen.size)
            }, enabled = found.isNotEmpty()) { Text(t("save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } },
    )
}
