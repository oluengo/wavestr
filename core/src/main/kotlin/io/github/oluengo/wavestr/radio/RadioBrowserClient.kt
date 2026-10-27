package io.github.oluengo.wavestr.radio

import io.github.oluengo.wavestr.nostr.NostrJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Cliente de Radio Browser (https://www.radio-browser.info): directorio libre y sin cuenta.
 * Elige un servidor de la lista oficial y, si falla, prueba otro.
 */
/** [userAgent]: Radio Browser pide identificar la app, p. ej. "Wavestr/0.5.0 (github.com/oluengo/wavestr)". */
class RadioBrowserClient(private val http: OkHttpClient, private val scope: CoroutineScope, private val userAgent: String) {
    private val mutex = Mutex()
    private var base: String? = null

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("vacío")
        }
    }

    private fun resolveServers(): List<String> = runCatching {
        (NostrJson.parseToJsonElement(get("https://all.api.radio-browser.info/json/servers")) as JsonArray)
            .mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull }
            .distinct().shuffled().map { "https://$it" }
    }.getOrDefault(emptyList()) + FALLBACK

    private suspend fun json(path: String): JsonElement = withContext(Dispatchers.IO) {
        val servers = mutex.withLock { base?.let { b -> listOf(b) + FALLBACK.filter { it != b } } ?: resolveServers() }
        var last: Exception? = null
        for (s in servers.take(3)) {
            try {
                val el = NostrJson.parseToJsonElement(get(s + path))
                mutex.withLock { base = s }
                return@withContext el
            } catch (e: Exception) {
                last = e
                mutex.withLock { if (base == s) base = null }
            }
        }
        throw last ?: IOException("sin servidores")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun stations(path: String): List<Station> =
        Stations.group(RadioBrowserJson.parseStations(json(path) as JsonArray))

    suspend fun top(limit: Int = 80) =
        stations("/json/stations/search?order=votes&reverse=true&hidebroken=true&limit=$limit")

    /** Emisoras al azar del directorio (solo las que funcionan). */
    suspend fun random(limit: Int = 40) =
        stations("/json/stations/search?order=random&hidebroken=true&limit=$limit")

    suspend fun byCountry(code: String) =
        stations("/json/stations/search?countrycode=${enc(code)}&order=votes&reverse=true&hidebroken=true&limit=120")

    suspend fun byTag(tag: String) =
        stations("/json/stations/search?tag=${enc(tag)}&tagExact=true&order=votes&reverse=true&hidebroken=true&limit=120")

    /** Por nombre y, además, por estilo con el mismo texto. Incluye las caídas (se marcan como tal). */
    suspend fun search(q: String): List<Station> {
        val byName = RadioBrowserJson.parseStations(json("/json/stations/search?name=${enc(q)}&order=votes&reverse=true&limit=80") as JsonArray)
        val byTag = runCatching {
            RadioBrowserJson.parseStations(json("/json/stations/search?tag=${enc(q.lowercase())}&order=votes&reverse=true&hidebroken=true&limit=40") as JsonArray)
        }.getOrDefault(emptyList())
        return Stations.group((byName + byTag).distinctBy { it.uuid })
    }

    suspend fun tags(): List<Pair<String, Int>> =
        RadioBrowserJson.parseTags(json("/json/tags?order=stationcount&reverse=true&hidebroken=true&limit=80") as JsonArray)

    /** Códigos de país con al menos [min] emisoras. */
    suspend fun countries(min: Int = 15): List<String> =
        (json("/json/countries?order=stationcount&reverse=true&hidebroken=true") as JsonArray).mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val code = (o["iso_3166_1"] as? JsonPrimitive)?.contentOrNull?.uppercase()?.takeIf { it.length == 2 } ?: return@mapNotNull null
            val n = (o["stationcount"] as? JsonPrimitive)?.intOrNull ?: 0
            code.takeIf { n >= min }
        }.distinct()

    /** Avisa al directorio de una escucha, como pide su API (sirve para las «más escuchadas»). */
    fun countClick(uuid: String?) {
        if (uuid == null) return
        scope.launch(Dispatchers.IO) { runCatching { json("/json/url/${enc(uuid)}") } }
    }

    /** Descarga una lista .pls/.m3u (máx. 256 KB). */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val src = r.body?.source() ?: throw IOException("vacío")
            src.request(256 * 1024L)
            src.buffer.readUtf8(minOf(src.buffer.size, 256 * 1024L))
        }
    }

    companion object {
        private val FALLBACK = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
        )
    }
}
