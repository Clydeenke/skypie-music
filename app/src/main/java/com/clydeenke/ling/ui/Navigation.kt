package com.clydeenke.ling

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.clydeenke.ling.ui.components.SharedPlayerContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.ui.screen.search.OnlineSearchScreen
import com.clydeenke.ling.ui.screen.settings.SettingsScreen
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val pagerState   = rememberPagerState(pageCount = { 2 })
    val context      = LocalContext.current

    var showFolderScreen by remember { mutableStateOf(false) }
    var showOnlineSearch by remember { mutableStateOf(false) }

    // 预见式返回的手势进度（0=没在划，1=完全划过去了）
    var backProgress by remember { mutableFloatStateOf(0f) }

    val scope           = rememberCoroutineScope()
    val openPlayerEvent by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    // ✅ 预见式返回：文件夹/云端搜索 → 返回主页，手指跟随有动画
    PredictiveBackHandler(enabled = showFolderScreen || showOnlineSearch) { progress ->
        try {
            progress.collect { event ->
                // 让页面跟着手指往右移动，产生"快要返回"的视觉预览
                backProgress = event.progress
            }
            // 手松开确认返回：先切页面，等动画结束再重置进度，避免闪回
            showFolderScreen = false
            showOnlineSearch = false
            scope.launch {
                delay(250)
                backProgress = 0f
            }
        } catch (e: CancellationException) {
            // 手缩回去，取消返回，恢复原位
            backProgress = 0f
        }
    }

    // 每 5 秒保存播放进度
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5_000)
            viewModel.savePlaybackProgress()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val hazeState = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {

            AnimatedContent(
                targetState    = Triple(showFolderScreen, showOnlineSearch, false),
                modifier       = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
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
                val isSubPage = inFolder || inOnline
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 手指往里划时，子页面跟着往右移+缩小，像系统原生返回动画
                            if (isSubPage && backProgress > 0f) {
                                translationX = size.width * backProgress * 0.35f
                                scaleX = 1f - backProgress * 0.06f
                                scaleY = 1f - backProgress * 0.06f
                                alpha  = 1f - backProgress * 0.3f
                            }
                        }
                ) {
                    when {
                        inFolder -> FolderScreen(
                            viewModel = viewModel,
                            onBack    = { showFolderScreen = false }
                        )
                        inOnline -> {
                            val prefs  = context.getSharedPreferences("ling_settings", 0)
                            val apiUrl = prefs.getString("api_url", "") ?: ""
                            OnlineSearchScreen(
                                apiBaseUrl         = apiUrl,
                                viewModel          = viewModel,
                                onBack             = { showOnlineSearch = false },
                                onDownloadComplete = { viewModel.refresh() },
                                onOpenPlayer       = { viewModel.requestOpenPlayer() }
                            )
                        }
                        else -> HorizontalPager(
                            state    = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> LibraryScreen(
                                    viewModel          = viewModel,
                                    onSongClick        = { songs, index -> viewModel.playSong(songs, index) },
                                    onOpenPlayer       = { viewModel.requestOpenPlayer() },
                                    onOpenOnlineSearch = { showOnlineSearch = true },
                                    onRefresh          = { viewModel.refresh() }
                                )
                                1 -> SettingsScreen(
                                    viewModel     = viewModel,
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
                    hazeState           = hazeState,
                    modifier            = Modifier.fillMaxSize()
                )
            }
        }
    }
}