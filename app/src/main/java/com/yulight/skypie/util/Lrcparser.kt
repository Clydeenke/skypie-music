package com.yulight.skypie.util

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.charset.Charset
import java.util.logging.Level
import java.util.logging.Logger

data class LyricWord(val text: String, val startMs: Long, val durationMs: Long)

data class LrcLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList(), val translation: String = "")

object LrcParser {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private val LRC_RE = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")
    private val WORD_TIME_RE = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>""")

    fun parse(content: String): List<LrcLine> {
        if (content.isBlank()) return emptyList()
        if (content.contains("<0") || content.contains("<1")) {
            val wordResult = parseWordByWord(content)
            if (wordResult.isNotEmpty()) return wordResult
        }
        return parseLrc(content)
    }

    private fun parseWordByWord(content: String): List<LrcLine> {
        // 第一遍：收集所有行（逐字行 + 非逐字行）
        data class RawLine(val timeMs: Long, val text: String, val isWordByWord: Boolean, val words: List<LyricWord> = emptyList())

        val rawLines = mutableListOf<RawLine>()
        content.lines().forEach { line ->
            val timeMatch = LRC_RE.find(line) ?: return@forEach
            val (min, sec, sub) = timeMatch.destructured
            val multiplier = if (sub.length == 2) 10L else 1L
            val lineTimeMs = min.toLong() * 60_000L + sec.toLong() * 1_000L + sub.toLong() * multiplier
            val afterTimeTag = line.substring(timeMatch.range.last + 1)

            if (afterTimeTag.contains(WORD_TIME_RE)) {
                val words = mutableListOf<LyricWord>()
                val timeMatches = WORD_TIME_RE.findAll(afterTimeTag).toList()
                if (timeMatches.size >= 2) {
                    var i = 0
                    while (i < timeMatches.size - 1) {
                        val (startMin, startSec, startSub) = timeMatches[i].destructured
                        val startMult = if (startSub.length == 2) 10L else 1L
                        val startTime = startMin.toLong() * 60_000L + startSec.toLong() * 1_000L + startSub.toLong() * startMult
                        val (endMin, endSec, endSub) = timeMatches[i + 1].destructured
                        val endMult = if (endSub.length == 2) 10L else 1L
                        val endTime = endMin.toLong() * 60_000L + endSec.toLong() * 1_000L + endSub.toLong() * endMult
                        val charStart = timeMatches[i].range.last + 1
                        val charEnd = timeMatches[i + 1].range.first
                        val charText = afterTimeTag.substring(charStart, charEnd)
                        if (charText.isNotEmpty()) {
                            words.add(LyricWord(charText, startTime, endTime - startTime))
                        }
                        i += 2
                    }
                }
                if (words.isNotEmpty()) rawLines += RawLine(lineTimeMs, words.joinToString("") { it.text }, true, words)
            } else if (afterTimeTag.isNotBlank()) {
                rawLines += RawLine(lineTimeMs, afterTimeTag.trim(), false)
            }
        }

        // 第二遍：逐字行创建 LrcLine，非逐字行合并为翻译
        val result = mutableListOf<LrcLine>()
        val pendingTranslations = mutableMapOf<Long, String>()

        for (raw in rawLines) {
            if (raw.isWordByWord) {
                val translation = pendingTranslations.remove(raw.timeMs) ?: ""
                result += LrcLine(raw.timeMs, raw.text, raw.words, translation)
            } else {
                // 非逐字行：尝试合并到同时间戳的逐字行
                val target = result.indexOfFirst { it.timeMs == raw.timeMs }
                if (target >= 0) {
                    result[target] = result[target].copy(translation = raw.text)
                } else {
                    // 还没有对应的逐字行（顺序问题），暂存翻译
                    pendingTranslations[raw.timeMs] = raw.text
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun parseLrc(content: String): List<LrcLine> {
        // 第一遍：收集所有行
        data class RawLrc(val timeMs: Long, val text: String)
        val rawLines = mutableListOf<RawLrc>()
        content.lines().forEach { rawLine ->
            var remaining = rawLine.trimStart()
            while (remaining.startsWith("[")) {
                val m = LRC_RE.find(remaining) ?: break
                val (min, sec, sub) = m.destructured
                val multiplier = if (sub.length == 2) 10L else 1L
                val timeMs = min.toLong() * 60_000L + sec.toLong() * 1_000L + sub.toLong() * multiplier
                val text = remaining.substring(m.range.last + 1).trim()
                if (text.isNotEmpty()) rawLines += RawLrc(timeMs, text)
                remaining = remaining.substring(m.range.last + 1)
            }
        }
        // 第二遍：同时间戳的行合并（第一行当正文，其余当翻译）
        val result = mutableListOf<LrcLine>()
        for (raw in rawLines) {
            val existing = result.indexOfFirst { it.timeMs == raw.timeMs }
            if (existing >= 0) {
                val existingLine = result[existing]
                val mergedTranslation = if (existingLine.translation.isNotBlank()) {
                    existingLine.translation + "\n" + raw.text
                } else raw.text
                result[existing] = existingLine.copy(translation = mergedTranslation)
            } else {
                result += LrcLine(raw.timeMs, raw.text)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    fun loadForSong(folderPath: String, title: String, filePath: String = "", artist: String = ""): List<LrcLine>? {
        if (filePath.isNotBlank()) { val embedded = loadEmbeddedLyrics(filePath); if (embedded != null) return embedded }
        return loadFromLrcFile(folderPath, title, artist, filePath)
    }

    private fun loadEmbeddedLyrics(filePath: String): List<LrcLine>? {
        return try {
            val file = File(filePath); if (!file.exists()) return null
            val audioFile = AudioFileIO.read(file); val tag = audioFile.tag ?: return null
            val raw = tag.getFirst(FieldKey.LYRICS); if (raw.isNullOrBlank()) return null
            val parsed = parse(raw)
            if (parsed.isNotEmpty()) parsed
            else raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, line -> LrcLine(i * 5000L, line) }.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    private fun loadFromLrcFile(folderPath: String, title: String, artist: String = "", filePath: String = ""): List<LrcLine>? {
        val folder = File(folderPath); if (!folder.isDirectory) return null
        val titleNorm = title.lowercase().trim(); val artistNorm = artist.lowercase().trim()
        if (filePath.isNotBlank()) { val exactLrc = File(filePath.substringBeforeLast(".") + ".lrc"); if (exactLrc.exists()) return tryParse(exactLrc) }
        listOf("$title - $artist.lrc", "$artist - $title.lrc", "$title.lrc").forEach { name -> val f = File(folder, name); if (f.exists()) return tryParse(f) }
        val lrcFiles = folder.listFiles { f -> f.isFile && f.extension.equals("lrc", ignoreCase = true) } ?: return null
        if (lrcFiles.isEmpty()) return null
        data class Candidate(val file: File, val score: Float)
        val candidates = lrcFiles.mapNotNull { f ->
            val stem = f.nameWithoutExtension.lowercase().trim(); val parts = stem.split(" - ", limit = 2)
            val stemTitle = if (parts.size == 2) parts[1] else stem; val stemArtist = if (parts.size == 2) parts[0] else ""
            val titleScore = when { stem == titleNorm -> 2.0f; stemTitle == titleNorm -> 2.0f; stem.startsWith(titleNorm) || stemTitle.startsWith(titleNorm) -> 1.5f; titleNorm.length >= 4 && similarityRatio(stemTitle.ifBlank { stem }, titleNorm) >= 0.55f -> similarityRatio(stemTitle.ifBlank { stem }, titleNorm); else -> return@mapNotNull null }
            val artistScore = when { artistNorm.isBlank() || stemArtist.isBlank() -> 0f; stemArtist == artistNorm -> 1.0f; similarityRatio(stemArtist, artistNorm) >= 0.6f -> similarityRatio(stemArtist, artistNorm) * 0.5f; else -> -0.3f }
            Candidate(f, titleScore + artistScore)
        }
        if (candidates.isEmpty()) return null; val best = candidates.maxByOrNull { it.score } ?: return null; if (best.score < 0.8f) return null; return tryParse(best.file)
    }

    private fun similarityRatio(a: String, b: String): Float { if (a.isEmpty() || b.isEmpty()) return 0f; if (a == b) return 1f; return 2f * lcsLength(a, b) / (a.length + b.length) }
    private fun lcsLength(a: String, b: String): Int { val m = a.length; val n = b.length; var prev = IntArray(n + 1); var curr = IntArray(n + 1); for (i in 1..m) { for (j in 1..n) { curr[j] = if (a[i-1] == b[j-1]) prev[j-1]+1 else maxOf(curr[j-1], curr[j]) }; val t=prev; prev=curr; curr=t; curr.fill(0) }; return prev[n] }
    /**
     * 对比两组歌词文本是否匹配（忽略时间戳，只比较文字内容）
     * 用于验证云端逐字歌词是否和本地/标准 LRC 是同一首歌
     */
    fun lyricsMatch(a: List<LrcLine>, b: List<LrcLine>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val textsA = a.filter { it.words.isNotEmpty() }.map { it.text.trim() }
        val textsB = b.map { it.text.trim() }
        if (textsA.isEmpty() || textsB.isEmpty()) return false
        val sampleSize = minOf(5, textsA.size, textsB.size)
        if (sampleSize < 2) return false
        val matchCount = (0 until sampleSize).count { i -> textsA[i] == textsB[i] }
        return matchCount >= sampleSize / 2
    }

    private val CHARSETS = listOf(Charsets.UTF_8, runCatching { Charset.forName("GBK") }.getOrNull(), runCatching { Charset.forName("GB18030") }.getOrNull()).filterNotNull()
    private fun tryParse(file: File): List<LrcLine>? { for (cs in CHARSETS) { try { return parse(file.readText(cs)) } catch (_: Exception) {} }; return null }
}
