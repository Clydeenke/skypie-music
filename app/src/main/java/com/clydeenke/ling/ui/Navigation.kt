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
import com.clydeenke.ling.ui.screen.search.OnlineSearchScreen
import com.clydeenke.ling.ui.screen.settings.SettingsScreen
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.clydeenke.ling.ui.screen.library.LibraryScreen

// 🌟 新增：用一个干净的数据类管理页面状态，彻底抛弃旧的 Pager
private data class NavState(val folder: Boolean, val online: Boolean, val settings: Boolean) {
    val isSubPage: Boolean get() = folder || online || settings
}

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val context      = LocalContext.current

    var showFolderScreen by remember { mutableStateOf(false) }
    var showOnlineSearch by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) } // 🌟 新增：控制设置界面的状态

    // 预见式返回的手势进度（0=没在划，1=完全划过去了）
    var backProgress by remember { mutableFloatStateOf(0f) }

    val scope           = rememberCoroutineScope()
    val openPlayerEvent by viewModel.openPlayerEvent.collectAsStateWithLifecycle()

    // ✅ 预见式返回：文件夹/云端搜索/设置 → 返回主页，手指跟随有动画
    PredictiveBackHandler(enabled = showFolderScreen || showOnlineSearch || showSettingsScreen) { progress ->
        try {
            progress.collect { event ->
                // 让页面跟着手指往右移动，产生"快要返回"的视觉预览
                backProgress = event.progress
            }
            // 手松开确认返回：精确判断当前在那一层，逐层关闭
            if (showFolderScreen) {
                showFolderScreen = false
            } else if (showOnlineSearch) {
                showOnlineSearch = false
            } else {
                showSettingsScreen = false
            }

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
                targetState    = NavState(showFolderScreen, showOnlineSearch, showSettingsScreen),
                modifier       = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                transitionSpec = {
                    val goingDeeper = targetState.isSubPage
                    if (goingDeeper) {
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
                            // 手指往里划时，子页面跟着往右移+缩小，像系统原生返回动画
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
                        state.settings -> {
                            // 🌟 设置界面现在作为一个独立的子层级弹出，不再跟侧边栏滑动冲突
                            SettingsScreen(
                                viewModel     = viewModel,
                                onOpenFolders = { showFolderScreen = true }
                            )
                        }
                        else -> {
                            // 🌟 剥离了 HorizontalPager，LibraryScreen 现在是绝对的底层主页
                            LibraryScreen(
                                viewModel          = viewModel,
                                onSongClick        = { songs, index -> viewModel.playSong(songs, index) },
                                onOpenPlayer       = { viewModel.requestOpenPlayer() },
                                onOpenOnlineSearch = { showOnlineSearch = true },
                                onNavigateToSettings = { showSettingsScreen = true }, // 连接到我们刚写的侧滑抽屉
                                onRefresh          = { viewModel.refresh() },
                                hazeState          = hazeState,
                            )
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