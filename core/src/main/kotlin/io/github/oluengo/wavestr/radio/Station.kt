package io.github.oluengo.wavestr.radio

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Un formato concreto en el que emite una emisora (cada uno es una URL distinta). */
data class StreamVariant(
    val url: String,
    val codec: String?,
    val bitrate: Int,
    val hls: Boolean = false,
    /** id de Radio Browser (stationuuid) de esta variante, para contar escuchas. */
    val uuid: String? = null,
) {
    /** «AAC · 128 kbps», «MP3», «FLAC» o null si no se sabe nada. */
    val label: String?
        get() {
            val c = codec?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() && it != "UNKNOWN" }
            val b = bitrate.takeIf { it > 0 }?.let { "$it kbps" }
            return listOfNotNull(c, b).joinToString(" · ").ifEmpty { null }
        }
}

data class Station(
    val id: String,
    val name: String,
    /** De mayor a menor calidad. */
    val variants: List<StreamVariant>,
    val homepage: String? = null,
    val favicon: String? = null,
    val tags: List<String> = emptyList(),
    val country: String? = null,
    val countryCode: String? = null,
    val language: String? = null,
    val votes: Int = 0,
    /** false si el directorio la marca como caída en su última comprobación. */
    val ok: Boolean = true,
    val imported: Boolean = false,
) {
    val best: StreamVariant get() = variants.first()
    val lowest: StreamVariant get() = variants.last()
}

/** Ficha de Radio Browser tal cual llega (una por URL). */
data class RawStation(
    val uuid: String,
    val name: String,
    val url: String,
    val homepage: String?,
    val favicon: String?,
    val tags: List<String>,
    val country: String?,
    val countryCode: String?,
    val language: String?,
    val codec: String?,
    val bitrate: Int,
    val hls: Boolean,
    val ok: Boolean,
    val votes: Int,
)

object RadioBrowserJson {
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 0
    private fun JsonObject.flag(k: String) = (this[k] as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: (p.contentOrNull == "1") } ?: false

    fun parseStations(arr: JsonArray): List<RawStation> = arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val url = o.str("url_resolved") ?: o.str("url") ?: return@mapNotNull null
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@mapNotNull null
        RawStation(
            uuid = o.str("stationuuid") ?: return@mapNotNull null,
            name = o.str("name") ?: return@mapNotNull null,
            url = url,
            homepage = o.str("homepage"),
            favicon = o.str("favicon")?.takeIf { it.startsWith("http", true) },
            tags = o.str("tags")?.split(',')?.map { it.trim().lowercase(Locale.ROOT) }?.filter { it.isNotEmpty() }?.distinct()?.take(8).orEmpty(),
            country = o.str("country"),
            countryCode = o.str("countrycode")?.uppercase(Locale.ROOT),
            language = o.str("language"),
            codec = o.str("codec"),
            bitrate = o.int("bitrate"),
            hls = o.flag("hls"),
            ok = (o["lastcheckok"] as? JsonPrimitive)?.let { o.flag("lastcheckok") } ?: true,
            votes = o.int("votes"),
        )
    }

    /** Etiquetas: lista de (nombre, nº de emisoras). */
    fun parseTags(arr: JsonArray): List<Pair<String, Int>> = arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val n = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.length in 2..30 } ?: return@mapNotNull null
        n to ((o["stationcount"] as? JsonPrimitive)?.jsonPrimitive?.intOrNull ?: 0)
    }
}

object Stations {
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun host(url: String?): String? = runCatching { URI(url!!.trim()).host?.removePrefix("www.")?.lowercase(Locale.ROOT) }.getOrNull()

    /**
     * Radio Browser tiene una ficha por URL. Juntamos en una emisora las que comparten nombre y web
     * (o nombre y país si no hay web), y cada URL pasa a ser una variante de calidad.
     */
    fun group(raw: List<RawStation>): List<Station> {
        val groups = LinkedHashMap<String, MutableList<RawStation>>()
        raw.forEach { r ->
            val key = normalize(r.name) + "|" + (host(r.homepage) ?: r.countryCode.orEmpty())
            groups.getOrPut(key) { mutableListOf() }.add(r)
        }
        return groups.values.map { list ->
            val main = list.maxBy { it.votes }
            val variants = list.distinctBy { it.url.trimEnd('/') }
                .map { StreamVariant(it.url, it.codec, it.bitrate, it.hls, it.uuid) }
                .sortedWith(compareByDescending<StreamVariant> { it.bitrate }.thenBy { it.url })
            Station(
                id = "rb:" + main.uuid,
                name = main.name,
                variants = variants,
                homepage = main.homepage,
                favicon = list.firstNotNullOfOrNull { it.favicon },
                tags = list.flatMap { it.tags }.distinct().take(8),
                country = main.country,
                countryCode = main.countryCode,
                language = main.language,
                votes = list.sumOf { it.votes },
                ok = list.any { it.ok },
            )
        }
    }

    /** Variante a usar: la elegida, o en Auto la mejor con wifi y la más ligera con datos móviles. */
    fun pick(station: Station, chosen: Int?, cellular: Boolean): StreamVariant =
        chosen?.let { station.variants.getOrNull(it) } ?: if (cellular) station.lowest else station.best
}
