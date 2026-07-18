package com.yulight.skypie.util

import com.yulight.skypie.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * 歌词搜索器
 * 从逐字歌词API获取歌词
 */
object LyricsSearcher {

    /**
     * 搜索歌词
     * @param title 歌曲标题
     * @param artist 歌手名（备用）
     * @return 歌词内容，失败返回null
     */
    suspend fun searchLyrics(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        searchFromLyricsAPI(title)
    }

    /**
     * 从逐字歌词API获取歌词
     * API地址从 local.properties → BuildConfig.LYRICS_API_URL 读取
     */
    private fun searchFromLyricsAPI(title: String): String? {
        val apiUrl = BuildConfig.LYRICS_API_URL
        if (apiUrl.isBlank()) return null
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "$apiUrl?title=$encoded"
            val json = JSONObject(URL(url).readText())
            if (json.optInt("code") == 200) {
                val data = json.getJSONArray("data")
                if (data.length() > 0) {
                    data.getJSONObject(0).optString("lyrics")
                } else null
            } else null
        } catch (_: Exception) { null }
    }
}
