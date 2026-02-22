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
import com.clydeenke.ling.ui.components.MiniPlayer
// ↓ 按你实际路径选 screen 或 screens
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.ui.screen.player.PlayerScreen
import com.clydeenke.ling.viewmodel.MusicViewModel

private val PAGE_TITLES = listOf("音乐库", "文件夹")

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    // 是否展示全屏播放器
    var showPlayer by remember { mutableStateOf(false) }

    // HorizontalPager 状态（0 = 音乐库，1 = 文件夹）
    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 主界面（HorizontalPager） ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()   // 统一在这里处理状态栏，子页面不需要再加
        ) {
            // 当前页标题
            Text(
                text     = PAGE_TITLES[pagerState.currentPage],
                style    = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
            )

            // 页面内容
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> LibraryScreen(
                        viewModel   = viewModel,
                        onSongClick = { songs, index ->
                            viewModel.playSong(songs, index)
                            showPlayer = true
                        }
                    )
                    1 -> FolderScreen(viewModel = viewModel)
                }
            }

            // 页面指示器小点
            PagerIndicator(
                pageCount   = 2,
                currentPage = pagerState.currentPage,
                modifier    = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 6.dp)
            )

            // ── MiniPlayer：弹簧动画进出 ──────────────────────────
            AnimatedVisibility(
                visible = currentSong != null,
                enter   = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(),
                exit    = slideOutVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessMedium
                    )
                ) { it } + fadeOut()
            ) {
                MiniPlayer(
                    viewModel     = viewModel,
                    onExpandClick = { showPlayer = true },
                    modifier      = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.navigationBarsPadding())
        }

        // ── 全屏播放器：从底部弹出 ────────────────────────────────
        AnimatedVisibility(
            visible = showPlayer,
            enter   = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(tween(200)),
            exit    = slideOutVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            ) { it } + fadeOut(tween(150))
        ) {
            PlayerScreen(
                viewModel = viewModel,
                onBack    = { showPlayer = false }
            )
        }
    }
}

// ── 小圆点指示器 ──────────────────────────────────────────────────────────────
@Composable
private fun PagerIndicator(
    pageCount   : Int,
    currentPage : Int,
    modifier    : Modifier = Modifier
) {
    Row(
        modifier            = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment   = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue   = if (isSelected) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label         = "indicatorWidth"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
            )
        }
    }
}