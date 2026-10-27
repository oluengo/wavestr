package io.github.oluengo.wavestr.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LoginMethod { NONE, AMBER, READ_ONLY }

/** Colores de acento (los mismos que en la demo). */
enum class SurpriseKind { RADIO, LIVE, BOTH }

enum class Accent { VIOLET, GREEN, CORAL, BLUE, RASPBERRY }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val audioOnScreenOff: Boolean = true,
    val audioOnCellular: Boolean = true,
    val pubkey: String? = null,
    val loginMethod: LoginMethod = LoginMethod.NONE,
    val signerPackage: String? = null,
    val accent: Accent = Accent.VIOLET,
    /** Guardar favoritos en Nostr: null = aún no se ha preguntado. */
    val favSync: Boolean? = null,
    /** Relays de Wavestr; null = los de serie. */
    val appRelays: List<String>? = null,
    val surpriseKind: SurpriseKind = SurpriseKind.BOTH,
    val surpriseFromFavorites: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val audioOff = booleanPreferencesKey("audio_on_screen_off")
        val audioCell = booleanPreferencesKey("audio_on_cellular")
        val pubkey = stringPreferencesKey("pubkey")
        val login = stringPreferencesKey("login_method")
        val signer = stringPreferencesKey("signer_package")
        val accent = stringPreferencesKey("accent")
        val favSync = booleanPreferencesKey("fav_sync")
        val relays = stringPreferencesKey("app_relays")
        val surpriseKind = stringPreferencesKey("surprise_kind")
        val surpriseFav = booleanPreferencesKey("surprise_fav")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            audioOnScreenOff = p[K.audioOff] ?: true,
            audioOnCellular = p[K.audioCell] ?: true,
            pubkey = p[K.pubkey],
            loginMethod = p[K.login]?.let { runCatching { LoginMethod.valueOf(it) }.getOrNull() } ?: LoginMethod.NONE,
            signerPackage = p[K.signer],
            accent = p[K.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.VIOLET,
            favSync = p[K.favSync],
            appRelays = p[K.relays]?.split('\n')?.filter { it.isNotBlank() },
            surpriseKind = p[K.surpriseKind]?.let { runCatching { SurpriseKind.valueOf(it) }.getOrNull() } ?: SurpriseKind.BOTH,
            surpriseFromFavorites = p[K.surpriseFav] ?: false,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[K.theme] = mode.name }
        applyNightMode(mode)
    }

    suspend fun setAccent(a: Accent) = context.dataStore.edit { it[K.accent] = a.name }
    /** null = volver a los de serie. */
    suspend fun setAppRelays(list: List<String>?) = context.dataStore.edit {
        if (list == null) it.remove(K.relays) else it[K.relays] = list.joinToString("\n")
    }
    suspend fun setSurprise(kind: SurpriseKind, fromFavorites: Boolean) = context.dataStore.edit {
        it[K.surpriseKind] = kind.name; it[K.surpriseFav] = fromFavorites
    }
    suspend fun setFavSync(v: Boolean) = context.dataStore.edit { it[K.favSync] = v }
    suspend fun setAudioOnScreenOff(v: Boolean) = context.dataStore.edit { it[K.audioOff] = v }
    suspend fun setAudioOnCellular(v: Boolean) = context.dataStore.edit { it[K.audioCell] = v }

    suspend fun login(pubkey: String, method: LoginMethod, signerPackage: String? = null) =
        context.dataStore.edit {
            it[K.pubkey] = pubkey
            it[K.login] = method.name
            if (signerPackage != null) it[K.signer] = signerPackage else it.remove(K.signer)
        }

    suspend fun logout() = context.dataStore.edit {
        it.remove(K.pubkey); it.remove(K.login); it.remove(K.signer); it.remove(K.favSync)
    }

    companion object {
        fun applyNightMode(mode: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
            )
        }
    }
}

/**
 * Idioma de la app (ca/es/en). Sin elección explícita sigue al dispositivo;
 * si el dispositivo usa otro idioma, Android cae en values/ = inglés.
 */
object AppLanguage {
    val supported = listOf("ca", "es", "en")

    /** null = idioma del dispositivo. */
    fun current(): String? {
        val l = AppCompatDelegate.getApplicationLocales()
        return if (l.isEmpty) null else l[0]?.language
    }

    fun set(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }
}
