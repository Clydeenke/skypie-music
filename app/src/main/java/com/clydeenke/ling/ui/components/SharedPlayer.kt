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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.border

private val AppleEasing = CubicBezierEasing(0.32f, 0.94f, 0.60f, 1.0f)

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

    // 多选模式激活时隐藏迷你播放条（底部操作栏独占空间）
    val isMultiSelectActive by viewModel.isMultiSelectActive.collectAsStateWithLifecycle()
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
                // 本地播放：优先读 ID3 嵌入歌词，找不到再查 .lrc 文件
                val fp  = song?.folderPath ?: return@LaunchedEffect
                val t   = song?.title      ?: return@LaunchedEffect
                val fp2 = song?.filePath   ?: ""
                val ar  = song?.artist     ?: ""
                withContext(Dispatchers.IO) {
                    val embedded = if (fp2.isNotBlank()) try {
                        AudioFileIO.read(java.io.File(fp2)).tag
                            ?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) { null } else null

                    lrcLines = if (!embedded.isNullOrBlank()) LrcParser.parse(embedded)
                    else LrcParser.loadForSong(fp, t, fp2, ar) ?: emptyList()
                }
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
                    if (p <= 0f) return@drawBehind
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
                        screenHPx   = screenHPx,
                        onCollapse  = { scope.launch { animOffset.animateTo(dragRange, tween(400, easing = SettleEasing)) } },
                        modifier    = Modifier.fillMaxSize()
                    )
                }
            }

            if (isMiniVisible && !isMultiSelectActive) {
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
fun MiniPlayer(
    viewModel: MusicViewModel,
    gestureMod: Modifier = Modifier,
    hazeState: HazeState
) {
    val song by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val s = song ?: return
    val radius = MiniPlayerHeight / 2
    val isDark = isSystemInDarkTheme()

    val targetColor = MaterialTheme.colorScheme.primaryContainer

    val dominantColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(800, 0, AppleEasing),
        label = "ColorTransition"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(radius),
                spotColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.15f),
                ambientColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.1f)
            )
            .border(
                width = 0.8.dp,
                color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                shape = RoundedCornerShape(radius)
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(radius))
                .hazeEffect(
                    state = hazeState,
                    style = dev.chrisbanes.haze.HazeStyle(
                        blurRadius = 12.dp,
                        noiseFactor = 0.02f,
                        tints = listOf(
                            dev.chrisbanes.haze.HazeTint(
                                color = if (isDark) Color.Transparent
                                else Color.Black.copy(alpha = 0.02f)
                            )
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val h = size.height
                    val cr = CornerRadius(h / 2f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.15f else 0.45f),
                                Color.Transparent
                            ),
                            startY = 0f, endY = h * 0.5f
                        ),
                        cornerRadius = cr,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = gestureMod.weight(1f).height(MiniPlayerHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = s.albumArtUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .shadow(2.dp, RoundedCornerShape(10.dp))
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = if (isDark) Color.White else Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = s.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val iconTint = if (isDark) Color.White else Color.Black

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.playerController.skipToPrevious() }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(22.dp), tint = iconTint)
                }

                IconButton(
                    onClick = { viewModel.playerController.togglePlayPause() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = iconTint
                    )
                }

                IconButton(onClick = { viewModel.playerController.skipToNext() }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(22.dp), tint = iconTint)
                }
            }
        }
    }
}

