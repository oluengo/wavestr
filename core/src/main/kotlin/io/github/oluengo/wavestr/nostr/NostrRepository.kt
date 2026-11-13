package io.github.oluengo.wavestr.nostr

import java.util.concurrent.ConcurrentHashMap

/** Acceso a Nostr para las pantallas: seguidos, directos, perfiles y grabaciones. */
class NostrRepository(private val pool: RelayPool) {

    private val profileCache = ConcurrentHashMap<String, Profile>()
    private val relayCache = ConcurrentHashMap<String, List<String>>()
    private val followCache = ConcurrentHashMap<String, List<String>>()

    private fun now() = System.currentTimeMillis() / 1000

    /** Relays de lectura del usuario (NIP-65) + los de por defecto. */
    suspend fun relaysFor(pubkey: String?): List<String> {
        if (pubkey == null) return RelayPool.DEFAULT
        relayCache[pubkey]?.let { return it }
        val list = pool.query(
            RelayPool.INDEXERS + RelayPool.DEFAULT,
            listOf(Filter(kinds = listOf(Kinds.RELAY_LIST), authors = listOf(pubkey), limit = 1)),
        ).maxByOrNull { it.createdAt }?.readRelays().orEmpty()
        val merged = (list.take(6) + RelayPool.DEFAULT).distinct()
        relayCache[pubkey] = merged
        return merged
    }

    /** Relays para los datos propios: los de escritura del usuario + los de por defecto. */
    suspend fun ownRelays(pubkey: String): List<String> {
        val ev = pool.query(
            RelayPool.INDEXERS + RelayPool.DEFAULT,
            listOf(Filter(kinds = listOf(Kinds.RELAY_LIST), authors = listOf(pubkey), limit = 1)),
        ).maxByOrNull { it.createdAt }
        return ((ev?.writeRelays().orEmpty()).take(5) + RelayPool.DEFAULT).distinct()
    }

    /** Al cambiar los relays de Wavestr hay que olvidar lo calculado con los anteriores. */
    fun clearRelayCaches() { relayCache.clear(); followCache.clear() }

    /** Lista NIP-65 del usuario como (url, marca): "read", "write" o "" (ambos). */
    suspend fun relayList(pubkey: String): List<Pair<String, String>> =
        pool.query(
            RelayPool.INDEXERS + RelayPool.DEFAULT,
            listOf(Filter(kinds = listOf(Kinds.RELAY_LIST), authors = listOf(pubkey), limit = 1)),
        ).maxByOrNull { it.createdAt }?.tagsNamed("r").orEmpty()
            .filter { it.size > 1 && it[1].startsWith("wss://") }
            .map { it[1].trimEnd('/') to (it.getOrNull(2) ?: "") }
            .distinctBy { it.first }

    /** Directos activos ahora de unos anfitriones concretos (p. ej. tus streamers favoritos). */
    suspend fun liveOf(hosts: Collection<String>, viewer: String?): List<LiveStream> {
        if (hosts.isEmpty()) return emptyList()
        val list = hosts.toList()
        val since = now() - 24 * 3600
        return pool.query(
            relaysFor(viewer),
            listOf(
                Filter(kinds = listOf(Kinds.LIVE_EVENT), authors = list, since = since),
                Filter(kinds = listOf(Kinds.LIVE_EVENT), tags = mapOf("p" to list), since = since),
            ),
        ).latestByAddress().mapNotNull(LiveStream::from)
            .filter { it.isLiveNow(now()) && it.hostPubkey in hosts }
    }

