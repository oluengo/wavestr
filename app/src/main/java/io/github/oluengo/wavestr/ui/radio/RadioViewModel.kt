package io.github.oluengo.wavestr.ui.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.data.FavItem
import io.github.oluengo.wavestr.data.station
import io.github.oluengo.wavestr.radio.Playlists
import io.github.oluengo.wavestr.radio.Station
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class RadioViewModel(private val c: AppContainer) : ViewModel() {
    enum class Tab { POPULAR, NEAR, TAGS, IMPORTED }

    data class UiState(
        val tab: Tab = Tab.POPULAR,
        val query: String = "",
        val loading: Boolean = false,
        val failed: Boolean = false,
        val stations: List<Station> = emptyList(),
        val tags: List<Pair<String, Int>> = emptyList(),
        val tag: String? = null,
        val country: String = Locale.getDefault().country.ifBlank { "ES" },
        val countries: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun setTab(t: Tab) { _state.update { it.copy(tab = t, query = "", stations = emptyList()) }; load() }
    fun setCountry(code: String) { _state.update { it.copy(country = code) }; load() }
    fun setTag(tag: String?) { _state.update { it.copy(tag = tag, stations = emptyList()) }; load() }

    /** Búsqueda con espera de 400 ms para no lanzar una consulta por cada letra. */
    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        load(debounceMs = 400)
    }

    fun load(debounceMs: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val st = _state.value
            if (st.tab == Tab.IMPORTED && st.query.isBlank()) { _state.update { it.copy(loading = false, failed = false) }; return@launch }
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val q = st.query.trim()
                when {
                    q.length >= 2 -> _state.update { it.copy(stations = c.radio.search(q)) }
                    st.tab == Tab.POPULAR -> _state.update { it.copy(stations = c.radio.top()) }
                    st.tab == Tab.NEAR -> {
                        val countries = st.countries.ifEmpty { runCatching { c.radio.countries() }.getOrDefault(emptyList()) }
                        _state.update { it.copy(countries = countries, stations = c.radio.byCountry(st.country)) }
                    }
                    st.tab == Tab.TAGS -> {
                        val tags = st.tags.ifEmpty { c.radio.tags() }
                        val list = st.tag?.let { c.radio.byTag(it) }.orEmpty()
                        _state.update { it.copy(tags = tags, stations = list) }
                    }
                }
                _state.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    // ---------- Importar ----------
    data class ImportState(
        val text: String = "",
        val busy: Boolean = false,
        val error: ImportError? = null,
        val kind: Playlists.Kind? = null,
        val items: List<Playlists.Item> = emptyList(),
        val picked: Set<Int> = emptySet(),
        val folderId: String? = null,
        val toFavorites: Boolean = false,
    )

    enum class ImportError { EMPTY_INPUT, NOTHING_FOUND, HLS_CONTENT, DOWNLOAD_FAILED, NOTHING_PICKED, FILE_UNREADABLE }

    private val _import = MutableStateFlow(ImportState())
    val import: StateFlow<ImportState> = _import.asStateFlow()

    fun resetImport() { _import.value = ImportState() }
    fun setImportText(t: String) = _import.update { it.copy(text = t, error = null) }
    fun fileUnreadable() = _import.update { it.copy(error = ImportError.FILE_UNREADABLE) }
    fun togglePick(i: Int) = _import.update { it.copy(picked = if (i in it.picked) it.picked - i else it.picked + i, error = null) }
    fun setDestination(toFavorites: Boolean, folderId: String?) = _import.update { it.copy(toFavorites = toFavorites, folderId = folderId) }

    fun parseImport(text: String = _import.value.text) {
        _import.update { it.copy(text = text, busy = true, error = null, items = emptyList(), kind = null) }
        viewModelScope.launch {
            var result = Playlists.parse(text)
            if (result is Playlists.Result.NeedsDownload) {
                result = try { Playlists.parse(c.radio.fetchText(result.url)) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    _import.update { it.copy(busy = false, error = ImportError.DOWNLOAD_FAILED) }; return@launch
                }
            }
            _import.update {
                when (val r = result) {
                    is Playlists.Result.Found -> it.copy(busy = false, kind = r.kind, items = r.items, picked = r.items.indices.toSet())
                    Playlists.Result.HlsContent -> it.copy(busy = false, error = ImportError.HLS_CONTENT)
                    Playlists.Result.Empty -> it.copy(busy = false, error = if (text.isBlank()) ImportError.EMPTY_INPUT else ImportError.NOTHING_FOUND)
                    is Playlists.Result.NeedsDownload -> it.copy(busy = false, error = ImportError.NOTHING_FOUND)
                }
            }
        }
    }

    /** Devuelve cuántas se han añadido, o null si no había ninguna marcada. */
    fun confirmImport(): Int? {
        val st = _import.value
        val kind = st.kind ?: return null
        val chosen = st.items.filterIndexed { i, _ -> i in st.picked }
        if (chosen.isEmpty()) { _import.update { it.copy(error = ImportError.NOTHING_PICKED) }; return null }
        val stations = chosen.map { Playlists.toStation(it, kind) }
        c.library.update { lib ->
            var l = lib.addImported(stations)
            if (st.toFavorites) stations.forEach { s ->
                l = l.add(FavItem.station(s).copy(folderId = st.folderId))
            }
            l
        }
        resetImport()
        _state.update { it.copy(tab = Tab.IMPORTED, query = "") }
        return stations.size
    }
}
