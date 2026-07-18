package com.yulight.skypie.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.yulight.skypie.util.LyricWord

@Composable
fun KaraokeLineCanvas(
    text: String,
    words: List<LyricWord>,
    currentMs: Long,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    defaultColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val charProgress = if (currentMs >= 0) calculateCharProgress(words, currentMs, text) else null
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = defaultColor.copy(alpha = 0.35f),
            onTextLayout = { if (textLayout == null) textLayout = it }
        )

        if (charProgress != null && textLayout != null) {
            val path = remember(charProgress, textLayout) {
                buildRevealPath(charProgress, textLayout!!)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = primaryColor,
                modifier = Modifier.drawWithContent {
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(path)
                    drawContent()
                    drawContext.canvas.restore()
                }
            )
        }
    }
}

@Composable
fun KaraokeGlow(
    text: String,
    words: List<LyricWord>,
    currentMs: Long,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (words.isEmpty()) return
    val charProgress = calculateCharProgress(words, currentMs, text)
    if (charProgress.charIndex == 0 && charProgress.fraction <= 0f) return

    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Transparent,
            onTextLayout = { if (textLayout == null) textLayout = it }
        )

        if (textLayout != null) {
            val path = remember(charProgress, textLayout) {
                buildRevealPath(charProgress, textLayout!!)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = primaryColor.copy(alpha = 0.65f),
                modifier = Modifier
                    .blur(6.dp)
                    .drawWithContent {
                        drawContext.canvas.save()
                        drawContext.canvas.clipPath(path)
                        drawContent()
                        drawContext.canvas.restore()
                    }
            )
        }
    }
}

private data class CharProgress(
    val charIndex: Int,
    val wordEndIndex: Int,
    val fraction: Float
)

private fun calculateCharProgress(
    words: List<LyricWord>,
    currentMs: Long,
    fullText: String
): CharProgress {
    if (words.isEmpty()) return CharProgress(0, 0, 0f)

    var pos = 0
    for (word in words) {
        val start = fullText.indexOf(word.text, pos).coerceAtLeast(pos)
        val end = start + word.text.length

        if (currentMs < word.startMs) {
            return CharProgress(start, end, 0f)
        }
        if (currentMs < word.startMs + word.durationMs) {
            val progress = ((currentMs - word.startMs).toFloat() / word.durationMs).coerceIn(0f, 1f)
            return CharProgress(start, end, progress)
        }
        pos = end
    }
    return CharProgress(fullText.length, fullText.length, 1f)
}

private fun buildRevealPath(charProgress: CharProgress, layout: TextLayoutResult): Path {
    val path = Path()
    if (charProgress.fraction <= 0f && charProgress.charIndex <= 0) return path

    val textLen = layout.layoutInput.text.length

    // 把 word 的 fraction 转成文本位置：在 charIndex ~ wordEndIndex 之间插值
    val effectivePos = charProgress.charIndex +
        (charProgress.wordEndIndex - charProgress.charIndex) * charProgress.fraction

    for (line in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val lineTop = layout.getLineTop(line).toFloat()
        val lineBottom = layout.getLineBottom(line).toFloat()

        if (effectivePos >= lineEnd.toFloat()) {
            path.addRect(Rect(0f, lineTop, layout.getLineRight(line), lineBottom))
        } else if (effectivePos > lineStart.toFloat()) {
            // 在当前行内插值
            val charIdx = effectivePos.toInt().coerceIn(lineStart, lineEnd)
            val nextIdx = (charIdx + 1).coerceAtMost(lineEnd)
            val charFrac = effectivePos - charIdx
            val x1 = layout.getHorizontalPosition(charIdx.coerceAtMost(textLen), true)
            val x2 = layout.getHorizontalPosition(nextIdx.coerceAtMost(textLen), true)
            val x = x1 + (x2 - x1) * charFrac
            path.addRect(Rect(0f, lineTop, x.coerceAtLeast(x1), lineBottom))
        }
    }
    return path
}
