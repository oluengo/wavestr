package io.github.oluengo.wavestr.radio

import io.github.oluengo.wavestr.data.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Busca la carátula de una emisora cuando el directorio no trae una buena: mira su web
 * (apple-touch-icon, og:image, iconos) y las rutas habituales, y comprueba que de verdad es una imagen.
 * El resultado (o «no hay») se guarda para no repetir la búsqueda.
 */
class ArtworkResolver(private val prefs: KeyValueStore, http: OkHttpClient) {
    private val http = http.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    private val memory = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val parallel = Semaphore(4)
    /** Búsquedas en la web hechas (para la prueba de arranque). */
    @Volatile var searches = 0
        private set

    /** Lo que ya sabemos sin red: url, "" (no hay) o null (no se ha buscado). */
    fun cached(st: Station): String? = memory[st.id] ?: prefs.get(st.id)?.also { memory[st.id] = it }

    /** [skip]: URLs que ya han fallado al cargarse (p. ej. el favicon del directorio). */
    suspend fun resolve(st: Station, skip: Set<String> = emptySet()): String? {
        cached(st)?.let { c -> if (c !in skip) return c.ifEmpty { null } }
        val mine = CompletableDeferred<String?>()
        val existing = inFlight.putIfAbsent(st.id, mine)
        if (existing != null) return existing.await()
        val found = try {
            parallel.withPermit { withContext(Dispatchers.IO) { search(st, skip) } }
        } catch (e: Exception) {
            null
        }
        val value = found.orEmpty()
        memory[st.id] = value
        prefs.put(st.id, value)
        inFlight.remove(st.id)
        mine.complete(found)
        return found
    }

    private fun search(st: Station, skip: Set<String>): String? {
        searches++
        val page = SiteIcons.pageFor(st)
        val fromSite = page?.let { p -> runCatching { SiteIcons.fromHtml(fetchHtml(p), p) }.getOrDefault(emptyList()) }.orEmpty()
        val candidates = buildList {
            addAll(fromSite)
            st.favicon?.let { add(it) }
            page?.let { addAll(SiteIcons.conventional(it)) }
        }.distinct().filter { it !in skip }
        return candidates.take(8).firstOrNull(::isImage)
    }

    private fun fetchHtml(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "text/html").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return ""
            val type = r.header("Content-Type").orEmpty()
            if (!type.contains("html", true)) return ""
            val src = r.body?.source() ?: return ""
            src.request(200_000)
            return src.buffer.readUtf8(minOf(src.buffer.size, 200_000))
        }
    }

    private fun isImage(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Range", "bytes=0-1023").build()
        http.newCall(req).execute().use { r ->
            val type = r.header("Content-Type").orEmpty().lowercase()
            r.isSuccessful && (type.startsWith("image/") || (type.isEmpty() || type.contains("octet")) && url.substringBefore('?').matches(Regex(".*\\.(png|jpe?g|webp|gif|ico|svg)$", RegexOption.IGNORE_CASE)))
        }
    }.getOrDefault(false)

    companion object {
        private const val UA = "Mozilla/5.0 (compatible; Wavestr)"
    }
}
