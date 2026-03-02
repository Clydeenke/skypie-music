package com.clydeenke.ling.util

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.charset.Charset
import java.util.logging.Level
import java.util.logging.Logger

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {

    init {
        // 关掉 JAudioTagger 的日志，避免刷屏
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private val TAG_RE = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]([^\[]*)""")

    fun parse(content: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()
        content.lines().forEach { rawLine ->
            var remaining = rawLine.trimStart()
            while (remaining.startsWith("[")) {
                val m = TAG_RE.find(remaining) ?: break
                val (min, sec, sub, text) = m.destructured
                val multiplier = if (sub.length == 2) 10L else 1L
                val timeMs = min.toLong() * 60_000L +
                        sec.toLong() * 1_000L +
                        sub.toLong() * multiplier
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) result += LrcLine(timeMs, trimmed)
                remaining = remaining.substring(m.range.last + 1)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /**
     * 主入口：先读内嵌歌词，读不到再找同目录 .lrc 文件
     *
     * @param folderPath 歌曲所在文件夹
     * @param title      歌曲标题
     * @param filePath   歌曲完整路径（用于 JAudioTagger 读内嵌歌词）
     */
    fun loadForSong(folderPath: String, title: String, filePath: String = "", artist: String = ""): List<LrcLine>?
    {
        // 1. ✅ 优先读音乐文件内嵌歌词
        if (filePath.isNotBlank()) {
            val embedded = loadEmbeddedLyrics(filePath)
            if (embedded != null) return embedded
        }

        // 2. 再找同目录 .lrc 文件
        return loadFromLrcFile(folderPath, title, artist)
    }

    /**
     * 用 JAudioTagger 读取音乐文件内嵌歌词
     * 支持 MP3 (ID3 USLT)、FLAC (Vorbis LYRICS)、M4A (iTunes LYRICS) 等格式
     */
    private fun loadEmbeddedLyrics(filePath: String): List<LrcLine>? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val audioFile = AudioFileIO.read(file)
            val tag       = audioFile.tag ?: return null
            val raw       = tag.getFirst(FieldKey.LYRICS)

            if (raw.isNullOrBlank()) return null

            // 尝试按 LRC 格式解析
            val parsed = parse(raw)
            if (parsed.isNotEmpty()) {
                parsed
            } else {
                // 纯文本歌词，没有时间轴，每行给一个假时间戳（每行5秒）
                raw.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, line -> LrcLine(index * 5000L, line) }
                    .ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从同目录 .lrc 文件加载歌词
     */
    private fun loadFromLrcFile(folderPath: String, title: String, artist: String = ""): List<LrcLine>? {
        val folder = File(folderPath)
        if (!folder.isDirectory) return null
        val artistNorm = artist.lowercase().trim()
        val byArtistTitle = File(folder, "$artist - $title.lrc")
        if (byArtistTitle.exists()) return tryParse(byArtistTitle)
        val byTitleArtist = File(folder, "$title - $artist.lrc")
        if (byTitleArtist.exists()) return tryParse(byTitleArtist)

        // 1. 精确匹配（含 "歌手 - 歌名" 常见格式）
        listOf(
            "$title.lrc",
            "$artist - $title.lrc",
            "$title - $artist.lrc"
        ).forEach { name ->
            val f = File(folder, name)
            if (f.exists()) return tryParse(f)
        }

        val lrcFiles = folder.listFiles { f ->
            f.isFile && f.extension.equals("lrc", ignoreCase = true)
        } ?: return null
        if (lrcFiles.isEmpty()) return null

        val titleNorm = title.lowercase().trim()

        // 2. 大小写不敏感精确匹配
        val caseInsensitive = lrcFiles.firstOrNull { f ->
            f.nameWithoutExtension.lowercase().trim() == titleNorm
        }
        if (caseInsensitive != null) return tryParse(caseInsensitive)

        // 3. 文件名以歌曲标题开头（如"忘不掉的你-h3R3.lrc"匹配"忘不掉的你"）
        val startsWith = lrcFiles.firstOrNull { f ->
            val stem = f.nameWithoutExtension.lowercase().trim()
            stem.startsWith(titleNorm)
        }
        if (startsWith != null) return tryParse(startsWith)

        // 4. 模糊相似度匹配
        if (titleNorm.length >= 4) {
            val fuzzy = lrcFiles.firstOrNull { f ->
                val stem = f.nameWithoutExtension.lowercase().trim()
                // 如果文件名是 "歌手 - 歌名" 格式，只取后半部分比较
                val part = if (stem.contains(" - ")) stem.substringAfterLast(" - ") else stem
                part.length >= 4 && similarityRatio(part, titleNorm) >= 0.60f
            }
            if (fuzzy != null) return tryParse(fuzzy)
        }

        return null
    }

    private fun similarityRatio(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val lcs = lcsLength(a, b)
        return 2f * lcs / (a.length + b.length)
    }

    private fun lcsLength(a: String, b: String): Int {
        val m = a.length; val n = b.length
        var prev = IntArray(n + 1); var curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else maxOf(curr[j - 1], prev[j])
            }
            val tmp = prev; prev = curr; curr = tmp; curr.fill(0)
        }
        return prev[n]
    }

    private val CHARSETS = listOf(
        Charsets.UTF_8,
        runCatching { Charset.forName("GBK")     }.getOrNull(),
        runCatching { Charset.forName("GB18030")  }.getOrNull()
    ).filterNotNull()

    private fun tryParse(file: File): List<LrcLine>? {
        for (cs in CHARSETS) {
            try { return parse(file.readText(cs)) } catch (_: Exception) { }
        }
        return null
    }
}