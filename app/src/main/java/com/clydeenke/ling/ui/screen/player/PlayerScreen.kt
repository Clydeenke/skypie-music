package com.clydeenke.ling.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

    // ✅ 白屏修复：如果 song 还没到，显示占位界面而不是直接 return
    if (song == null) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val currentSong = song!!

    // 进度轮询
    var currentMs  by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    LaunchedEffect(isPlaying, currentSong.id) {
        while (isActive) {
            currentMs  = viewModel.playerController.getCurrentPosition()
            durationMs = viewModel.playerController.getDuration().coerceAtLeast(1L)
            delay(500)
        }
    }

    // 左右滑动切歌状态
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 100f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 左右滑动切歌
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset < -swipeThreshold -> viewModel.playerController.skipToNext()
                            dragOffset > swipeThreshold  -> viewModel.playerController.skipToPrevious()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragOffset += dragAmount }
                )
            }
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── 顶栏 ──────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 下箭头收起
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "收起",
                        modifier = Modifier.size(28.dp),
                        tint     = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { /* TODO: 更多菜单 */ }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── 专辑封面（圆角正方形，Apple 风格大图）─────────────────────
            AsyncImage(
                model              = currentSong.albumArtUri,
                contentDescription = "专辑封面",
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 32.dp,
                        shape     = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(20.dp))
            )

            // 滑动提示指示器（小点）
            Row(
                modifier            = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == 1) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == 1) MaterialTheme.colorScheme.primary
                                else        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 歌曲信息 + 收藏 ─────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = currentSong.title,
                        style    = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color    = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = currentSong.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { /* TODO 收藏 */ }) {
                    Icon(
                        Icons.Rounded.FavoriteBorder,
                        contentDescription = "收藏",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 进度条 ───────────────────────────────────────────────────
            Slider(
                value         = (currentMs.toFloat() / durationMs).coerceIn(0f, 1f),
                onValueChange = { viewModel.playerController.seekTo((it * durationMs).toLong()) },
                modifier      = Modifier.fillMaxWidth(),
                colors        = SliderDefaults.colors(
                    thumbColor            = MaterialTheme.colorScheme.onBackground,
                    activeTrackColor      = MaterialTheme.colorScheme.onBackground,
                    inactiveTrackColor    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
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

            Spacer(Modifier.height(20.dp))

            // ── 主控制区 ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // 随机播放
                IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "随机",
                        tint     = if (shuffleMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 上一首
                IconButton(
                    onClick  = { viewModel.playerController.skipToPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(38.dp),
                        tint     = MaterialTheme.colorScheme.onBackground
                    )
                }

                // 播放/暂停（主按钮，Apple 风格大圆）
                FilledIconButton(
                    onClick  = { viewModel.playerController.togglePlayPause() },
                    modifier = Modifier.size(70.dp),
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier           = Modifier.size(38.dp),
                        tint               = MaterialTheme.colorScheme.background
                    )
                }

                // 下一首
                IconButton(
                    onClick  = { viewModel.playerController.skipToNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(38.dp),
                        tint     = MaterialTheme.colorScheme.onBackground
                    )
                }

                // 循环模式
                IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else                   -> Icons.Rounded.Repeat
                        },
                        contentDescription = "循环",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 左右滑动提示文字（淡淡的）
            Text(
                "← 左右滑动切歌 →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}