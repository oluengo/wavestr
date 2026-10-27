package io.github.oluengo.wavestr.radio

import java.security.MessageDigest

/** Lector de listas de radio: PLS, M3U/M3U8 extendido o una URL de stream suelta. */
object Playlists {
    enum class Kind { PLS, M3U, URL }

    data class Item(val title: String, val url: String)

    sealed interface Result {
        data class Found(val kind: Kind, val items: List<Item>) : Result
        /** Es un enlace a una lista (.pls/.m3u): hay que descargarla y volver a leer. */
        data class NeedsDownload(val url: String) : Result
        /** Es el contenido de un stream HLS, no una lista de emisoras. */
        data object HlsContent : Result
        data object Empty : Result
    }

    private val PLAYLIST_URL = Regex("^https?://\\S+\\.(pls|m3u8?)(\\?\\S*)?$", RegexOption.IGNORE_CASE)
    private val STREAM_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

    fun isPlaylistUrl(s: String) = PLAYLIST_URL.matches(s.trim())

    fun parse(input: String): Result {
        val text = input.replace("\r", "").trim().removePrefix("﻿")
        if (text.isEmpty()) return Result.Empty
        if (!text.contains('\n')) {
            if (PLAYLIST_URL.matches(text) && !text.lowercase().contains(".m3u8")) return Result.NeedsDownload(text)
            if (STREAM_URL.matches(text)) return Result.Found(Kind.URL, listOf(Item(Stations.host(text) ?: text, text)))
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val items = mutableListOf<Item>()
        val kind: Kind
        if (lines.first().equals("[playlist]", ignoreCase = true)) {
            kind = Kind.PLS
            val files = sortedMapOf<Int, String>()
            val titles = mutableMapOf<Int, String>()
            val file = Regex("^File(\\d+)=(.+)$", RegexOption.IGNORE_CASE)
            val title = Regex("^Title(\\d+)=(.*)$", RegexOption.IGNORE_CASE)
            lines.forEach { l ->
                file.find(l)?.let { files[it.groupValues[1].toInt()] = it.groupValues[2].trim() }
                title.find(l)?.let { titles[it.groupValues[1].toInt()] = it.groupValues[2].trim() }
            }
            files.forEach { (n, url) -> items += Item(titles[n]?.takeIf { it.isNotBlank() } ?: Stations.host(url) ?: url, url) }
        } else {
            kind = Kind.M3U
            if (lines.any { it.startsWith("#EXT-X-STREAM-INF") || it.startsWith("#EXT-X-TARGETDURATION") }) return Result.HlsContent
            var pending: String? = null
            lines.forEach { l ->
                when {
                    l.startsWith("#EXTINF", ignoreCase = true) -> pending = l.substringAfter(',', "").trim().ifEmpty { null }
                    l.startsWith("#") -> Unit
                    STREAM_URL.matches(l) -> { items += Item(pending ?: Stations.host(l) ?: l, l); pending = null }
                }
            }
        }
        val clean = items.filter { STREAM_URL.matches(it.url) }.distinctBy { it.url }
        return if (clean.isEmpty()) Result.Empty else Result.Found(kind, clean)
    }

    /** Emisora local a partir de un elemento importado (id estable según la URL). */
    fun toStation(item: Item, kind: Kind): Station {
        val hash = MessageDigest.getInstance("SHA-256").digest(item.url.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val codec = when {
            item.url.contains(".m3u8", true) -> "HLS"
            item.url.contains(".aac", true) -> "AAC"
            item.url.contains(".mp3", true) -> "MP3"
            item.url.contains(".ogg", true) || item.url.contains(".opus", true) -> "OGG"
            else -> null
        }
        return Station(
            id = "imp:$hash",
            name = item.title.take(80),
            variants = listOf(StreamVariant(item.url, codec, 0, hls = codec == "HLS")),
            homepage = Stations.host(item.url),
            tags = listOf(kind.name.lowercase()),
            imported = true,
        )
    }
}