@Composable
private fun FullPlayer(
    viewModel   : MusicViewModel,
    animOffset  : Animatable<Float, *>,
    dragRange   : Float,
    lyricsSlide : Animatable<Float, *>,
    screenHPx   : Float,
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

    val context = LocalContext.current
    var isFavorite by remember(displaySong?.id) {
        val prefs = context.getSharedPreferences("ling_favorites", 0)
        mutableStateOf(prefs.getBoolean(displaySong?.id?.toString() ?: "", false))
    }
    var isDownloading by remember { mutableStateOf(false) }

    fun toggleFavorite() {
        val prefs    = context.getSharedPreferences("ling_favorites", 0)
        val newState = !isFavorite
        isFavorite   = newState
        prefs.edit().putBoolean(displaySong?.id?.toString() ?: "", newState).apply()

        if (newState) {
            val streamUrl = controller.getCurrentStreamUrl() ?: return
            val song      = displaySong             ?: return
            val title     = song.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val artist    = song.artist.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val fileName  = "$artist - $title.mp3"

            isDownloading = true
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(streamUrl)).apply {
                setTitle(song.title)
                setDescription(song.artist)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "Ling/$fileName")
                addRequestHeader("User-Agent", "Mozilla/5.0")
            }
            val dm = context.getSystemService(android.app.DownloadManager::class.java)
            dm.enqueue(req)
            isDownloading = false
        }
    }

    val onlineLrcText by viewModel.playerController.onlineLrcText.collectAsStateWithLifecycle()
    val isOnlineMode  by viewModel.playerController.isOnlineMode.collectAsStateWithLifecycle()
    var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    LaunchedEffect(displaySong?.id, isOnlineMode, onlineLrcText) {
        lrcLines = emptyList()
        if (isOnlineMode) {
            if (onlineLrcText.isNotBlank()) withContext(Dispatchers.IO) { lrcLines = LrcParser.parse(onlineLrcText) }
        } else {
            val fp  = displaySong?.folderPath ?: return@LaunchedEffect
            val t   = displaySong?.title      ?: return@LaunchedEffect
            val fp2 = displaySong?.filePath   ?: ""
            val ar  = displaySong?.artist     ?: ""
            withContext(Dispatchers.IO) {
                val embedded = if (fp2.isNotBlank()) try {
                    AudioFileIO.read(java.io.File(fp2)).tag
                        ?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
                } catch (_: Exception) { null } else null

                lrcLines = if (!embedded.isNullOrBlank()) LrcParser.parse(embedded)
                else LrcParser.loadForSong(fp, t, fp2, ar) ?: emptyList()
            }
        }
    }

    val currentLrcIdx = remember(currentMs, lrcLines) {
        if (lrcLines.isEmpty()) -1
        else {
            var idx = lrcLines.indexOfLast { it.timeMs <= currentMs }
            if (idx < 0) idx = 0
            idx
        }
    }

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
                        modifier           = Modifier.fillMaxWidth(0.88f).aspectRatio(1f)
                            .graphicsLayer { clip = true; shape = RoundedCornerShape(22.dp) })
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(fadeAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState   = displaySong?.title ?: "",
                    transitionSpec = {
                        (slideInHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 3 } + fadeIn(tween(350)))
                            .togetherWith(fadeOut(tween(250)))
                    },
                    label = "title"
                ) { title ->
                    Text(title,
                        style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color    = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(3.dp))
                AnimatedContent(
                    targetState   = displaySong?.artist ?: "",
                    transitionSpec = {
                        (slideInHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it / 4 } + fadeIn(tween(380)))
                            .togetherWith(fadeOut(tween(250)))
                    },
                    label = "artist"
                ) { artist ->
                    Text(artist,
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = onBg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val favScale by animateFloatAsState(
                targetValue   = if (isFavorite) 1.25f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                label         = "fav"
            )
            IconButton(onClick = { if (!isDownloading) toggleFavorite() }, Modifier.size(48.dp)) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = onBg.copy(alpha = 0.7f), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector        = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        modifier           = Modifier.size(26.dp).graphicsLayer { scaleX = favScale; scaleY = favScale },
                        tint               = if (isFavorite) Color(0xFFFF4D6A) else onBg.copy(alpha = 0.65f)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val lrcLineHeightDp = 24.dp
        val lrcWindowLines  = 3
        val lrcListState    = rememberLazyListState()

        LaunchedEffect(currentLrcIdx) {
            if (currentLrcIdx >= 0 && lrcLines.isNotEmpty()) {
                lrcListState.animateScrollToItem(
                    index        = (currentLrcIdx).coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }

        val lrcWindowHeight = lrcLineHeightDp * lrcWindowLines

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lrcWindowHeight)
                .padding(horizontal = 24.dp)
                .then(fadeAlpha)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black,
                            1f to Color.Black
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.65f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            if (lrcLines.isNotEmpty()) {
                LazyColumn(
                    state             = lrcListState,
                    userScrollEnabled = false,
                    modifier          = Modifier.fillMaxSize()
                ) {
                    item { Spacer(Modifier.height(lrcLineHeightDp)) }

                    itemsIndexed(lrcLines) { idx, line ->
                        val isActive = idx == currentLrcIdx
                        val textAlpha by animateFloatAsState(
                            targetValue   = when {
                                isActive        -> 1f
                                idx == currentLrcIdx - 1 -> 0.45f
                                idx == currentLrcIdx + 1 -> 0.45f
                                else            -> 0.18f
                            },
                            animationSpec = tween(600),
                            label         = "lrcAlpha$idx"
                        )
                        val textScale by animateFloatAsState(
                            targetValue   = if (isActive) 1.05f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessLow
                            ),
                            label         = "lrcScale$idx"
                        )
                        Text(
                            text     = line.text,
                            style    = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color    = onBg.copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lrcLineHeightDp)
                                .wrapContentHeight(Alignment.CenterVertically)
                                .graphicsLayer {
                                    scaleX          = textScale
                                    scaleY          = textScale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                        )
                    }

                    item { Spacer(Modifier.height(lrcLineHeightDp)) }
                }
            } else {
                Spacer(Modifier.height(lrcWindowHeight))
            }
        }

        Spacer(Modifier.height(10.dp))

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
                Icon(
                    imageVector = when (repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) onBg else onBg.copy(alpha = 0.35f)
                )
            }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp), tint = onBg) }
            FilledIconButton(onClick = { controller.togglePlayPause() }, Modifier.size(74.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(36.dp), tint = Color(0xFF1A1A1A)) }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp), tint = onBg) }
            IconButton(onClick = { scope.launch { lyricsSlide.animateTo(screenHPx, tween(550, easing = androidx.compose.animation.core.EaseInOutCubic)) } }, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Lyrics, null, Modifier.size(22.dp), tint = onBg.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun formatMs(ms: Long): String = "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)