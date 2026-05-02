package com.yulight.skypie

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yulight.skypie.ui.components.SharedPlayerContainer
import com.yulight.skypie.ui.screen.folders.FolderScreen
import com.yulight.skypie.ui.screen.library.LibraryScreen
import com.yulight.skypie.ui.screen.playlist.PlaylistDetailScreen
import com.yulight.skypie.ui.screen.playlist.PlaylistListScreen
import com.yulight.skypie.ui.screen.search.OnlineSearchScreen
import com.yulight.skypie.ui.screen.settings.SettingsScreen
import com.yulight.skypie.viewmodel.MusicViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class NavState(
    val folder     : Boolean,
    val online     : Boolean,
    val settings   : Boolean,
    val playlists  : Boolean,
    val playlistId : Long?
) {
    val depth: Int get() = when {
        playlistId != null -> 3
        playlists          -> 2
        folder             -> 2
        online || settings -> 1
        else               -> 0
    }
    val isSubPage: Boolean get() = depth > 0
}

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val context      = LocalContext.current

    var showFolderScreen   by remember { mutableStateOf(false)       }
    var showOnlineSearch   by remember { mutableStateOf(false)       }
    var showSettingsScreen by remember { mutableStateOf(false)       }
    var showPlaylistList   by remember { mutableStateOf(false)       }
    var showPlaylistDetail by remember { mutableStateOf<Long?>(null) }

    var backProgress by remember { mutableFloatStateOf(0f) }

    val scope           = rememberCoroutineScope()
    val openPlayerEvent by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    val isSubPageOpen = showFolderScreen || showOnlineSearch || showSettingsScreen
            || showPlaylistList || showPlaylistDetail != null

    PredictiveBackHandler(enabled = isSubPageOpen) { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            when {
                showFolderScreen -> showFolderScreen = false
                showOnlineSearch -> showOnlineSearch = false
                showSettingsScreen -> showSettingsScreen = false
                showPlaylistDetail != null -> showPlaylistDetail = null
                showPlaylistList -> showPlaylistList = false
            }
                scope.launch {
                    delay(250)
                    backProgress = 0f
            }
        } catch (e: CancellationException) {
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

            AnimatedContent(
                targetState = NavState(
                    folder     = showFolderScreen,
                    online     = showOnlineSearch,
                    settings   = showSettingsScreen,
                    playlists  = showPlaylistList,
                    playlistId = showPlaylistDetail
                ),
                modifier       = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                transitionSpec = {
                    if (targetState.depth >= initialState.depth) {
                        slideInHorizontally { it } + fadeIn(tween(220)) togetherWith
                                slideOutHorizontally { -it / 4 } + fadeOut(tween(180))
                    } else {
                        slideInHorizontally { -it / 4 } + fadeIn(tween(220)) togetherWith
                                slideOutHorizontally { it } + fadeOut(tween(180))
                    }
                },
                label = "mainNav"
            ) { state ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (state.isSubPage && backProgress > 0f) {
                                translationX = size.width * backProgress * 0.35f
                                scaleX = 1f - backProgress * 0.06f
                                scaleY = 1f - backProgress * 0.06f
                                alpha  = 1f - backProgress * 0.3f
                            }
                        }
                ) {
                    when {
                        state.folder -> FolderScreen(
                            viewModel = viewModel,
                            onBack    = { showFolderScreen = false }
                        )
                        state.online -> {
                            val prefs  = context.getSharedPreferences("skypie_settings", 0)
                            val apiUrl = prefs.getString("api_url", "") ?: ""
                            OnlineSearchScreen(
                                onBack             = { showOnlineSearch = false },
                                onDownloadComplete = { viewModel.refresh() },
                                onOpenPlayer       = { viewModel.requestOpenPlayer() }
                            )
                        }
                        state.settings -> SettingsScreen(
                            viewModel     = viewModel,
                            onBack        = { showSettingsScreen = false }, // ← 修复：补上 onBack
                            onOpenFolders = { showFolderScreen = true }
                        )
                        state.playlistId != null -> PlaylistDetailScreen(
                            playlistId = state.playlistId,
                            viewModel  = viewModel,
                            onBack     = { showPlaylistDetail = null }
                        )
                        state.playlists -> PlaylistListScreen(
                            viewModel      = viewModel,
                            onBack         = { showPlaylistList = false },
                            onOpenPlaylist = { id: Long -> showPlaylistDetail = id }
                        )
                        else -> LibraryScreen(
                            viewModel             = viewModel,
                            onSongClick           = { songs, index -> viewModel.playSong(songs, index) },
                            onOpenPlayer          = { viewModel.requestOpenPlayer() },
                            onOpenOnlineSearch    = { showOnlineSearch = true },
                            onNavigateToSettings  = { showSettingsScreen = true },
                            onNavigateToPlaylists = { showPlaylistList = true },
                            onRefresh             = { viewModel.refresh() },
                            hazeState             = hazeState,
                        )
                    }
                }
            }

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