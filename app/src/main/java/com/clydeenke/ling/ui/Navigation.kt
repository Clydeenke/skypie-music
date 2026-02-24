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

val MINI_BAR_HEIGHT_DP = 64.dp

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val pagerState   = rememberPagerState(pageCount = { 2 })

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // ✅ 最外层 Box，SharedPlayerContainer 作为最后一个子项（最高层级）
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 主界面 ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Text(
                    text     = listOf("音乐库", "文件夹")[pagerState.currentPage],
                    style    = MaterialTheme.typography.headlineLarge,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
                )

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> LibraryScreen(
                            viewModel   = viewModel,
                            onSongClick = { songs, index -> viewModel.playSong(songs, index) }
                        )
                        1 -> FolderScreen(viewModel = viewModel)
                    }
                }

                PagerIndicator(
                    pageCount   = 2,
                    currentPage = pagerState.currentPage,
                    modifier    = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 6.dp)
                )

                // Mini 胶囊占位 spacer（防止列表内容被播放器遮住）
                if (currentSong != null) {
                    Spacer(Modifier.height(MINI_BAR_HEIGHT_DP + 12.dp))
                }

                Spacer(Modifier.navigationBarsPadding())
            }

            // ✅ SharedPlayerContainer 作为全屏覆盖层（fillMaxSize）
            // 由内部自己决定 Mini/Full 的具体位置，避免外层 wrapContent 造成高度错误
            AnimatedVisibility(
                visible = currentSong != null,
                enter   = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMediumLow
                    )
                ) { it } + fadeIn(tween(200)),
                exit    = slideOutVertically { it } + fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                SharedPlayerContainer(
                    viewModel = viewModel,
                    modifier  = Modifier.fillMaxSize()
                )
            }
        }
    }
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