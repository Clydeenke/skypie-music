package com.yulight.skypie.data.remote

import android.util.Log
import com.yulight.skypie.util.KrcDecryptor
import com.yulight.skypie.util.QrcDecryptor
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
 *   搜索/榜单接口使用公开的 We b 端接口，仅供个人学习研究。
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
                        "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
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
            val encrypted = conn.inputStream.readBytes().also { conn.disconnect() }
            // 尝试解密KRC格式
            val decrypted = KrcDecryptor.decrypt(encrypted)
            decrypted ?: String(encrypted)
        } catch (_: Exception) { "" }
    }

    // ── 网易云 ──────────────────────────────────────────────────────────────────

    /**
     * 搜索网易云歌曲
     * @param page 页码，从 1 开始
     */
    suspend fun searchNetease(keyword: String, page: Int = 1): List<OnlineSong> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val conn = openConnection(
                "http://interface3.music.163.com/api/search/get/web?s=$encoded&type=1&limit=30&offset=${(page - 1) * 30}"
            )
            val json = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
            val songs = json.getJSONObject("result").getJSONArray("songs")

            // 收集所有歌曲ID
            val songIds = mutableListOf<String>()
            for (i in 0 until songs.length()) {
                val id = songs.getJSONObject(i).optString("id")
                if (id.isNotBlank()) songIds.add(id)
            }

            // 批量调用歌曲详情API获取封面URL
            val coverMap = mutableMapOf<String, String>()
            if (songIds.isNotEmpty()) {
                try {
                    val idsParam = songIds.joinToString(",")
                    val detailConn = openConnection(
                        "http://music.163.com/api/song/detail?id=${songIds[0]}&ids=[$idsParam]"
                    )
                    val detailJson = JSONObject(detailConn.inputStream.bufferedReader().readText().also { detailConn.disconnect() })
                    val detailSongs = detailJson.getJSONArray("songs")
                    for (j in 0 until detailSongs.length()) {
                        val ds = detailSongs.getJSONObject(j)
                        val dsId = ds.optString("id")
                        val picUrl = ds.optJSONObject("album")?.optString("picUrl") ?: ""
                        if (picUrl.isNotBlank()) coverMap[dsId] = picUrl
                    }
                } catch (_: Exception) {}
            }

            // 构建搜索结果
            (0 until songs.length()).mapNotNull { i ->
                val item = songs.getJSONObject(i)
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                val artists = item.getJSONArray("artists")
                val artist = if (artists.length() > 0) artists.getJSONObject(0).optString("name") else "未知"
                val albumObj = item.optJSONObject("album")
                val album = albumObj?.optString("name") ?: "未知"
                val cover = coverMap[id] ?: ""
                OnlineSong(
                    id       = id,
                    title    = name,
                    artist   = artist,
                    album    = album,
                    duration = item.optInt("duration", 0) / 1000,
                    coverUrl = cover,
                    source   = MusicSource.NETEASE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取网易云歌词（LRC 格式）
     */
    suspend fun fetchNeteaseLyric(songId: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection(
                "http://interface3.music.163.com/api/song/lyric?id=$songId&os=Linux&lv=-1&kv=-1&tv=-1"
            )
            JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
                .getJSONObject("lrc").optString("lyric", "")
        } catch (_: Exception) { "" }
    }

    // ── QQ 音乐 ──────────────────────────────────────────────────────────────────

    /**
     * 搜索 QQ 音乐歌曲
     * @param page 页码，从 1 开始
     */
    suspend fun searchQQ(keyword: String, page: Int = 1): List<OnlineSong> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("comm", JSONObject().apply {
                    put("ct", "11"); put("cv", "14090508"); put("v", "14090508")
                    put("tmeAppID", "qqmusic"); put("phonetype", "EBG-AN10")
                    put("deviceScore", "553.47"); put("devicelevel", "50")
                    put("newdevicelevel", "20"); put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0")
                    put("os_ver", "12"); put("OpenUDID", "0"); put("OpenUDID2", "0")
                    put("QIMEI36", "0"); put("udid", "0"); put("chid", "0")
                    put("aid", "0"); put("oaid", "0"); put("taid", "0")
                    put("tid", "0"); put("wid", "0"); put("uid", "0")
                    put("sid", "0"); put("modeSwitch", "6"); put("teenMode", "0")
                    put("ui_mode", "2"); put("nettype", "1020"); put("v4ip", "")
                })
                put("req", JSONObject().apply {
                    put("module", "music.search.SearchCgiService")
                    put("method", "DoSearchForQQMusicMobile")
                    put("param", JSONObject().apply {
                        put("search_type", 0); put("query", keyword)
                        put("page_num", page); put("num_per_page", 30)
                        put("highlight", 0); put("nqc_flag", 0)
                        put("multi_zhida", 0); put("cat", 2)
                        put("grp", 1); put("sin", 30); put("sem", 0)
                    })
                })
            }
            val conn = (URL("https://u.y.qq.com/cgi-bin/musicu.fcg").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "QQMusic 14090508(android 12)")
                connectTimeout = 8000; readTimeout = 8000
                doOutput = true
            }
            conn.outputStream.write(body.toString().toByteArray())
            val json = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
            val songs = json.getJSONObject("req").getJSONObject("data").getJSONObject("body").getJSONArray("item_song")
            (0 until songs.length()).mapNotNull { i ->
                val item = songs.getJSONObject(i)
                val mid = item.optString("mid")
                val name = item.optString("name")
                if (mid.isBlank() || name.isBlank()) return@mapNotNull null
                val singers = item.getJSONArray("singer")
                val artist = if (singers.length() > 0) singers.getJSONObject(0).optString("name") else "未知"
                val album = item.optJSONObject("album")?.optString("name") ?: "未知"
                val pmid = item.optJSONObject("album")?.optString("pmid") ?: ""
                val cover = if (pmid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R300x300M000${pmid}.jpg" else ""
                OnlineSong(
                    id       = mid,
                    title    = name,
                    artist   = artist,
                    album    = album,
                    duration = item.optInt("interval", 0),
                    coverUrl = cover,
                    source   = MusicSource.QQ
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取 QQ 音乐歌词
     */
    suspend fun fetchQQLyric(songMid: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json&nobase64=1"
            )
            conn.setRequestProperty("Referer", "https://y.qq.com/")
            val json = JSONObject(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
            val lyric = json.optString("lyric", "")
            // 尝试解密QRC格式
            if (QrcDecryptor.isQrcFormat(lyric)) {
                QrcDecryptor.decrypt(lyric) ?: lyric
            } else {
                lyric
            }
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
                val requestUrl = "${apiBase.trimEnd('/')}/music/kw.php?id=$songId&level=$level"
                Log.d("KuwoAPI", "Request: $requestUrl")
                val json = JSONObject(URL(requestUrl).readText())
                Log.d("KuwoAPI", "Response: $json")
                if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
            } catch (e: Exception) {
                Log.e("KuwoAPI", "Error: ${e.message}")
                null
            }
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
                val requestUrl = "$base/kgqq1/kg.php?id=$hash&level=$level"
                Log.d("KugouAPI", "Request: $requestUrl")
                val json = JSONObject(URL(requestUrl).readText())
                Log.d("KugouAPI", "Response: $json")
                if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
            } catch (e: Exception) {
                Log.e("KugouAPI", "Error: ${e.message}")
                null
            }
        }

    /**
     * 通过用户自建 API 解析网易云播放链接
     */
    suspend fun resolveNeteasePlayUrl(apiBase: String, songId: String, level: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val ts = System.currentTimeMillis()
                val base = apiBase.trimEnd('/')
                    .replace(Regex("/music/kw.php$"), "")
                    .replace(Regex("/music$"), "")
                val requestUrl = "$base/wy/wy_proxy.php?id=$songId&type=json&level=$level&_ts=$ts&_nc=skypie"
                Log.d("NeteaseAPI", "Request: $requestUrl")
                val json = JSONObject(URL(requestUrl).readText())
                Log.d("NeteaseAPI", "Response: $json")
                if (json.optInt("code") == 200) json.getJSONObject("data").optString("url") else null
            } catch (e: Exception) {
                Log.e("NeteaseAPI", "Error: ${e.message}")
                null
            }
        }

    /**
     * 通过用户自建 API 解析 QQ 音乐播放链接
     */
    suspend fun resolveQQPlayUrl(apiBase: String, songMid: String, level: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val base = apiBase.trimEnd('/')
                    .replace(Regex("/music/kw.php$"), "")
                    .replace(Regex("/music$"), "")
                val requestUrl = "$base/music/qq_song_kw.php?id=$songMid&level=$level"
                Log.d("QQAPI", "Request: $requestUrl")
                val json = JSONObject(URL(requestUrl).readText())
                Log.d("QQAPI", "Response: $json")
                if (json.optInt("code") == 200) {
                    val url = json.getJSONObject("data").optString("url")
                    if (url.isNotBlank() && url != "None") url else null
                } else null
            } catch (e: Exception) {
                Log.e("QQAPI", "Error: ${e.message}")
                null
            }
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