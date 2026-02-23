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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clydeenke.ling.ui.components.MiniPlayer
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.ui.screen.player.PlayerScreen
import com.clydeenke.ling.viewmodel.MusicViewModel

private val PAGE_TITLES = listOf("音乐库", "文件夹")

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    var showPlayer  by remember { mutableStateOf(false) }
    val pagerState   = rememberPagerState(pageCount = { 2 })

    // ✅ Surface 替代 Box+background，自动管理 onBackground/onSurface 等文字颜色
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 主界面 ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 页面标题
                Text(
                    text     = PAGE_TITLES[pagerState.currentPage],
                    style    = MaterialTheme.typography.headlineLarge,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )

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

                // 页面指示器
                PagerIndicator(
                    pageCount   = 2,
                    currentPage = pagerState.currentPage,
                    modifier    = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 6.dp)
                )

                // ── 迷你播放条：弹簧动画滑入 ─────────────────────────
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter   = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
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
                    MiniPlayer(
                        viewModel     = viewModel,
                        onExpandClick = { showPlayer = true },
                        modifier      = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.navigationBarsPadding())
            }

            // ── 全屏播放器：从底部放大展开（模拟从迷你播放条膨胀）──
            AnimatedVisibility(
                visible = showPlayer,
                enter   = scaleIn(
                    animationSpec   = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    ),
                    initialScale    = 0.08f,
                    transformOrigin = TransformOrigin(0.5f, 1f)  // 从底部中心展开
                ) + fadeIn(tween(250)),
                exit    = scaleOut(
                    animationSpec   = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessMedium
                    ),
                    targetScale     = 0.08f,
                    transformOrigin = TransformOrigin(0.5f, 1f)  // 收回底部中心
                ) + fadeOut(tween(200))
            ) {
                // ✅ Surface 确保播放器内文字颜色正确
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack    = { showPlayer = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun PagerIndicator(
    pageCount   : Int,
    currentPage : Int,
    modifier    : Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue   = if (isSelected) 20.dp else 6.dp,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label         = "dot"
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