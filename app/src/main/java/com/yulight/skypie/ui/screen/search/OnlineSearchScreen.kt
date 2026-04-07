package com.yulight.skypie.ui.screen.search

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.data.remote.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    onBack            : () -> Unit = {},
    onDownloadComplete: () -> Unit = {},
    onOpenPlayer      : () -> Unit = {},
    viewModel         : OnlineSearchViewModel = hiltViewModel()
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()

    val searchQuery     by viewModel.searchQuery.collectAsStateWithLifecycle()
    val kuwoResults     by viewModel.kuwoResults.collectAsStateWithLifecycle()
    val kugouResults    by viewModel.kugouResults.collectAsStateWithLifecycle()
    val kuwoSearching   by viewModel.kuwoSearching.collectAsStateWithLifecycle()
    val kugouSearching  by viewModel.kugouSearching.collectAsStateWithLifecycle()
    val kuwoError       by viewModel.kuwoError.collectAsStateWithLifecycle()
    val kugouError      by viewModel.kugouError.collectAsStateWithLifecycle()
    val rankSongs       by viewModel.rankSongs.collectAsStateWithLifecycle()
    val rankLoading     by viewModel.rankLoading.collectAsStateWithLifecycle()
    val currentRankIdx  by viewModel.currentRankIndex.collectAsStateWithLifecycle()
    val downloadStates  by viewModel.downloadStates.collectAsStateWithLifecycle()
    val playStates      by viewModel.playStates.collectAsStateWithLifecycle()

    var songForDownload by remember { mutableStateOf<OnlineSong?>(null) }

    val tabs       = MusicSource.entries.map { it.displayName }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // 搜索框变化时触发当前 Tab 搜索
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(500)
            viewModel.search(pagerState.currentPage)
        }
    }

    // 切换 Tab 时，如果该 Tab 还没搜过就补搜一次
    LaunchedEffect(pagerState.currentPage) {
        if (searchQuery.isNotBlank()) viewModel.search(pagerState.currentPage)
    }

    // 无 API 时的统一提示（避免在 ViewModel 里依赖 Context）
    fun showNoApiToast() = Toast.makeText(
        context,
        "请先在「设置 → API 接口地址」中配置音源",
        Toast.LENGTH_LONG
    ).show()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

            // ── 搜索栏 ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "返回") }
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder   = { Text("搜索歌曲、歌手…") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    trailingIcon  = {
                        if (searchQuery.isNotBlank())
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Clear, null)
                            }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )
                IconButton(onClick = { viewModel.search(pagerState.currentPage) }) {
                    Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 探索 / 搜索结果 ───────────────────────────────────────────────
            Crossfade(
                targetState   = searchQuery.isBlank(),
                animationSpec = tween(400),
                label         = "search_mode"
            ) { isExplore ->
                if (isExplore) {
                    // 榜单模式
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScrollableTabRow(
                            selectedTabIndex = currentRankIdx,
                            edgePadding      = 16.dp,
                            containerColor   = Color.Transparent,
                            divider          = {}
                        ) {
                            KUWO_RANKS.forEachIndexed { i, rank ->
                                Tab(
                                    selected = currentRankIdx == i,
                                    onClick  = { viewModel.setRankIndex(i) },
                                    text     = {
                                        Text(
                                            text       = rank.name,
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (currentRankIdx == i) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    selectedContentColor   = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (rankLoading) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(
                                modifier      = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 108.dp, top = 8.dp)
                            ) {
                                itemsIndexed(rankSongs, key = { _, s -> s.id }) { index, song ->
                                    if (index == rankSongs.lastIndex) {
                                        SideEffect { viewModel.loadMoreRank() }
                                    }
                                    SongListItem(
                                        song          = song,
                                        downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                        playState     = playStates[song.id]     ?: PlayState.Idle,
                                        onDownload    = { songForDownload = song },
                                        onPlay        = {
                                            viewModel.play(song, onOpenPlayer, ::showNoApiToast)
                                        }
                                    )
                                    if (index < rankSongs.lastIndex) {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(start = 74.dp),
                                            thickness = 0.5.dp,
                                            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 搜索结果模式
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = pagerState.currentPage) {
                            tabs.forEachIndexed { i, title ->
                                Tab(
                                    selected = pagerState.currentPage == i,
                                    onClick  = { scope.launch { pagerState.animateScrollToPage(i) } },
                                    text     = { Text(title) }
                                )
                            }
                        }

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val isLoading = if (page == 0) kuwoSearching else kugouSearching
                            val error     = if (page == 0) kuwoError     else kugouError
                            val results   = if (page == 0) kuwoResults   else kugouResults

                            Box(Modifier.fillMaxSize()) {
                                when {
                                    isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(Modifier.height(12.dp))
                                            Text("搜索中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.WifiOff, null, Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                                            Spacer(Modifier.height(12.dp))
                                            Text(error, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.Search, null, Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                            Spacer(Modifier.height(12.dp))
                                            Text("无搜索结果", style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 108.dp)) {
                                        item {
                                            Text(
                                                "${results.size} 条结果",
                                                style    = MaterialTheme.typography.labelMedium,
                                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        itemsIndexed(results, key = { _, s -> s.id }) { index, song ->
                                            SongListItem(
                                                song          = song,
                                                downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                                playState     = playStates[song.id]     ?: PlayState.Idle,
                                                onDownload    = { songForDownload = song },
                                                onPlay        = {
                                                    viewModel.play(song, onOpenPlayer, ::showNoApiToast)
                                                }
                                            )
                                            if (index < results.lastIndex) {
                                                HorizontalDivider(
                                                    modifier  = Modifier.padding(start = 74.dp),
                                                    thickness = 0.5.dp,
                                                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 音质选择 BottomSheet ───────────────────────────────────────────────
        songForDownload?.let { song ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { songForDownload = null },
                sheetState       = sheetState,
                containerColor   = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        "选择音质下载：${song.title}",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    listOf(AudioQuality.Standard, AudioQuality.High, AudioQuality.Lossless).forEach { q ->
                        ListItem(
                            headlineContent  = { Text(q.title) },
                            supportingContent = { Text("${q.bitRate} | ${q.desc}") },
                            trailingContent  = {
                                Icon(Icons.Rounded.Download, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            },
                            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.download(
                                        song       = song,
                                        quality    = q,
                                        onNoApi    = {
                                            Toast.makeText(context,
                                                "请先在「设置 → API 接口地址」中配置音源",
                                                Toast.LENGTH_LONG).show()
                                        },
                                        onQueued   = {
                                            Toast.makeText(context,
                                                "已加入后台下载队列",
                                                Toast.LENGTH_SHORT).show()
                                        },
                                        onComplete = onDownloadComplete
                                    )
                                    songForDownload = null
                                }
                        )
                    }
                }
            }
        }
    }
}

// ── 歌曲列表条目（纯 UI，无业务逻辑） ────────────────────────────────────────

@Composable
private fun SongListItem(
    song          : OnlineSong,
    downloadState : DownloadState,
    playState     : PlayState,
    onDownload    : () -> Unit,
    onPlay        : () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier        = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(song.coverUrl, null, Modifier.matchParentSize())
            if (playState is PlayState.Resolving) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.titleMedium,
                color    = if (playState is PlayState.Playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playState is PlayState.Playing) {
                    Icon(Icons.Rounded.GraphicEq, null,
                        modifier = Modifier.size(13.dp),
                        tint     = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text     = "${song.artist} · ${song.album}",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (playState is PlayState.Playing)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text  = formatSeconds(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))

        when (downloadState) {
            is DownloadState.Idle    -> IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Download, "下载", tint = MaterialTheme.colorScheme.primary)
            }
            is DownloadState.Loading -> Box(Modifier.size(40.dp), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is DownloadState.Done    -> Box(Modifier.size(40.dp), Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, "完成", tint = MaterialTheme.colorScheme.primary)
            }
            is DownloadState.Error   -> IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.ErrorOutline, "重试", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatSeconds(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)