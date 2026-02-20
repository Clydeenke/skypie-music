package com.clydeenke.ling

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clydeenke.ling.ui.Screen
import com.clydeenke.ling.ui.components.MiniPlayer
import com.clydeenke.ling.ui.screens.library.LibraryScreen
import com.clydeenke.ling.ui.screens.player.PlayerScreen
import com.clydeenke.ling.ui.theme.LingTheme
import com.clydeenke.ling.viewmodel.MusicViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    // 1. 权限申请启动器：处理 Android 13+ 细分权限
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.scanMusic() // 拿到了权限就开始扫歌
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 开启 2026 年标配的全屏沉浸式体验

        requestStoragePermission()

        setContent {
            LingTheme { // 使用我们自定义的主题
                MainApp(viewModel = viewModel)
            }
        }
    }

    private fun requestStoragePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO) // Android 13+ 专用
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions)
        } else {
            viewModel.scanMusic()
        }
    }
}

@Composable
fun MainApp(viewModel: MusicViewModel) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val currentSong by viewModel.playerController.currentSong.collectAsState()

    Scaffold(
        bottomBar = {
            // 只有不在全屏播放页时，才显示底部导航和迷你播放条
            if (currentRoute != Screen.PLAYER) {
                Column {
                    if (currentSong != null) {
                        MiniPlayer(
                            viewModel = viewModel,
                            onClick = { navController.navigate(Screen.PLAYER) }
                        )
                    }
                    NavigationBar {
                        val items = listOf(
                            Triple(Screen.LIBRARY, Icons.Rounded.MusicNote, "歌曲"),
                            Triple(Screen.ALBUMS, Icons.Rounded.Album, "专辑"),
                            Triple(Screen.ARTISTS, Icons.Rounded.Person, "歌手")
                        )
                        items.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                icon = { Icon(icon, label) },
                                label = { Text(label) },
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.LIBRARY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LIBRARY) { LibraryScreen(viewModel = viewModel) }

            composable(Screen.ALBUMS) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("专辑页（待开发）")
                }
            }

            composable(Screen.ARTISTS) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("歌手页（待开发）")
                }
            }

            // 播放页：采用从底部滑入的丝滑动画
            composable(
                Screen.PLAYER,
                enterTransition = { slideInVertically(initialOffsetY = { it }) + fadeIn() },
                exitTransition = { slideOutVertically(targetOffsetY = { it }) + fadeOut() }
            ) {
                PlayerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}