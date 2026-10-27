package io.github.oluengo.wavestr.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Accent { VIOLET, GREEN, CORAL, BLUE, RASPBERRY }
enum class SurpriseKind { RADIO, LIVE, BOTH }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.VIOLET,
    /** null = idioma del sistema. */
    val language: String? = null,
    /** Sesión en solo lectura con npub (en el escritorio aún no hay firmante). */
    val pubkey: String? = null,
    val appRelays: List<String>? = null,
    /** Al cerrar la ventana, seguir sonando con el icono en la bandeja. */
    val keepInTray: Boolean = true,
    val surpriseKind: SurpriseKind = SurpriseKind.BOTH,
    val surpriseFromFavorites: Boolean = false,
    val window: WindowBox? = null,
    val radioCountry: String? = null,
)

/** Tamaño y posición de la ventana, para abrirla donde se dejó. */
data class WindowBox(val x: Int, val y: Int, val w: Int, val h: Int)

class SettingsRepository(private val store: FileStore) {
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val current: Settings get() = _flow.value

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E =
        store.get(key)?.let { v -> runCatching { enumValueOf<E>(v) }.getOrNull() } ?: default

    private fun read() = Settings(
        theme = enumOr("theme", ThemeMode.SYSTEM),
        accent = enumOr("accent", Accent.VIOLET),
        language = store.get("language")?.takeIf { it in I18n.supported },
        pubkey = store.get("pubkey"),
        appRelays = store.get("relays")?.split('\n')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
        keepInTray = store.get("keep_in_tray")?.toBoolean() ?: true,
        surpriseKind = enumOr("surprise_kind", SurpriseKind.BOTH),
        surpriseFromFavorites = store.get("surprise_fav")?.toBoolean() ?: false,
        window = store.get("window")?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 }?.let { WindowBox(it[0], it[1], it[2], it[3]) },
        radioCountry = store.get("radio_country"),
    )

    private fun set(key: String, value: String?) {
        store.put(key, value)
        _flow.value = read()
    }

    fun setTheme(v: ThemeMode) = set("theme", v.name)
    fun setAccent(v: Accent) = set("accent", v.name)
    fun setLanguage(v: String?) { set("language", v); I18n.load(v) }
    fun login(pubkey: String) = set("pubkey", pubkey)
    fun logout() = set("pubkey", null)
    fun setAppRelays(list: List<String>?) = set("relays", list?.joinToString("\n"))
    fun setKeepInTray(v: Boolean) = set("keep_in_tray", v.toString())
    fun setSurprise(kind: SurpriseKind, fromFavorites: Boolean) { store.put("surprise_kind", kind.name); set("surprise_fav", fromFavorites.toString()) }
    fun setWindow(b: WindowBox) = store.put("window", "${b.x},${b.y},${b.w},${b.h}")
    fun setRadioCountry(code: String?) = set("radio_country", code)
}
