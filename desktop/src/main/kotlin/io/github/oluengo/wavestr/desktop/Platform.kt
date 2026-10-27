package io.github.oluengo.wavestr.desktop

import io.github.oluengo.wavestr.data.KeyValueStore
import java.io.File
import java.util.Properties

/** Versión y canal, generados en la compilación (wavestr.properties). */
object AppInfo {
    private val props = Properties().apply {
        AppInfo::class.java.getResourceAsStream("/wavestr.properties")?.use { load(it) }
    }
    val version: String = props.getProperty("version", "dev")
    val beta: Boolean = props.getProperty("beta", "false").toBoolean()
    val name: String = props.getProperty("name", "Wavestr")
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** Carpeta de datos: %APPDATA%\Wavestr en Windows, ~/.config/wavestr en Linux (XDG). */
    val dataDir: File by lazy {
        val folder = if (beta) "wavestr-beta" else "wavestr"
        val base = if (isWindows) System.getenv("APPDATA")?.let(::File)?.let { File(it, if (beta) "Wavestr Beta" else "Wavestr") }
        else (System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".config")).let { File(it, folder) }
        (base ?: File(System.getProperty("user.home"), ".$folder")).apply { mkdirs() }
    }
}

/** [KeyValueStore] en un archivo .properties; se guarda en cada cambio. */
class FileStore(private val file: File) : KeyValueStore {
    private val props = Properties()

    init {
        runCatching { if (file.exists()) file.inputStream().use { props.load(it) } }
    }

    @Synchronized
    override fun get(key: String): String? = props.getProperty(key)

    @Synchronized
    override fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { props.store(it, null) }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
