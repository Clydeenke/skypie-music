package com.clydeenke.ling.ui.screen.search

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.*
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel
import com.clydeenke.ling.worker.MusicDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

// ── 数据模型 ──────────────────────────────────────────────────────────────────
data class OnlineSong(
    val id       : String,
    val title    : String,
    val artist   : String,
    val album    : String,
    val duration : Int,
    val coverUrl : String,
    val source   : MusicSource = MusicSource.KUWO
)

enum class MusicSource { KUWO, KUGOU }

sealed class DownloadState {
    object Idle    : DownloadState()
    object Loading : DownloadState()
    object Done    : DownloadState()
    data class Error(val msg: String) : DownloadState()
}

sealed class PlayState {
    object Idle       : PlayState()
    object Resolving  : PlayState()
    object Playing    : PlayState()
    data class Error(val msg: String) : PlayState()
}

sealed class AudioQuality(val title: String, val level: String, val bitRate: String, val desc: String) {
    data object Standard : AudioQuality("标准音质", "standard", "128k", "适合移动网络")
    data object High     : AudioQuality("高品质", "high", "320k", "音质清晰细节丰富")
    data object Lossless : AudioQuality("无损音质", "lossless", "FLAC", "母带原音重现")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    apiBaseUrl         : String,
    viewModel          : MusicViewModel,
    onBack             : () -> Unit = {},
    onDownloadComplete : () -> Unit = {},
    onOpenPlayer       : () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val tabs       = listOf("酷我", "酷狗")
    val pagerState = rememberPagerState(pageCount = { 2 })
    var query      by remember { mutableStateOf("") }

    val results        = remember { mutableStateMapOf<Int, List<OnlineSong>>() }
    val isSearching    = remember { mutableStateMapOf<Int, Boolean>() }
    val errorMsgs      = remember { mutableStateMapOf<Int, String?>() }
    val downloadStates = remember { mutableStateMapOf<String, DownloadState>() }
    val playStates     = remember { mutableStateMapOf<String, PlayState>() }

    var currentPlayingId by remember { mutableStateOf<String?>(null) }
    var songForDownload  by remember { mutableStateOf<OnlineSong?>(null) }

    val rankings = listOf("热歌榜" to 16, "新歌榜" to 17, "飙升榜" to 93, "抖音榜" to 158)
    var currentRankIndex by remember { mutableStateOf(0) }
    var currentPage      by remember { mutableIntStateOf(1) }
    var isMoreLoading    by remember { mutableStateOf(false) }
    var hotSongs         by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var isHotLoading     by remember { mutableStateOf(true) }

    LaunchedEffect(currentRankIndex) {
        isHotLoading = true; currentPage = 1; hotSongs = emptyList()
        hotSongs = fetchKuwoRank(rankings[currentRankIndex].second, 1)
        isHotLoading = false
    }

    val loadNextPage: () -> Unit = {
        if (!isMoreLoading && !isHotLoading && query.isBlank() && hotSongs.isNotEmpty()) {
            scope.launch {
                isMoreLoading = true; delay(300)
                try {
                    val newSongs = fetchKuwoRank(rankings[currentRankIndex].second, currentPage + 1)
                    if (newSongs.isNotEmpty()) { hotSongs = hotSongs + newSongs; currentPage++ }
                    else android.util.Log.d("MusicAPI", "没有更多歌曲或请求受限")
                } catch (_: Exception) {}
                finally { delay(1000); isMoreLoading = false }
            }
        }
    }

    fun doSearch(tabIndex: Int = pagerState.currentPage) {
        if (query.isBlank()) return
        scope.launch {
            isSearching[tabIndex] = true; errorMsgs[tabIndex] = null; results[tabIndex] = emptyList()
            try {
                results[tabIndex] = when (tabIndex) { 0 -> searchKuwo(query); 1 -> searchKugou(query); else -> emptyList() }
            } catch (e: Exception) { errorMsgs[tabIndex] = "搜索失败：${e.message}" }
            isSearching[tabIndex] = false
        }
    }

    LaunchedEffect(query) { if (query.isNotBlank()) { delay(500); doSearch(pagerState.currentPage) } }
    LaunchedEffect(pagerState.currentPage) {
        val idx = pagerState.currentPage
        if (query.isNotBlank() && results[idx] == null) doSearch(idx)
    }

