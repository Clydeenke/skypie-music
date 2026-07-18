@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yulight.skypie.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.yulight.skypie.util.LrcLine
import com.yulight.skypie.util.FavoriteManager
import com.yulight.skypie.util.FavoriteSong
import com.yulight.skypie.util.LrcParser
import com.yulight.skypie.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberGyroscopeState(enabled: Boolean = true): Pair<Float, Float> {
    val context = LocalContext.current
    val maxAngle = 12f  // 最大角度

    var angleX by remember { mutableFloatStateOf(0f) }
    var angleY by remember { mutableFloatStateOf(0f) }
    var lastTimestamp by remember { mutableLongStateOf(0L) }

    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(SensorManager::class.java)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val currentTime = event.timestamp
                if (lastTimestamp == 0L) {
                    lastTimestamp = currentTime
                    return
                }

                val deltaTime = (currentTime - lastTimestamp) / 1_000_000_000f
                lastTimestamp = currentTime
                if (deltaTime > 0.1f) return

                val rawDeltaX = -Math.toDegrees(event.values[1].toDouble() * deltaTime).toFloat()
                val rawDeltaY = -Math.toDegrees(event.values[0].toDouble() * deltaTime).toFloat()

                val ratioX = (kotlin.math.abs(angleX) / maxAngle).coerceIn(0f, 1f)
                val ratioY = (kotlin.math.abs(angleY) / maxAngle).coerceIn(0f, 1f)
                val pushingX = (angleX > 0 && rawDeltaX > 0) || (angleX < 0 && rawDeltaX < 0)
                val pushingY = (angleY > 0 && rawDeltaY > 0) || (angleY < 0 && rawDeltaY < 0)
                val dampingX = if (pushingX) 1f - ratioX * ratioX else 1f
                val dampingY = if (pushingY) 1f - ratioY * ratioY else 1f

                angleX = (angleX + rawDeltaX * dampingX).coerceIn(-maxAngle, maxAngle)
                angleY = (angleY + rawDeltaY * dampingY).coerceIn(-maxAngle, maxAngle)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return angleX to angleY
}