    suspend fun follows(pubkey: String, refresh: Boolean = false): List<String> {
        if (!refresh) followCache[pubkey]?.let { return it }
        val ev = pool.query(
            RelayPool.INDEXERS + relaysFor(pubkey),
            listOf(Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey), limit = 1)),
        ).maxByOrNull { it.createdAt }
        val list = ev?.followedPubkeys().orEmpty()
        followCache[pubkey] = list
        return list
    }

    /**
     * Lista de seguidos (kind 3) completa y lo más reciente posible, para editarla: se busca en los
     * indexadores y en los relays de lectura y de escritura del usuario. null si no aparece en ninguno.
     */
    suspend fun contactList(pubkey: String): Event? = pool.query(
        (RelayPool.INDEXERS + relaysFor(pubkey) + ownRelays(pubkey)).distinct(),
        listOf(Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey), limit = 1)),
    ).filter { it.pubkey == pubkey && it.kind == Kinds.CONTACTS }.maxByOrNull { it.createdAt }

    /** Tras publicar una lista nueva, la caché pasa a ser esa. */
    fun rememberFollows(pubkey: String, list: Event) { followCache[pubkey] = list.followedPubkeys() }

    /** Directos de la gente que sigues: como anfitrión (#p) o como autor del evento. */
    suspend fun liveFromFollows(pubkey: String, refresh: Boolean = false): List<LiveStream> {
        val follows = follows(pubkey, refresh)
        if (follows.isEmpty()) return emptyList()
        val since = now() - 24 * 3600
        val filters = follows.chunked(300).flatMap { chunk ->
            listOf(
                Filter(kinds = listOf(Kinds.LIVE_EVENT), authors = chunk, since = since),
                Filter(kinds = listOf(Kinds.LIVE_EVENT), tags = mapOf("p" to chunk), since = since),
            )
        }
        val followSet = follows.toSet()
        return pool.query(relaysFor(pubkey), filters).latestByAddress()
            .mapNotNull(LiveStream::from)
            .filter { it.isLiveNow(now()) && (it.hostPubkey in followSet || it.eventPubkey in followSet) }
            .sortedByDescending { it.participants ?: 0 }
    }

    suspend fun liveGlobal(pubkey: String?): List<LiveStream> =
        pool.query(
            relaysFor(pubkey),
            listOf(Filter(kinds = listOf(Kinds.LIVE_EVENT), since = now() - 6 * 3600, limit = 300)),
        ).latestByAddress()
            .mapNotNull(LiveStream::from)
            .filter { it.isLiveNow(now()) }
            .sortedByDescending { it.participants ?: 0 }

    /**
     * Próximos directos programados (NIP-53 «planned») y eventos de calendario con hora (NIP-52) de
     * [hosts], desde ahora hasta [days] días. Ordenados por hora de inicio.
     */
    suspend fun upcoming(hosts: Collection<String>, viewer: String?, days: Int = 14): List<Scheduled> {
        if (hosts.isEmpty()) return emptyList()
        val now = now()
        val list = hosts.distinct()
        val filters = list.chunked(300).flatMap { chunk ->
            listOf(
                Filter(kinds = listOf(Kinds.LIVE_EVENT, Kinds.CALENDAR_TIME), authors = chunk, since = now - 60L * 24 * 3600, limit = 300),
                Filter(kinds = listOf(Kinds.LIVE_EVENT, Kinds.CALENDAR_TIME), tags = mapOf("p" to chunk), since = now - 60L * 24 * 3600, limit = 300),
            )
        }
        val set = list.toSet()
        return pool.query(relaysFor(viewer), filters).latestByAddress()
            .mapNotNull(Scheduled::from)
            .filter { it.hostPubkey in set && it.startsAt > now - 900 && it.startsAt < now + days * 24L * 3600 }
            .distinctBy { it.address }
            .sortedBy { it.startsAt }
    }

    /** La última versión de un directo por su dirección (kind:pubkey:d). */
    suspend fun stream(address: String, viewer: String?): LiveStream? {
        val parts = address.split(':', limit = 3)
        if (parts.size < 3 || parts[0].toIntOrNull() != Kinds.LIVE_EVENT) return null
        return pool.query(
            relaysFor(viewer),
            listOf(Filter(kinds = listOf(Kinds.LIVE_EVENT), authors = listOf(parts[1]), tags = mapOf("d" to listOf(parts[2])), limit = 5)),
        ).maxByOrNull { it.createdAt }?.let(LiveStream::from)
    }

    /** Directos y grabaciones de un anfitrión. */
    suspend fun streamsOf(host: String, pubkey: String?): List<LiveStream> =
        pool.query(
            relaysFor(pubkey),
            listOf(
                Filter(kinds = listOf(Kinds.LIVE_EVENT), authors = listOf(host), limit = 50),
                Filter(kinds = listOf(Kinds.LIVE_EVENT), tags = mapOf("p" to listOf(host)), limit = 50),
            ),
        ).latestByAddress()
            .mapNotNull(LiveStream::from)
            .filter { it.hostPubkey == host }
            .sortedByDescending { it.startsAt ?: it.updatedAt }

    data class Activity(val chat: List<ChatMessage>, val zaps: List<ZapReceipt>, val reactions: List<Reaction>)

    /** Chat, zaps y reacciones (al stream y a los mensajes) de un directo desde [since]. */
    suspend fun activityFull(address: String, since: Long, viewer: String?): Activity {
        val relays = relaysFor(viewer)
        val evs = pool.query(
            relays,
            listOf(Filter(kinds = listOf(Kinds.LIVE_CHAT, Kinds.ZAP_RECEIPT, Kinds.REACTION), tags = mapOf("a" to listOf(address)), since = since, limit = 500)),
            timeoutMs = 5_000,
        )
        val chat = evs.mapNotNull(ChatMessage::from).filter { it.streamAddress == address }.sortedBy { it.createdAt }
        // Hay clientes que reaccionan a un mensaje sin la etiqueta a: se buscan también por #e.
        val ids = chat.takeLast(60).map { it.id }
        val extra = if (ids.isEmpty()) emptyList() else runCatching {
            pool.query(relays, listOf(Filter(kinds = listOf(Kinds.REACTION, Kinds.ZAP_RECEIPT), tags = mapOf("e" to ids), since = since)), timeoutMs = 4_000)
        }.getOrDefault(emptyList())
        val all = (evs + extra).distinctBy { it.id }
        val known = ids.toSet()
        val zaps = all.mapNotNull(ZapReceipt::from).filter { it.streamAddress == address || it.eventId in known }.sortedBy { it.createdAt }
        // Una reacción con etiqueta a cuyo e no es un mensaje del chat va al stream (p. ej. al evento 30311).
        val reactions = all.mapNotNull { Reaction.from(it, address) }.map { if (it.eventId != null && it.eventId !in known) it.copy(eventId = null) else it }
            .distinctBy { Triple(it.pubkey, it.eventId, it.emoji) }
        return Activity(chat, zaps, reactions)
    }

    /** Chat (kind 1311) y zaps (kind 9735) de un directo desde [since]. */
    suspend fun activity(address: String, since: Long, viewer: String?): Pair<List<ChatMessage>, List<ZapReceipt>> {
        val evs = pool.query(
            relaysFor(viewer),
            listOf(Filter(kinds = listOf(Kinds.LIVE_CHAT, Kinds.ZAP_RECEIPT), tags = mapOf("a" to listOf(address)), since = since, limit = 300)),
            timeoutMs = 5_000,
        )
        val chat = evs.mapNotNull(ChatMessage::from).filter { it.streamAddress == address }.sortedBy { it.createdAt }
        val zaps = evs.mapNotNull(ZapReceipt::from).sortedBy { it.createdAt }
        return chat to zaps
    }

    fun cachedProfile(pubkey: String): Profile? = profileCache[pubkey]

    suspend fun profiles(pubkeys: Collection<String>, viewer: String? = null): Map<String, Profile> {
        val missing = pubkeys.filter { !profileCache.containsKey(it) }.distinct()
        if (missing.isNotEmpty()) {
            pool.query(
                (RelayPool.INDEXERS + relaysFor(viewer)).distinct(),
                missing.chunked(300).map { Filter(kinds = listOf(Kinds.METADATA), authors = it) },
            ).groupBy { it.pubkey }
                .mapNotNull { (_, evs) -> evs.maxBy { it.createdAt }.let(Profile::from) }
                .forEach { profileCache[it.pubkey] = it }
        }
        return pubkeys.mapNotNull { k -> profileCache[k]?.let { k to it } }.toMap()
    }
}
