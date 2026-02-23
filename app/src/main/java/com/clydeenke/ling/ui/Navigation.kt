package com.clydeenke.ling

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clydeenke.ling.ui.components.SharedPlayerContainer // 确保 import 了新组件
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.viewmodel.MusicViewModel

private val PAGE_TITLES = listOf("音乐库", "文件夹")

@OptIn(ExperimentalSharedTransitionApi::class) // 必须添加，因为共享元素 API 还是实验性的
@Composable
fun MainNavigation() {
    val viewModel: MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── 主界面层 ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 页面标题
                Text(
                    text = PAGE_TITLES[pagerState.currentPage],
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> LibraryScreen(
                            viewModel = viewModel,
                            onSongClick = { songs, index ->
                                viewModel.playSong(songs, index)
                            }
                        )
                        1 -> FolderScreen(viewModel = viewModel)
                    }
                }

                // 指示器放在 Column 底部
                PagerIndicator(
                    pageCount = 2,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                )

                // ── 播放器容器 ─────────────────────────
                // 注意：这里我们让它在 Column 的最底部显示
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    SharedPlayerContainer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp) // 给 Mini 状态加点边距更好看
                    )
                }

                // 适配系统导航栏
                Spacer(Modifier.navigationBarsPadding())
            } // Column 结束
        } // Box 结束
    } // Surface 结束
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isSelected) 20.dp else 6.dp,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "dot_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                label = "dot_color"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}