@Composable
internal fun FullPlayer(
    viewModel   : MusicViewModel,
    animOffset  : Animatable<Float, *>,
    dragRange   : Float,
    lyricsSlide : Animatable<Float, *>,
    screenHPx   : Float,
    onCollapse  : () -> Unit,
    onQueueClick: () -> Unit = {},
    modifier    : Modifier = Modifier
) {
    val controller    = viewModel.playerController
    val song          by controller.currentSong.collectAsStateWithLifecycle()
    val rawIsPlaying  by controller.isPlaying.collectAsStateWithLifecycle()
    val playMode      by controller.playMode.collectAsStateWithLifecycle()

    // 播放状态防抖：切歌瞬间避免闪烁
    var debouncedIsPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(rawIsPlaying) {
        if (!rawIsPlaying) delay(250)
        debouncedIsPlaying = rawIsPlaying
    }
    val scope         = rememberCoroutineScope()
    val controllerRef by rememberUpdatedState(controller)

    // 读取 3D 封面设置
    val context = LocalContext.current
    val enable3DCover = remember {
        context.getSharedPreferences("skypie_settings", 0).getBoolean("enable_3d_cover", true)
    }
    val (rawTiltX, rawTiltY) = rememberGyroscopeState(enabled = enable3DCover)
    val tiltX = if (enable3DCover) rawTiltX else 0f
    val tiltY = if (enable3DCover) rawTiltY else 0f

    var currentMs   by remember { mutableLongStateOf(0L) }
    var durationMs  by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProg   by remember { mutableFloatStateOf(0f) }
    var lastSongId  by remember { mutableStateOf(song?.id) }
    val progressAnim = remember { Animatable(0f) }

    // 切歌时进度点滑动归零动画（仅非拖动时触发）
    LaunchedEffect(song?.id) {
        if (lastSongId != null && song?.id != lastSongId && !isScrubbing) {
            progressAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        }
        lastSongId = song?.id
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isScrubbing) {
                currentMs  = controllerRef.getCurrentPosition()
                durationMs = controllerRef.getDuration().coerceAtLeast(1L)
                progressAnim.snapTo((currentMs.toFloat() / durationMs).coerceIn(0f, 1f))
            }
            delay(500)
        }
    }

    val queueSize  = remember(song) { controller.getCurrentQueueSize().coerceAtLeast(1) }
    val initIdx    = remember(song) { controller.getCurrentIndex().coerceIn(0, queueSize - 1) }
    val pagerState = rememberPagerState(initialPage = initIdx, pageCount = { queueSize })

    LaunchedEffect(song?.id) {
        val t = controller.getCurrentIndex().coerceIn(0, pagerState.pageCount - 1)
        if (pagerState.currentPage != t) {
            pagerState.animateScrollToPage(
                page = t,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
        }
    }

    // 滑动切歌：停下后识别停在哪首歌，直接播放它
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.pageCount > 0) {
            val idx = pagerState.currentPage
            if (idx != controller.getCurrentIndex()) {
                controller.playAtIndex(idx)
            }
        }
    }

    val displaySong = controller.getSongAt(pagerState.currentPage) ?: song
    val onBg        = Color.White

    // ── 收藏 ──────────────────────────────────────────────────────────────────
    var isFavorite by remember(displaySong?.id) {
        mutableStateOf(FavoriteManager.isFavorite(context, displaySong?.id?.toString() ?: ""))
    }
    var isDownloading by remember { mutableStateOf(false) }

    fun toggleFavorite() {
        val s = displaySong ?: return
        val newState = !isFavorite
        isFavorite = newState
        if (newState) {
            val favSong = FavoriteSong(
                songId = s.id.toString(),
                title = s.title,
                artist = s.artist,
                coverUrl = s.albumArtUri ?: "",
                source = "",
                duration = s.duration.toInt()
            )
            FavoriteManager.addFavorite(context, favSong)
            // 下载歌曲
            val streamUrl = controller.getCurrentStreamUrl() ?: return
            isDownloading = true
            val settings = context.getSharedPreferences("skypie_settings", 0)
            val downloadDir = settings.getString("download_dir", com.yulight.skypie.ui.screen.settings.DEFAULT_DOWNLOAD_DIR) ?: com.yulight.skypie.ui.screen.settings.DEFAULT_DOWNLOAD_DIR
            val title = s.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val artist = s.artist.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(streamUrl)).apply {
                setTitle(s.title); setDescription(s.artist)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "$downloadDir/$artist - $title.mp3")
                addRequestHeader("User-Agent", "Mozilla/5.0")
            }
            context.getSystemService(android.app.DownloadManager::class.java).enqueue(req)
            isDownloading = false
        } else {
            FavoriteManager.removeFavorite(context, s.id.toString())
        }
    }

    // ── 歌词 ──────────────────────────────────────────────────────────────────
    val onlineLrcText by viewModel.playerController.onlineLrcText.collectAsStateWithLifecycle()
    val isOnlineMode  by viewModel.playerController.isOnlineMode.collectAsStateWithLifecycle()
    var lrcLines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    LaunchedEffect(displaySong?.id, isOnlineMode, onlineLrcText) {
        lrcLines = emptyList()
        val karaokeEnabled = context.getSharedPreferences("skypie_settings", 0).getBoolean("enable_karaoke", false)
        if (isOnlineMode) {
            withContext(Dispatchers.IO) {
                if (karaokeEnabled) {
                    val t = displaySong?.title ?: ""
                    val ar = displaySong?.artist ?: ""
                    val networkLyrics = com.yulight.skypie.util.LyricsSearcher.searchLyrics(t, ar)
                    if (!networkLyrics.isNullOrBlank()) {
                        lrcLines = LrcParser.parse(networkLyrics)
                        return@withContext
                    }
                }
                if (onlineLrcText.isNotBlank()) { lrcLines = LrcParser.parse(onlineLrcText) }
            }
        } else {
            val fp  = displaySong?.folderPath ?: return@LaunchedEffect
            val t   = displaySong?.title      ?: return@LaunchedEffect
            val fp2 = displaySong?.filePath   ?: ""
            val ar  = displaySong?.artist     ?: ""
            withContext(Dispatchers.IO) {
                if (karaokeEnabled) {
                    // 逐字开关开 → 先尝试云端逐字歌词
                    val networkLyrics = com.yulight.skypie.util.LyricsSearcher.searchLyrics(t, ar)
                    if (!networkLyrics.isNullOrBlank()) {
                        lrcLines = LrcParser.parse(networkLyrics)
                        return@withContext
                    }
                }
                // 逐字开关关 或 没有逐字 → 读内嵌歌词
                val embedded: String? = if (fp2.isNotBlank()) {
                    try { AudioFileIO.read(java.io.File(fp2)).tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
                } else null
                if (!embedded.isNullOrBlank()) {
                    lrcLines = LrcParser.parse(embedded)
                } else {
                    val localLrc = LrcParser.loadForSong(fp, t, fp2, ar)
                    if (localLrc != null && localLrc.isNotEmpty()) {
                        lrcLines = localLrc
                    }
                }
            }
        }
    }

    val currentLrcIdx = remember(currentMs, lrcLines, displaySong?.id) {
        if (lrcLines.isEmpty()) -1
        else {
            // 切歌时 currentMs 可能还是旧值，先找最接近 0 的歌词行
            val idx = lrcLines.indexOfLast { it.timeMs <= currentMs }
            if (idx < 0) 0 else idx
        }
    }

    val fadeAlpha = Modifier.graphicsLayer {
        val p = (1f - animOffset.value / dragRange).coerceIn(0f, 1f)
        alpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
    }

    Column(
        modifier            = modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -lyricsSlide.value }
            .systemBarsPadding(),
        horizontalAlignment = Alignment.Start
    ) {
        // ── 顶部栏 ─────────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).then(fadeAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PressableIcon(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "收起", Modifier.size(30.dp), tint = onBg.copy(alpha = 0.90f))
            }
            Text(
                "N O W  P L A Y I N G",
                style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 3.sp),
                color    = onBg.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            PressableIcon(onClick = onQueueClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.FormatListBulleted, "播放队列", Modifier.size(22.dp), tint = onBg.copy(alpha = 0.90f))
            }
        }

        // ── 封面翻页 ───────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = FullPagerPad), contentAlignment = Alignment.Center) {
            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { page ->
                val isCurrentPage = page == pagerState.currentPage
                val distance = kotlin.math.abs(page - pagerState.currentPage)
                val shouldLoad = distance <= 1

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model              = if (shouldLoad) controller.getSongAt(page)?.albumArtUri ?: song?.albumArtUri else null,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                if (isCurrentPage && enable3DCover) {
                                    rotationX = tiltY
                                    rotationY = -tiltX
                                    cameraDistance = 12f * density
                                }
                                clip = true
                                shape = RoundedCornerShape(22.dp)
                            }
                            .drawWithContent {
                                drawContent()
                                if (isCurrentPage && enable3DCover) {
                                    val lightX = size.width * (0.5f + tiltX / 30f)
                                    val lightY = size.height * (0.4f - tiltY / 30f)
                                    drawRect(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.2f),
                                                Color.White.copy(alpha = 0.05f),
                                                Color.Transparent
                                            ),
                                            center = Offset(lightX, lightY),
                                            radius = size.width * 0.9f
                                        )
                                    )
                                }
                            }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── 歌名 + 收藏 ────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 24.dp).then(fadeAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState   = displaySong?.title ?: "",
                    transitionSpec = { (slideInHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 3 } + fadeIn(tween(350))).togetherWith(fadeOut(tween(250))) },
                    label = "title"
                ) { title ->
                    Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(3.dp))
                AnimatedContent(
                    targetState   = displaySong?.artist ?: "",
                    transitionSpec = { (slideInHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it / 4 } + fadeIn(tween(380))).togetherWith(fadeOut(tween(250))) },
                    label = "artist"
                ) { artist ->
                    Text(artist, style = MaterialTheme.typography.bodyLarge, color = onBg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val favScale by animateFloatAsState(if (isFavorite) 1.25f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "fav")
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

        // ── 歌词预览条 ─────────────────────────────────────────────────────────
        val lrcLineHeightDp = 24.dp
        val lrcListState    = rememberLazyListState()

        LaunchedEffect(currentLrcIdx) {
            if (currentLrcIdx >= 0 && lrcLines.isNotEmpty())
                lrcListState.animateScrollToItem(currentLrcIdx.coerceAtLeast(0))
        }
        val lrcWindowHeight = lrcLineHeightDp * 3

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lrcWindowHeight)
                .padding(horizontal = 24.dp)
                .then(fadeAlpha)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black, 1f to Color.Black), blendMode = BlendMode.DstIn)
                    drawRect(brush = Brush.verticalGradient(0f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }
        ) {
            if (lrcLines.isNotEmpty()) {
                LazyColumn(state = lrcListState, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(lrcLineHeightDp)) }
                    itemsIndexed(lrcLines) { idx, line ->
                        val isActive = idx == currentLrcIdx
                        val textAlpha by animateFloatAsState(
                            targetValue   = when { isActive -> 1f; idx == currentLrcIdx - 1 || idx == currentLrcIdx + 1 -> 0.45f; else -> 0.18f },
                            animationSpec = tween(600), label = "lrcAlpha$idx"
                        )
                        val textScale by animateFloatAsState(
                            targetValue = if (isActive) 1.05f else 1f,
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                            label = "lrcScale$idx"
                        )

                        // 预览条：始终使用普通文字，不做逐字染色
                        Text(
                            text     = line.text,
                            style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal),
                            color    = onBg.copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lrcLineHeightDp)
                                .wrapContentHeight(Alignment.CenterVertically)
                                .graphicsLayer { scaleX = textScale; scaleY = textScale; transformOrigin = TransformOrigin(0f, 0.5f) }
                        )
                    }
                    item { Spacer(Modifier.height(lrcLineHeightDp)) }
                }
            } else {
                Spacer(Modifier.height(lrcWindowHeight))
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 进度条 ─────────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 20.dp).then(fadeAlpha)) {
            val prog = if (isScrubbing) scrubProg else progressAnim.value
            var isDragging by remember { mutableStateOf(false) }

            // 线条粗细动画
            val strokeWidth by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 3.dp,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "strokeWidth"
            )

            // 手柄透明度动画
            val thumbAlpha by animateFloatAsState(
                targetValue = if (isDragging) 1f else 0f,
                animationSpec = if (isDragging) tween(durationMillis = 180) else tween(durationMillis = 250, delayMillis = 200),
                label = "thumbAlpha"
            )

            // 手柄缩放动画
            val thumbScale by animateFloatAsState(
                targetValue = if (isDragging) 1.15f else 1f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "thumbScale"
            )

            // 呼吸光点动画（仅播放中且未拖动时激活）
            val showBreath = debouncedIsPlaying && !isDragging
            val breathScale by rememberInfiniteTransition(label = "breath").animateFloat(
                initialValue = 1f,
                targetValue = if (showBreath) 1.12f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathScale"
            )
            val breathAlpha by rememberInfiniteTransition(label = "breathAlpha").animateFloat(
                initialValue = 0.7f,
                targetValue = if (showBreath) 1f else 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            isScrubbing = true
                            scrubProg = (down.position.x / size.width).coerceIn(0f, 1f)
                            currentMs = (scrubProg * durationMs).toLong()

                            var previousX = down.position.x
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val dx = change.position.x - previousX
                                if (kotlin.math.abs(dx) > 0.5f) {
                                    previousX = change.position.x
                                }
                                change.consume()
                                scrubProg = (change.position.x / size.width).coerceIn(0f, 1f)
                                currentMs = (scrubProg * durationMs).toLong()
                            }

                            val targetMs = (scrubProg * durationMs).toLong()
                            val targetProg = scrubProg
                            scope.launch {
                                progressAnim.snapTo(targetProg)
                                controllerRef.seekTo(targetMs)
                                delay(50)
                                isDragging = false
                                isScrubbing = false
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)

                    // 背景轨道
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // 已播放轨道
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, y),
                        end = Offset(size.width * prog, y),
                        strokeWidth = strokeWidth.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    // 呼吸光点（仅播放中且未拖动时显示）
                    if (!isDragging && debouncedIsPlaying) {
                        drawCircle(
                            color = Color.White.copy(alpha = breathAlpha * 0.6f),
                            radius = (strokeWidth.toPx() / 2f + 4.dp.toPx()) * breathScale,
                            center = Offset(size.width * prog, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = strokeWidth.toPx() / 2f,
                            center = Offset(size.width * prog, y)
                        )
                    }

                    // 拖动手柄
                    if (thumbAlpha > 0.01f) {
                        val thumbRadius = 7.dp.toPx() * thumbScale
                        val thumbX = size.width * prog
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.15f * thumbAlpha),
                            radius = thumbRadius + 3.dp.toPx(),
                            center = Offset(thumbX, y)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = thumbAlpha),
                            radius = thumbRadius,
                            center = Offset(thumbX, y)
                        )
                    }
                }
            }

            // 时间文字
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatMs(currentMs), style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.70f))
                Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.70f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 控制按钮行 ─────────────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp).then(fadeAlpha),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            PressableIcon(
                onClick = { controller.togglePlayMode() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = when (playMode) {
                        1 -> Icons.Rounded.RepeatOne
                        2 -> Icons.Rounded.Shuffle
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (playMode != 0) onBg else onBg.copy(alpha = 0.35f)
                )
            }
            PressableIcon(
                onClick = { controller.skipToPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp), tint = onBg)
            }
            PressableIcon(
                onClick = { controller.togglePlayPause() },
                modifier = Modifier.size(74.dp)
            ) {
                Crossfade(
                    targetState = debouncedIsPlaying,
                    animationSpec = tween(200),
                    label = "playPause"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }
            PressableIcon(
                onClick = { controller.skipToNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp), tint = onBg)
            }
            PressableIcon(
                onClick = { scope.launch { lyricsSlide.animateTo(screenHPx, tween(550, easing = EaseInOutCubic)) } },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Lyrics, null, Modifier.size(22.dp), tint = onBg.copy(alpha = 0.55f))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun PressableIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "pressScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(120),
        label = "pressAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

internal fun formatMs(ms: Long): String = "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)