package com.yulight.skypie.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线音乐网络层
 *
 * 说明：
 *   搜索/榜单接口使用公开的 Web 端接口，仅供个人学习研究。
 *   播放链接解析依赖用户自行搭建的兼容 API 服务，本应用不内置任何解析逻辑。
 *
 * 用户自建 API 接口规范见 README.md。
 */
@Singleton
class OnlineMusicApi @Inject constructor() {

    // ── 酷我 ──────────────────────────────────────────────────────────────────

    /**
     * 获取酷我榜单歌曲
     * @param rankId 榜单 ID（见 [KUWO_RANKS]）
     * @param page   页码，从 1 开始
     */
    suspend fun fetchKuwoRank(rankId: Int, page: Int): List<OnlineSong> = withContext(Dispatchers.IO) {
        try {
            val conn = openKuwoConnection(
                "https://wapi.kuwo.cn/api/www/bang/bang/musicList?bangId=$rankId&pn=$page&rn=30"
            )
            val json = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
            if (json.optInt("code") != 200) return@withContext emptyList()
            val list = json.getJSONObject("data").getJSONArray("musicList")
            (0 until list.length()).mapNotNull { i ->
                val item = list.getJSONObject(i)
                OnlineSong(
                    id       = item.optString("rid").ifBlank { return@mapNotNull null },
                    title    = item.optString("name").ifBlank { return@mapNotNull null },
                    artist   = item.optString("artist").ifBlank { "未知" },
                    album    = item.optString("album").ifBlank { "未知" },
                    duration = item.optInt("duration"),
                    coverUrl = item.optString("pic"),
                    source   = MusicSource.KUWO
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 搜索酷我歌曲
     * @param page 页码，从 1 开始
     */
    suspend fun searchKuwo(keyword: String, page: Int = 1): List<OnlineSong> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val conn = openConnection(
                "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=${page - 1}&rn=30" +
                        "&uid=794762&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
                        "&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&mobi=1&issubtitle=1"
            )
            val text = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
            val list = JSONObject(text).getJSONArray("abslist")
            (0 until list.length()).mapNotNull { i ->
                val item  = list.getJSONObject(i)
                val title = item.optString("SONGNAME").ifBlank { item.optString("NAME") }
                val id    = item.optString("DC_TARGETID")
                if (id.isBlank() || title.isBlank()) return@mapNotNull null
                val picShort = item.optString("web_albumpic_short").ifBlank { item.optString("MVPIC") }
                val cover = when {
                    picShort.startsWith("http") -> picShort
                    picShort.isNotBlank()        ->
                        "https://img2.kuwo.cn/star/albumcover/${picShort.replaceFirst(Regex("^\\d+/"), "1200/")}"
                    else -> ""
                }
                OnlineSong(
                    id       = id,
                    title    = title,
                    artist   = item.optString("ARTIST").ifBlank { "未知" },
                    album    = item.optString("ALBUM").ifBlank { "未知" },
                    duration = item.optString("DURATION").toIntOrNull() ?: 0,
                    coverUrl = cover,
                    source   = MusicSource.KUWO
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取酷我歌词（LRC 格式）
     */
    suspend fun fetchKuwoLyric(songId: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection(
                "https://wapi.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$songId&httpsStatus=1"
            )
            val lrcList = JSONObject(
                conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
            ).getJSONObject("data").getJSONArray("lrclist")
            buildString {
                for (i in 0 until lrcList.length()) {
                    val item = lrcList.getJSONObject(i)
                    val t    = item.optString("time").toDoubleOrNull() ?: continue
                    appendLine("[%02d:%05.2f]%s".format((t / 60).toInt(), t % 60, item.optString("lineLyric")))
                }
            }
        } catch (_: Exception) { "" }
    }

    // ── 酷狗 ──────────────────────────────────────────────────────────────────

    /**
     * 搜索酷狗歌曲
     * @param page 页码，从 1 开始
     */
    suspend fun searchKugou(keyword: String, page: Int = 1): List<OnlineSong> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val conn = openConnection(
                "https://songsearch.kugou.com/song_search_v2?keyword=$encoded" +
                        "&page=$page&pagesize=30&userid=0&clientver=&platform=WebFilter" +
                        "&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
            )
            val text = conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
            val list = JSONObject(text).getJSONObject("data").getJSONArray("lists")
            (0 until list.length()).mapNotNull { i ->
                val item     = list.getJSONObject(i)
                val rawTitle = item.optString("SongName").ifBlank { return@mapNotNull null }
                val hash     = item.optString("FileHash").ifBlank { return@mapNotNull null }
                val cover    = item.optString("Image").replace("{size}", "800")
                val singerRaw = item.optString("SingerName")
                val (title, artist) = when {
                    singerRaw.isNotBlank()   -> rawTitle to singerRaw
                    rawTitle.contains(" - ") ->
                        rawTitle.split(" - ", limit = 2).let { it[0].trim() to it[1].trim() }
                    else -> rawTitle to "未知"
                }
                OnlineSong(
                    id       = hash,
                    title    = title,
                    artist   = artist,
                    album    = item.optString("AlbumName").ifBlank { "未知" },
                    duration = item.optInt("Duration", 0),
                    coverUrl = cover,
                    source   = MusicSource.KUGOU
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 获取酷狗歌词
     */
    suspend fun fetchKugouLyric(hash: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection(
                "http://m.kugou.com/app/i/krc.php?cmd=100&hash=$hash&timelength=1"
            )
            conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        } catch (_: Exception) { "" }
    }

    // ── 用户自建 API（播放链接解析） ──────────────────────────────────────────
    // 本应用不内置解析逻辑，所有流媒体 URL 均通过用户配置的 API 服务获取。
    // 接口规范见 README.md。

    /**
     * 通过用户自建 API 解析酷我播放链接
     * @param apiBase 用户在设置中填入的 API 根地址
     * @param songId  酷我歌曲 ID
     * @param level   音质等级（standard / high / lossless）
     */
    suspend fun resolveKuwoPlayUrl(apiBase: String, songId: String, level: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(
                    URL("${apiBase.trimEnd('/')}/music/kw.php?id=$songId&level=$level").readText()
                )
                if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
            } catch (_: Exception) { null }
        }

    /**
     * 通过用户自建 API 解析酷狗播放链接
     */
    suspend fun resolveKugouPlayUrl(apiBase: String, hash: String, level: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val base = apiBase.trimEnd('/')
                    .replace("/music/kw.php", "")
                    .replace(Regex("/music$"), "")
                val json = JSONObject(URL("$base/kgqq/kg.php?id=$hash&level=$level").readText())
                if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
            } catch (_: Exception) { null }
        }

    // ── 工具函数 ──────────────────────────────────────────────────────────────

    private fun openConnection(url: String, timeoutMs: Int = 8000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            connectTimeout = timeoutMs
            readTimeout    = timeoutMs
        }

    /** 酷我接口需要额外的 Referer 和 token header */
    private fun openKuwoConnection(url: String): HttpURLConnection =
        openConnection(url).apply {
            setRequestProperty("Referer", "https://www.kuwo.cn/")
            // 酷我 Web 接口的 CSRF 校验值，非敏感凭据，为公开请求参数
            setRequestProperty("csrf",   "skypie_APP")
            setRequestProperty("Cookie", "kw_token=skypie_APP;")
        }
}