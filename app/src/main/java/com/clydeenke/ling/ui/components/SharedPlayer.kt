package com.clydeenke.ling.ui.components

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.clydeenke.ling.util.LrcLine
import com.clydeenke.ling.util.LrcParser
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException

internal val MiniPlayerHeight = 68.dp
private  val MiniBottomPad    = 24.dp
private  val MiniSidePad      = 14.dp
private  val FullPagerPad     = 4.dp

private val TapSpring    = spring<Float>(stiffness = 320f, dampingRatio = Spring.DampingRatioNoBouncy)
private val SettleEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

private data class AlbumPalette(
    val color1: Color = Color.Transparent,
    val color2: Color = Color.Transparent,
    val color3: Color = Color.Transparent
)

@Composable
fun SharedPlayerContainer(
    viewModel           : MusicViewModel,
    openPlayerRequested : Boolean    = false,
    onOpenPlayerHandled : () -> Unit = {},
    hazeState           : HazeState,
    modifier            : Modifier   = Modifier
) {
    val song by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    if (song == null) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope   = rememberCoroutineScope()
    val isDark  = isSystemInDarkTheme()

    var rawPalette by remember { mutableStateOf(AlbumPalette()) }
    LaunchedEffect(song?.albumArtUri) {
        val uri = song?.albumArtUri ?: run { rawPalette = AlbumPalette(); return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(uri).size(120).allowHardware(false).build()
                val bmp = (context.imageLoader.execute(req) as? SuccessResult)
                    ?.drawable?.let { (it as? BitmapDrawable)?.bitmap } ?: return@withContext
                val p = Palette.Builder(bmp).generate()

                fun adjustColor(argb: Int, maxLight: Float): Color {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(argb, hsl)
                    hsl[1] = (hsl[1] * 1.3f).coerceAtMost(1.0f)
                    hsl[2] = hsl[2].coerceAtMost(maxLight)
                    return Color(ColorUtils.HSLToColor(hsl))
                }

                val c1 = p.getDominantColor(0).let { adjustColor(it, if (isDark) 0.40f else 0.55f) }
                val c2 = (p.getVibrantColor(0).takeIf { it != 0 }
                    ?: p.getMutedColor(0).takeIf { it != 0 }
                    ?: p.getDominantColor(0)).let { adjustColor(it, if (isDark) 0.38f else 0.50f) }
                val c3 = (p.getDarkVibrantColor(0).takeIf { it != 0 }
                    ?: p.getDarkMutedColor(0).takeIf { it != 0 }
                    ?: p.getDominantColor(0)).let { adjustColor(it, if (isDark) 0.35f else 0.45f) }

                rawPalette = AlbumPalette(c1, c2, c3)
            } catch (_: Exception) {}
        }
    }

    val color1 by animateColorAsState(rawPalette.color1, tween(1000), label = "c1")
    val color2 by animateColorAsState(rawPalette.color2, tween(1200), label = "c2")
    val color3 by animateColorAsState(rawPalette.color3, tween(1400), label = "c3")

    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val flowAngle1 by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label         = "angle1"
    )
    val flowAngle2 by infiniteTransition.animateFloat(
        initialValue  = (Math.PI).toFloat(),
        targetValue   = (3 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label         = "angle2"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHPx   = with(density) { maxHeight.toPx() }
        val screenWPx   = with(density) { maxWidth.toPx() }
        val miniHPx     = with(density) { MiniPlayerHeight.toPx() }
        val botPx       = with(density) { MiniBottomPad.toPx() }
        val sidePx      = with(density) { MiniSidePad.toPx() }
        val cornerMaxPx = with(density) { 34.dp.toPx() }

        val dragRange   = (screenHPx - miniHPx - botPx).coerceAtLeast(1f)
        val animOffset  = remember { Animatable(dragRange, Float.VectorConverter) }
        val lyricsSlide = remember { Animatable(0f, Float.VectorConverter) }

        LaunchedEffect(openPlayerRequested) {
            if (openPlayerRequested) {
                animOffset.animateTo(0f, tween(400, easing = SettleEasing))
                onOpenPlayerHandled()
            }
        }

        val playerSnapCh = remember { Channel<Float>(Channel.CONFLATED) }
        val lyricsSnapCh = remember { Channel<Float>(Channel.CONFLATED) }

        LaunchedEffect(dragRange) { playerSnapCh.receiveAsFlow().collect { animOffset.snapTo(it) } }
        LaunchedEffect(Unit)      { lyricsSnapCh.receiveAsFlow().collect { lyricsSlide.snapTo(it) } }

        val isFullVisible   by remember { derivedStateOf { animOffset.value < dragRange * 0.85f } }
        val isMiniVisible   by remember { derivedStateOf { animOffset.value > dragRange * 0.70f } }
        val isLyricsVisible by remember { derivedStateOf { lyricsSlide.value > 0f } }
        val isLyricsOpen    by remember { derivedStateOf { lyricsSlide.value > 1f } }

        var isPlayerOpen    by remember { mutableStateOf(false) }
        var isBackGesturing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            snapshotFlow { animOffset.value }
                .collect { offset ->
                    if (!isBackGesturing) {
                        isPlayerOpen = offset < dragRange * 0.1f
                    }
                }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { animOffset.value > dragRange * 0.95f }
                .distinctUntilChanged()
                .collect { if (it) lyricsSlide.snapTo(0f) }
        }

        var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
        val onlineLrcText by viewModel.playerController.onlineLrcText.collectAsStateWithLifecycle()
        val isOnlineMode  by viewModel.playerController.isOnlineMode.collectAsStateWithLifecycle()

        LaunchedEffect(song?.id, isOnlineMode, onlineLrcText) {
            lrcLines = emptyList()
            if (isOnlineMode) {
                // 在线播放：直接解析内存里的歌词文本
                if (onlineLrcText.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        lrcLines = LrcParser.parse(onlineLrcText)
                    }
                }
            } else {
                // 本地播放：从文件读
                val fp  = song?.folderPath ?: return@LaunchedEffect
                val t   = song?.title      ?: return@LaunchedEffect
                val fp2 = song?.filePath   ?: ""
                val ar  = song?.artist     ?: ""
                withContext(Dispatchers.IO) { lrcLines = LrcParser.loadForSong(fp, t, fp2, ar) ?: emptyList() }
            }
        }

        var currentMsForLyrics by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (isActive) { currentMsForLyrics = viewModel.playerController.getCurrentPosition(); delay(500) }
        }

        val velocityTracker = remember { VelocityTracker() }

        fun settlePlayer(vy: Float) {
            val p = 1f - animOffset.value / dragRange
            scope.launch { animOffset.animateTo(if (vy < -600f || (vy in -600f..600f && p > 0.45f)) 0f else dragRange, tween(400, easing = SettleEasing)) }
        }
        fun settleLyrics(vy: Float) {
            scope.launch { lyricsSlide.animateTo(if (vy < -400f || (vy in -400f..400f && lyricsSlide.value > screenHPx * 0.4f)) screenHPx else 0f, tween(400, easing = SettleEasing)) }
        }

        PredictiveBackHandler(enabled = isLyricsOpen) { progress ->
            try {
                progress.collect { event -> lyricsSlide.snapTo(screenHPx * (1f - event.progress)) }
                lyricsSlide.animateTo(0f, tween(300, easing = SettleEasing))
            } catch (e: CancellationException) {
                lyricsSlide.animateTo(screenHPx, tween(300, easing = SettleEasing))
            }
        }

        PredictiveBackHandler(enabled = isPlayerOpen && !isLyricsOpen) { progress ->
            isBackGesturing = true
            try {
                progress.collect { event -> animOffset.snapTo(dragRange * event.progress) }
                animOffset.animateTo(dragRange, tween(300, easing = SettleEasing))
            } catch (e: CancellationException) {
                animOffset.animateTo(0f, tween(300, easing = SettleEasing))
            } finally {
                isBackGesturing = false
            }
        }

        val miniGestureMod = Modifier.pointerInput(dragRange) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { animOffset.stop() }
                velocityTracker.resetTracking()
                velocityTracker.addPointerInputChange(down)
                var totalDy = 0f; var dragging = false; var cur = animOffset.value
                while (true) {
                    val event  = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dy = change.position.y - change.previousPosition.y
                    totalDy += dy
                    if (!dragging && abs(totalDy) > viewConfiguration.touchSlop) dragging = true
                    if (dragging) { velocityTracker.addPointerInputChange(change); cur = (cur + dy).coerceIn(0f, dragRange); playerSnapCh.trySend(cur); change.consume() }
                    if (!change.pressed) break
                }
                if (dragging) settlePlayer(velocityTracker.calculateVelocity().y)
                else scope.launch { animOffset.animateTo(0f, TapSpring) }
            }
        }

        val fullGestureMod = Modifier.pointerInput(dragRange, screenHPx) {
            var lastTargetLyrics = false; var pv = animOffset.value; var lv = lyricsSlide.value
            detectVerticalDragGestures(
                onDragStart = {
                    scope.launch { animOffset.stop(); lyricsSlide.stop() }
                    velocityTracker.resetTracking(); pv = animOffset.value; lv = lyricsSlide.value
                    lastTargetLyrics = lv > screenHPx * 0.1f
                },
                onVerticalDrag = { change, dy ->
                    velocityTracker.addPointerInputChange(change)
                    when {
                        dy < 0f && lv > 0f              -> { lastTargetLyrics = true;  lv = (lv - dy).coerceAtMost(screenHPx); lyricsSnapCh.trySend(lv) }
                        dy > 0f && lv > 0f              -> { lastTargetLyrics = true;  lv = (lv - dy).coerceAtLeast(0f);       lyricsSnapCh.trySend(lv) }
                        dy < 0f && lv == 0f && pv == 0f -> { lastTargetLyrics = true;  lv = (lv - dy).coerceAtMost(screenHPx); lyricsSnapCh.trySend(lv) }
                        else                            -> { lastTargetLyrics = false; pv = (pv + dy).coerceIn(0f, dragRange);  playerSnapCh.trySend(pv) }
                    }
                },
                onDragEnd    = { val vel = velocityTracker.calculateVelocity().y; if (lastTargetLyrics) settleLyrics(vel) else settlePlayer(vel) },
                onDragCancel = { scope.launch { if (lastTargetLyrics) lyricsSlide.animateTo(0f, tween(400, easing = SettleEasing)) else animOffset.animateTo(dragRange, tween(400, easing = SettleEasing)) } }
            )
        }

        val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)

        fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowingBg(
            alpha: Float = 1f,
            width: Float = size.width,
            height: Float = size.height
        ) {
            if (color1 == Color.Transparent) return
            val cx = width / 2f; val cy = height / 2f
            val r  = width.coerceAtLeast(height)
            val baseAlpha = if (isDark) 0.55f else 0.70f
            drawRect(color = color1.copy(alpha = baseAlpha * alpha), size = Size(width, height))
            val h1x = cx + cos(flowAngle1) * width * 0.42f
            val h1y = cy + sin(flowAngle1) * height * 0.35f
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = (if (isDark) 0.85f else 0.90f) * alpha), Color.Transparent),
                    center = Offset(h1x, h1y), radius = r * 0.75f),
                radius = r * 0.75f, center = Offset(h1x, h1y))
            val h2x = cx + cos(flowAngle2) * width * 0.38f
            val h2y = cy + sin(flowAngle2) * height * 0.40f
            drawCircle(
                brush  = Brush.radialGradient(
                    colors = listOf(color3.copy(alpha = (if (isDark) 0.75f else 0.80f) * alpha), Color.Transparent),
                    center = Offset(h2x, h2y), radius = r * 0.65f),
                radius = r * 0.65f, center = Offset(h2x, h2y))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = (if (isDark) 0.55f else 0.30f) * alpha)),
                    startY = height * 0.45f, endY = height),
                size = Size(width, height))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animOffset.value.roundToInt()) }
                .drawBehind {
                    val p = (1f - animOffset.value / dragRange).coerceIn(0f, 1f)

                    // ✅ 关键改动：p=0（迷你状态）时完全不画背景卡片
                    // 让迷你播放条自己的玻璃层负责背景，不再遮挡它
                    // p>0 开始往上滑时，卡片背景慢慢淡入
                    if (p <= 0f) return@drawBehind

                    // 前15%的滑动行程里，卡片从透明渐变到不透明（过渡更自然）
                    val cardAlpha = (p / 0.15f).coerceIn(0f, 1f)

                    val bgX = sidePx * (1f - p)
                    val bgW = size.width - 2f * sidePx * (1f - p)
                    val bgH = miniHPx + (size.height - miniHPx) * p
                    val cr  = CornerRadius(cornerMaxPx * (1f - p))

                    drawRoundRect(
                        color        = surfaceColor.copy(alpha = cardAlpha),
                        topLeft      = Offset(bgX, 0f),
                        size         = Size(bgW, bgH),
                        cornerRadius = cr
                    )

                    if (p > 0.01f) {
                        val clipPath = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                left = bgX, top = 0f, right = bgX + bgW, bottom = bgH,
                                cornerRadius = CornerRadius(cornerMaxPx * (1f - p))
                            ))
                        }
                        clipPath(clipPath) {
                            drawFlowingBg(alpha = p, width = bgW, height = bgH)
                        }
                    }
                }
        ) {
            if (isFullVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = (1f - animOffset.value / dragRange).coerceIn(0f, 1f)
                            alpha = ((p - 0.15f) / 0.25f).coerceIn(0f, 1f)
                        }
                        .then(fullGestureMod)
                ) {
                    if (isLyricsVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val frac = (lyricsSlide.value / screenHPx).coerceIn(0f, 1f)
                                    translationY = screenHPx * (1f - frac)
                                    alpha        = frac
                                }
                        ) {
                            Box(Modifier.fillMaxSize().drawBehind {
                                drawRect(surfaceColor)
                                drawFlowingBg()
                            })
                            LyricsScreen(
                                lrcLines  = lrcLines,
                                currentMs = currentMsForLyrics,
                                onCollapse = { scope.launch { lyricsSlide.animateTo(0f, tween(400, easing = SettleEasing)) } },
                                onSeekTo   = { ms -> viewModel.playerController.seekTo(ms) },
                                modifier   = Modifier.fillMaxSize().statusBarsPadding()
                            )
                        }
                    }

                    FullPlayer(
                        viewModel   = viewModel,
                        animOffset  = animOffset,
                        dragRange   = dragRange,
                        lyricsSlide = lyricsSlide,
                        onCollapse  = { scope.launch { animOffset.animateTo(dragRange, tween(400, easing = SettleEasing)) } },
                        modifier    = Modifier.fillMaxSize()
                    )
                }
            }

            // ✅ 迷你播放条：始终固定在底部，不随卡片移动
            // 位置始终保持在屏幕底部正确位置
            if (isMiniVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MiniSidePad)
                        .offset { IntOffset(0, (screenHPx - miniHPx - botPx - animOffset.value).roundToInt()) }
                        .graphicsLayer {
                            val sinkFrac = ((1f - animOffset.value / dragRange) / 0.30f).coerceIn(0f, 1f)
                            alpha        = 1f - sinkFrac
                            translationY = with(density) { 60.dp.toPx() } * sinkFrac
                        }
                ) {
                    MiniPlayer(viewModel = viewModel, gestureMod = miniGestureMod, hazeState = hazeState)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    viewModel  : MusicViewModel,
    gestureMod : Modifier = Modifier,
    hazeState  : HazeState
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val s = song ?: return
    val radius = MiniPlayerHeight / 2
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .shadow(
                elevation    = 20.dp,
                shape        = RoundedCornerShape(radius),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.15f),
                spotColor    = Color.Black.copy(alpha = if (isDark) 0.4f else 0.12f),
                clip         = false
            )
    ) {

        // 层1：Haze 真实背景模糊 + 底色
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(radius))
                .hazeEffect(state = hazeState)
                .background(
                    if (isDark) Color.Black.copy(alpha = 0.75f)
                    else Color.White.copy(alpha = 0.85f)
                )
        )

        // 层2：轻微顶部高光让卡片有立体感
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val h  = size.height
                    val cr = CornerRadius(h / 2f)
                    // 顶部细边高光
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.18f else 0.50f),
                                Color.Transparent
                            ),
                            startY = 0f, endY = h * 0.4f
                        ),
                        cornerRadius = cr,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
        )

        // 层3：内容
        Row(
            modifier          = Modifier.fillMaxWidth().height(MiniPlayerHeight).padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = gestureMod.weight(1f).height(MiniPlayerHeight), contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model              = s.albumArtUri,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(50.dp).graphicsLayer { clip = true; shape = RoundedCornerShape(13.dp) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            style    = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color    = if (isDark) Color.White else Color.Black,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            s.artist,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = if (isDark) Color.White.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.60f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.playerController.skipToPrevious() }, Modifier.size(42.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(24.dp), tint = if (isDark) Color.White else Color.Black)
            }
            IconButton(onClick = { viewModel.playerController.togglePlayPause() }, Modifier.size(46.dp)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(28.dp), tint = if (isDark) Color.White else Color.Black)
            }
            IconButton(onClick = { viewModel.playerController.skipToNext() }, Modifier.size(42.dp)) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(24.dp), tint = if (isDark) Color.White else Color.Black)
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun FullPlayer(
    viewModel   : MusicViewModel,
    animOffset  : Animatable<Float, *>,
    dragRange   : Float,
    lyricsSlide : Animatable<Float, *>,
    onCollapse  : () -> Unit,
    modifier    : Modifier = Modifier
) {
    val controller    = viewModel.playerController
    val song          by controller.currentSong.collectAsStateWithLifecycle()
    val isPlaying     by controller.isPlaying.collectAsStateWithLifecycle()
    val repeatMode    by controller.repeatMode.collectAsStateWithLifecycle()
    val scope         = rememberCoroutineScope()
    val controllerRef by rememberUpdatedState(controller)

    var currentMs   by remember { mutableLongStateOf(0L) }
    var durationMs  by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProg   by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isScrubbing) { currentMs = controllerRef.getCurrentPosition(); durationMs = controllerRef.getDuration().coerceAtLeast(1L) }
            delay(500)
        }
    }

    val queueSize  = remember(song) { controller.getCurrentQueueSize().coerceAtLeast(1) }
    val initIdx    = remember(song) { controller.getCurrentIndex().coerceIn(0, queueSize - 1) }
    val pagerState = rememberPagerState(initialPage = initIdx, pageCount = { queueSize })

    LaunchedEffect(song?.id) {
        val t = controller.getCurrentIndex().coerceIn(0, pagerState.pageCount - 1)
        if (pagerState.currentPage != t) pagerState.scrollToPage(t)
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.pageCount > 0) {
            val idx = pagerState.currentPage
            if (idx != controller.getCurrentIndex()) controller.playAtIndex(idx)
        }
    }

    val displaySong = controller.getSongAt(pagerState.currentPage) ?: song
    val onBg = Color.White

    val fadeAlpha = Modifier.graphicsLayer {
        val p = (1f - animOffset.value / dragRange).coerceIn(0f, 1f)
        alpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
    }

    Column(
        modifier            = modifier.fillMaxSize()
            .graphicsLayer { translationY = -lyricsSlide.value.toFloat() }
            .systemBarsPadding(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).then(fadeAlpha),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "收起", Modifier.size(30.dp), tint = onBg.copy(alpha = 0.90f)) }
            Text("N O W  P L A Y I N G",
                style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 3.sp),
                color    = onBg.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = {}, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, "更多", Modifier.size(22.dp), tint = onBg.copy(alpha = 0.90f)) }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = FullPagerPad), contentAlignment = Alignment.Center) {
            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model              = controller.getSongAt(page)?.albumArtUri ?: song?.albumArtUri,
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier           = Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                            .graphicsLayer { clip = true; shape = RoundedCornerShape(22.dp) })
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(displaySong?.title ?: "", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(fadeAlpha))
        Spacer(Modifier.height(5.dp))
        Text(displaySong?.artist ?: "", style = MaterialTheme.typography.bodyLarge,
            color = onBg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(fadeAlpha))
        Spacer(Modifier.height(24.dp))

        Column(Modifier.padding(horizontal = 20.dp).then(fadeAlpha)) {
            val prog = if (isScrubbing) scrubProg else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Slider(value = prog,
                onValueChange         = { isScrubbing = true; scrubProg = it; currentMs = (it * durationMs).toLong() },
                onValueChangeFinished = { controllerRef.seekTo((scrubProg * durationMs).toLong()); isScrubbing = false },
                modifier = Modifier.fillMaxWidth(),
                colors   = SliderDefaults.colors(thumbColor = onBg, activeTrackColor = onBg, inactiveTrackColor = onBg.copy(alpha = 0.20f)))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatMs(currentMs), style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.70f))
                Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.70f))
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).then(fadeAlpha),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller.toggleRepeat() }, Modifier.size(48.dp)) {
                Icon(when (repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat },
                    null, Modifier.size(24.dp),
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else onBg.copy(alpha = 0.35f)) }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp), tint = onBg) }
            FilledIconButton(onClick = { controller.togglePlayPause() }, Modifier.size(74.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(36.dp), tint = Color(0xFF1A1A1A)) }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp), tint = onBg) }
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatMs(ms: Long): String = "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)