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

// ─── 路由常量 ─────────────────────────────────────────────────────────────────
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

    // 全屏播放器时隐藏底部所有 UI
    val showBottomUi = currentRoute != Routes.PLAYER

    Scaffold(
        // ✅ 不在 Scaffold 的 bottomBar 里塞内容
        // 改为在 NavHost 外层手动叠加，防止 Scaffold 的 padding 影响全屏播放器
        bottomBar = {}
    ) { _ ->  // 不使用 paddingValues，自己管理 padding
        Box(modifier = Modifier.fillMaxSize()) {
            // ── NavHost（全屏，不受 Scaffold padding 影响）───────────────
            NavHost(
                navController    = navController,
                startDestination = Routes.LIBRARY,
                modifier         = Modifier.fillMaxSize()
            ) {
                composable(Routes.LIBRARY) {
                    // 给 LibraryScreen 底部留出迷你播放栏的空间
                    Box(
                        modifier = Modifier.padding(
                            bottom = if (currentSong != null) 80.dp else 0.dp
                        )
                    ) {
                        LibraryScreen(
                            viewModel   = viewModel,
                            onSongClick = { songs, index ->
                                viewModel.playSong(songs, index)
                                navController.navigate(Routes.PLAYER)
                            }
                        )
                    }
                }
                composable(Routes.FOLDERS) {
                    Box(
                        modifier = Modifier.padding(
                            bottom = if (currentSong != null) 80.dp else 0.dp
                        )
                    ) {
                        FolderScreen(viewModel = viewModel)
                    }
                }
                // 全屏播放器不加任何额外 padding
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack    = { navController.popBackStack() }
                    )
                }
            }

            // ── 底部 UI（迷你播放栏 + 导航栏），叠加在 NavHost 上方 ───────
            AnimatedVisibility(
                visible = showBottomUi,
                enter   = fadeIn() + slideInVertically { it },
                exit    = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            ) {
                Column {
                    // 迷你播放栏
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
                                icon     = { Icon(tab.icon, contentDescription = tab.label) },
                                label    = {
                                    Text(
                                        tab.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors   = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}