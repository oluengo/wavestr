package io.github.oluengo.wavestr.data

import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.NostrJson
import io.github.oluengo.wavestr.radio.Station
import io.github.oluengo.wavestr.radio.StreamVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Algo compartido: una emisora o streamer, una carpeta (con sus subcarpetas) o todos los favoritos.
 * Viaja comprimido dentro de un enlace (`…/s/#código`), así que no pasa por ningún servidor.
 */
data class Shared(
    val kind: Kind,
    /** Nombre de la carpeta, de la emisora o «Favoritos». */
    val name: String,
    val folders: List<Folder>,
    val items: List<FavItem>,
) {
    enum class Kind(val code: String) { ITEM("i"), FOLDER("f"), ALL("a") }

    val stations: Int get() = items.count { it.kind == FavItem.Kind.STATION }
    val streamers: Int get() = items.count { it.kind == FavItem.Kind.STREAMER }

    /** Como biblioteca (con los ids originales), para la M3U y para recorrer las carpetas. */
    fun asLibrary() = Library(folders, items)

    // ---------- Enlace ----------

    fun toCode(): String {
        val json = buildJsonObject {
            put("v", 1); put("k", kind.code); put("n", name)
            if (folders.isNotEmpty()) putJsonArray("f") {
                folders.forEach { f -> addJsonObject { put("i", f.id); put("n", f.name); f.parentId?.let { put("p", it) } } }
            }
            putJsonArray("x") {
                items.forEach { it ->
                    addJsonObject {
                        put("t", it.title)
                        it.folderId?.let { f -> put("f", f) }
                        val st = it.station
                        if (st != null) put("s", compact(st))
                        else {
                            it.hostPubkey?.let { h -> put("h", h) }
                            it.image?.let { i -> put("i", i) }
                        }
                    }
                }
            }
        }.toString()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(json.toByteArray()))
    }

    fun link(): String = "$LINK_BASE#${toCode()}"

    // ---------- Texto ----------

    /** Texto legible: nombre y dirección de cada emisora (y enlace de cada streamer), por carpetas. */
    fun toText(): String {
        val lib = asLibrary()
        val sb = StringBuilder()
        fun line(i: FavItem) {
            val url = i.station?.best?.url ?: i.hostPubkey?.let { "https://njump.me/" + Nip19.hexToNpub(it) } ?: return
            sb.append("• ").append(i.title).append(" — ").append(url).append('\n')
        }
        if (kind == Kind.ITEM) {
            items.firstOrNull()?.let { i ->
                sb.append(i.title).append('\n')
                (i.station?.best?.url ?: i.hostPubkey?.let { "https://njump.me/" + Nip19.hexToNpub(it) })?.let { sb.append(it).append('\n') }
            }
            return sb.toString().trimEnd()
        }
        sb.append(name).append('\n')
        lib.itemsIn(null).forEach(::line)
        fun walk(parent: String?) {
            lib.subfolders(parent).forEach { f ->
                val inside = lib.itemsIn(f.id)
                if (inside.isNotEmpty()) {
                    sb.append('\n').append(lib.path(f.id).joinToString(" / ") { it.name }).append('\n')
                    inside.forEach(::line)
                }
                walk(f.id)
            }
        }
        walk(null)
        return sb.toString().trimEnd()
    }

    /** Lista M3U de las emisoras (los streamers no caben en una M3U). */
    fun toM3u(): String = asLibrary().toM3u()

    /**
     * Añade a [lib] los elementos de [keys]. Una carpeta compartida se crea de nuevo en la raíz (con sus
     * subcarpetas); «todos los favoritos» van dentro de una carpeta nueva llamada [wrapperName]; una
     * emisora suelta, a la raíz. Solo se crean las carpetas que acaban teniendo algo. Lo que ya es
     * favorito se deja como está.
     */
    fun importInto(lib: Library, keys: Set<String>, wrapperName: String, newId: () -> String): Library {
        val chosen = items.filter { it.key in keys && !lib.isFavorite(it.key) }
        if (chosen.isEmpty()) return lib
        val src = asLibrary()
        val ids = HashMap<String, String>()
        var out = lib
        val wrapper: String? = if (kind == Kind.ALL) newId().also { out = out.addFolder(it, wrapperName, null) } else null
        // Carpetas necesarias: las de los elementos elegidos y todos sus padres.
        val needed = chosen.mapNotNull { it.folderId }.flatMap { f -> src.path(f).map { it.id } }.toSet()
        fun create(parent: String?, newParent: String?) {
            src.subfolders(parent).filter { it.id in needed }.forEach { f ->
                val id = newId().also { ids[f.id] = it }
                out = out.addFolder(id, f.name, newParent)
                create(f.id, id)
            }
        }
        create(null, wrapper)
        val now = System.currentTimeMillis()
        chosen.forEach { i -> out = out.add(i.copy(folderId = i.folderId?.let { ids[it] } ?: wrapper, addedAt = now)) }
        return out
    }

    companion object {
        const val LINK_BASE = "https://oluengo.github.io/wavestr/s/"

        fun ofItem(item: FavItem) = Shared(Kind.ITEM, item.title, emptyList(), listOf(item.copy(folderId = null)))

        /** La carpeta y todo lo que cuelga de ella; la carpeta queda en la raíz. */
        fun ofFolder(lib: Library, folderId: String): Shared? {
            val root = lib.folder(folderId) ?: return null
            val tree = listOf(root.copy(parentId = null)) + lib.tree(folderId).map { it.first }
            val ids = tree.map { it.id }.toSet()
            return Shared(Kind.FOLDER, root.name, tree, lib.favorites.filter { it.folderId in ids })
        }

        fun ofAll(lib: Library, name: String) = Shared(Kind.ALL, name, lib.folders, lib.favorites)

        /** Saca el código de un enlace de Wavestr (https o wavestr://), o null si no lo es. */
        fun codeFrom(uri: String): String? {
            val u = uri.trim()
            val ok = u.startsWith("https://oluengo.github.io/wavestr/s", true) || u.startsWith("wavestr://", true)
            if (!ok) return null
            return u.substringAfter('#', "").takeIf { it.isNotBlank() }
        }

        /** Tolerante: lo que no se entienda se descarta; null si el código no vale. */
        fun decode(code: String): Shared? = runCatching {
            val bytes = inflate(Base64.getUrlDecoder().decode(code.trim().trimEnd('=')))
            val o = NostrJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject
            val kind = Kind.entries.firstOrNull { it.code == o.s("k") } ?: Kind.ALL
            val folders = (o["f"] as? JsonArray).orEmpty().mapNotNull { el ->
                val f = el as? JsonObject ?: return@mapNotNull null
                Folder(f.s("i") ?: return@mapNotNull null, f.s("n")?.take(40) ?: return@mapNotNull null, f.s("p"))
            }
            val fids = folders.map { it.id }.toSet()
            val items = (o["x"] as? JsonArray).orEmpty().take(2000).mapNotNull { el ->
                val x = el as? JsonObject ?: return@mapNotNull null
                val folder = x.s("f")?.takeIf { it in fids }
                val st = expand(x["s"] as? JsonObject)
                when {
                    st != null -> FavItem.station(st).copy(title = x.s("t") ?: st.name, folderId = folder)
                    x.s("h") != null -> {
                        val h = Nip19.normalizePubkey(x.s("h")!!) ?: return@mapNotNull null
                        FavItem.streamer(h, x.s("t") ?: Nip19.shortNpub(h), x.s("i")).copy(folderId = folder)
                    }
                    else -> null
                }
            }.distinctBy { it.key }
            if (items.isEmpty()) return null
            Shared(kind, o.s("n") ?: "", folders.map { if (it.parentId != null && it.parentId !in fids) it.copy(parentId = null) else it }, items)
        }.getOrNull()

        private fun compact(s: Station) = buildJsonObject {
            put("id", s.id); put("n", s.name)
            putJsonArray("v") {
                s.variants.forEach { v ->
                    addJsonObject {
                        put("u", v.url); v.codec?.let { put("c", it) }
                        if (v.bitrate > 0) put("b", v.bitrate)
                        if (v.hls) put("h", true)
                        v.uuid?.let { put("id", it) }
                    }
                }
            }
            s.favicon?.let { put("fv", it) }; s.homepage?.let { put("hp", it) }
            if (s.tags.isNotEmpty()) putJsonArray("t") { s.tags.take(6).forEach { add(JsonPrimitive(it)) } }
            s.country?.let { put("co", it) }; s.countryCode?.let { put("cc", it) }; s.language?.let { put("l", it) }
            if (s.imported) put("im", true)
        }

        private fun expand(o: JsonObject?): Station? = runCatching {
            o ?: return null
            val variants = (o["v"] as JsonArray).mapNotNull { el ->
                val v = el as? JsonObject ?: return@mapNotNull null
                val url = v.s("u")?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) } ?: return@mapNotNull null
                StreamVariant(url, v.s("c"), (v["b"] as? JsonPrimitive)?.intOrNull ?: 0, (v["h"] as? JsonPrimitive)?.booleanOrNull ?: false, v.s("id"))
            }
            if (variants.isEmpty()) return null
            Station(
                id = o.s("id") ?: return null, name = o.s("n") ?: return null, variants = variants,
                favicon = o.s("fv"), homepage = o.s("hp"),
                tags = (o["t"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                country = o.s("co"), countryCode = o.s("cc"), language = o.s("l"),
                imported = (o["im"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }.getOrNull()

        private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

        private fun deflate(data: ByteArray): ByteArray {
            val d = Deflater(Deflater.BEST_COMPRESSION, true)
            d.setInput(data); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }

        private fun inflate(data: ByteArray): ByteArray {
            val i = Inflater(true)
            i.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!i.finished()) {
                val n = i.inflate(buf)
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break
                out.write(buf, 0, n)
                require(out.size() < 4_000_000)
            }
            i.end()
            return out.toByteArray()
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
