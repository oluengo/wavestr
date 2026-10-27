package io.github.oluengo.wavestr.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Textos de la app desde los strings.xml de Android (ca/es/en): mismas claves y traducciones.
 * Sin elección, el idioma del sistema si es catalán o castellano; si no, inglés.
 */
object I18n {
    val supported = listOf("ca", "es", "en")
    private var strings: Map<String, String> = emptyMap()
    private var plurals: Map<String, Map<String, String>> = emptyMap()
    var locale: Locale = Locale.ENGLISH
        private set
    /** Cambia al cambiar de idioma: la UI que lo lee se vuelve a dibujar. */
    private var version by mutableIntStateOf(0)

    fun load(choice: String?) {
        val lang = (choice ?: Locale.getDefault().language).takeIf { it in supported } ?: "en"
        val base = parse("values")
        val local = if (lang == "en") Parsed(emptyMap(), emptyMap()) else parse("values-$lang")
        strings = base.strings + local.strings
        plurals = base.plurals + local.plurals
        locale = Locale.forLanguageTag(lang)
        version++
    }

    operator fun get(key: String): String {
        version
        return strings[key] ?: key
    }

    fun format(key: String, vararg args: Any?): String = String.format(locale, get(key), *args)

    fun plural(key: String, n: Int, vararg args: Any?): String {
        version
        val forms = plurals[key] ?: return n.toString()
        val f = (if (n == 1) forms["one"] else null) ?: forms["other"] ?: forms.values.first()
        return String.format(locale, f, *args)
    }

    private class Parsed(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun parse(dir: String): Parsed = runCatching {
        val stream = I18n::class.java.getResourceAsStream("/i18n/$dir/strings.xml") ?: return Parsed(emptyMap(), emptyMap())
        val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val s = HashMap<String, String>()
        val sn = doc.getElementsByTagName("string")
        for (i in 0 until sn.length) {
            val e = sn.item(i) as Element
            s[e.getAttribute("name")] = unescape(e.textContent)
        }
        val p = HashMap<String, Map<String, String>>()
        val pn = doc.getElementsByTagName("plurals")
        for (i in 0 until pn.length) {
            val e = pn.item(i) as Element
            val items = e.getElementsByTagName("item")
            p[e.getAttribute("name")] = (0 until items.length).associate { j ->
                val it = items.item(j) as Element
                it.getAttribute("quantity") to unescape(it.textContent)
            }
        }
        Parsed(s, p)
    }.getOrDefault(Parsed(emptyMap(), emptyMap()))

    /** Escapes de Android: \' \" \n \@ \? y comillas de bloque. */
    private fun unescape(raw: String): String {
        var t = raw.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length - 1)
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length) {
                when (val n = t[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t')
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}

/** Atajos para la UI. */
fun t(key: String): String = I18n[key]
fun t(key: String, vararg args: Any?): String = I18n.format(key, *args)
