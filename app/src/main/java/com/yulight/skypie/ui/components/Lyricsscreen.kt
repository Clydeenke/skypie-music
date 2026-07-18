package com.yulight.skypie.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yulight.skypie.util.LrcLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private val HALF_SCREEN_PAD = 360.dp

private suspend fun androidx.compose.foundation.lazy.LazyListState.fullySmoothScrollToCenter(
    index          : Int,
    contentPadTopPx: Int,   // contentPadding top 转换成 px
    durationMs     : Int = 1000
) {
    val info    = layoutInfo
    val vpH     = info.viewportSize.height
    if (vpH == 0) return

    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return

    val delta: Float

    val itemInfo = visible.firstOrNull { it.index == index }
    if (itemInfo != null) {
        // ✅ 目标可见：读真实坐标，完全精确
        val itemTop = itemInfo.offset - info.viewportStartOffset
        delta = (itemTop - vpH / 2).toFloat()
    } else {
        // 目标不可见：用平均行高从当前位置算到目标位置
        val avgH = visible.sumOf { it.size } / visible.size

        // 当前滚动位置（内容坐标，从列表内容顶部算起）
        val currentScrollPx = firstVisibleItemIndex.toLong() * avgH -
                firstVisibleItemScrollOffset

        // 目标行在内容坐标中的位置（加上 contentPadding top）
        val targetContentPx = contentPadTopPx.toLong() + index.toLong() * avgH

        // 让目标行顶部在 viewport 中点
        val targetScrollPx = targetContentPx - vpH / 2

        delta = (targetScrollPx - currentScrollPx).toFloat()
    }

    if (abs(delta) < 4f) return

    scroll {
        val scope = this; var last = 0f
        Animatable(0f).animateTo(
            targetValue   = delta,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
        ) {
            val d = value - last; last = value
            scope.scrollBy(d)
        }
    }
}

