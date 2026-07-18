package com.yulight.skypie.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.yulight.skypie.data.model.DownloadStatus
import com.yulight.skypie.data.model.DownloadTask
import com.yulight.skypie.ui.screen.settings.DEFAULT_DOWNLOAD_DIR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val queue = ConcurrentLinkedQueue<DownloadTask>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isProcessing = false

    var onDownloadComplete: (() -> Unit)? = null

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "music_download"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // 加载持久化的历史记录
        loadHistory()
    }

    fun enqueue(task: DownloadTask) {
        _tasks.value = _tasks.value + task
        queue.offer(task)
        saveHistory()
        processQueue()
    }

    fun cancel(taskId: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == taskId) it.copy(status = DownloadStatus.Cancelled) else it
        }
        queue.removeIf { it.id == taskId }
        notificationManager.cancel(taskId.hashCode())
        saveHistory()
    }

    fun removeTask(taskId: String) {
        _tasks.value = _tasks.value.filter { it.id != taskId }
        saveHistory()
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { it.status != DownloadStatus.Done }
        saveHistory()
    }

    private fun processQueue() {
        if (isProcessing) return
        val task = queue.poll() ?: return
        if (task.status == DownloadStatus.Cancelled) {
            processQueue()
            return
        }
        isProcessing = true
        scope.launch {
            try {
                updateTask(task.id, status = DownloadStatus.Downloading)
                downloadAudio(task)
                updateTask(task.id, status = DownloadStatus.Done, progress = 100)
                showNotification(task, "下载完成", -1)
                withContext(Dispatchers.Main) {
                    onDownloadComplete?.invoke()
                }
            } catch (e: CancellationException) {
                updateTask(task.id, status = DownloadStatus.Cancelled)
            } catch (e: Exception) {
                updateTask(task.id, status = DownloadStatus.Failed, errorMessage = e.message ?: "下载失败")
                showNotification(task, "下载失败: ${e.message}", -1)
            } finally {
                isProcessing = false
                saveHistory()
                processQueue()
            }
        }
    }

    private fun showNotification(task: DownloadTask, text: String, progress: Int) {
        val id = task.id.hashCode()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${task.title} - ${task.artist}")
            .setContentText(text)
            .setAutoCancel(progress < 0)
            .setOngoing(progress in 0..99)
        if (progress in 0..99) {
            builder.setProgress(100, progress, false)
        }
        notificationManager.notify(id, builder.build())
    }

    private suspend fun downloadAudio(task: DownloadTask) {
        val safeName = "${task.artist} - ${task.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val cacheDir = File(context.cacheDir, "dl_tmp").apply { mkdirs() }
        val tmpFile = File(cacheDir, "$safeName.tmp")
        val tmpCover = File(cacheDir, "$safeName.jpg")

        withContext(Dispatchers.IO) {
            val conn = URL(task.streamUrl).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()

            val totalBytes = conn.contentLength.toLong()
            var downloaded = 0L
            var lastNotify = 0L

            conn.getInputStream().use { input ->
                tmpFile.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        downloaded += n
                        n = input.read(buf)

                        val progress = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                        updateTask(task.id, progress = progress, downloadedBytes = downloaded, totalBytes = totalBytes)
                        // 每 500ms 或进度变化 5% 时更新通知
                        val now = System.currentTimeMillis()
                        if (progress >= lastNotify + 5 || now - lastNotify > 500) {
                            showNotification(task, "下载中 $progress%", progress)
                            lastNotify = now
                        }
                    }
                }
            }

            val ext = detectAudioFormat(tmpFile)
            val audioFile = File(cacheDir, "$safeName.$ext")
            tmpFile.renameTo(audioFile)

            var hasCover = false
            if (task.coverUrl.isNotBlank()) {
                try {
                    URL(task.coverUrl).openStream().use { input ->
                        tmpCover.outputStream().use { out -> input.copyTo(out) }
                    }
                    hasCover = true
                } catch (_: Exception) {}
            }

            try {
                val audioFileIO = org.jaudiotagger.audio.AudioFileIO.read(audioFile)
                val tag = audioFileIO.tagOrCreateAndSetDefault
                tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, task.title)
                tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, task.artist)
                if (task.lrcText.isNotBlank()) {
                    tag.setField(org.jaudiotagger.tag.FieldKey.LYRICS, task.lrcText)
                }
                if (hasCover) {
                    try {
                        val coverBytes = tmpCover.readBytes()
                        if (tag is org.jaudiotagger.tag.flac.FlacTag) {
                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(tmpCover.absolutePath, options)
                            val pictureBlock = org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture(
                                coverBytes, 3, "image/jpeg", "", options.outWidth, options.outHeight, 24, 0
                            )
                            val imagesField = org.jaudiotagger.tag.flac.FlacTag::class.java.getDeclaredField("images")
                            imagesField.isAccessible = true
                            @Suppress("UNCHECKED_CAST")
                            val imagesList = imagesField.get(tag) as MutableList<org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture>
                            imagesList.clear()
                            imagesList.add(pictureBlock)
                        } else {
                            val artwork = org.jaudiotagger.tag.images.StandardArtwork().apply {
                                binaryData = coverBytes; mimeType = "image/jpeg"; pictureType = 3
                            }
                            tag.deleteArtworkField()
                            tag.setField(artwork)
                        }
                    } catch (_: Exception) {}
                }
                org.jaudiotagger.audio.AudioFileIO.write(audioFileIO)
            } catch (_: Exception) {}

            saveToMediaStore(audioFile, task, ext)
            audioFile.delete()
            tmpCover.delete()
        }
    }

    private suspend fun saveToMediaStore(file: File, task: DownloadTask, ext: String) {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val safeName = "${task.artist} - ${task.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.$ext")
                put(MediaStore.Audio.Media.TITLE, task.title)
                put(MediaStore.Audio.Media.ARTIST, task.artist)
                put(MediaStore.Audio.Media.MIME_TYPE, if (ext == "flac") "audio/flac" else "audio/mpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val prefs = context.getSharedPreferences("skypie_settings", 0)
                    val downloadDir = prefs.getString("download_dir", DEFAULT_DOWNLOAD_DIR) ?: DEFAULT_DOWNLOAD_DIR
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$downloadDir")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, values) ?: return@withContext
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
    }

    private fun updateTask(
        id: String, status: DownloadStatus? = null, progress: Int? = null,
        totalBytes: Long? = null, downloadedBytes: Long? = null, errorMessage: String? = null
    ) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) {
                task.copy(
                    status = status ?: task.status, progress = progress ?: task.progress,
                    totalBytes = totalBytes ?: task.totalBytes,
                    downloadedBytes = downloadedBytes ?: task.downloadedBytes,
                    errorMessage = errorMessage ?: task.errorMessage
                )
            } else task
        }
    }

    private fun detectAudioFormat(file: File): String {
        return try {
            file.inputStream().use {
                val header = ByteArray(4); it.read(header, 0, 4)
                val hex = header.joinToString("") { byte -> "%02X".format(byte) }
                when {
                    hex.startsWith("664C6143") -> "flac"
                    hex.startsWith("494433") || hex.startsWith("FFFB") ||
                            hex.startsWith("FFF3") || hex.startsWith("FFF2") -> "mp3"
                    hex.startsWith("4F676753") -> "ogg"
                    hex.startsWith("00000018") || hex.startsWith("00000020") -> "m4a"
                    else -> "mp3"
                }
            }
        } catch (_: Exception) { "mp3" }
    }

    // ── 持久化下载历史 ──────────────────────────────────────────────────────────

    private fun getHistoryFile() = File(context.filesDir, "download_history.json")

    private fun saveHistory() {
        val arr = JSONArray()
        _tasks.value.forEach { task ->
            arr.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("artist", task.artist)
                put("streamUrl", task.streamUrl)
                put("coverUrl", task.coverUrl)
                put("lrcText", task.lrcText)
                put("status", task.status.name)
                put("progress", task.progress)
                put("totalBytes", task.totalBytes)
                put("downloadedBytes", task.downloadedBytes)
                put("errorMessage", task.errorMessage ?: "")
            })
        }
        getHistoryFile().writeText(arr.toString())
    }

    private fun loadHistory() {
        val file = getHistoryFile()
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            val loaded = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadTask(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    streamUrl = obj.getString("streamUrl"),
                    coverUrl = obj.optString("coverUrl", ""),
                    lrcText = obj.optString("lrcText", ""),
                    status = try { DownloadStatus.valueOf(obj.getString("status")) } catch (_: Exception) { DownloadStatus.Done },
                    progress = obj.optInt("progress", 0),
                    totalBytes = obj.optLong("totalBytes", 0),
                    downloadedBytes = obj.optLong("downloadedBytes", 0),
                    errorMessage = obj.optString("errorMessage", "")
                )
            }
            _tasks.value = loaded
        } catch (_: Exception) {}
    }
}
