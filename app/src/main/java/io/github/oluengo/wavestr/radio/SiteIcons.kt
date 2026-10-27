package io.github.oluengo.wavestr.radio

import java.net.URI

/**
 * Busca la mejor imagen de una web: apple-touch-icon (suele ser 180 px), og:image / twitter:image,
 * iconos declarados (los más grandes primero, SVG bien valorado) y, al final, las rutas habituales.
 * Sin dependencias de Android: se prueba en JVM.
 */
object SiteIcons {
    private val TAG = Regex("<(link|meta)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")

    private fun attrs(s: String): Map<String, String> =
        ATTR.findAll(s).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: "").trim()
        }

    private fun resolve(base: String, href: String): String? = runCatching {
        val h = href.trim().replace("&amp;", "&")
        if (h.isEmpty() || h.startsWith("data:")) return null
        val u = URI(base).resolve(if (h.startsWith("//")) "https:$h" else h).toString()
        u.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrNull()

    private fun biggestSize(sizes: String?): Int =
        sizes?.split(' ')?.mapNotNull { it.lowercase().substringBefore('x').toIntOrNull() }?.maxOrNull() ?: 0

    /** Candidatas ordenadas de mejor a peor (sin repetir). */
    fun fromHtml(html: String, pageUrl: String): List<String> {
        val head = html.take(200_000)
        val scored = mutableListOf<Pair<String, Int>>()
        TAG.findAll(head).forEach { m ->
            val a = attrs(m.groupValues[2])
            if (m.groupValues[1].equals("link", true)) {
                val rel = a["rel"]?.lowercase() ?: return@forEach
                val href = a["href"]?.let { resolve(pageUrl, it) } ?: return@forEach
                val size = biggestSize(a["sizes"])
                val isSvg = href.substringBefore('?').endsWith(".svg", true) || a["type"]?.contains("svg") == true
                when {
                    "apple-touch-icon" in rel -> scored += href to 400 + size.coerceAtMost(512)
                    "icon" in rel || "shortcut" in rel -> scored += href to (if (isSvg) 300 else 100) + size.coerceAtMost(512)
                }
            } else {
                val key = (a["property"] ?: a["name"] ?: a["itemprop"])?.lowercase() ?: return@forEach
                val content = a["content"]?.let { resolve(pageUrl, it) } ?: return@forEach
                when (key) {
                    "og:image", "og:image:url", "og:image:secure_url" -> scored += content to 350
                    "twitter:image", "twitter:image:src" -> scored += content to 340
                    "msapplication-tileimage", "image" -> scored += content to 250
                }
            }
        }
        return scored.sortedByDescending { it.second }.map { it.first }.distinct()
    }

    /** Rutas que muchas webs sirven aunque no las declaren. */
    fun conventional(pageUrl: String): List<String> = listOfNotNull(
        resolve(pageUrl, "/apple-touch-icon.png"),
        resolve(pageUrl, "/apple-touch-icon-precomposed.png"),
        resolve(pageUrl, "/favicon.ico"),
    )

    /** Página a consultar para una emisora: su web o, si no tiene, la raíz del servidor del stream. */
    fun pageFor(st: Station): String? {
        val home = st.homepage?.trim()?.takeIf { it.isNotEmpty() }
        if (home != null) return if (home.startsWith("http", true)) home else "https://$home"
        val host = Stations.host(st.best.url) ?: return null
        return "https://$host/"
    }

    /** El favicon del directorio vale si no es un .ico (suelen ser de 16–32 px). */
    fun goodDirectoryIcon(url: String?): Boolean =
        url != null && !url.substringBefore('?').endsWith(".ico", true)
}
