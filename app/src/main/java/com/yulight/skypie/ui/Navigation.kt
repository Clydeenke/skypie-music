package com.yulight.skypie

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yulight.skypie.ui.components.SharedPlayerContainer
import com.yulight.skypie.ui.screen.download.DownloadScreen
import com.yulight.skypie.ui.screen.folders.FolderScreen
import com.yulight.skypie.ui.screen.library.LibraryScreen
import com.yulight.skypie.ui.screen.playlist.PlaylistDetailScreen
import com.yulight.skypie.ui.screen.playlist.PlaylistListScreen
import com.yulight.skypie.ui.screen.search.OnlineSearchScreen
import com.yulight.skypie.ui.screen.settings.AboutScreen
import com.yulight.skypie.ui.screen.settings.SettingsScreen
import com.yulight.skypie.viewmodel.MusicViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val openPlayerEvent by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    // Predictive Back 进度状态
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackGestureActive by remember { mutableStateOf(false) }

    // 系统 Predictive Back 手势
    PredictiveBackHandler(enabled = true) { progress ->
        isBackGestureActive = true
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            // 手势完成，执行返回
            navController.popBackStack()
        } catch (_: Exception) {
            // 手势取消
        } finally {
            isBackGestureActive = false
            backProgress = 0f
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            viewModel.savePlaybackProgress()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val hazeState = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {

            // NavHost + Predictive Back 动画
            PredictiveBackNavHost(
                navController = navController,
                viewModel = viewModel,
                hazeState = hazeState,
                backProgress = backProgress,
                isBackGestureActive = isBackGestureActive
            )

            AnimatedVisibility(
                visible  = currentSong != null,
                enter    = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180)),
                exit     = slideOutVertically { it } + fadeOut(tween(130)),
                modifier = Modifier.fillMaxSize()
            ) {
                SharedPlayerContainer(
                    viewModel           = viewModel,
                    openPlayerRequested = openPlayerEvent,
                    onOpenPlayerHandled = { viewModel.consumeOpenPlayer() },
                    hazeState           = hazeState,
                    modifier            = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PredictiveBackNavHost(
    navController: NavHostController,
    viewModel: MusicViewModel,
    hazeState: dev.chrisbanes.haze.HazeState,
    backProgress: Float,
    isBackGestureActive: Boolean
) {
    // 计算动画值
    val currentScale by animateFloatAsState(
        targetValue = if (isBackGestureActive) 1f - backProgress * 0.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "currentScale"
    )
    val currentTranslationX by animateFloatAsState(
        targetValue = if (isBackGestureActive) backProgress * 100f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "currentTranslationX"
    )
    val previousScale by animateFloatAsState(
        targetValue = if (isBackGestureActive) 0.95f + backProgress * 0.05f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "previousScale"
    )
    val previousTranslationX by animateFloatAsState(
        targetValue = if (isBackGestureActive) -30f * (1f - backProgress) else -30f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "previousTranslationX"
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isBackGestureActive) backProgress * 0.3f else 0f,
        animationSpec = tween(200),
        label = "scrimAlpha"
    )

    NavHost(
        navController = navController,
        startDestination = "library",
        modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(200))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(tween(150))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(200))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(tween(150))
        }
    ) {
        composable("library") {
            LibraryScreen(
                viewModel             = viewModel,
                onSongClick           = { songs, index -> viewModel.playSong(songs, index) },
                onOpenPlayer          = { viewModel.requestOpenPlayer() },
                onOpenOnlineSearch    = { navController.navigate("online") },
                onOpenDownload        = { navController.navigate("download") },
                onNavigateToSettings  = { navController.navigate("settings") },
                onNavigateToPlaylists = { navController.navigate("playlists") },
                onRefresh             = { viewModel.refresh() },
                hazeState             = hazeState,
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel     = viewModel,
                onBack        = { navController.popBackStack() },
                onOpenFolders = { navController.navigate("folder") },
                onOpenAbout   = { navController.navigate("about") }
            )
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable("folder") {
            FolderScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable("online") {
            OnlineSearchScreen(
                onBack             = { navController.popBackStack() },
                onDownloadComplete = { viewModel.refresh() },
                onOpenPlayer       = { viewModel.requestOpenPlayer() }
            )
        }
        composable("download") {
            DownloadScreen(onBack = { navController.popBackStack() })
        }
        composable("playlists") {
            PlaylistListScreen(
                viewModel      = viewModel,
                onBack         = { navController.popBackStack() },
                onOpenPlaylist = { id -> navController.navigate("playlist/$id") }
            )
        }
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            PlaylistDetailScreen(
                playlistId = playlistId,
                viewModel  = viewModel,
                onBack     = { navController.popBackStack() }
            )
        }
    }
}
