package io.github.oluengo.wavestr.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.oluengo.wavestr.data.LibraryRepository
import io.github.oluengo.wavestr.desktop.ui.WavestrDesktop
import io.github.oluengo.wavestr.nostr.NostrRepository
import io.github.oluengo.wavestr.nostr.RelayPool
import io.github.oluengo.wavestr.radio.ArtworkResolver
import io.github.oluengo.wavestr.radio.RadioBrowserClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.awt.Dimension
import java.io.File
import java.util.concurrent.TimeUnit

/** Dependencias de la app de escritorio (igual que AppContainer en Android). */
class DesktopContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    val settings = SettingsRepository(FileStore(File(AppInfo.dataDir, "settings.properties")))
    val relays = RelayPool(http)
    val nostr = NostrRepository(relays)
    val radio = RadioBrowserClient(http, scope, "${AppInfo.name.replace(' ', '-')}/${AppInfo.version} (github.com/oluengo/wavestr)")
    val library = LibraryRepository(File(AppInfo.dataDir, "library.json"), scope)
    val artwork = ArtworkResolver(FileStore(File(AppInfo.dataDir, "artwork.properties")), http)
    val player = VlcPlayer(scope, onStationPlay = radio::countClick)

    init {
        RelayPool.DEFAULT = settings.current.appRelays ?: RelayPool.BUILTIN
        scope.launch {
            settings.flow.map { it.appRelays }.distinctUntilChanged().collect { list ->
                val next = list ?: RelayPool.BUILTIN
                if (next != RelayPool.DEFAULT) { RelayPool.DEFAULT = next; nostr.clearRelayCaches() }
            }
        }
    }
}

fun main() {
    val c = DesktopContainer()
    I18n.load(c.settings.current.language)
    application { WavestrApplication(c) }
}

@OptIn(FlowPreview::class)
@Composable
private fun ApplicationScope.WavestrApplication(c: DesktopContainer) {
    val saved = remember { c.settings.current.window }
    // Primera vez: formato móvil. Después, como se dejó.
    val state = rememberWindowState(
        size = DpSize((saved?.w ?: 420).dp, (saved?.h ?: 820).dp),
        position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
    )
    var visible by remember { mutableStateOf(true) }
    val icon = painterResource("icon.png")
    val ps by c.player.state.collectAsState()

    fun quit() {
        c.player.release()
        exitApplication()
    }

    Tray(
        icon = icon,
        tooltip = AppInfo.name,
        onAction = { visible = true },
        menu = {
            Item(t("desk_tray_show"), onClick = { visible = true })
            if (ps.nowPlaying != null) {
                Item(if (ps.isPlaying) t("pause") else t("play"), onClick = { c.player.togglePlay() })
                Item(t("stop"), onClick = { c.player.stop() })
            }
            Separator()
            Item(t("desk_tray_quit"), onClick = ::quit)
        },
    )

    Window(
        onCloseRequest = {
            val playing = c.player.state.value.nowPlaying != null
            if (c.settings.current.keepInTray && playing) visible = false else quit()
        },
        visible = visible,
        state = state,
        title = AppInfo.name,
        icon = icon,
    ) {
        LaunchedEffect(Unit) { window.minimumSize = Dimension(360, 600) }
        LaunchedEffect(state) {
            snapshotFlow { state.size to state.position }
                .debounce(800)
                .collect { (size, pos) ->
                    val x = if (pos.isSpecified) pos.x.value.toInt() else 100
                    val y = if (pos.isSpecified) pos.y.value.toInt() else 100
                    c.settings.setWindow(WindowBox(x, y, size.width.value.toInt(), size.height.value.toInt()))
                }
        }
        WavestrDesktop(c)
        if (System.getProperty("wavestr.smoke") != null) LaunchedEffect(Unit) { smokeTest(c, window); quit() }
    }
}

/**
 * Prueba de arranque para CI (-Dwavestr.smoke=1, con xvfb): abre la ventana, reproduce una radio y un vídeo
 * HLS unos segundos y deja el resultado como anotaciones de GitHub Actions.
 */
private suspend fun smokeTest(c: DesktopContainer, window: java.awt.Window) {
    fun report(level: String, msg: String) = println("::$level::$msg")
    kotlinx.coroutines.delay(4_000)
    report("notice", "Escritorio: ventana ${window.width}x${window.height} · VLC=${c.player.state.value.available} · idioma=${I18n.locale} · «${t("tab_live")}» / «${t("tab_radio")}»")
    if (!c.player.state.value.available) { report("error", "Escritorio: no se encuentra VLC"); return }
    c.player.play(NowPlaying("smoke-radio", "https://ice2.somafm.com/groovesalad-128-mp3", "SomaFM", "", null, null, isLive = false))
    kotlinx.coroutines.delay(12_000)
    c.player.state.value.let { report(if (it.isPlaying) "notice" else "error", "Escritorio radio: sonando=${it.isPlaying} error=${it.error} canción=${it.icyTitle}") }
    val before = c.player.video.frame.longValue
    c.player.play(NowPlaying("smoke-video", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "HLS", "", null, null, isLive = true))
    kotlinx.coroutines.delay(15_000)
    val frames = c.player.video.frame.longValue - before
    val bmp = c.player.video.bitmap
    c.player.state.value.let { report(if (frames > 10) "notice" else "error", "Escritorio vídeo: sonando=${it.isPlaying} fotogramas=$frames tamaño=${bmp?.width}x${bmp?.height}") }
    runCatching {
        val r = window.bounds
        val img = java.awt.Robot().createScreenCapture(r)
        // Comprobación burda de que se ha pintado algo: número de colores distintos en la captura.
        val colors = HashSet<Int>()
        for (y in 0 until img.height step 7) for (x in 0 until img.width step 7) colors += img.getRGB(x, y)
        report("notice", "Escritorio captura: ${img.width}x${img.height}, ${colors.size} colores distintos")
    }.onFailure { report("warning", "Escritorio captura: ${it.message}") }
}
