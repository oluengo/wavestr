package io.github.oluengo.radiostr

import android.app.Application
import android.content.Context
import io.github.oluengo.radiostr.data.LibraryRepository
import io.github.oluengo.radiostr.data.SettingsRepository
import io.github.oluengo.radiostr.radio.RadioBrowserClient
import io.github.oluengo.radiostr.nostr.NostrRepository
import io.github.oluengo.radiostr.nostr.RelayPool
import io.github.oluengo.radiostr.player.HlsInspector
import io.github.oluengo.radiostr.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RadiostrApp : Application(), coil.ImageLoaderFactory {
    /** Coil con soporte SVG (muchas webs de radio solo tienen el logo en SVG). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .crossfade(true)
        .build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Aplicar el tema guardado antes de crear la primera Activity (evita un parpadeo).
        val theme = runBlocking { container.settings.current().theme }
        SettingsRepository.applyNightMode(theme)
    }
}

/** Dependencias de la app, sin framework de inyección (ver docs/decisiones/0002). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    val settings = SettingsRepository(app)
    val relays = RelayPool(http)
    val nostr = NostrRepository(relays)
    val radio = RadioBrowserClient(http, appScope)
    val library = LibraryRepository(app, appScope)
    val favSync = io.github.oluengo.radiostr.data.FavoritesSync(app, settings, library, nostr, relays, appScope).also { it.start() }
    val artwork = io.github.oluengo.radiostr.radio.ArtworkResolver(app, http)
    val wallet = io.github.oluengo.radiostr.data.WalletRepository(app, relays)
    val zaps = io.github.oluengo.radiostr.nostr.ZapClient(http)
    val player = PlayerConnection(app, settings, HlsInspector(http), appScope, onStationPlay = radio::countClick)
}

val Context.container: AppContainer get() = (applicationContext as RadiostrApp).container
