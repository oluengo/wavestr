package io.github.oluengo.wavestr.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.oluengo.wavestr.AppContainer
import io.github.oluengo.wavestr.R
import io.github.oluengo.wavestr.ui.favorites.FavoritesScreen
import io.github.oluengo.wavestr.ui.profile.MeScreen
import io.github.oluengo.wavestr.ui.radio.RadioScreen
import io.github.oluengo.wavestr.ui.live.LiveScreen
import io.github.oluengo.wavestr.ui.player.MiniPlayer
import io.github.oluengo.wavestr.ui.player.PlayerScreen
import io.github.oluengo.wavestr.ui.profile.ProfileScreen
import io.github.oluengo.wavestr.ui.settings.SettingsScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    LIVE("live", R.string.tab_live, Icons.Outlined.Sensors),
    RADIO("radio", R.string.tab_radio, Icons.Outlined.Radio),
    FAVORITES("favorites", R.string.tab_favorites, Icons.Outlined.FavoriteBorder),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

/** Compacto: barra inferior. Mediano (tablet, horizontal): navegación lateral. Ancho: además, reproductor fijo a la derecha. */
private enum class Layout { COMPACT, MEDIUM, EXPANDED }

@Composable
fun WavestrRoot(c: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val player by c.player.state.collectAsStateWithLifecycle()
    val onTab = Tab.entries.any { it.route == route }
    val width = LocalConfiguration.current.screenWidthDp
    val layout = when { width >= 1000 -> Layout.EXPANDED; width >= 600 -> Layout.MEDIUM; else -> Layout.COMPACT }
    // En ancho el reproductor ya está a la vista: no se abre a pantalla completa.
    val openPlayer: () -> Unit = if (layout == Layout.EXPANDED) ({}) else ({ nav.navigate("player") { launchSingleTop = true } })
    if (layout == Layout.EXPANDED && route == "player") LaunchedEffect(Unit) { nav.popBackStack() }

    io.github.oluengo.wavestr.ui.favorites.AmberRequestHost(c)
    io.github.oluengo.wavestr.ui.favorites.SyncAskDialog(c)

    CompositionLocalProvider(io.github.oluengo.wavestr.ui.common.LocalOpenPlayer provides openPlayer) {
        Row(Modifier.fillMaxSize()) {
            if (layout != Layout.COMPACT && (onTab || layout == Layout.EXPANDED)) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (onTab) Column {
                        if (player.nowPlaying != null && layout != Layout.EXPANDED) {
                            MiniPlayer(player, onOpen = openPlayer, onToggle = c.player::togglePlay)
                        }
                        if (layout == Layout.COMPACT) NavigationBar {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = route == t.route,
                                    onClick = { nav.goTab(t.route) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(stringResource(t.label)) },
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                NavHost(nav, startDestination = Tab.LIVE.route, modifier = Modifier.padding(pad)) {
                    composable(Tab.LIVE.route) {
                        LiveScreen(c, onPlay = { c.player.play(it); openPlayer() }, onSignIn = { nav.navigate("me") }, onOpenMe = { nav.navigate("me") })
                    }
                    composable(Tab.RADIO.route) {
                        RadioScreen(c, onOpenPlayer = openPlayer, onOpenMe = { nav.navigate("me") })
                    }
                    composable(Tab.FAVORITES.route) {
                        FavoritesScreen(
                            c, onOpenPlayer = openPlayer,
                            onOpenProfile = { nav.navigate("profile/$it") }, onOpenMe = { nav.navigate("me") },
                        )
                    }
                    composable(Tab.SETTINGS.route) { SettingsScreen(c, onOpenMe = { nav.navigate("me") }) }
                    composable("me") { MeScreen(c, onBack = { nav.popBackStack() }) }
                    composable("player") {
                        PlayerScreen(c, onClose = { nav.popBackStack() }, onOpenProfile = { nav.navigate("profile/$it") })
                    }
                    composable("profile/{pk}") { e ->
                        ProfileScreen(
                            c, pubkey = e.arguments?.getString("pk").orEmpty(),
                            onBack = { nav.popBackStack() },
                            onPlay = {
                                c.player.play(it)
                                if (layout != Layout.EXPANDED) nav.navigate("player") { popUpTo("player") { inclusive = true } }
                            },
                        )
                    }
                }
            }
            if (layout == Layout.EXPANDED) {
                VerticalDivider()
                Box(Modifier.width(380.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    PlayerScreen(c, onClose = null, onOpenProfile = { nav.navigate("profile/$it") })
                }
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
