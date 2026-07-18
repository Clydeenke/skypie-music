package com.yulight.skypie.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CacheManager {

    private const val MAX_CACHE = 200

    private fun getFile(context: Context) = File(context.filesDir, "song_cache.json")

    fun getCached(context: Context): List<FavoriteSong> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FavoriteSong(
                    songId = obj.optString("songId", ""),
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    coverUrl = obj.optString("coverUrl", ""),
                    source = obj.optString("source", ""),
                    duration = obj.optInt("duration", 0),
                    streamUrl = obj.optString("streamUrl", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addCache(context: Context, song: FavoriteSong) {
        val list = getCached(context).toMutableList()
        list.removeAll { it.songId == song.songId }
        list.add(0, song)
        val trimmed = if (list.size > MAX_CACHE) list.take(MAX_CACHE) else list
        saveList(context, trimmed)
    }

    fun removeCache(context: Context, songId: String) {
        val list = getCached(context).toMutableList()
        list.removeAll { it.songId == songId }
        saveList(context, list)
    }

    fun clearCache(context: Context) {
        getFile(context).delete()
    }

    private fun saveList(context: Context, list: List<FavoriteSong>) {
        val arr = JSONArray()
        list.forEach { song ->
            arr.put(JSONObject().apply {
                put("songId", song.songId)
                put("title", song.title)
                put("artist", song.artist)
                put("coverUrl", song.coverUrl)
                put("source", song.source)
                put("duration", song.duration)
                put("streamUrl", song.streamUrl)
            })
        }
        getFile(context).writeText(arr.toString())
    }
}
