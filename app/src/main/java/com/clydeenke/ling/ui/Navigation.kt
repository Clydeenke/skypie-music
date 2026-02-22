package com.clydeenke.ling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.clydeenke.ling.ui.components.MiniPlayer
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.ui.screen.player.PlayerScreen
import com.clydeenke.ling.viewmodel.MusicViewModel

object Routes {
    const val LIBRARY = "library"
    const val FOLDERS = "folders"
    const val PLAYER  = "player"
}

private sealed class Tab(
    val route : String,
    val label : String,
    val icon  : androidx.compose.ui.graphics.vector.ImageVector
) {
    object Library : Tab(Routes.LIBRARY, "音乐库", Icons.Rounded.LibraryMusic)
    object Folders : Tab(Routes.FOLDERS, "文件夹", Icons.Rounded.FolderOpen)
}

private val tabs = listOf(Tab.Library, Tab.Folders)

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val viewModel     : MusicViewModel = hiltViewModel()
    val currentSong   by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    val showBottomBar = currentRoute != Routes.PLAYER

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter   = fadeIn() + slideInVertically { it },
                exit    = fadeOut() + slideOutVertically { it }
            ) {
                Column {
                    // 迷你播放栏（有正在播放的歌曲才显示）
                    if (currentSong != null) {
                        MiniPlayer(
                            viewModel     = viewModel,
                            onExpandClick = { navController.navigate(Routes.PLAYER) }
                        )
                    }

                    // 底部导航栏
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            val selected = navBackStack?.destination
                                ?.hierarchy?.any { it.route == tab.route } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick  = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                icon  = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController    = navController,
            startDestination = Routes.LIBRARY,
            modifier         = Modifier.padding(paddingValues)
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    viewModel   = viewModel,
                    onSongClick = { songs, index ->
                        viewModel.playSong(songs, index)
                        navController.navigate(Routes.PLAYER)
                    }
                )
            }
            composable(Routes.FOLDERS) {
                FolderScreen(viewModel = viewModel)
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack    = { navController.popBackStack() }
                )
            }
        }
    }
}