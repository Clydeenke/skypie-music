package com.yulight.skypie.worker

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
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

class MusicDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 关闭 jaudiotagger 的底层日志，保持控制台整洁
        Logger.getLogger("org.jaudiotagger").level = Level.OFF

        val title    = inputData.getString("title")    ?: "未知歌曲"
        val artist   = inputData.getString("artist")   ?: "未知歌手"
        val playUrl  = inputData.getString("playUrl")  ?: return@withContext Result.failure()
        val coverUrl = inputData.getString("coverUrl") ?: ""
        val lrcText  = inputData.getString("lrcText")  ?: ""

        // 规范化文件名（剔除特殊符号）
        val safeName = "$artist - $title".replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // 使用应用私有缓存作为安全手术台
        val cacheDir = File(context.cacheDir, "dl_tmp").apply { mkdirs() }
        val tmpAudio = File(cacheDir, "$safeName.tmp") // 先用 tmp，一会嗅探真实格式
        val tmpCover = File(cacheDir, "$safeName.jpg")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "music_download"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "音乐下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notifId = safeName.hashCode()
        val notifBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载中: $safeName")
            .setOngoing(true)

        try {
            // ── 1. 下载音频流 ───────────────────────────────────────────
            val audioConn = URL(playUrl).openConnection()
            audioConn.connectTimeout = 15000
            audioConn.readTimeout = 15000
            audioConn.connect()

            val totalBytes = audioConn.contentLength
            var downloaded = 0

            audioConn.getInputStream().use { input ->
                tmpAudio.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    var lastTime = System.currentTimeMillis()
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        downloaded += n
                        n = input.read(buf)

                        val now = System.currentTimeMillis()
                        if (now - lastTime > 500) { // 限制刷新频率，避免卡顿
                            val progress = if (totalBytes > 0) (downloaded * 100 / totalBytes) else 0
                            nm.notify(
                                notifId,
                                notifBuilder.setProgress(100, progress, totalBytes <= 0)
                                    .setContentText("已下载 $progress%")
                                    .build()
                            )
                            lastTime = now
                        }
                    }
                }
            }

            // ── 2. 嗅探真实文件格式，拒绝套壳 ────────────────────────────────
            val realExt = detectAudioFormat(tmpAudio)
            val audioFileWithExt = File(cacheDir, "$safeName.$realExt")
            tmpAudio.renameTo(audioFileWithExt)

            // ── 3. 下载封面 ───────────────────────────────────────────────
            var hasCover = false
            if (coverUrl.isNotBlank()) {
                try {
                    URL(coverUrl).openStream().use { input ->
                        tmpCover.outputStream().use { out -> input.copyTo(out) }
                    }
                    hasCover = true
                } catch (_: Exception) {
                    // 封面下载失败不影响音频主体
                }
            }

            // ── 4. 完美内嵌 (文字与封面彻底隔离，采用终极反射黑魔法) ───────
            try {
                val audioFile = AudioFileIO.read(audioFileWithExt)
                val tag = audioFile.tagOrCreateAndSetDefault

                tag.setField(FieldKey.TITLE, title)
                tag.setField(FieldKey.ARTIST, artist)

                if (lrcText.isNotBlank()) {
                    tag.setField(FieldKey.LYRICS, lrcText)
                }

                // 🚀 终极封面嵌入黑魔法
                if (hasCover) {
                    try {
                        val coverBytes = tmpCover.readBytes()

                        if (tag is org.jaudiotagger.tag.flac.FlacTag) {
                            // FLAC 官方协议强制要求封面必须写入宽高。
                            // 官方库一算宽高就会调用 Windows 组件导致崩溃。
                            // 破解法：用 Android 原生 API 算好宽高，利用“反射”强行绕过验证，直接塞入底层数据块！
                            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(tmpCover.absolutePath, options)

                            val pictureBlock = org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture(
                                coverBytes,
                                3, // 3 = Front Cover (封面)
                                "image/jpeg",
                                "",
                                options.outWidth,
                                options.outHeight,
                                24, // 默认 24位色深
                                0
                            )

                            // 反射打开 FlacTag 的私有后门列表 "images"
                            val imagesField = org.jaudiotagger.tag.flac.FlacTag::class.java.getDeclaredField("images")
                            imagesField.isAccessible = true
                            @Suppress("UNCHECKED_CAST")
                            val imagesList = imagesField.get(tag) as MutableList<org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture>

                            imagesList.clear() // 清除自带的旧封面
                            imagesList.add(pictureBlock)

                        } else {
                            // MP3 等格式不需要强制宽高，直接塞入纯净二进制
                            val artwork = org.jaudiotagger.tag.images.StandardArtwork().apply {
                                binaryData = coverBytes
                                mimeType = "image/jpeg"
                                pictureType = 3
                            }
                            tag.deleteArtworkField()
                            tag.setField(artwork)
                        }
                    } catch (err: Throwable) {
                        android.util.Log.e("DownloadWorker", "黑魔法封面注入依然失败(只保留歌词)", err)
                    }
                }

                // 这一步绝对能执行到！
                AudioFileIO.write(audioFile)
            } catch (e: Throwable) {
                android.util.Log.e("DownloadWorker", "音频标签核心写入失败", e)
            }

            // ── 5. MediaStore 正规入库到 Music/BingYin ─────────────────────
            val resolver = context.contentResolver
            val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.$realExt")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                val mimeType = if (realExt == "flac") "audio/flac" else "audio/mpeg"
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // ✨ 存入你专属的 BingYin 文件夹
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/BingYin")
                    put(MediaStore.Audio.Media.IS_PENDING, 1) // 锁定状态，告知系统正在写入
                }
            }

            val newUri = resolver.insert(audioCollection, values)
                ?: throw IllegalStateException("MediaStore 拒绝了文件写入请求")

            resolver.openOutputStream(newUri)?.use { outStream ->
                audioFileWithExt.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            // 解除锁定，触发系统扫描
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(newUri, values, null, null)
            }

            // ── 6. 清理缓存，毁尸灭迹 ───────────────────────────────────────
            audioFileWithExt.delete()
            tmpAudio.delete()
            tmpCover.delete()
            coil.Coil.imageLoader(context).memoryCache?.clear()

            nm.notify(notifId, NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("下载完成")
                .setContentText("已保存至 Music/BingYin")
                .setAutoCancel(true).build())

            Result.success()
        } catch (e: Exception) {
            tmpAudio.delete()
            tmpCover.delete()
            nm.notify(notifId, NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("下载失败")
                .setContentText("$safeName: ${e.message}")
                .setAutoCancel(true).build())
            Result.failure()
        }
    }

    /**
     * 读取文件头魔数 (Magic Bytes)，精准判断真实音频格式
     */
    private fun detectAudioFormat(file: File): String {
        return try {
            file.inputStream().use {
                val header = ByteArray(4)
                it.read(header, 0, 4)
                val hex = header.joinToString("") { byte -> "%02X".format(byte) }
                when {
                    hex.startsWith("664C6143") -> "flac" // fLaC
                    hex.startsWith("494433") || hex.startsWith("FFFB") ||
                            hex.startsWith("FFF3") || hex.startsWith("FFF2") -> "mp3" // ID3 or MPEG ADTS
                    hex.startsWith("4F676753") -> "ogg"  // OggS
                    hex.startsWith("00000018") || hex.startsWith("00000020") -> "m4a" // ftyp
                    else -> "mp3" // 兜底
                }
            }
        } catch (e: Exception) {
            "mp3"
        }
    }
}