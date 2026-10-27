package io.github.oluengo.wavestr.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.github.oluengo.wavestr.desktop.DesktopContainer
import io.github.oluengo.wavestr.desktop.NowPlaying
import io.github.oluengo.wavestr.desktop.t
import io.github.oluengo.wavestr.nostr.LiveStream
import io.github.oluengo.wavestr.nostr.Nip19
import io.github.oluengo.wavestr.nostr.Profile

enum class Tab(val label: String, val icon: ImageVector) {
    LIVE("tab_live", Icons.Outlined.Sensors),
    RADIO("tab_radio", Icons.Outlined.Radio),
    FAVORITES("tab_favorites", Icons.Outlined.FavoriteBorder),
    SETTINGS("tab_settings", Icons.Outlined.Settings),
}

/** Acciones de navegación que necesitan las pantallas. */
class Nav(val openPlayer: () -> Unit, val openAccount: () -> Unit, val me: Pair<String?, Profile?>)

val LocalNav = staticCompositionLocalOf { Nav({}, {}, null to null) }

private enum class Layout { COMPACT, MEDIUM, EXPANDED }

fun liveNowPlaying(s: LiveStream, host: Profile?) = NowPlaying(
    id = s.address, url = s.streamingUrl!!, title = s.title.ifBlank { t("untitled_stream") },
    subtitle = host?.bestName ?: Nip19.shortNpub(s.hostPubkey),
    artworkUrl = s.image ?: host?.picture, hostPubkey = s.hostPubkey, isLive = true,
)

@Composable
fun WavestrDesktop(c: DesktopContainer) {
    val s by c.settings.flow.collectAsState()
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.crossfade(true).build()
    }
    val me by produceState<Profile?>(null, s.pubkey) {
        value = s.pubkey?.let { pk -> runCatching { c.nostr.profiles(listOf(pk), pk)[pk] }.getOrNull() }
    }

    WavestrTheme(s.theme, s.accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val layout = when { maxWidth >= 1000.dp -> Layout.EXPANDED; maxWidth >= 600.dp -> Layout.MEDIUM; else -> Layout.COMPACT }
                var tab by remember { mutableStateOf(Tab.LIVE) }
                var playerOpen by remember { mutableStateOf(false) }
                if (layout == Layout.EXPANDED && playerOpen) LaunchedEffect(Unit) { playerOpen = false }
                val nav = Nav(
                    openPlayer = { if (layout != Layout.EXPANDED) playerOpen = true },
                    openAccount = { tab = Tab.SETTINGS; playerOpen = false },
                    me = s.pubkey to me,
                )
                val ps by c.player.state.collectAsState()

                CompositionLocalProvider(LocalNav provides nav) {
                    Row(Modifier.fillMaxSize()) {
                        if (layout != Layout.COMPACT) {
                            NavigationRail(Modifier.fillMaxHeight()) {
                                Spacer(Modifier.height(12.dp))
                                Tab.entries.forEach { tb ->
                                    NavigationRailItem(
                                        selected = tab == tb && !playerOpen,
                                        onClick = { tab = tb; playerOpen = false },
                                        icon = { Icon(tb.icon, null) },
                                        label = { Text(t(tb.label)) },
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (!ps.available) VlcMissing()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if (playerOpen) PlayerPanel(c, onClose = { playerOpen = false })
                                else when (tab) {
                                    Tab.LIVE -> LiveScreen(c)
                                    Tab.RADIO -> RadioScreen(c)
                                    Tab.FAVORITES -> FavoritesScreen(c)
                                    Tab.SETTINGS -> SettingsScreen(c)
                                }
                            }
                            val np = ps.nowPlaying
                            if (np != null && !playerOpen && layout != Layout.EXPANDED) {
                                MiniPlayer(np, ps.isPlaying, onOpen = { playerOpen = true }, onToggle = c.player::togglePlay)
                            }
                            if (layout == Layout.COMPACT) NavigationBar {
                                Tab.entries.forEach { tb ->
                                    NavigationBarItem(
                                        selected = tab == tb && !playerOpen,
                                        onClick = { tab = tb; playerOpen = false },
                                        icon = { Icon(tb.icon, null) },
                                        label = { Text(t(tb.label)) },
                                    )
                                }
                            }
                        }
                        if (layout == Layout.EXPANDED) {
                            VerticalDivider()
                            Box(Modifier.width(380.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
                                PlayerPanel(c, onClose = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VlcMissing() {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().background(Rs.colors.zapSoft).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t("desk_vlc_missing"), color = Rs.colors.zap, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { runCatching { uri.openUri("https://www.videolan.org/vlc/") } }) { Text("videolan.org") }
    }
}

@Composable
private fun MiniPlayer(np: NowPlaying, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Column {
        HorizontalDivider(color = Rs.colors.line)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Artwork(np.artworkUrl, np.id, np.title, Modifier.size(40.dp), corner = 8.dp)
            Column(Modifier.weight(1f)) {
                Text(np.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np.subtitle, color = Rs.colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (np.isLive) LiveBadge()
            FilledIconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, t(if (playing) "pause" else "play"))
            }
        }
    }
}

/** Dado y avatar, en la cabecera de cada pestaña. */
@Composable
fun HeaderActions(c: DesktopContainer) {
    val nav = LocalNav.current
    SurpriseButton(c)
    MeButton(nav.me.first, nav.me.second, nav.openAccount)
}
