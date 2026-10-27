package io.github.oluengo.radiostr.data

import io.github.oluengo.radiostr.radio.Station
import io.github.oluengo.radiostr.radio.StreamVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class Folder(val id: String, val name: String, val parentId: String? = null)

/** Un favorito: una emisora (con todos sus datos) o un streamer de Nostr (sus directos cambian). */
data class FavItem(
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val folderId: String? = null,
    val station: Station? = null,
    val hostPubkey: String? = null,
    val addedAt: Long = 0,
) {
    enum class Kind { STATION, STREAMER }

    companion object
}

/**
 * Biblioteca local: carpetas, favoritos y emisoras importadas. Inmutable: cada operación devuelve una
 * copia, así es fácil de probar y de guardar. La sincronización con Nostr (NIP-51) llegará después.
 */
data class Library(
    val folders: List<Folder> = emptyList(),
    val favorites: List<FavItem> = emptyList(),
    val imported: List<Station> = emptyList(),
) {
    fun isFavorite(key: String) = favorites.any { it.key == key }
    fun folder(id: String?) = folders.firstOrNull { it.id == id }
    fun subfolders(parentId: String?) = folders.filter { it.parentId == parentId }.sortedBy { it.name.lowercase() }
    fun itemsIn(folderId: String?) = favorites.filter { it.folderId == folderId }

    /** Ruta desde la raíz hasta la carpeta (para las migas de pan). */
    fun path(folderId: String?): List<Folder> {
        val out = ArrayList<Folder>()
        var f = folder(folderId)
        val seen = HashSet<String>()
        while (f != null && seen.add(f.id)) { out.add(0, f); f = folder(f.parentId) }
        return out
    }

    /** Árbol aplanado con profundidad, para elegir destino al mover. */
    fun tree(parentId: String? = null, depth: Int = 0): List<Pair<Folder, Int>> =
        subfolders(parentId).flatMap { listOf(it to depth) + tree(it.id, depth + 1) }

    fun countIn(folderId: String) = itemsIn(folderId).size to subfolders(folderId).size

    fun addFolder(id: String, name: String, parentId: String?) =
        copy(folders = folders + Folder(id, name.trim().take(40), parentId?.takeIf { folder(it) != null }))

    fun renameFolder(id: String, name: String) =
        copy(folders = folders.map { if (it.id == id) it.copy(name = name.trim().take(40)) else it })

    /** Al borrar, subcarpetas y favoritos suben a la carpeta padre. No se borra ningún favorito. */
    fun deleteFolder(id: String): Library {
        val f = folder(id) ?: return this
        return copy(
            folders = folders.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = f.parentId) else it },
            favorites = favorites.map { if (it.folderId == id) it.copy(folderId = f.parentId) else it },
        )
    }

    fun add(item: FavItem) = if (isFavorite(item.key)) this else copy(favorites = favorites + item)
    fun remove(key: String) = copy(favorites = favorites.filter { it.key != key })
    fun toggle(item: FavItem) = if (isFavorite(item.key)) remove(item.key) else add(item)
    fun move(key: String, folderId: String?) =
        copy(favorites = favorites.map { if (it.key == key) it.copy(folderId = folderId?.takeIf { f -> folder(f) != null }) else it })

    fun addImported(list: List<Station>) = copy(imported = imported + list.distinctBy { it.id }.filter { s -> imported.none { it.id == s.id } })
    fun removeImported(id: String) = copy(imported = imported.filter { it.id != id }, favorites = favorites.filter { it.key != id })

    /**
     * Combina con otra biblioteca (p. ej. una copia importada): se suman carpetas, favoritos e
     * importadas; si algo ya existe, gana la versión importada. No se borra nada.
     */
    fun merge(other: Library): Library {
        val fs = (folders.associateBy { it.id } + other.folders.associateBy { it.id }).values.toList()
        val ids = fs.map { it.id }.toSet()
        return Library(
            folders = fs.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it },
            favorites = (favorites.associateBy { it.key } + other.favorites.associateBy { it.key }).values
                .map { if (it.folderId != null && it.folderId !in ids) it.copy(folderId = null) else it },
            imported = (imported.associateBy { it.id } + other.imported.associateBy { it.id }).values.toList(),
        )
    }

    /** Copia de seguridad: la biblioteca con una cabecera para reconocerla. */
    fun toBackup(exportedAt: Long): JsonObject = buildJsonObject {
        put("app", "radiostr"); put("v", 1); put("exported", exportedAt)
        put("library", toJson())
    }

    /**
     * Las radios favoritas (y las importadas que no lo son) como lista M3U, con la carpeta como grupo
     * y el logo, para usarlas en cualquier reproductor.
     */
    fun toM3u(): String {
        val sb = StringBuilder("#EXTM3U\n")
        fun clean(x: String) = x.replace('"', '\'').replace('\n', ' ').replace(',', ' ').trim()
        val seen = HashSet<String>()
        favorites.filter { it.kind == FavItem.Kind.STATION && it.station != null }.forEach { f ->
            val st = f.station!!
            if (!seen.add(st.id)) return@forEach
            val group = path(f.folderId).joinToString(" / ") { it.name }
            sb.append("#EXTINF:-1")
            if (group.isNotEmpty()) sb.append(" group-title=\"").append(clean(group)).append('"')
            st.favicon?.let { sb.append(" tvg-logo=\"").append(it).append('"') }
            sb.append(',').append(clean(st.name)).append('\n').append(st.best.url).append('\n')
        }
        imported.filter { seen.add(it.id) }.forEach { st ->
            sb.append("#EXTINF:-1,").append(clean(st.name)).append('\n').append(st.best.url).append('\n')
        }
        return sb.toString()
    }

    // ---------- JSON (formato propio y versionado) ----------
    fun toJson(): JsonObject = buildJsonObject {
        put("v", 1)
        putJsonArray("folders") { folders.forEach { f -> addJsonObject { put("id", f.id); put("name", f.name); f.parentId?.let { put("parent", it) } } } }
        putJsonArray("favorites") {
            favorites.forEach { i ->
                addJsonObject {
                    put("key", i.key); put("kind", i.kind.name); put("title", i.title)
                    i.subtitle?.let { put("subtitle", it) }; i.image?.let { put("image", it) }
                    i.folderId?.let { put("folder", it) }; i.hostPubkey?.let { put("host", it) }
                    put("added", i.addedAt)
                    i.station?.let { put("station", stationJson(it)) }
                }
            }
        }
        putJsonArray("imported") { imported.forEach { add(stationJson(it)) } }
    }

    companion object {
        fun stationJson(s: Station): JsonObject = buildJsonObject {
            put("id", s.id); put("name", s.name)
            putJsonArray("variants") {
                s.variants.forEach { v -> addJsonObject { put("url", v.url); v.codec?.let { put("codec", it) }; put("bitrate", v.bitrate); put("hls", v.hls); v.uuid?.let { put("uuid", it) } } }
            }
            s.homepage?.let { put("homepage", it) }; s.favicon?.let { put("favicon", it) }
            putJsonArray("tags") { s.tags.forEach { add(it) } }
            s.country?.let { put("country", it) }; s.countryCode?.let { put("cc", it) }; s.language?.let { put("lang", it) }
            put("votes", s.votes); put("ok", s.ok); put("imported", s.imported)
        }

        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

        fun stationFrom(el: JsonElement?): Station? = runCatching {
            val o = el as JsonObject
            val variants = (o["variants"] as JsonArray).map { v ->
                v as JsonObject
                StreamVariant(v.s("url")!!, v.s("codec"), v.i("bitrate") ?: 0, v.b("hls") ?: false, v.s("uuid"))
            }
            require(variants.isNotEmpty())
            Station(
                id = o.s("id")!!, name = o.s("name")!!, variants = variants,
                homepage = o.s("homepage"), favicon = o.s("favicon"),
                tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                country = o.s("country"), countryCode = o.s("cc"), language = o.s("lang"),
                votes = o.i("votes") ?: 0, ok = o.b("ok") ?: true, imported = o.b("imported") ?: false,
            )
        }.getOrNull()

        /** Acepta una copia de Radiostr (con cabecera) o una biblioteca a secas; null si no lo es. */
        fun fromBackup(o: JsonObject?): Library? {
            if (o == null) return null
            val lib = (o["library"] as? JsonObject) ?: o.takeIf { it.containsKey("favorites") || it.containsKey("folders") } ?: return null
            return fromJson(lib)
        }

        /** Tolerante: lo que no se entienda se descarta, nunca se pierde el resto. */
        fun fromJson(o: JsonObject?): Library {
            if (o == null) return Library()
            val folders = (o["folders"] as? JsonArray).orEmpty().mapNotNull { el ->
                val f = el as? JsonObject ?: return@mapNotNull null
                Folder(f.s("id") ?: return@mapNotNull null, f.s("name") ?: return@mapNotNull null, f.s("parent"))
            }
            val ids = folders.map { it.id }.toSet()
            val favorites = (o["favorites"] as? JsonArray).orEmpty().mapNotNull { el ->
                val f = el as? JsonObject ?: return@mapNotNull null
                val kind = runCatching { FavItem.Kind.valueOf(f.s("kind")!!) }.getOrNull() ?: return@mapNotNull null
                val station = stationFrom(f["station"])
                if (kind == FavItem.Kind.STATION && station == null) return@mapNotNull null
                FavItem(
                    key = f.s("key") ?: return@mapNotNull null, kind = kind, title = f.s("title").orEmpty(),
                    subtitle = f.s("subtitle"), image = f.s("image"),
                    folderId = f.s("folder")?.takeIf { it in ids }, station = station, hostPubkey = f.s("host"),
                    addedAt = (f["added"] as? JsonPrimitive)?.longOrNull ?: 0,
                )
            }.distinctBy { it.key }
            val imported = (o["imported"] as? JsonArray).orEmpty().mapNotNull { stationFrom(it) }.distinctBy { it.id }
            // Carpetas con padre inexistente pasan a la raíz.
            return Library(folders.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it }, favorites, imported)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
