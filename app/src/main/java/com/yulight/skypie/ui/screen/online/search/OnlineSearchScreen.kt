package com.yulight.skypie.ui.screen.online.search

import android.content.Context
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.memory.MemoryCache
import com.yulight.skypie.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_SEARCH_HISTORY = "online_search_history"
private const val KEY_SEARCH_HISTORY = "history"
private const val MAX_HISTORY_SIZE = 20

private fun loadSearchHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_SEARCH_HISTORY, Context.MODE_PRIVATE)
    val historyStr = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
    return if (historyStr.isBlank()) emptyList()
    else historyStr.split(",").filter { it.isNotBlank() }
}

private fun saveSearchHistory(context: Context, query: String, history: MutableList<String>) {
    history.remove(query)
    history.add(0, query)
    if (history.size > MAX_HISTORY_SIZE) {
        history.removeAt(history.lastIndex)
    }
    val prefs = context.getSharedPreferences(PREFS_SEARCH_HISTORY, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SEARCH_HISTORY, history.joinToString(",")).apply()
}

private fun clearSearchHistory(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_SEARCH_HISTORY, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
}

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

    val searchQuery       by viewModel.searchQuery.collectAsStateWithLifecycle()
    val kuwoResults       by viewModel.kuwoResults.collectAsStateWithLifecycle()
    val kugouResults      by viewModel.kugouResults.collectAsStateWithLifecycle()
    val neteaseResults    by viewModel.neteaseResults.collectAsStateWithLifecycle()
    val qqResults         by viewModel.qqResults.collectAsStateWithLifecycle()
    val kuwoSearching     by viewModel.kuwoSearching.collectAsStateWithLifecycle()
    val kugouSearching    by viewModel.kugouSearching.collectAsStateWithLifecycle()
    val neteaseSearching  by viewModel.neteaseSearching.collectAsStateWithLifecycle()
    val qqSearching       by viewModel.qqSearching.collectAsStateWithLifecycle()
    val kuwoError         by viewModel.kuwoError.collectAsStateWithLifecycle()
    val kugouError        by viewModel.kugouError.collectAsStateWithLifecycle()
    val neteaseError      by viewModel.neteaseError.collectAsStateWithLifecycle()
    val qqError           by viewModel.qqError.collectAsStateWithLifecycle()
    val rankSongs         by viewModel.rankSongs.collectAsStateWithLifecycle()
    val rankLoading       by viewModel.rankLoading.collectAsStateWithLifecycle()
    val rankLoadingMore   by viewModel.rankLoadingMore.collectAsStateWithLifecycle()
    val currentRankIdx    by viewModel.currentRankIndex.collectAsStateWithLifecycle()
    val downloadStates    by viewModel.downloadStates.collectAsStateWithLifecycle()
    val playStates        by viewModel.playStates.collectAsStateWithLifecycle()
    val kuwoLoadingMore   by viewModel.kuwoLoadingMore.collectAsStateWithLifecycle()
    val kugouLoadingMore  by viewModel.kugouLoadingMore.collectAsStateWithLifecycle()
    val neteaseLoadingMore by viewModel.neteaseLoadingMore.collectAsStateWithLifecycle()
    val qqLoadingMore     by viewModel.qqLoadingMore.collectAsStateWithLifecycle()

    // 搜索历史状态
    val searchHistory = remember { mutableStateListOf<String>() }
    var hasSearched by remember { mutableStateOf(false) }

    // 加载搜索历史
    LaunchedEffect(Unit) {
        searchHistory.clear()
        searchHistory.addAll(loadSearchHistory(context))
    }

    // 退出时重置搜索状态
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSearchQuery("")
        }
    }

    // 预加载搜索结果封面
    LaunchedEffect(kuwoResults) {
        if (kuwoResults.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                kuwoResults.take(30).forEach { song ->
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            val cacheKey = MemoryCache.Key(song.coverUrl)
                            if (context.imageLoader.memoryCache?.get(cacheKey) != null) return@forEach
                            val request = ImageRequest.Builder(context)
                                .data(song.coverUrl)
                                .size(120)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(song.coverUrl)
                                .build()
                            context.imageLoader.execute(request)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    LaunchedEffect(kugouResults) {
        if (kugouResults.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                kugouResults.take(30).forEach { song ->
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            val cacheKey = MemoryCache.Key(song.coverUrl)
                            if (context.imageLoader.memoryCache?.get(cacheKey) != null) return@forEach
                            val request = ImageRequest.Builder(context)
                                .data(song.coverUrl)
                                .size(120)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(song.coverUrl)
                                .build()
                            context.imageLoader.execute(request)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    var songForDownload by remember { mutableStateOf<OnlineSong?>(null) }

    // 预加载榜单封面
    LaunchedEffect(rankSongs) {
        if (rankSongs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                rankSongs.take(30).forEach { song ->
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            // 先检查内存缓存是否已存在
                            val cacheKey = MemoryCache.Key(song.coverUrl)
                            if (context.imageLoader.memoryCache?.get(cacheKey) != null) return@forEach
                            val request = ImageRequest.Builder(context)
                                .data(song.coverUrl)
                                .size(120)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(song.coverUrl)
                                .build()
                            context.imageLoader.execute(request)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val tabs       = MusicSource.entries.map { it.displayName }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

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
                            IconButton(onClick = {
                                viewModel.setSearchQuery("")
                                hasSearched = false
                            }) {
                                Icon(Icons.Rounded.Clear, null)
                            }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )
                IconButton(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            saveSearchHistory(context, searchQuery, searchHistory)
                            hasSearched = true
                        }
                        viewModel.search(pagerState.currentPage)
                    },
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 探索 / 搜索结果 ───────────────────────────────────────────────
            Crossfade(
                targetState   = !hasSearched,
                animationSpec = tween(400),
                label         = "search_mode"
            ) { isExplore ->
                if (isExplore) {
                    // 搜索历史
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (searchHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "搜索历史",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "清除",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        clearSearchHistory(context)
                                        searchHistory.clear()
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                searchHistory.forEach { query ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            viewModel.setSearchQuery(query)
                                            viewModel.clearAllResults()
                                            hasSearched = true
                                            viewModel.search(pagerState.currentPage)
                                        }
                                    ) {
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "输入关键词搜索歌曲",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // 搜索结果模式
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 圆角Tab（无水波效果）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tabs.forEachIndexed { i, title ->
                                val isSelected = pagerState.currentPage == i
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            scope.launch { pagerState.scrollToPage(i) }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                               else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val isLoading = when (page) {
                                0 -> kuwoSearching; 1 -> kugouSearching
                                2 -> neteaseSearching; 3 -> qqSearching
                                else -> false
                            }
                            val error = when (page) {
                                0 -> kuwoError; 1 -> kugouError
                                2 -> neteaseError; 3 -> qqError
                                else -> null
                            }
                            val results = when (page) {
                                0 -> kuwoResults; 1 -> kugouResults
                                2 -> neteaseResults; 3 -> qqResults
                                else -> emptyList()
                            }

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
                                            if (index == results.lastIndex) {
                                                SideEffect {
                                                    when (page) {
                                                        0 -> viewModel.loadMoreKuwo()
                                                        1 -> viewModel.loadMoreKugou()
                                                        2 -> viewModel.loadMoreNetease()
                                                        3 -> viewModel.loadMoreQQ()
                                                    }
                                                }
                                            }
                                            SongListItem(
                                                song          = song,
                                                downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                                playState     = playStates[song.id]     ?: PlayState.Idle,
                                                onDownload    = { songForDownload = song },
                                                onPlay        = {
                                                    viewModel.play(results, index, onOpenPlayer, ::showNoApiToast)
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
                                        // 加载更多指示器
                                        if ((page == 0 && kuwoLoadingMore) || (page == 1 && kugouLoadingMore)) {
                                            item {
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp
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
fun SongListItem(
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

fun formatSeconds(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)