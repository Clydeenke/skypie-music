package com.clydeenke.ling.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaScanner"

@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 核心扫描方法。
     *
     * @param folderUriStrings 用户选择的文件夹 URI 列表（SAF URI）。
     * 传空列表 = 全局扫描（不推荐，权限不足容易失败）。
     * @param onLog            扫描过程日志回调，用于 UI 实时展示进度。
     */
    fun scan(
        folderUriStrings: List<String>,
        onLog: (ScanLog) -> Unit = {}
    ): List<Song> {

        onLog(ScanLog(message = "🔍 开始扫描，目标文件夹数：${folderUriStrings.size}"))

        return if (folderUriStrings.isEmpty()) {
            // 没有指定文件夹 → 全局扫描 MediaStore
            onLog(ScanLog(level = ScanLog.Level.WARN, message = "⚠️ 未指定文件夹，将扫描全部 MediaStore（可能权限不足）"))
            scanAllMediaStore(onLog)
        } else {
            // 按文件夹扫描，结果合并去重
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

    // ─────────────────────────────────────────────────────────────
    // 全局扫描（不限文件夹）
    // ─────────────────────────────────────────────────────────────
    private fun scanAllMediaStore(onLog: (ScanLog) -> Unit): List<Song> {
        // 注意：去掉了 IS_MUSIC 过滤！很多手动拷贝的文件 IS_MUSIC = 0
        val selection  = "${MediaStore.Audio.Media.DURATION} >= 10000"
        return querySongs(
            selection  = selection,
            selectionArgs = null,
            folderHint = "全部",
            onLog      = onLog
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 按文件夹 URI 扫描
    // ─────────────────────────────────────────────────────────────
    private fun scanByFolderUri(uriStr: String, onLog: (ScanLog) -> Unit): List<Song> {
        return try {
            val folderUri = Uri.parse(uriStr)

            // 把 SAF URI 转成 MediaStore 可以理解的 DATA 路径前缀
            // 例：content://com.android.externalstorage.documents/tree/primary:Music
            // 解析出来是 /storage/emulated/0/Music
            val folderPath = resolveFolderPath(folderUri)

            onLog(ScanLog(message = "📂 扫描文件夹：$folderPath"))

            if (folderPath == null) {
                onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 无法解析文件夹路径，跳过：$uriStr"))
                return emptyList()
            }

            // 用 DATA 字段做路径过滤（LIKE 匹配子目录）
            // DATA 字段在 Android 10+ 已弃用，但查询 MediaStore 仍然可用
            val selection     = "${MediaStore.Audio.Media.DURATION} >= 10000 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$folderPath%")

            val songs = querySongs(selection, selectionArgs, folderPath, onLog)

            if (songs.isEmpty()) {
                onLog(ScanLog(level = ScanLog.Level.WARN, message = "⚠️ 此文件夹内没有找到音频（或权限不够）：$folderPath"))
            }

            songs
        } catch (e: Exception) {
            onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 扫描出错：${e.message}"))
            Log.e(TAG, "scanByFolderUri error", e)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 实际查询 MediaStore.Audio.Media
    // ─────────────────────────────────────────────────────────────
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
            MediaStore.Audio.Media.DATA           // 实际文件路径（仅供调试和文件夹归类）
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
                    onLog(ScanLog(
                        level   = ScanLog.Level.WARN,
                        message = "⚠️ 查询结果为空，可能原因：① 权限未授予 ② 文件夹内无音频 ③ 路径解析错误"
                    ))
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

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val folderPath = data.substringBeforeLast("/")

                    songs.add(Song(
                        id          = id,
                        title       = cursor.getString(colTitle)?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                        artist      = cursor.getString(colArtist)?.takeIf { it != "<unknown>" } ?: "未知艺术家",
                        album       = cursor.getString(colAlbum)?.takeIf { it != "<unknown>" } ?: "未知专辑",
                        duration    = cursor.getLong(colDuration),
                        uri         = contentUri,
                        albumArtUri = albumArtUri,
                        size        = cursor.getLong(colSize),
                        dateAdded   = cursor.getLong(colDate),
                        folderPath  = folderPath
                    ))
                }
            } ?: onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ ContentResolver.query 返回 null，权限可能被拒绝"))

        } catch (e: SecurityException) {
            // 这是最常见的扫描失败原因：权限被拒绝
            onLog(ScanLog(
                level   = ScanLog.Level.ERROR,
                message = "❌ SecurityException！权限被拒绝。请检查是否已授予 READ_MEDIA_AUDIO（Android 13+）或 READ_EXTERNAL_STORAGE（Android 12 及以下）。\n详情：${e.message}"
            ))
            Log.e(TAG, "Permission denied", e)
        } catch (e: Exception) {
            onLog(ScanLog(level = ScanLog.Level.ERROR, message = "❌ 未知错误：${e.message}"))
            Log.e(TAG, "Scan error", e)
        }

        return songs
    }

    // ─────────────────────────────────────────────────────────────
    // SAF URI → 实际文件路径
    // ─────────────────────────────────────────────────────────────
    private fun resolveFolderPath(uri: Uri): String? {
        // SAF 树形 URI 格式：
        // content://com.android.externalstorage.documents/tree/primary:Music/Playlist
        // lastPathSegment = "primary:Music/Playlist"
        val segment = uri.lastPathSegment ?: return null
        return when {
            segment.startsWith("primary:") -> {
                "/storage/emulated/0/" + segment.removePrefix("primary:")
            }
            segment.contains(":") -> {
                // 外置 SD 卡：格式类似 1234-ABCD:Music
                val parts = segment.split(":", limit = 2)
                "/storage/${parts[0]}/${parts.getOrElse(1) { "" }}"
            }
            else -> null
        }
    }
}