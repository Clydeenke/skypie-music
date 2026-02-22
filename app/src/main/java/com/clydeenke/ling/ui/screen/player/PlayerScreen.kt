package com.clydeenke.ling.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// 用于判断封面动画方向
private enum class SwipeDirection { NONE, NEXT, PREV }

@Composable
fun PlayerScreen(
    viewModel : MusicViewModel,
    onBack    : () -> Unit
) {
    BackHandler(onBack = onBack)

    val song        by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying   by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val repeatMode  by viewModel.playerController.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.playerController.shuffleMode.collectAsStateWithLifecycle()

    // ── 进度条：本地拖拽状态，拖动时即时响应 ─────────────────────
    var currentMs     by remember { mutableLongStateOf(0L) }
    var durationMs    by remember { mutableLongStateOf(1L) }
    var isDragging    by remember { mutableStateOf(false) }
    var dragProgress  by remember { mutableFloatStateOf(0f) }

    // 非拖拽状态下轮询实际进度
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                currentMs  = viewModel.playerController.getCurrentPosition()
                durationMs = viewModel.playerController.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    // 显示的进度：拖动时用本地值，否则用实际值
    val displayProgress = if (isDragging) dragProgress
    else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)

    // ── 封面切换方向跟踪 ──────────────────────────────────────────
    var swipeDirection by remember { mutableStateOf(SwipeDirection.NONE) }

    // ── 手势：左右滑动切歌 ────────────────────────────────────────
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDraggingAlbum by remember { mutableStateOf(false) }
    val animatedDrag by animateFloatAsState(
        targetValue   = if (isDraggingAlbum) dragOffset else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "albumDrag"
    )

    // 封面随播放状态缩放（Apple Music 风格）
    val albumScale by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "albumScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        if (song == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── 顶栏 ──────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起",
                        modifier           = Modifier.size(32.dp)
                    )
                }
                Text(
                    text      = "正在播放",
                    style     = MaterialTheme.typography.titleLarge,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── 专辑封面：AnimatedContent 切换动画 ────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                // AnimatedContent 根据 song?.id 变化触发动画
                // 动画方向由 swipeDirection 决定
                AnimatedContent(
                    targetState  = song,
                    transitionSpec = {
                        val direction = swipeDirection
                        // 向左滑（下一首）：新封面从右边滑入，旧封面向左滑出
                        // 向右滑（上一首）：新封面从左边滑入，旧封面向右滑出
                        val enterOffset  = if (direction == SwipeDirection.NEXT) 1 else -1
                        val exitOffset   = if (direction == SwipeDirection.NEXT) -1 else 1

                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth * enterOffset },
                            animationSpec  = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> fullWidth * exitOffset },
                                    animationSpec  = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness    = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(tween(150)))
                    },
                    label        = "albumArt"
                ) { targetSong ->
                    AsyncImage(
                        model              = targetSong?.albumArtUri,
                        contentDescription = "专辑封面",
                        modifier           = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .scale(albumScale)
                            .offset { IntOffset((animatedDrag / 6).roundToInt(), 0) }
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                // 手势层：铺在 AnimatedContent 上面捕获滑动
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { isDraggingAlbum = true },
                                onDragEnd = {
                                    isDraggingAlbum = false
                                    when {
                                        dragOffset < -120 -> {
                                            swipeDirection = SwipeDirection.NEXT
                                            viewModel.playerController.skipToNext()
                                        }
                                        dragOffset > 120 -> {
                                            swipeDirection = SwipeDirection.PREV
                                            viewModel.playerController.skipToPrevious()
                                        }
                                    }
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    isDraggingAlbum = false
                                    dragOffset = 0f
                                },
                                onHorizontalDrag = { _, delta ->
                                    dragOffset = (dragOffset + delta).coerceIn(-300f, 300f)
                                }
                            )
                        }
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── 歌曲信息：也加淡入动画 ────────────────────────────
            AnimatedContent(
                targetState  = song,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label        = "songInfo"
            ) { targetSong ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text     = targetSong?.title ?: "",
                            style    = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text     = targetSong?.artist ?: "",
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Rounded.FavoriteBorder,
                            contentDescription = "收藏",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 进度条：拖拽即时响应 ──────────────────────────────
            Slider(
                value             = displayProgress,
                onValueChange     = { newValue ->
                    isDragging   = true
                    dragProgress = newValue
                    // 拖动时实时更新显示的时间文字
                    currentMs = (newValue * durationMs).toLong()
                },
                onValueChangeFinished = {
                    // 手指松开时才真正 seek，避免卡顿
                    viewModel.playerController.seekTo((dragProgress * durationMs).toLong())
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = SliderDefaults.colors(
                    thumbColor       = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatMs(currentMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatMs(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 控制按钮 ──────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // 随机
                IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "随机",
                        tint     = if (shuffleMode) MaterialTheme.colorScheme.primary
                        else             MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 上一首
                IconButton(
                    onClick  = {
                        swipeDirection = SwipeDirection.PREV
                        viewModel.playerController.skipToPrevious()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier           = Modifier.size(32.dp),
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 播放 / 暂停
                FilledIconButton(
                    onClick  = { viewModel.playerController.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier           = Modifier.size(38.dp),
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // 下一首
                IconButton(
                    onClick  = {
                        swipeDirection = SwipeDirection.NEXT
                        viewModel.playerController.skipToNext()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier           = Modifier.size(32.dp),
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 循环
                IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else                   -> Icons.Rounded.Repeat
                        },
                        contentDescription = "循环",
                        tint     = if (repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}