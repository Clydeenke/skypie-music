package com.yulight.skypie.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
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

@Composable
internal fun FullPlayer(
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
            if (!isScrubbing) {
                currentMs  = controllerRef.getCurrentPosition()
                durationMs = controllerRef.getDuration().coerceAtLeast(1L)
            }
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
    val onBg        = Color.White
    val context     = LocalContext.current

    // ── 收藏 ──────────────────────────────────────────────────────────────────
    var isFavorite by remember(displaySong?.id) {
        val prefs = context.getSharedPreferences("skypie_favorites", 0)
        mutableStateOf(prefs.getBoolean(displaySong?.id?.toString() ?: "", false))
    }
    var isDownloading by remember { mutableStateOf(false) }

    fun toggleFavorite() {
        val prefs    = context.getSharedPreferences("skypie_favorites", 0)
        val newState = !isFavorite
        isFavorite   = newState
        prefs.edit().putBoolean(displaySong?.id?.toString() ?: "", newState).apply()
        if (newState) {
            val streamUrl = controller.getCurrentStreamUrl() ?: return
            val s         = displaySong ?: return
            val title     = s.title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val artist    = s.artist.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            isDownloading = true
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(streamUrl)).apply {
                setTitle(s.title); setDescription(s.artist)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "skypie/$artist - $title.mp3")
                addRequestHeader("User-Agent", "Mozilla/5.0")
            }
            context.getSystemService(android.app.DownloadManager::class.java).enqueue(req)
            isDownloading = false
        }
    }

    // ── 歌词 ──────────────────────────────────────────────────────────────────
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
                val embedded: String? = if (fp2.isNotBlank()) {
                    try { AudioFileIO.read(java.io.File(fp2)).tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
                } else null
                lrcLines = if (!embedded.isNullOrBlank()) LrcParser.parse(embedded)
                else LrcParser.loadForSong(fp, t, fp2, ar) ?: emptyList()
            }
        }
    }

    val currentLrcIdx = remember(currentMs, lrcLines) {
        if (lrcLines.isEmpty()) -1
        else lrcLines.indexOfLast { it.timeMs <= currentMs }.let { if (it < 0) 0 else it }
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
            IconButton(onClick = onCollapse, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "收起", Modifier.size(30.dp), tint = onBg.copy(alpha = 0.90f))
            }
            Text(
                "N O W  P L A Y I N G",
                style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 3.sp),
                color    = onBg.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = {}, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, "更多", Modifier.size(22.dp), tint = onBg.copy(alpha = 0.90f))
            }
        }

        // ── 封面翻页 ───────────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = FullPagerPad), contentAlignment = Alignment.Center) {
            HorizontalPager(state = pagerState, Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model              = controller.getSongAt(page)?.albumArtUri ?: song?.albumArtUri,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxWidth(0.88f)
                            .aspectRatio(1f)
                            .graphicsLayer { clip = true; shape = RoundedCornerShape(22.dp) }
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
                        val textScale by animateFloatAsState(if (isActive) 1.05f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "lrcScale$idx")
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
            val prog = if (isScrubbing) scrubProg else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Slider(
                value                 = prog,
                onValueChange         = { isScrubbing = true; scrubProg = it; currentMs = (it * durationMs).toLong() },
                onValueChangeFinished = { controllerRef.seekTo((scrubProg * durationMs).toLong()); isScrubbing = false },
                modifier              = Modifier.fillMaxWidth(),
                colors                = SliderDefaults.colors(thumbColor = onBg, activeTrackColor = onBg, inactiveTrackColor = onBg.copy(alpha = 0.20f))
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(formatMs(currentMs),  style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.70f))
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
            IconButton(onClick = { controller.toggleRepeat() }, Modifier.size(48.dp)) {
                Icon(
                    imageVector        = when (repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat },
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp),
                    tint               = if (repeatMode != Player.REPEAT_MODE_OFF) onBg else onBg.copy(alpha = 0.35f)
                )
            }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp), tint = onBg)
            }
            FilledIconButton(
                onClick = { controller.togglePlayPause() },
                modifier = Modifier.size(74.dp),
                shape    = CircleShape,
                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(36.dp), tint = Color(0xFF1A1A1A))
            }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)) } }, Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp), tint = onBg)
            }
            IconButton(onClick = { scope.launch { lyricsSlide.animateTo(screenHPx, tween(550, easing = EaseInOutCubic)) } }, Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Lyrics, null, Modifier.size(22.dp), tint = onBg.copy(alpha = 0.55f))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

internal fun formatMs(ms: Long): String = "%d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)