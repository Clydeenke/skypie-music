package com.clydeenke.ling.ui.screen.search

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    val coverUrl : String
)

sealed class DownloadState {
    object Idle    : DownloadState()
    object Loading : DownloadState()
    object Done    : DownloadState()
    data class Error(val msg: String) : DownloadState()
}

// 封面存到 App 私有目录，用户在文件管理器里看不到
fun getCoverFile(context: Context, safeName: String): java.io.File {
    val dir = java.io.File(context.filesDir, "covers").also { it.mkdirs() }
    return java.io.File(dir, "$safeName.jpg")
}

@Composable
fun OnlineSearchScreen(
    apiBaseUrl : String,
    onBack     : () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var query       by remember { mutableStateOf("") }
    var results     by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    val downloadStates = remember { mutableStateMapOf<String, DownloadState>() }

    fun doSearch() {
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            errorMsg    = null
            results     = emptyList()
            try {
                results = searchKuwo(query)
            } catch (e: Exception) {
                errorMsg = "搜索失败：${e.javaClass.simpleName}: ${e.message}"
            }
            isSearching = false
        }
    }

    fun doDownload(song: OnlineSong) {
        scope.launch {
            downloadStates[song.id] = DownloadState.Loading
            try {
                val playUrl = resolvePlayUrl(apiBaseUrl, song.id)
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
                    // 1️⃣ 下载音频
                    val audioFile = java.io.File(musicDir, "$safeName.$ext")
                    URL(playUrl).openStream().use { input ->
                        audioFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // 2️⃣ 下载封面到 App 私有目录（用户看不到，不会乱）
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            val coverFile = getCoverFile(context, safeName)
                            URL(song.coverUrl).openStream().use { input ->
                                coverFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch (_: Exception) {}
                    }

                    // 3️⃣ 下载歌词到 App 私有目录
                    val lrcText = fetchKuwoLyric(song.id)
                    if (lrcText.isNotBlank()) {
                        val lrcDir  = java.io.File(context.filesDir, "lyrics").also { it.mkdirs() }
                        val lrcFile = java.io.File(lrcDir, "$safeName.lrc")
                        lrcFile.writeText(lrcText, Charsets.UTF_8)
                    }
                }

                downloadStates[song.id] = DownloadState.Done
            } catch (e: Exception) {
                downloadStates[song.id] = DownloadState.Error(e.message ?: "未知错误")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, "返回")
            }
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("搜索歌曲、歌手…") },
                singleLine    = true,
                modifier      = Modifier.weight(1f),
                shape         = RoundedCornerShape(24.dp),
                trailingIcon  = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, null)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            )
            IconButton(onClick = { doSearch() }, enabled = !isSearching) {
                Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isSearching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("搜索中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.WifiOff, null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            Spacer(Modifier.height(12.dp))
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            Spacer(Modifier.height(12.dp))
                            Text("输入关键词开始搜索",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (apiBaseUrl.isBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("⚠️ 请先在设置中填写 API 地址",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 108.dp)) {
                        item {
                            Text("${results.size} 条结果（酷我）",
                                style    = MaterialTheme.typography.labelMedium,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
                        }
                        itemsIndexed(results, key = { _, s -> s.id }) { index, song ->
                            OnlineSongItem(
                                song          = song,
                                downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                onDownload    = { doDownload(song) }
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

@Composable
private fun OnlineSongItem(
    song          : OnlineSong,
    downloadState : DownloadState,
    onDownload    : () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = song.coverUrl,
            contentDescription = null,
            modifier           = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title,
                style    = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("${song.artist} · ${song.album}",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatSec(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        when (downloadState) {
            is DownloadState.Idle -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Download, "下载", tint = MaterialTheme.colorScheme.primary)
                }
            }
            is DownloadState.Loading -> {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            is DownloadState.Done -> {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, "完成", tint = MaterialTheme.colorScheme.primary)
                }
            }
            is DownloadState.Error -> {
                IconButton(onClick = onDownload, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, "重试", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── 网络请求 ──────────────────────────────────────────────────────────────────

private suspend fun searchKuwo(keyword: String): List<OnlineSong> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(keyword, "UTF-8")
    val url     = "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=0&rn=30&uid=794762&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1"
    val conn    = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    conn.connectTimeout = 8000
    conn.readTimeout    = 8000
    val text = conn.inputStream.bufferedReader().readText()
    conn.disconnect()

    val json    = JSONObject(text)
    val list    = json.getJSONArray("abslist")
    val results = mutableListOf<OnlineSong>()
    for (i in 0 until list.length()) {
        val item     = list.getJSONObject(i)
        val title    = item.optString("SONGNAME").ifBlank { item.optString("NAME") }
        val artist   = item.optString("ARTIST").ifBlank { "未知艺术家" }
        val id       = item.optString("DC_TARGETID")
        val album    = item.optString("ALBUM").ifBlank { "未知专辑" }
        val dur      = item.optString("DURATION").toIntOrNull() ?: 0
        val picShort = item.optString("web_albumpic_short").ifBlank { item.optString("MVPIC") }
        val cover    = when {
            picShort.startsWith("http") -> picShort
            picShort.isNotBlank() -> {
                val hdPath = picShort.replaceFirst(Regex("^\\d+/"), "500/")
                "https://img2.kuwo.cn/star/albumcover/$hdPath"
            }
            else -> ""
        }
        if (id.isNotBlank() && title.isNotBlank()) {
            results.add(OnlineSong(id, title, artist, album, dur, cover))
        }
    }
    results
}

private suspend fun resolvePlayUrl(apiBase: String, songId: String): String? = withContext(Dispatchers.IO) {
    try {
        val base = apiBase.trimEnd('/')
        val text = URL("$base/music/kw.php?id=$songId&level=lossless").readText()
        val json = JSONObject(text)
        if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
    } catch (e: Exception) { null }
}

private suspend fun fetchKuwoLyric(songId: String): String = withContext(Dispatchers.IO) {
    try {
        val url  = "https://wapi.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$songId&httpsStatus=1"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = 6000
        conn.readTimeout    = 6000
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json    = JSONObject(text)
        val lrcList = json.getJSONObject("data").getJSONArray("lrclist")
        val sb      = StringBuilder()
        for (i in 0 until lrcList.length()) {
            val item    = lrcList.getJSONObject(i)
            val timeSec = item.optString("time").toDoubleOrNull() ?: continue
            val lyric   = item.optString("lineLyric")
            val min     = (timeSec / 60).toInt()
            val sec     = timeSec % 60
            sb.appendLine("[%02d:%05.2f]%s".format(min, sec, lyric))
        }
        sb.toString()
    } catch (e: Exception) { "" }
}

private fun formatSec(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)