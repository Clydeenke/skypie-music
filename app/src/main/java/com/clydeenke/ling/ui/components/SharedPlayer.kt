package com.clydeenke.ling.ui.components

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MiniPlayerHeight = 68.dp

/**
 * 顶层共享播放器。
 *
 * - 迷你条永远固定在底部小点上方一点，不会跑到屏幕顶部。
 * - 全屏播放器从底部向上弹出，与迷你条共用一套状态。
 * - 垂直方向用自定义 pointerInput 做 1:1 跟手，水平方向优先交给 HorizontalPager。
 */
@Composable
fun SharedPlayerContainer(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val song by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    if (song == null) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val miniHeightPx = with(density) { MiniPlayerHeight.toPx() }
        val bottomMarginPx = with(density) { 20.dp.toPx() }

        // FullPlayer 的垂直拖动范围：0f（全屏展开）到 dragRange（完全收起，只剩迷你条）
        val dragRange = (fullHeightPx - miniHeightPx - bottomMarginPx).coerceAtLeast(0f)

        var offsetY by remember { mutableFloatStateOf(dragRange) } // 初始为收起状态

        // 0f = 迷你条状态，1f = 全屏状态
        val progress by remember {
            derivedStateOf {
                if (dragRange <= 0f) 1f
                else (1f - offsetY / dragRange).coerceIn(0f, 1f)
            }
        }

        fun settle(velocityY: Float) {
            val shouldExpand = progress > 0.5f || velocityY < -1200f
            val target = if (shouldExpand) 0f else dragRange
            scope.launch {
                androidx.compose.animation.core.Animatable(offsetY).animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) {
                    offsetY = value
                }
            }
        }

        // 统一的垂直拖拽状态，Full/Mini 共享
        val dragState = rememberDraggableState { delta ->
            offsetY = (offsetY + delta).coerceIn(0f, dragRange)
        }

        // 收起时只占底部一条，不挡音乐库滚动；展开时全屏
        val isCollapsed = progress < 0.5f
        Box(
            modifier = if (isCollapsed) {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom)
                    .align(Alignment.BottomCenter)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            // 全屏时才有 FullPlayer，收起时完全不画，避免挡住下面的列表
            if (!isCollapsed) {
                FullPlayer(
                    viewModel = viewModel,
                    progress = progress,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = offsetY
                            transformOrigin = TransformOrigin(0.5f, 0.0f)
                        }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settle(velocity) }
                        ),
                    onCollapse = {
                        scope.launch {
                            androidx.compose.animation.core.Animatable(offsetY).animateTo(
                                targetValue = dragRange,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { offsetY = value }
                        }
                    }
                )
            }

            // 迷你条：收起时唯一内容；展开时渐隐，全屏后不再绘制
            if (progress <= 0.92f) {
                MiniPlayer(
                    viewModel = viewModel,
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settle(velocity) }
                        ),
                    onExpand = {
                        scope.launch {
                            androidx.compose.animation.core.Animatable(offsetY).animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { offsetY = value }
                        }
                    }
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Mini Player（底部迷你条）
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun MiniPlayer(
    viewModel: MusicViewModel,
    progress: Float,
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val song by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()

    if (song == null) return

    // 收起时完全不透明；展开时随 progress 渐隐，避免退出来后变淡
    val alpha by remember {
        derivedStateOf {
            if (progress <= 0.5f) 1f else (1f - progress).coerceIn(0f, 1f)
        }
    }
    if (alpha <= 0.01f) return

    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song?.albumArtUri,
                contentDescription = "封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { viewModel.playerController.skipToPrevious() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首"
                )
            }
            IconButton(
                onClick = { viewModel.playerController.togglePlayPause() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放 / 暂停"
                )
            }
            IconButton(
                onClick = { viewModel.playerController.skipToNext() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首"
                )
            }
        }

        // 点击空白区域也能展开
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 120.dp)
                .clickable(onClick = onExpand)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Full Player（全屏播放器，带 HorizontalPager 切歌）
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun FullPlayer(
    viewModel: MusicViewModel,
    progress: Float,
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit
) {
    val controller = viewModel.playerController
    val song by controller.currentSong.collectAsStateWithLifecycle()
    val isPlaying by controller.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by controller.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by controller.shuffleMode.collectAsStateWithLifecycle()

    // 进度条状态
    val controllerRef by rememberUpdatedState(controller)
    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                currentMs = controllerRef.getCurrentPosition()
                durationMs = controllerRef.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    val isDark = isSystemInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary

    val bgAlpha = ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f)
    val gradientColors = if (isDark) {
        listOf(
            primary.copy(alpha = 0.85f * bgAlpha),
            primary.copy(alpha = 0.45f * bgAlpha),
            MaterialTheme.colorScheme.background
        )
    } else {
        listOf(
            primary.copy(alpha = 0.35f * bgAlpha),
            primary.copy(alpha = 0.15f * bgAlpha),
            MaterialTheme.colorScheme.background
        )
    }

    // 共享元素感：progress 0.5→1 时封面从“迷你条大小”展开到全屏
    val expandProgress by remember {
        derivedStateOf { ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f) }
    }
    val coverScale by remember { derivedStateOf { 0.2f + 0.8f * expandProgress } }
    val coverCorner: Dp by remember { derivedStateOf { lerp(12.dp, 0.dp, expandProgress) } }

    val scope = rememberCoroutineScope()

    // 播放队列 + Pager
    val queueSize = remember(song) { controller.getCurrentQueueSize().coerceAtLeast(1) }
    val initialIndex = remember(song) { controller.getCurrentIndex().coerceIn(0, queueSize - 1) }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { queueSize }
    )

    // 外部切歌时同步 Pager
    LaunchedEffect(song?.id) {
        val target = controller.getCurrentIndex().coerceIn(0, pagerState.pageCount - 1)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // Pager 滑动结束时切歌（不依赖 snapshotFlow，避免版本兼容问题）
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress &&
            pagerState.pageCount > 0 &&
            pagerState.currentPage != controller.getCurrentIndex()
        ) {
            controller.playAtIndex(pagerState.currentPage)
        }
    }

    Box(modifier = modifier) {
        // 背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        if (bgAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(colors = gradientColors)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶栏：保留左右内边距
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起"
                    )
                }
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { /* more */ }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 标题：上一首从左滑入，下一首从右滑入（以 pager 页码为准）
            var prevPage by remember { mutableStateOf(pagerState.currentPage) }
            var slideFromLeft by remember { mutableStateOf(false) }
            LaunchedEffect(pagerState.currentPage) {
                val cur = pagerState.currentPage.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                slideFromLeft = cur < prevPage
                prevPage = cur
            }
            val titleKey = controller.getSongAt(pagerState.currentPage)?.title ?: song?.title ?: ""
            AnimatedContent(
                targetState = Pair(titleKey, slideFromLeft),
                label = "title-slide",
                transitionSpec = { target, _ ->
                    val fromLeft = (target as? Pair<*, *>)?.second as? Boolean ?: false
                    val slideIn = slideInHorizontally(
                        initialOffsetX = { if (fromLeft) -it else it }
                    ) + fadeIn()
                    val slideOut = slideOutHorizontally(
                        targetOffsetX = { if (fromLeft) it else -it }
                    ) + fadeOut()
                    slideIn togetherWith slideOut
                }
            ) { (title, _) ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = song?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // 封面 Pager：铺满到屏幕边缘，共享元素式缩放（仅封面缩放）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    userScrollEnabled = true
                ) { page ->
                    val pageSong = controller.getSongAt(page) ?: song
                    AsyncImage(
                        model = pageSong?.albumArtUri,
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = coverScale
                                scaleY = coverScale
                                shape = RoundedCornerShape(coverCorner)
                                clip = true
                                shadowElevation = 24f * coverScale
                            }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 进度条
            val sliderProgress = if (isDragging) {
                dragProgress
            } else {
                (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
            }

            Slider(
                value = sliderProgress,
                onValueChange = { v ->
                    isDragging = true
                    dragProgress = v
                    currentMs = (v * durationMs).toLong()
                },
                onValueChangeFinished = {
                    controllerRef.seekTo((dragProgress * durationMs).toLong())
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMs(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // 控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "随机播放",
                        tint = if (shuffleMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                        scope.launch {
                            pagerState.animateScrollToPage(target)
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首"
                    )
                }

                FilledIconButton(
                    onClick = { controller.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "播放 / 暂停",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                IconButton(
                    onClick = {
                        val target = (pagerState.currentPage + 1)
                            .coerceAtMost(pagerState.pageCount - 1)
                        scope.launch {
                            pagerState.animateScrollToPage(target)
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首"
                    )
                }

                IconButton(onClick = { controller.toggleRepeat() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "循环模式",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 工具
// ──────────────────────────────────────────────────────────────────────────────
private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}