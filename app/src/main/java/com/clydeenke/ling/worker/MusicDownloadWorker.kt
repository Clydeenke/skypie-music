package com.clydeenke.ling.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.coroutines.resume

class MusicDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val title    = inputData.getString("title")    ?: "未知歌曲"
        val artist   = inputData.getString("artist")   ?: "未知歌手"
        val playUrl  = inputData.getString("playUrl")  ?: return@withContext Result.failure()
        val coverUrl = inputData.getString("coverUrl") ?: ""
        val ext      = inputData.getString("ext")      ?: "mp3"
        val lrcText  = inputData.getString("lrcText")  ?: ""

        // 文件名做安全化处理
        val safeName = "$title - $artist".replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // 统一存入 Music/Ling/ 子目录，歌曲、封面、歌词三个文件放在一起，不再散落在 Music 根目录
        val lingDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Ling"
        )
        if (!lingDir.exists()) lingDir.mkdirs()

        // 在 Ling/ 目录下放一个 .nomedia，防止封面图片出现在系统相册
        val nomedia = File(lingDir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "music_download"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notifId      = safeName.hashCode()
        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载中: $safeName")
            .setOngoing(true)

        try {
            // 1. 下载音频文件（带进度通知）
            val audioFile = File(lingDir, "$safeName.$ext")
            val conn = URL(playUrl).openConnection()
            conn.connectTimeout = 15000; conn.readTimeout = 15000; conn.connect()
            val totalBytes = conn.contentLength; var downloadedBytes = 0
            conn.getInputStream().use { input ->
                audioFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024); var bytes = input.read(buffer)
                    var lastUpdate = System.currentTimeMillis()
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes); downloadedBytes += bytes; bytes = input.read(buffer)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes) else 0
                            notificationManager.notify(notifId, notifBuilder.setProgress(100, progress, totalBytes <= 0).setContentText("已下载 $progress%").build())
                            lastUpdate = now
                        }
                    }
                }
            }

            // 2. 下载封面（存在 Ling/ 目录，.nomedia 已阻止相册扫描）
            if (coverUrl.isNotBlank()) {
                try { URL(coverUrl).openStream().use { i -> File(lingDir, "$safeName.jpg").outputStream().use { o -> i.copyTo(o) } } } catch (_: Exception) {}
            }

            // 3. 保存歌词 .lrc（与音频同目录同名，播放器直接识别）
            if (lrcText.isNotBlank()) {
                File(lingDir, "$safeName.lrc").writeText(lrcText, Charsets.UTF_8)
            }

            // 4. 扫描进 MediaStore，扫完立刻修正 title / artist
            //    系统扫描器会把文件名当成 title、artist 标"未知"，这里主动覆盖
            scanAndFixMetadata(audioFile.absolutePath, title, artist)

            // 5. 完成通知
            notificationManager.notify(
                notifId,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("下载完成").setContentText(safeName).setAutoCancel(true).build()
            )
            Result.success()
        } catch (e: Exception) {
            notificationManager.notify(
                notifId,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("下载失败").setContentText("$safeName: ${e.message}").setAutoCancel(true).build()
            )
            Result.failure()
        }
    }

    private suspend fun scanAndFixMetadata(filePath: String, title: String, artist: String) =
        suspendCancellableCoroutine { cont ->
            android.media.MediaScannerConnection.scanFile(context, arrayOf(filePath), null) { _, uri ->
                if (uri != null) {
                    context.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE,  title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                    }, null, null)
                }
                if (cont.isActive) cont.resume(Unit)
            }
        }
}