@Composable
fun LyricsScreen(
    lrcLines   : List<LrcLine>,
    currentMs  : Long,
    onCollapse : () -> Unit = {},
    onSeekTo   : (Long) -> Unit = {},
    modifier   : Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density   = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val enableKaraoke = remember {
        context.getSharedPreferences("skypie_settings", 0).getBoolean("enable_karaoke", false)
    }

    // contentPadding top = HALF_SCREEN_PAD，转成 px 供滚动计算用
    val contentPadTopPx = remember(density) {
        with(density) { HALF_SCREEN_PAD.roundToPx() }
    }

    // ── 当前播放行 ────────────────────────────────────────────────────────────
    var currentLineIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentMs, lrcLines) {
        val next = if (lrcLines.isEmpty()) 0
        else lrcLines.indexOfLast { it.timeMs <= currentMs }.coerceAtLeast(0)
        if (next != currentLineIndex) currentLineIndex = next
    }

    // ── 首次进入：等 layout 真正完成（vpH > 0）后瞬间定位 ───────────────────
    LaunchedEffect(lrcLines) {
        if (lrcLines.isEmpty()) return@LaunchedEffect
        // 轮询直到 viewport 完成测量，最多等 20 帧
        var vpH = 0
        repeat(20) {
            vpH = listState.layoutInfo.viewportSize.height
            if (vpH > 0) return@repeat
            delay(16)
        }
        if (vpH > 0) {
            listState.scrollToItem(
                index        = currentLineIndex,
                scrollOffset = -(vpH / 2)
            )
        }
    }

    // ── 用户滑动状态（仅用于自动滚动控制） ────────────────────────────────────
    var isUserScrolskypie  by remember { mutableStateOf(false) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastUserScrollMs) {
        if (lastUserScrollMs > 0L) {
            delay(2500L)
            if (System.currentTimeMillis() - lastUserScrollMs >= 2300L) {
                isUserScrolskypie = false
            }
        }
    }

    val coroutineScope   = rememberCoroutineScope()
    var clickResumeJob   by remember { mutableStateOf<Job?>(null) }

    // ── 可见行索引 ────────────────────────────────────────────────────────────
    val visibleIndices by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
        }
    }

    // ── 自动平滑回到当前行 ─────────────────────────────────────────────────────
    LaunchedEffect(listState) {
        snapshotFlow { currentLineIndex to isUserScrolskypie }
            .distinctUntilChanged()
            .collectLatest { (idx, scrolskypie) ->
                if (!scrolskypie && lrcLines.isNotEmpty()) {
                    listState.fullySmoothScrollToCenter(
                        index           = idx,
                        contentPadTopPx = contentPadTopPx,
                        durationMs      = 1000
                    )
                }
            }
    }

    // ── NestedScroll：只检测用户手指 ─────────────────────────────────────────
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    isUserScrolskypie  = true
                    lastUserScrollMs = System.currentTimeMillis()
                }
                return Offset.Zero
            }
        }
    }

    val onBg    = Color.White
    val primary = Color.White

    if (lrcLines.isEmpty()) {
        Text(
            text      = "暂无歌词",
            style     = MaterialTheme.typography.bodyLarge,
            color     = onBg.copy(alpha = 0.30f),
            modifier  = modifier.fillMaxWidth().padding(top = 240.dp),
            textAlign = TextAlign.Center
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── 顶部药丸，往下拖回全屏 ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 6f) onCollapse()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(onBg.copy(alpha = 0.25f))
            )
        }

        LazyColumn(
            state          = listState,
            modifier       = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f, endY = size.height * 0.22f
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height * 0.78f, endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = HALF_SCREEN_PAD)
        ) {
            itemsIndexed(items = lrcLines, key = { idx, _ -> idx }) { index, line ->
                val isCurrent = index == currentLineIndex
                val dist      = abs(index - currentLineIndex)

                // 透明度：当前行最亮，越远越暗
                val targetAlpha = when {
                    dist == 0 -> 1.00f
                    dist == 1 -> 0.55f
                    dist == 2 -> 0.28f
                    else      -> 0.12f
                }
                val alpha by animateFloatAsState(
                    targetValue = targetAlpha, animationSpec = tween(700), label = "a$index"
                )

                // 判断是否为逐字歌词
                val hasWords = line.words.isNotEmpty()

                if (hasWords && enableKaraoke) {
                    // 逐字歌词
                    // 高亮保持逻辑：播放过的行保持高亮，直到当前行之后第3行开始播放才消失
                    val isCurrentLine = index == currentLineIndex
                    val isPastLine = index < currentLineIndex

                    // 找当前行之后第3行的开始时间
                    val fadeOutThresholdMs = remember(currentLineIndex, lrcLines) {
                        val futureIdx = (currentLineIndex + 3).coerceAtMost(lrcLines.lastIndex)
                        lrcLines.getOrNull(futureIdx)?.timeMs ?: Long.MAX_VALUE
                    }

                    val shouldKeepHighlight = when {
                        isCurrentLine -> true
                        isPastLine -> currentMs < fadeOutThresholdMs
                        else -> false
                    }

                    val lineCurrentMs = when {
                        isCurrentLine -> currentMs
                        shouldKeepHighlight -> {
                            // 保持满格高亮
                            val lastWord = line.words.lastOrNull()
                            if (lastWord != null) lastWord.startMs + lastWord.durationMs + 1 else -1L
                        }
                        else -> -1L
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isUserScrolskypie = true
                                lastUserScrollMs = System.currentTimeMillis()
                                onSeekTo(line.timeMs)
                                clickResumeJob?.cancel()
                                clickResumeJob = coroutineScope.launch {
                                    delay(500L)
                                    isUserScrolskypie = false
                                    lastUserScrollMs = 0L
                                }
                            }
                            .padding(vertical = 4.dp)
                            .graphicsLayer { this.alpha = alpha }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (shouldKeepHighlight) {
                                KaraokeGlow(
                                    text = line.text,
                                    words = line.words,
                                    currentMs = lineCurrentMs,
                                    primaryColor = primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            KaraokeLineCanvas(
                                text = line.text,
                                words = line.words,
                                currentMs = lineCurrentMs,
                                primaryColor = primary,
                                defaultColor = onBg,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (line.translation.isNotBlank()) {
                            Text(
                                text = line.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onBg.copy(alpha = alpha * 0.6f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // 普通歌词 — 白色实体文字 + 暗色未播放
                    val isCurrentLine = index == currentLineIndex
                    val isPastLine = index < currentLineIndex
                    val fadeOutThresholdMs = remember(currentLineIndex, lrcLines) {
                        val futureIdx = (currentLineIndex + 3).coerceAtMost(lrcLines.lastIndex)
                        lrcLines.getOrNull(futureIdx)?.timeMs ?: Long.MAX_VALUE
                    }
                    val shouldHighlight = when {
                        isCurrentLine -> true
                        isPastLine -> currentMs < fadeOutThresholdMs
                        else -> false
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isUserScrolskypie = true
                                lastUserScrollMs = System.currentTimeMillis()
                                onSeekTo(line.timeMs)
                                clickResumeJob?.cancel()
                                clickResumeJob = coroutineScope.launch {
                                    delay(500L)
                                    isUserScrolskypie = false
                                    lastUserScrollMs = 0L
                                }
                            }
                            .padding(vertical = 4.dp)
                            .graphicsLayer { this.alpha = alpha }
                    ) {
                        Text(
                            text     = line.text,
                            style    = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = if (shouldHighlight) FontWeight.Bold else FontWeight.Normal
                            ),
                            color    = if (shouldHighlight) Color.White else Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (line.translation.isNotBlank()) {
                            Text(
                                text = line.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = alpha * 0.6f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(if (isCurrent) 18.dp else 14.dp))
            }
        }
    }
}