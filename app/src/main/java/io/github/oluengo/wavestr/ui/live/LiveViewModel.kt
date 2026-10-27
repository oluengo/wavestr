package io.github.oluengo.wavestr.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LiveViewModel(private val c: AppContainer) : ViewModel() {
    enum class Scope { FOLLOWING, GLOBAL }

    data class Item(val stream: LiveStream, val host: Profile?)

    data class UiState(
        val scope: Scope = Scope.FOLLOWING,
        val pubkey: String? = null,
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val failed: Boolean = false,
        val items: List<Item> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            c.settings.flow.map { it.pubkey }.distinctUntilChanged().collect { pk ->
                // Sin sesión, empezamos en Global para que la pantalla no salga vacía.
                _state.update { it.copy(pubkey = pk, scope = if (pk == null) Scope.GLOBAL else Scope.FOLLOWING) }
                load()
            }
        }
    }

    fun setScope(scope: Scope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope, items = emptyList(), loaded = false) }
        load()
    }

    fun load(refresh: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val st = _state.value
            if (st.scope == Scope.FOLLOWING && st.pubkey == null) {
                _state.update { it.copy(loading = false, loaded = true, items = emptyList()) }
                return@launch
            }
            _state.update { it.copy(loading = true, failed = false) }
            try {
                val streams = when (st.scope) {
                    Scope.GLOBAL -> c.nostr.liveGlobal(st.pubkey)
                    Scope.FOLLOWING -> c.nostr.liveFromFollows(st.pubkey!!, refresh)
                }
                val profiles = c.nostr.profiles(streams.map { it.hostPubkey }, st.pubkey)
                _state.update {
                    it.copy(loading = false, loaded = true, items = streams.map { s -> Item(s, profiles[s.hostPubkey]) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loaded = true, failed = true) }
            }
        }
    }
}
