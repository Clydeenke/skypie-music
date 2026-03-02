package com.clydeenke.ling

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clydeenke.ling.ui.components.SharedPlayerContainer
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.ui.screen.search.OnlineSearchScreen
import com.clydeenke.ling.ui.screen.settings.SettingsScreen
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val pagerState   = rememberPagerState(pageCount = { 2 })
    val context      = LocalContext.current

    var showFolderScreen  by remember { mutableStateOf(false) }
    var showOnlineSearch  by remember { mutableStateOf(false) }

    val scope            = rememberCoroutineScope()
    val openPlayerEvent  by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    // 每 5 秒保存播放进度
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5_000)
            viewModel.savePlaybackProgress()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {

            AnimatedContent(
                targetState    = Triple(showFolderScreen, showOnlineSearch, false),
                modifier       = Modifier.fillMaxSize(),
                transitionSpec = {
                    val goingDeeper = targetState.first || targetState.second
                    if (goingDeeper) {
                        slideInHorizontally { it } + fadeIn(tween(220)) togetherWith
                                slideOutHorizontally { -it / 4 } + fadeOut(tween(180))
                    } else {
                        slideInHorizontally { -it / 4 } + fadeIn(tween(220)) togetherWith
                                slideOutHorizontally { it } + fadeOut(tween(180))
                    }
                },
                label = "mainNav"
            ) { (inFolder, inOnline, _) ->
                when {
                    inFolder -> {
                        FolderScreen(
                            viewModel = viewModel,
                            onBack    = { showFolderScreen = false }
                        )
                    }
                    inOnline -> {
                        val prefs  = context.getSharedPreferences("ling_settings", 0)
                        val apiUrl = prefs.getString("api_url", "") ?: ""
                        OnlineSearchScreen(
                            apiBaseUrl = apiUrl,
                            onBack     = { showOnlineSearch = false }
                        )
                    }
                    else -> {
                        HorizontalPager(
                            state    = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> LibraryScreen(
                                    viewModel          = viewModel,
                                    onSongClick        = { songs, index -> viewModel.playSong(songs, index) },
                                    onOpenPlayer       = { viewModel.requestOpenPlayer() },
                                    onOpenOnlineSearch = { showOnlineSearch = true }
                                )
                                1 -> SettingsScreen(
                                    onOpenFolders = { showFolderScreen = true }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible  = currentSong != null,
                enter    = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(tween(180)),
                exit     = slideOutVertically { it } + fadeOut(tween(130)),
                modifier = Modifier.fillMaxSize()
            ) {
                SharedPlayerContainer(
                    viewModel           = viewModel,
                    openPlayerRequested = openPlayerEvent,
                    onOpenPlayerHandled = { viewModel.consumeOpenPlayer() },
                    modifier            = Modifier.fillMaxSize()
                )
            }
        }
    }
}