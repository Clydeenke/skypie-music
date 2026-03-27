package com.clydeenke.ling.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class MusicDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 从输入参数获取下载信息
        val title    = inputData.getString("title") ?: "未知歌曲"
        val artist   = inputData.getString("artist") ?: "未知歌手"
        val playUrl  = inputData.getString("playUrl") ?: return@withContext Result.failure()
        val coverUrl = inputData.getString("coverUrl") ?: ""
        val ext      = inputData.getString("ext") ?: "mp3"
        val lrcText  = inputData.getString("lrcText") ?: ""

        val safeName = "$title - $artist".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!musicDir.exists()) musicDir.mkdirs()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "music_download"

        // 适配 Android 8.0+ 的通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notifId = safeName.hashCode()
        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download) // 你可以换成自己的图标
            .setContentTitle("下载中: $safeName")
            .setOngoing(true)

        try {
            // 1. 下载音频文件（带有防卡死重试和通知栏进度）
            val audioFile = File(musicDir, "$safeName.$ext")
            val conn = URL(playUrl).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.connect()

            val totalBytes = conn.contentLength
            var downloadedBytes = 0

            conn.getInputStream().use { input ->
                audioFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes = input.read(buffer)
                    var lastUpdate = System.currentTimeMillis()

                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        bytes = input.read(buffer)

                        // 每 500ms 更新一次通知栏进度，防止高频刷新卡顿 UI
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes) else 0
                            notificationManager.notify(
                                notifId,
                                notifBuilder.setProgress(100, progress, totalBytes <= 0)
                                    .setContentText("已下载 $progress%").build()
                            )
                            lastUpdate = now
                        }
                    }
                }
            }

            // 2. 下载封面和写入歌词
            if (coverUrl.isNotBlank()) {
                try {
                    val coverFile = File(musicDir, "$safeName.jpg")
                    URL(coverUrl).openStream().use { i -> coverFile.outputStream().use { o -> i.copyTo(o) } }
                } catch (_: Exception) {}
            }
            if (lrcText.isNotBlank()) {
                File(musicDir, "$safeName.lrc").writeText(lrcText, Charsets.UTF_8)
            }

            // 3. 通知系统媒体库扫描新文件
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(audioFile.absolutePath), null, null
            )

            // 4. 下载完成通知
            notificationManager.notify(
                notifId,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("下载完成")
                    .setContentText(safeName)
                    .setAutoCancel(true)
                    .build()
            )

            Result.success()
        } catch (e: Exception) {
            notificationManager.notify(
                notifId,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("下载失败")
                    .setContentText("$safeName: ${e.message}")
                    .setAutoCancel(true)
                    .build()
            )
            Result.failure()
        }
    }
}