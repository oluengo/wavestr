package io.github.oluengo.wavestr.nostr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Qué streamers favoritos están emitiendo ahora (para Favoritos y el widget).
 * [live] es null mientras no se ha consultado; luego, anfitrión → su stream activo.
 */
class LiveStatus(private val nostr: NostrRepository, private val now: () -> Long = System::currentTimeMillis) {
    private val _live = MutableStateFlow<Map<String, LiveStream>?>(null)
    val live: StateFlow<Map<String, LiveStream>?> = _live.asStateFlow()
    private var checkedAt = 0L
    private var checkedHosts = emptySet<String>()

    /** Consulta los relays si han pasado más de [maxAgeMs] o cambian los anfitriones. */
    suspend fun refresh(hosts: Set<String>, viewer: String?, maxAgeMs: Long = 60_000) {
        if (hosts.isEmpty()) { _live.value = emptyMap(); checkedHosts = hosts; return }
        if (hosts == checkedHosts && now() - checkedAt < maxAgeMs && _live.value != null) return
        val streams = runCatching { nostr.liveOf(hosts, viewer) }.getOrNull() ?: return
        _live.value = streams.groupBy { it.hostPubkey }.mapValues { (_, list) -> list.maxBy { it.updatedAt } }
        checkedAt = now(); checkedHosts = hosts
    }
}
