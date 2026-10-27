package io.github.oluengo.radiostr.nostr

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
