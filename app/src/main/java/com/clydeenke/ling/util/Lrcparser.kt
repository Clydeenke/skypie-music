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

    fun loadForSong(folderPath: String, title: String, filePath: String = "", artist: String = ""): List<LrcLine>? {
        // 1. 优先读音乐文件内嵌歌词
        if (filePath.isNotBlank()) {
            val embedded = loadEmbeddedLyrics(filePath)
            if (embedded != null) return embedded
        }
        // 2. 再找同目录 .lrc 文件
        return loadFromLrcFile(folderPath, title, artist, filePath)
    }

    private fun loadEmbeddedLyrics(filePath: String): List<LrcLine>? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val audioFile = AudioFileIO.read(file)
            val tag       = audioFile.tag ?: return null
            val raw       = tag.getFirst(FieldKey.LYRICS)
            if (raw.isNullOrBlank()) return null
            val parsed = parse(raw)
            if (parsed.isNotEmpty()) {
                parsed
            } else {
                raw.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .mapIndexed { index, line -> LrcLine(index * 5000L, line) }
                    .ifEmpty { null }
            }
        } catch (_: Exception) { null }
    }

    private fun loadFromLrcFile(folderPath: String, title: String, artist: String = "", filePath: String = ""): List<LrcLine>? {
        val folder = File(folderPath)
        if (!folder.isDirectory) return null

        val titleNorm  = title.lowercase().trim()
        val artistNorm = artist.lowercase().trim()

        // ── 第0步：音频文件同名 .lrc（最可靠，完全不靠模糊匹配） ──────────────
        // 比如音频是 "两个我们 - 任立佳.mp3"，就直接找 "两个我们 - 任立佳.lrc"
        if (filePath.isNotBlank()) {
            val exactLrc = File(filePath.substringBeforeLast(".") + ".lrc")
            if (exactLrc.exists()) return tryParse(exactLrc)
        }

        // ── 第1步：精确文件名匹配（同时含标题+歌手，优先级最高） ──────────────
        listOf(
            "$title - $artist.lrc",
            "$artist - $title.lrc",
            "$title.lrc"
        ).forEach { name ->
            val f = File(folder, name)
            if (f.exists()) return tryParse(f)
        }

        // ── 第2步：收集所有 .lrc 文件 ─────────────────────────────────────────
        val lrcFiles = folder.listFiles { f ->
            f.isFile && f.extension.equals("lrc", ignoreCase = true)
        } ?: return null
        if (lrcFiles.isEmpty()) return null

        // ── 第3步：对每个文件打分，取最高分 ──────────────────────────────────
        // 评分规则：
        //   标题完全匹配 → +2.0
        //   标题模糊匹配 → +相似度(0~1)
        //   歌手完全匹配 → +1.0（额外加分，这样同名不同人的歌不会用错）
        //   歌手模糊匹配 → +相似度*0.5
        data class Candidate(val file: File, val score: Float)

        val candidates = lrcFiles.mapNotNull { f ->
            val stem = f.nameWithoutExtension.lowercase().trim()

            // 把 "歌手 - 标题" 或 "标题 - 歌手" 两种格式都拆出来
            val parts = stem.split(" - ", limit = 2)
            val stemTitle  = if (parts.size == 2) parts[1] else stem
            val stemArtist = if (parts.size == 2) parts[0] else ""

            // 标题相似度
            val titleScore = when {
                stem == titleNorm       -> 2.0f  // 文件名就是标题，完全一致
                stemTitle == titleNorm  -> 2.0f  // 拆出来的标题完全一致
                stem.startsWith(titleNorm) || stemTitle.startsWith(titleNorm) -> 1.5f
                titleNorm.length >= 4 && similarityRatio(stemTitle.ifBlank { stem }, titleNorm) >= 0.55f ->
                    similarityRatio(stemTitle.ifBlank { stem }, titleNorm)
                else -> return@mapNotNull null  // 标题完全不像，直接排除
            }

            // 歌手相似度（没有歌手信息就不加分也不扣分）
            val artistScore = when {
                artistNorm.isBlank() || stemArtist.isBlank() -> 0f
                stemArtist == artistNorm -> 1.0f
                similarityRatio(stemArtist, artistNorm) >= 0.6f ->
                    similarityRatio(stemArtist, artistNorm) * 0.5f
                else -> -0.3f  // 歌手明显不对，小幅扣分
            }

            Candidate(f, titleScore + artistScore)
        }

        if (candidates.isEmpty()) return null

        // 取得分最高的文件
        val best = candidates.maxByOrNull { it.score } ?: return null

        // 如果最高分太低（说明没有真正匹配的），不返回
        if (best.score < 0.8f) return null

        return tryParse(best.file)
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
        runCatching { Charset.forName("GBK")    }.getOrNull(),
        runCatching { Charset.forName("GB18030") }.getOrNull()
    ).filterNotNull()

    private fun tryParse(file: File): List<LrcLine>? {
        for (cs in CHARSETS) {
            try { return parse(file.readText(cs)) } catch (_: Exception) { }
        }
        return null
    }
}