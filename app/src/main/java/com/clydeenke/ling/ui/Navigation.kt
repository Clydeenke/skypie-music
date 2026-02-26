package com.clydeenke.ling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clydeenke.ling.ui.components.SharedPlayerContainer
import com.clydeenke.ling.ui.screen.folders.FolderScreen
import com.clydeenke.ling.ui.screen.library.LibraryScreen
import com.clydeenke.ling.viewmodel.MusicViewModel

@Composable
fun MainNavigation() {
    val viewModel   : MusicViewModel = hiltViewModel()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val pagerState   = rememberPagerState(pageCount = { 2 })

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── 主界面内容 ─────────────────────────────────────────────────
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

                // 两个页面：音乐库 / 文件夹
                // 列表一直延伸到屏幕底部，迷你条悬浮覆盖在上方
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> LibraryScreen(
                            viewModel   = viewModel,
                            onSongClick = { songs, index -> viewModel.playSong(songs, index) }
                        )
                        1 -> FolderScreen(viewModel = viewModel)
                    }
                }

                // 底部导航栏安全区，不再加迷你条占位 Spacer
                Spacer(Modifier.navigationBarsPadding())
            }

            // ── SharedPlayerContainer：悬浮在内容层上方 ────────────────────
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
                    viewModel = viewModel,
                    modifier  = Modifier.fillMaxSize()
                )
            }
        }
    }
}