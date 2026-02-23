package com.clydeenke.ling.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

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

    // ── 进度条：本地状态，拖动即时响应 ──────────────────────────────
    var currentMs    by remember { mutableLongStateOf(0L) }
    var durationMs   by remember { mutableLongStateOf(1L) }
    var isDragging   by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                currentMs  = viewModel.playerController.getCurrentPosition()
                durationMs = viewModel.playerController.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    val displayProgress = if (isDragging) dragProgress
    else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)

    // ── 封面动画方向 ─────────────────────────────────────────────────
    var swipeDirection by remember { mutableStateOf(SwipeDirection.NONE) }

    // ── 拖拽状态（无弹簧回弹，松手直接 tween 平滑归位）────────────
    var dragOffset      by remember { mutableFloatStateOf(0f) }
    var isDraggingAlbum by remember { mutableStateOf(false) }
    val animatedDrag by animateFloatAsState(
        targetValue   = if (isDraggingAlbum) dragOffset else 0f,
        // ✅ 拖动中：即时跟手（0ms）；松手：平滑归位（不弹）
        animationSpec = if (isDraggingAlbum) snap() else tween(200, easing = FastOutSlowInEasing),
        label         = "albumDrag"
    )

    // ✅ 封面缩放：不再用弹簧，用 tween，不会弹一下
    val albumScale by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0.88f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label         = "albumScale"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (song == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Spacer(Modifier.height(16.dp))

        // ── 顶栏 ──────────────────────────────────────────────────
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
                color     = MaterialTheme.colorScheme.onBackground,
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── 专辑封面 ───────────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            // ✅ key = song?.id  强制以歌曲 ID 为 key，ID 变化必定触发新内容
            AnimatedContent(
                targetState  = song?.id,
                transitionSpec = {
                    val dir   = swipeDirection
                    val enter = if (dir == SwipeDirection.NEXT) 1 else -1
                    val exit  = if (dir == SwipeDirection.NEXT) -1 else 1
                    (slideInHorizontally(
                        initialOffsetX = { w -> w * enter },
                        animationSpec  = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX  = { w -> w * exit },
                                animationSpec  = tween(280, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(150)))
                },
                label        = "albumArt"
            ) { _ ->
                // 直接使用外部 song（已确保与 key 同步）
                AsyncImage(
                    model              = song?.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier           = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .scale(albumScale)
                        .offset { IntOffset(animatedDrag.roundToInt() / 6, 0) }
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            // 透明手势层，铺在封面上接收滑动
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart  = { isDraggingAlbum = true },
                            onDragEnd    = {
                                isDraggingAlbum = false
                                when {
                                    dragOffset < -100 -> {
                                        swipeDirection = SwipeDirection.NEXT
                                        viewModel.playerController.skipToNext()
                                    }
                                    dragOffset > 100  -> {
                                        swipeDirection = SwipeDirection.PREV
                                        viewModel.playerController.skipToPrevious()
                                    }
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { isDraggingAlbum = false; dragOffset = 0f },
                            onHorizontalDrag = { _, delta ->
                                dragOffset = (dragOffset + delta).coerceIn(-280f, 280f)
                            }
                        )
                    }
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── 歌曲信息（随 song?.id 一起淡入淡出）────────────────────
        AnimatedContent(
            targetState  = song?.id,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
            label        = "songInfo"
        ) { _ ->
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = song?.title ?: "",
                        style    = MaterialTheme.typography.headlineMedium,
                        color    = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = song?.artist ?: "",
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "收藏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 进度条 ─────────────────────────────────────────────────
        Slider(
            value                 = displayProgress,
            onValueChange         = { v ->
                isDragging   = true
                dragProgress = v
                currentMs    = (v * durationMs).toLong()
            },
            onValueChangeFinished = {
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

        // ── 控制按钮 ───────────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "随机",
                    tint     = if (shuffleMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

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
                    modifier           = Modifier.size(32.dp)
                )
            }

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
                    modifier           = Modifier.size(32.dp)
                )
            }

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

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}