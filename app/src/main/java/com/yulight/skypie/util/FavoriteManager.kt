package com.yulight.skypie.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FavoriteSong(
    val songId: String,
    val title: String,
    val artist: String,
    val coverUrl: String = "",
    val source: String = "",
    val duration: Int = 0,
    val streamUrl: String = ""
)

object FavoriteManager {

    private fun getFile(context: Context) = File(context.filesDir, "favorites.json")

    fun getFavorites(context: Context): List<FavoriteSong> {
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

    fun isFavorite(context: Context, songId: String): Boolean {
        return getFavorites(context).any { it.songId == songId }
    }

    fun addFavorite(context: Context, song: FavoriteSong) {
        if (isFavorite(context, song.songId)) return
        val list = getFavorites(context).toMutableList()
        list.add(0, song)
        saveList(context, list)
    }

    fun removeFavorite(context: Context, songId: String) {
        val list = getFavorites(context).toMutableList()
        list.removeAll { it.songId == songId }
        saveList(context, list)
    }

    fun toggleFavorite(context: Context, song: FavoriteSong): Boolean {
        return if (isFavorite(context, song.songId)) {
            removeFavorite(context, song.songId)
            false
        } else {
            addFavorite(context, song)
            true
        }
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
