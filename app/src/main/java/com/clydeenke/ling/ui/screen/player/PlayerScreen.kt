package com.clydeenke.ling.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // 进度条轮询
    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    // 只有在正在播放或歌曲切换时才启动轮询
    LaunchedEffect(isPlaying, song) {
        while (isActive) {
            currentMs  = viewModel.playerController.getCurrentPosition()
            durationMs = viewModel.playerController.getDuration().coerceAtLeast(1L)
            delay(500)
        }
    }

    // ✅ 安全解包：如果没歌，直接返回
    val currentSong = song ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // 顶栏
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起")
                }
                Text(
                    text      = "正在播放",
                    style     = MaterialTheme.typography.titleLarge,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { /* TODO: 更多选项 */ }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                }
            }

            Spacer(Modifier.height(24.dp))

            // 专辑封面
            AsyncImage(
                model              = currentSong.albumArtUri,
                contentDescription = "专辑封面",
                modifier           = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(32.dp))

            // 歌曲信息
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = currentSong.title,
                        style    = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = currentSong.artist,
                        style    = MaterialTheme.typography.bodyLarge,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { /* TODO: 收藏逻辑 */ }) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "收藏")
                }
            }

            Spacer(Modifier.height(24.dp))

            // 进度条
            Slider(
                value         = (currentMs.toFloat() / durationMs).coerceIn(0f, 1f),
                onValueChange = { viewModel.playerController.seekTo((it * durationMs).toLong()) },
                modifier      = Modifier.fillMaxWidth()
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(currentMs),  style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))

            // 控制按钮行
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // 随机模式
                IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                    Icon(
                        imageVector        = Icons.Rounded.Shuffle,
                        contentDescription = "随机",
                        tint               = if (shuffleMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 上一首
                IconButton(
                    onClick  = { viewModel.playerController.skipToPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
                }

                // 播放/暂停
                FilledIconButton(
                    onClick  = { viewModel.playerController.togglePlayPause() },
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "切换播放状态",
                        modifier           = Modifier.size(36.dp)
                    )
                }

                // 下一首
                IconButton(
                    onClick  = { viewModel.playerController.skipToNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
                }

                // 循环模式
                IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                    Icon(
                        imageVector        = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "循环模式",
                        tint               = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
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