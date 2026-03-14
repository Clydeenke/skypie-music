package com.clydeenke.ling.ui.screen.search

import android.os.Environment
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
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

// 在线播放状态（每首歌独立）
sealed class PlayState {
    object Idle       : PlayState()
    object Resolving  : PlayState()   // 正在获取播放链接
    object Playing    : PlayState()   // 当前正在播放这首
    data class Error(val msg: String) : PlayState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    apiBaseUrl         : String,
    viewModel          : MusicViewModel,              // ← 新增，用于触发在线播放
    onBack             : () -> Unit = {},
    onDownloadComplete : () -> Unit = {},
    onOpenPlayer       : () -> Unit = {}              // ← 新增，播放后打开全屏播放器
){
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val tabs       = listOf("酷我", "酷狗")
    val pagerState = rememberPagerState(pageCount = { 2 })

    var query by remember { mutableStateOf("") }

    val results        = remember { mutableStateMapOf<Int, List<OnlineSong>>() }
    val isSearching    = remember { mutableStateMapOf<Int, Boolean>() }
    val errorMsgs      = remember { mutableStateMapOf<Int, String?>() }
    val downloadStates = remember { mutableStateMapOf<String, DownloadState>() }
    val playStates     = remember { mutableStateMapOf<String, PlayState>() }

    // 当前正在播放的在线歌曲ID（同一时间只有一首）
    var currentPlayingId by remember { mutableStateOf<String?>(null) }

    fun doSearch(tabIndex: Int = pagerState.currentPage) {
        if (query.isBlank()) return
        scope.launch {
            isSearching[tabIndex] = true
            errorMsgs[tabIndex]   = null
            results[tabIndex]     = emptyList()
            try {
                results[tabIndex] = when (tabIndex) {
                    0    -> searchKuwo(query)
                    1    -> searchKugou(query)
                    else -> emptyList()
                }
            } catch (e: Exception) {
                errorMsgs[tabIndex] = "搜索失败：${e.message}"
            }
            isSearching[tabIndex] = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val idx = pagerState.currentPage
        if (query.isNotBlank() && results[idx] == null) doSearch(idx)
    }

    // 在线播放：解析URL → 获取歌词 → 调PlayerController直接播放
    fun doPlay(song: OnlineSong) {
        scope.launch {
            // 如果点了正在播放的那首，直接打开播放器
            if (currentPlayingId == song.id) {
                onOpenPlayer()
                return@launch
            }

            // 标记"解析中"
            playStates[song.id] = PlayState.Resolving
            currentPlayingId?.let { playStates[it] = PlayState.Idle }
            currentPlayingId = song.id

            try {
                val streamUrl = when (song.source) {
                    MusicSource.KUWO   -> resolveKuwoUrl(apiBaseUrl, song.id)
                    MusicSource.KUGOU  -> resolveKugouUrl(apiBaseUrl, song.id)
                }
                if (streamUrl.isNullOrBlank()) {
                    playStates[song.id] = PlayState.Error("获取链接失败")
                    currentPlayingId = null
                    return@launch
                }

                val lrcText = withContext(Dispatchers.IO) {
                    when (song.source) {
                        MusicSource.KUWO   -> fetchKuwoLyric(song.id)
                        MusicSource.KUGOU  -> fetchKugouLyric(song.id)
                    }
                }

                // 直接播放，不下载，把歌词文本也传进去
                viewModel.playerController.playOnlineStream(
                    streamUrl = streamUrl,
                    title     = song.title,
                    artist    = song.artist,
                    coverUrl  = song.coverUrl,
                    songId    = song.id,
                    lrcText   = lrcText
                )
                playStates[song.id] = PlayState.Playing
                onOpenPlayer()
            } catch (e: Exception) {
                playStates[song.id] = PlayState.Error(e.message ?: "播放失败")
                currentPlayingId = null
            }
        }
    }

    fun doDownload(song: OnlineSong) {
        scope.launch {
            downloadStates[song.id] = DownloadState.Loading
            try {
                val playUrl = when (song.source) {
                    MusicSource.KUWO    -> resolveKuwoUrl(apiBaseUrl, song.id)
                    MusicSource.KUGOU   -> resolveKugouUrl(apiBaseUrl, song.id)
                }
                if (playUrl.isNullOrBlank()) {
                    downloadStates[song.id] = DownloadState.Error("获取链接失败")
                    return@launch
                }
                val ext = when {
                    playUrl.contains(".flac") -> "flac"
                    playUrl.contains(".aac")  -> "aac"
                    playUrl.contains(".ogg")  -> "ogg"
                    else                      -> "mp3"
                }
                val safeName = "${song.title} - ${song.artist}"
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

                withContext(Dispatchers.IO) {
                    val audioFile = java.io.File(musicDir, "$safeName.$ext")
                    URL(playUrl).openStream().use { i -> audioFile.outputStream().use { o -> i.copyTo(o) } }

                    if (song.coverUrl.isNotBlank()) {
                        try {
                            val coverFile = java.io.File(musicDir, "$safeName.jpg")
                            URL(song.coverUrl).openStream().use { i -> coverFile.outputStream().use { o -> i.copyTo(o) } }
                        } catch (_: Exception) {}
                    }

                    val lrcText = when (song.source) {
                        MusicSource.KUWO    -> fetchKuwoLyric(song.id)
                        MusicSource.KUGOU   -> fetchKugouLyric(song.id)
                    }
                    if (lrcText.isNotBlank()) {
                        java.io.File(musicDir, "$safeName.lrc").writeText(lrcText, Charsets.UTF_8)
                    }

                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(java.io.File(musicDir, "$safeName.$ext").absolutePath),
                        null, null
                    )
                }
                downloadStates[song.id] = DownloadState.Done
                onDownloadComplete()
            } catch (e: Exception) {
                downloadStates[song.id] = DownloadState.Error(e.message ?: "未知错误")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ── 顶部栏 ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "返回") }
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("搜索歌曲、歌手…") },
                singleLine    = true,
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                trailingIcon  = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            )
            IconButton(onClick = {
                results.clear()
                doSearch(pagerState.currentPage)
            }) {
                Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // ── Tab 栏 ────────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text     = { Text(title) }
                )
            }
        }

        // ── 内容 Pager ────────────────────────────────────────────────────────
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageResults   = results[page]
            val pageSearching = isSearching[page] == true
            val pageError     = errorMsgs[page]

            Box(Modifier.fillMaxSize()) {
                when {
                    pageSearching -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("搜索中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    pageError != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.WifiOff, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            Spacer(Modifier.height(12.dp))
                            Text(pageError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    pageResults.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            Spacer(Modifier.height(12.dp))
                            Text("输入关键词搜索",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 108.dp)) {
                        item {
                            Text("${pageResults.size} 条结果",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
                        }
                        itemsIndexed(pageResults, key = { _, s -> s.id }) { index, song ->
                            OnlineSongItem(
                                song          = song,
                                downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                playState     = playStates[song.id] ?: PlayState.Idle,
                                onDownload    = { doDownload(song) },
                                onPlay        = { doPlay(song) }
                            )
                            if (index < pageResults.lastIndex) {
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

@Composable
private fun OnlineSongItem(
    song          : OnlineSong,
    downloadState : DownloadState,
    playState     : PlayState,
    onDownload    : () -> Unit,
    onPlay        : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行点击 = 播放
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面：正在解析时显示转圈
        Box(
            modifier         = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(song.coverUrl, null, Modifier.matchParentSize())
            if (playState is PlayState.Resolving) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color       = Color.White
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style    = MaterialTheme.typography.titleMedium,
                color    = if (playState is PlayState.Playing)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 播放中显示小均衡器图标
                if (playState is PlayState.Playing) {
                    Icon(Icons.Rounded.GraphicEq, null,
                        modifier = Modifier.size(13.dp),
                        tint     = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    "${song.artist} · ${song.album}",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (playState is PlayState.Playing)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 时长
        Text(
            formatSec(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))

        // 只保留下载按钮
        when (downloadState) {
            is DownloadState.Idle    -> IconButton(onClick = onDownload, Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Download, "下载",
                    tint = MaterialTheme.colorScheme.primary)
            }
            is DownloadState.Loading -> Box(Modifier.size(40.dp), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            is DownloadState.Done    -> Box(Modifier.size(40.dp), Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, "完成",
                    tint = MaterialTheme.colorScheme.primary)
            }
            is DownloadState.Error   -> IconButton(onClick = onDownload, Modifier.size(40.dp)) {
                Icon(Icons.Rounded.ErrorOutline, "重试",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── 酷我 ──────────────────────────────────────────────────────────────────────

private suspend fun searchKuwo(keyword: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8")
    val url     = "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=0&rn=30&uid=794762&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1"
    val conn    = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    conn.connectTimeout = 8000; conn.readTimeout = 8000
    val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()

    val list = JSONObject(text).getJSONArray("abslist")
    (0 until list.length()).mapNotNull { i ->
        val item  = list.getJSONObject(i)
        val title = item.optString("SONGNAME").ifBlank { item.optString("NAME") }
        val id    = item.optString("DC_TARGETID")
        if (id.isBlank() || title.isBlank()) return@mapNotNull null
        val picShort = item.optString("web_albumpic_short").ifBlank { item.optString("MVPIC") }
        val cover = when {
            picShort.startsWith("http") -> picShort
            picShort.isNotBlank() -> "https://img2.kuwo.cn/star/albumcover/${picShort.replaceFirst(Regex("^\\d+/"), "800/")}"
            else -> ""
        }
        OnlineSong(id, title,
            item.optString("ARTIST").ifBlank { "未知艺术家" },
            item.optString("ALBUM").ifBlank { "未知专辑" },
            item.optString("DURATION").toIntOrNull() ?: 0,
            cover, MusicSource.KUWO)
    }
}

private suspend fun resolveKuwoUrl(apiBase: String, songId: String): String? = withContext(Dispatchers.IO) {
    try {
        val text = URL("${apiBase.trimEnd('/')}/music/kw.php?id=$songId&level=lossless").readText()
        val json = JSONObject(text)
        if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
    } catch (_: Exception) { null }
}

private suspend fun fetchKuwoLyric(songId: String): String = withContext(Dispatchers.IO) {
    try {
        val conn = java.net.URL("https://wapi.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$songId&httpsStatus=1")
            .openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
        val lrcList = JSONObject(text).getJSONObject("data").getJSONArray("lrclist")
        buildString {
            for (i in 0 until lrcList.length()) {
                val item    = lrcList.getJSONObject(i)
                val timeSec = item.optString("time").toDoubleOrNull() ?: continue
                appendLine("[%02d:%05.2f]%s".format((timeSec / 60).toInt(), timeSec % 60, item.optString("lineLyric")))
            }
        }
    } catch (_: Exception) { "" }
}

// ── 酷狗 ──────────────────────────────────────────────────────────────────────

private suspend fun searchKugou(keyword: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url     = "https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=1&pagesize=30&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        val conn    = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()

        val list = JSONObject(text).getJSONObject("data").getJSONArray("lists")
        (0 until list.length()).mapNotNull { i ->
            val item  = list.getJSONObject(i)
            val title = item.optString("SongName").ifBlank { return@mapNotNull null }
            val hash  = item.optString("FileHash").ifBlank { return@mapNotNull null }
            val cover = item.optString("Image").replace("{size}", "800")
            OnlineSong(
                id       = hash,
                title    = title,
                artist   = item.optString("SingerName").ifBlank { "未知艺术家" },
                album    = item.optString("AlbumName").ifBlank { "未知专辑" },
                duration = item.optInt("Duration", 0),
                coverUrl = cover,
                source   = MusicSource.KUGOU
            )
        }
    } catch (_: Exception) { emptyList() }
}

private suspend fun resolveKugouUrl(apiBase: String, hash: String): String? = withContext(Dispatchers.IO) {
    try {
        val base   = apiBase.trimEnd('/')
        val kgBase = base.replace("/music/kw.php", "").replace(Regex("/music$"), "")
        val text   = URL("$kgBase/kgqq/kg.php?id=$hash&level=standard").readText()
        val json   = JSONObject(text)
        if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
    } catch (_: Exception) { null }
}

private suspend fun fetchKugouLyric(hash: String): String = withContext(Dispatchers.IO) {
    try {
        val url  = "http://m.kugou.com/app/i/krc.php?cmd=100&hash=$hash&timelength=1"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        text
    } catch (_: Exception) { "" }
}

private fun formatSec(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)