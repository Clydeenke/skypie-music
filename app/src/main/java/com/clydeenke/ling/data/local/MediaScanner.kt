package com.clydeenke.ling.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.ui.screen.search.getCoverFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaScanner"

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scan(
        folderUriStrings: List<String>,
        onLog: (ScanLog) -> Unit = {}
    ): List<Song> {

        onLog(ScanLog(message = "🔍 开始扫描，目标文件夹数：${folderUriStrings.size}"))

        return if (folderUriStrings.isEmpty()) {
            onLog(ScanLog(level = ScanLog.Level.WARN, message = "⚠️ 未指定文件夹，将扫描全部 MediaStore"))
            scanAllMediaStore(onLog)
        } else {
            val allSongs = mutableMapOf<Long, Song>()
            folderUriStrings.forEach { uriStr ->
                val songs = scanByFolderUri(uriStr, onLog)
                songs.forEach { allSongs[it.id] = it }
            }
            val result = allSongs.values.toList()
            onLog(ScanLog(level = ScanLog.Level.SUCCESS, message = "✅ 扫描完成，共找到 ${result.size} 首歌曲"))
            result
        }
    }

    private fun scanAllMediaStore(onLog: (ScanLog) -> Unit): List<Song> {
        val selection = "${MediaStore.Audio.Media.DURATION} >= 10000"
        return querySongs(selection = selection, selectionArgs = null, folderHint = "全部", onLog = onLog)
    }

    private fun scanByFolderUri(uriStr: String, onLog: (ScanLog) -> Unit): List<Song> {
        return try {
            val folderUri  = Uri.parse(uriStr)
            val folderPath = resolveFolderPath(folderUri)

            onLog(ScanLog(message = "📂 扫描文件夹：$folderPath"))

            if (folderPath == null) {
                onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 无法解析文件夹路径，跳过：$uriStr"))
                return emptyList()
            }

            val selection     = "${MediaStore.Audio.Media.DURATION} >= 10000 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$folderPath%")
            val songs         = querySongs(selection, selectionArgs, folderPath, onLog)

            if (songs.isEmpty()) {
                onLog(ScanLog(level = ScanLog.Level.WARN, message = "⚠️ 此文件夹内没有找到音频：$folderPath"))
            }

            songs
        } catch (e: Exception) {
            onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 扫描出错：${e.message}"))
            Log.e(TAG, "scanByFolderUri error", e)
            emptyList()
        }
    }

    private fun querySongs(
        selection     : String,
        selectionArgs : Array<String>?,
        folderHint    : String,
        onLog         : (ScanLog) -> Unit
    ): List<Song> {

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        val songs = mutableListOf<Song>()

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->

                onLog(ScanLog(message = "📋 查询到 ${cursor.count} 条记录（文件夹：$folderHint）"))

                if (cursor.count == 0) {
                    onLog(ScanLog(level = ScanLog.Level.WARN, message = "⚠️ 查询结果为空"))
                    return emptyList()
                }

                val colId       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val colTitle    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val colArtist   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val colAlbum    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val colDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val colSize     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val colDate     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val colAlbumId  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val colData     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id      = cursor.getLong(colId)
                    val albumId = cursor.getLong(colAlbumId)
                    val data    = cursor.getString(colData) ?: ""

                    // 文件名（不含扩展名），用来找私有目录里的封面
                    val fileNameNoExt = data.substringAfterLast("/").substringBeforeLast(".")

                    // 优先级：① App私有目录封面（云端下载）→ ② MediaStore封面（本地歌曲）
                    val privateCover = getCoverFile(context, fileNameNoExt)
                    val albumArtUri = if (privateCover.exists()) {
                        "file://${privateCover.absolutePath}"
                    } else {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString()
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()

                    songs.add(Song(
                        id          = id,
                        title       = cursor.getString(colTitle)?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                        artist      = cursor.getString(colArtist)?.takeIf { it != "<unknown>" } ?: "未知艺术家",
                        album       = cursor.getString(colAlbum)?.takeIf  { it != "<unknown>" } ?: "未知专辑",
                        duration    = cursor.getLong(colDuration),
                        uri         = contentUri,
                        albumArtUri = albumArtUri,
                        size        = cursor.getLong(colSize),
                        dateAdded   = cursor.getLong(colDate),
                        folderPath  = data.substringBeforeLast("/"),
                        filePath    = data
                    ))
                }
            } ?: onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ ContentResolver.query 返回 null"))

        } catch (e: SecurityException) {
            onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 权限被拒绝：${e.message}"))
            Log.e(TAG, "Permission denied", e)
        } catch (e: Exception) {
            onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 未知错误：${e.message}"))
            Log.e(TAG, "Scan error", e)
        }

        return songs
    }

    private fun resolveFolderPath(uri: Uri): String? {
        val segment = uri.lastPathSegment ?: return null
        return when {
            segment.startsWith("primary:") -> "/storage/emulated/0/" + segment.removePrefix("primary:")
            segment.contains(":") -> {
                val parts = segment.split(":", limit = 2)
                "/storage/${parts[0]}/${parts.getOrElse(1) { "" }}"
            }
            else -> null
        }
    }
}