package com.yulight.skypie

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.SaveableStateHolderNavEntryDecorator
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
import kotlinx.serialization.Serializable

// 路由定义
@Serializable object Library : NavKey
@Serializable object Settings : NavKey
@Serializable object About : NavKey
@Serializable object Folder : NavKey
@Serializable object Online : NavKey
@Serializable object Download : NavKey
@Serializable object Playlists : NavKey
@Serializable data class PlaylistDetail(val playlistId: Long) : NavKey

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Library)

    val openPlayerEvent by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            viewModel.savePlaybackProgress()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val hazeState = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {

            // NavDisplay + Predictive Back
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut(tween(300)))
                },
                popTransitionSpec = {
                    (slideInHorizontally { -it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally { it } + fadeOut(tween(300)))
                },
                predictivePopTransitionSpec = {
                    (slideInHorizontally { -it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally { it } + fadeOut(tween(250)))
                },
                entryDecorators = listOf(
                    SaveableStateHolderNavEntryDecorator(rememberSaveableStateHolder()),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Library> {
                        LibraryScreen(
                            viewModel             = viewModel,
                            onSongClick           = { songs, index -> viewModel.playSong(songs, index) },
                            onOpenPlayer          = { viewModel.requestOpenPlayer() },
                            onOpenOnlineSearch    = { backStack.add(Online) },
                            onOpenDownload        = { backStack.add(Download) },
                            onNavigateToSettings  = { backStack.add(Settings) },
                            onNavigateToPlaylists = { backStack.add(Playlists) },
                            onRefresh             = { viewModel.refresh() },
                            hazeState             = hazeState,
                        )
                    }
                    entry<Settings> {
                        SettingsScreen(
                            viewModel     = viewModel,
                            onBack        = { backStack.removeLastOrNull() },
                            onOpenFolders = { backStack.add(Folder) },
                            onOpenAbout   = { backStack.add(About) }
                        )
                    }
                    entry<About> {
                        AboutScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Folder> {
                        FolderScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Online> {
                        OnlineSearchScreen(
                            onBack             = { backStack.removeLastOrNull() },
                            onDownloadComplete = { viewModel.refresh() },
                            onOpenPlayer       = { viewModel.requestOpenPlayer() }
                        )
                    }
                    entry<Download> {
                        DownloadScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Playlists> {
                        PlaylistListScreen(
                            viewModel      = viewModel,
                            onBack         = { backStack.removeLastOrNull() },
                            onOpenPlaylist = { id -> backStack.add(PlaylistDetail(id)) }
                        )
                    }
                    entry<PlaylistDetail> { key ->
                        PlaylistDetailScreen(
                            playlistId = key.playlistId,
                            viewModel  = viewModel,
                            onBack     = { backStack.removeLastOrNull() }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)
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
