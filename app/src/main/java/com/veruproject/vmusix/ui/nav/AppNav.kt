package com.veruproject.vmusix.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.veruproject.vmusix.R
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.MiniPlayer
import com.veruproject.vmusix.ui.screens.CacheScreen
import com.veruproject.vmusix.ui.screens.DetailScreen
import com.veruproject.vmusix.ui.screens.DownloadsScreen
import com.veruproject.vmusix.ui.screens.HomeScreen
import com.veruproject.vmusix.ui.screens.LibraryScreen
import com.veruproject.vmusix.ui.screens.PlaylistsScreen
import com.veruproject.vmusix.ui.screens.PlayerScreen
import com.veruproject.vmusix.ui.screens.SearchScreen
import com.veruproject.vmusix.ui.screens.SettingsScreen

/** Rute navigasi. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYER = "player"
    const val DOWNLOADS = "downloads"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val CACHE = "cache"
    const val DETAIL = "detail/{kind}/{id}"

    fun detail(kind: String, id: String) = "detail/$kind/$id"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val vm: AppViewModel = hiltViewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route != Routes.PLAYER

    val currentSong by vm.player.currentSong.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    currentSong?.let { song ->
                        MiniPlayer(
                            title = song.title,
                            artist = song.artist,
                            artwork = song.thumbnail,
                            isPlaying = isPlaying,
                            onClick = { navController.navigate(Routes.PLAYER) },
                            onToggle = { vm.player.togglePlayPause() },
                            onNext = { vm.player.next() },
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == Routes.HOME,
                            onClick = { navController.navigateToTab(Routes.HOME) },
                            icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_home)) },
                        )
                        NavigationBarItem(
                            selected = route == Routes.SEARCH,
                            onClick = { navController.navigateToTab(Routes.SEARCH) },
                            icon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_search)) },
                        )
                        NavigationBarItem(
                            selected = route == Routes.LIBRARY,
                            onClick = { navController.navigateToTab(Routes.LIBRARY) },
                            icon = { Icon(painterResource(R.drawable.ic_library), contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_library)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(vm, navController) }
            composable(Routes.SEARCH) { SearchScreen(vm, navController) }
            composable(Routes.LIBRARY) { LibraryScreen(vm, navController) }
            composable(Routes.PLAYER) { PlayerScreen(vm, navController) }
            composable(Routes.DOWNLOADS) { DownloadsScreen(vm, navController) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(vm, navController) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, navController) }
            composable(Routes.CACHE) { CacheScreen(vm, navController) }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType },
                ),
            ) { entry ->
                DetailScreen(
                    vm = vm,
                    navController = navController,
                    kind = entry.arguments?.getString("kind") ?: "",
                    id = entry.arguments?.getString("id") ?: "",
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
