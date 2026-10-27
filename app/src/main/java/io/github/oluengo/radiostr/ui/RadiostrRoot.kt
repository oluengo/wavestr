package io.github.oluengo.radiostr.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
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
import io.github.oluengo.radiostr.AppContainer
import io.github.oluengo.radiostr.R
import io.github.oluengo.radiostr.ui.favorites.FavoritesScreen
import io.github.oluengo.radiostr.ui.profile.MeScreen
import io.github.oluengo.radiostr.ui.radio.RadioScreen
import io.github.oluengo.radiostr.ui.live.LiveScreen
import io.github.oluengo.radiostr.ui.player.MiniPlayer
import io.github.oluengo.radiostr.ui.player.PlayerScreen
import io.github.oluengo.radiostr.ui.profile.ProfileScreen
import io.github.oluengo.radiostr.ui.settings.SettingsScreen

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    LIVE("live", R.string.tab_live, Icons.Outlined.Sensors),
    RADIO("radio", R.string.tab_radio, Icons.Outlined.Radio),
    FAVORITES("favorites", R.string.tab_favorites, Icons.Outlined.FavoriteBorder),
    SETTINGS("settings", R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun RadiostrRoot(c: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val player by c.player.state.collectAsStateWithLifecycle()
    val onTab = Tab.entries.any { it.route == route }
    io.github.oluengo.radiostr.ui.favorites.AmberRequestHost(c)
    io.github.oluengo.radiostr.ui.favorites.SyncAskDialog(c)

    Scaffold(
        bottomBar = {
            if (onTab) Column {
                if (player.nowPlaying != null) {
                    MiniPlayer(player, onOpen = { nav.navigate("player") }, onToggle = c.player::togglePlay)
                }
                NavigationBar {
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
                LiveScreen(c, onPlay = { c.player.play(it); nav.navigate("player") }, onSignIn = { nav.navigate("me") }, onOpenMe = { nav.navigate("me") })
            }
            composable(Tab.RADIO.route) {
                RadioScreen(c, onOpenPlayer = { nav.navigate("player") }, onOpenMe = { nav.navigate("me") })
            }
            composable(Tab.FAVORITES.route) {
                FavoritesScreen(
                    c, onOpenPlayer = { nav.navigate("player") },
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
                    onPlay = { c.player.play(it); nav.navigate("player") { popUpTo("player") { inclusive = true } } },
                )
            }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
