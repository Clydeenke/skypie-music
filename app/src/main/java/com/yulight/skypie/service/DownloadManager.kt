package com.yulight.skypie.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import com.yulight.skypie.data.model.DownloadStatus
import com.yulight.skypie.data.model.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // 下载完成回调
    var onDownloadComplete: (() -> Unit)? = null

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "music_download"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun enqueue(task: DownloadTask) {
        _tasks.value = _tasks.value + task
        queue.offer(task)
        processQueue()
    }

    fun cancel(taskId: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == taskId) it.copy(status = DownloadStatus.Cancelled) else it
        }
        // 同时从队列中移除
        queue.removeIf { it.id == taskId }
    }

    fun removeTask(taskId: String) {
        _tasks.value = _tasks.value.filter { it.id != taskId }
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { it.status != DownloadStatus.Done }
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
                // 通知下载完成，刷新音乐库
                withContext(Dispatchers.Main) {
                    onDownloadComplete?.invoke()
                }
            } catch (e: CancellationException) {
                updateTask(task.id, status = DownloadStatus.Cancelled)
            } catch (e: Exception) {
                updateTask(task.id, status = DownloadStatus.Failed, errorMessage = e.message ?: "下载失败")
            } finally {
                isProcessing = false
                processQueue()
            }
        }
    }

    private suspend fun downloadAudio(task: DownloadTask) {
        val safeName = "${task.artist} - ${task.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val cacheDir = File(context.cacheDir, "dl_tmp").apply { mkdirs() }
        val tmpFile = File(cacheDir, "$safeName.tmp")
        val tmpCover = File(cacheDir, "$safeName.jpg")

        withContext(Dispatchers.IO) {
            // 下载音频
            val conn = URL(task.streamUrl).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()

            val totalBytes = conn.contentLength.toLong()
            var downloaded = 0L

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
                    }
                }
            }

            // 检测格式
            val ext = detectAudioFormat(tmpFile)
            val audioFile = File(cacheDir, "$safeName.$ext")
            tmpFile.renameTo(audioFile)

            // 下载封面
            var hasCover = false
            if (task.coverUrl.isNotBlank()) {
                try {
                    URL(task.coverUrl).openStream().use { input ->
                        tmpCover.outputStream().use { out -> input.copyTo(out) }
                    }
                    hasCover = true
                } catch (_: Exception) {}
            }

            // 嵌入标签（标题、艺术家、歌词、封面）
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
                                binaryData = coverBytes
                                mimeType = "image/jpeg"
                                pictureType = 3
                            }
                            tag.deleteArtworkField()
                            tag.setField(artwork)
                        }
                    } catch (_: Exception) {}
                }

                org.jaudiotagger.audio.AudioFileIO.write(audioFileIO)
            } catch (_: Exception) {}

            // 写入 MediaStore
            saveToMediaStore(audioFile, task, ext)

            // 清理
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
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/BingYin")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, values) ?: return@withContext
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            // 通知系统扫描
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
    }

    private fun updateTask(
        id: String,
        status: DownloadStatus? = null,
        progress: Int? = null,
        totalBytes: Long? = null,
        downloadedBytes: Long? = null,
        errorMessage: String? = null
    ) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) {
                task.copy(
                    status = status ?: task.status,
                    progress = progress ?: task.progress,
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
                val header = ByteArray(4)
                it.read(header, 0, 4)
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
        } catch (e: Exception) {
            "mp3"
        }
    }
}