    // ── 在线播放 ─────────────────────────────────────────────────────────────
    fun doPlay(song: OnlineSong) {
        scope.launch {
            // API 未配置：明确引导，避免用户误以为是平台问题
            if (apiBaseUrl.isBlank()) {
                Toast.makeText(context, "请先在「设置 → API 接口地址」中配置音源", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (currentPlayingId == song.id) { onOpenPlayer(); return@launch }
            playStates[song.id] = PlayState.Resolving
            currentPlayingId?.let { playStates[it] = PlayState.Idle }
            currentPlayingId = song.id
            try {
                val streamUrl = when (song.source) {
                    MusicSource.KUWO  -> resolveKuwoUrl(apiBaseUrl, song.id, AudioQuality.Standard.level)
                    MusicSource.KUGOU -> resolveKugouUrl(apiBaseUrl, song.id, AudioQuality.Standard.level)
                }
                if (streamUrl.isNullOrBlank()) {
                    playStates[song.id] = PlayState.Error("获取链接失败"); currentPlayingId = null; return@launch
                }
                val lrcText = withContext(Dispatchers.IO) {
                    when (song.source) {
                        MusicSource.KUWO  -> fetchKuwoLyric(song.id)
                        MusicSource.KUGOU -> fetchKugouLyric(song.id)
                    }
                }
                viewModel.playerController.playOnlineStream(
                    streamUrl = streamUrl, title = song.title, artist = song.artist,
                    coverUrl = song.coverUrl, songId = song.id, lrcText = lrcText
                )
                playStates[song.id] = PlayState.Playing; onOpenPlayer()
            } catch (e: Exception) {
                playStates[song.id] = PlayState.Error(e.message ?: "播放失败"); currentPlayingId = null
            }
        }
    }

    // ── 后台下载 ──────────────────────────────────────────────────────────────
    fun dispatchDownload(song: OnlineSong, quality: AudioQuality) {
        scope.launch {
            // API 未配置：明确引导，避免用户看到"无该音质"的误导信息
            if (apiBaseUrl.isBlank()) {
                Toast.makeText(context, "请先在「设置 → API 接口地址」中配置音源", Toast.LENGTH_LONG).show()
                return@launch
            }
            downloadStates[song.id] = DownloadState.Loading
            try {
                val playUrl = when (song.source) {
                    MusicSource.KUWO  -> resolveKuwoUrl(apiBaseUrl, song.id, quality.level)
                    MusicSource.KUGOU -> resolveKugouUrl(apiBaseUrl, song.id, quality.level)
                }
                if (playUrl.isNullOrBlank()) {
                    downloadStates[song.id] = DownloadState.Error("当前资源无该音质")
                    Toast.makeText(context, "该音质暂时不可用，请尝试其他音质", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val ext = when { playUrl.contains(".flac") -> "flac"; playUrl.contains(".aac") -> "aac"; playUrl.contains(".ogg") -> "ogg"; else -> "mp3" }
                val lrcText = withContext(Dispatchers.IO) {
                    when (song.source) {
                        MusicSource.KUWO  -> fetchKuwoLyric(song.id)
                        MusicSource.KUGOU -> fetchKugouLyric(song.id)
                    }
                }
                val inputData = workDataOf(
                    "title" to song.title, "artist" to song.artist, "playUrl" to playUrl,
                    "coverUrl" to song.coverUrl, "ext" to ext, "lrcText" to lrcText
                )
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                        .setInputData(inputData)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
                downloadStates[song.id] = DownloadState.Done
                Toast.makeText(context, "已加入后台下载队列", Toast.LENGTH_SHORT).show()
                onDownloadComplete()
            } catch (e: Exception) {
                downloadStates[song.id] = DownloadState.Error(e.message ?: "未知错误")
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "返回") }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索歌曲、歌手…") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, null) } },
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                IconButton(onClick = { results.clear(); doSearch(pagerState.currentPage) }) {
                    Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Crossfade(targetState = query.isBlank(), animationSpec = tween(400), label = "search_transition") { isExplore ->
                if (isExplore) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScrollableTabRow(selectedTabIndex = currentRankIndex, edgePadding = 16.dp, containerColor = Color.Transparent, divider = {}) {
                            rankings.forEachIndexed { index, rank ->
                                Tab(
                                    selected = currentRankIndex == index, onClick = { currentRankIndex = index },
                                    text = { Text(rank.first, style = MaterialTheme.typography.titleMedium, fontWeight = if (currentRankIndex == index) FontWeight.Bold else FontWeight.Normal) },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isHotLoading) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        } else if (hotSongs.isNotEmpty()) {
                            LazyColumn(contentPadding = PaddingValues(bottom = 108.dp, top = 8.dp), modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(hotSongs, key = { _, s -> s.id }) { index, song ->
                                    if (index == hotSongs.size - 1) SideEffect { loadNextPage() }
                                    OnlineSongItem(song, downloadStates[song.id] ?: DownloadState.Idle, playStates[song.id] ?: PlayState.Idle, { songForDownload = song }, { doPlay(song) })
                                    if (index < hotSongs.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = pagerState.currentPage) {
                            tabs.forEachIndexed { i, title ->
                                Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(title) })
                            }
                        }
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val pageResults = results[page]; val pageSearching = isSearching[page] == true; val pageError = errorMsgs[page]
                            Box(Modifier.fillMaxSize()) {
                                when {
                                    pageSearching -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("搜索中…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                    pageError != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.WifiOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)); Spacer(Modifier.height(12.dp)); Text(pageError, color = MaterialTheme.colorScheme.error) } }
                                    pageResults.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Search, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)); Spacer(Modifier.height(12.dp)); Text("无搜索结果", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 108.dp)) {
                                        item { Text("${pageResults.size} 条结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                                        itemsIndexed(pageResults, key = { _, s -> s.id }) { index, song ->
                                            OnlineSongItem(song, downloadStates[song.id] ?: DownloadState.Idle, playStates[song.id] ?: PlayState.Idle, { songForDownload = song }, { doPlay(song) })
                                            if (index < pageResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (songForDownload != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { songForDownload = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)) {
                    Text("选择音质下载：${songForDownload!!.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                    listOf(AudioQuality.Standard, AudioQuality.High, AudioQuality.Lossless).forEach { q ->
                        ListItem(
                            headlineContent = { Text(q.title) }, supportingContent = { Text("${q.bitRate} | ${q.desc}") },
                            trailingContent = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dispatchDownload(songForDownload!!, q); songForDownload = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSongItem(song: OnlineSong, downloadState: DownloadState, playState: PlayState, onDownload: () -> Unit, onPlay: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(song.coverUrl, null, Modifier.matchParentSize())
            if (playState is PlayState.Resolving) { Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f))); CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, color = if (playState is PlayState.Playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playState is PlayState.Playing) { Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(3.dp)) }
                Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodyMedium, color = if (playState is PlayState.Playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatSec(song.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        when (downloadState) {
            is DownloadState.Idle    -> IconButton(onClick = onDownload, Modifier.size(40.dp)) { Icon(Icons.Rounded.Download, "下载", tint = MaterialTheme.colorScheme.primary) }
            is DownloadState.Loading -> Box(Modifier.size(40.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            is DownloadState.Done    -> Box(Modifier.size(40.dp), Alignment.Center) { Icon(Icons.Rounded.CheckCircle, "完成", tint = MaterialTheme.colorScheme.primary) }
            is DownloadState.Error   -> IconButton(onClick = onDownload, Modifier.size(40.dp)) { Icon(Icons.Rounded.ErrorOutline, "重试", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

// ── API 函数 ──────────────────────────────────────────────────────────────────

private suspend fun fetchKuwoRank(bangId: Int, page: Int): List<OnlineSong> = withContext(Dispatchers.IO) {
    try {
        val conn = java.net.URL("https://wapi.kuwo.cn/api/www/bang/bang/musicList?bangId=$bangId&pn=$page&rn=30").openConnection() as java.net.HttpURLConnection
        conn.apply { setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"); setRequestProperty("Referer", "https://www.kuwo.cn/"); setRequestProperty("csrf", "QWERTYUIOP"); setRequestProperty("Cookie", "kw_token=QWERTYUIOP;"); connectTimeout = 8000 }
        val json = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
        if (json.optInt("code") != 200) return@withContext emptyList()
        val list = json.getJSONObject("data").getJSONArray("musicList")
        (0 until list.length()).mapNotNull { i ->
            val item = list.getJSONObject(i)
            OnlineSong(id = item.optString("rid"), title = item.optString("name"), artist = item.optString("artist"), album = item.optString("album"), duration = item.optInt("duration"), coverUrl = item.optString("pic"), source = MusicSource.KUWO)
        }
    } catch (e: Exception) { e.printStackTrace(); emptyList() }
}

private suspend fun searchKuwo(keyword: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8")
    val conn = java.net.URL("http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=0&rn=30&uid=794762&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1").openConnection() as java.net.HttpURLConnection
    conn.apply { setRequestProperty("User-Agent", "Mozilla/5.0"); connectTimeout = 8000; readTimeout = 8000 }
    val text = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    val list = JSONObject(text).getJSONArray("abslist")
    (0 until list.length()).mapNotNull { i ->
        val item = list.getJSONObject(i)
        val title = item.optString("SONGNAME").ifBlank { item.optString("NAME") }
        val id    = item.optString("DC_TARGETID")
        if (id.isBlank() || title.isBlank()) return@mapNotNull null
        val picShort = item.optString("web_albumpic_short").ifBlank { item.optString("MVPIC") }
        val cover = when {
            picShort.startsWith("http") -> picShort
            picShort.isNotBlank()       -> "https://img2.kuwo.cn/star/albumcover/${picShort.replaceFirst(Regex("^\\d+/"), "800/")}"
            else                        -> ""
        }
        OnlineSong(id, title, item.optString("ARTIST").ifBlank { "未知" }, item.optString("ALBUM").ifBlank { "未知" }, item.optString("DURATION").toIntOrNull() ?: 0, cover, MusicSource.KUWO)
    }
}

private suspend fun resolveKuwoUrl(apiBase: String, songId: String, level: String): String? = withContext(Dispatchers.IO) {
    try { val json = JSONObject(URL("${apiBase.trimEnd('/')}/music/kw.php?id=$songId&level=$level").readText()); if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null } catch (_: Exception) { null }
}

private suspend fun fetchKuwoLyric(songId: String): String = withContext(Dispatchers.IO) {
    try {
        val conn = java.net.URL("https://wapi.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$songId&httpsStatus=1").openConnection() as java.net.HttpURLConnection
        conn.apply { setRequestProperty("User-Agent", "Mozilla/5.0"); connectTimeout = 6000; readTimeout = 6000 }
        val lrcList = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() }).getJSONObject("data").getJSONArray("lrclist")
        buildString { for (i in 0 until lrcList.length()) { val item = lrcList.getJSONObject(i); val t = item.optString("time").toDoubleOrNull() ?: continue; appendLine("[%02d:%05.2f]%s".format((t / 60).toInt(), t % 60, item.optString("lineLyric"))) } }
    } catch (_: Exception) { "" }
}

private suspend fun searchKugou(keyword: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val conn = java.net.URL("https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=30&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1").openConnection() as java.net.HttpURLConnection
        conn.apply { setRequestProperty("User-Agent", "Mozilla/5.0"); connectTimeout = 8000; readTimeout = 8000 }
        val text = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        val list = JSONObject(text).getJSONObject("data").getJSONArray("lists")
        (0 until list.length()).mapNotNull { i ->
            val item     = list.getJSONObject(i)
            val rawTitle = item.optString("SongName").ifBlank { return@mapNotNull null }
            val hash     = item.optString("FileHash").ifBlank { return@mapNotNull null }
            val cover    = item.optString("Image").replace("{size}", "480")
            // ── 歌手解析修复：SingerName 空时从标题拆分 ──────────────────────
            val singerRaw = item.optString("SingerName")
            val (title, artist) = when {
                singerRaw.isNotBlank()   -> rawTitle to singerRaw
                rawTitle.contains(" - ") -> rawTitle.split(" - ", limit = 2).let { it[0].trim() to it[1].trim() }
                else                     -> rawTitle to "未知"
            }
            OnlineSong(hash, title, artist, item.optString("AlbumName").ifBlank { "未知" }, item.optInt("Duration", 0), cover, MusicSource.KUGOU)
        }
    } catch (_: Exception) { emptyList() }
}

private suspend fun resolveKugouUrl(apiBase: String, hash: String, level: String): String? = withContext(Dispatchers.IO) {
    try { val kgBase = apiBase.trimEnd('/').replace("/music/kw.php", "").replace(Regex("/music$"), ""); val json = JSONObject(URL("$kgBase/kgqq/kg.php?id=$hash&level=$level").readText()); if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null } catch (_: Exception) { null }
}

private suspend fun fetchKugouLyric(hash: String): String = withContext(Dispatchers.IO) {
    try { val conn = java.net.URL("http://m.kugou.com/app/i/krc.php?cmd=100&hash=$hash&timelength=1").openConnection() as java.net.HttpURLConnection; conn.apply { setRequestProperty("User-Agent", "Mozilla/5.0"); connectTimeout = 6000; readTimeout = 6000 }; conn.inputStream.bufferedReader().readText().also { conn.disconnect() } } catch (_: Exception) { "" }
}

private fun formatSec(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)