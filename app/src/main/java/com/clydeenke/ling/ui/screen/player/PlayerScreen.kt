package com.clydeenke.ling.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clydeenke.ling.ui.screens.library.formatDuration
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler

@Composable
fun PlayerScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val currentSong by viewModel.playerController.currentSong.collectAsState()
    val isPlaying by viewModel.playerController.isPlaying.collectAsState()
    val shuffleMode by viewModel.playerController.shuffleMode.collectAsState()
    val repeatMode by viewModel.playerController.repeatMode.collectAsState()

    // 1. 进度条逻辑：每 500ms 采样一次当前播放位置
    var currentPosition by remember { mutableLongStateOf(0L) }
    val duration = currentSong?.duration ?: 1L

    LaunchedEffect(isPlaying, currentSong) {
        while (isPlaying) {
            currentPosition = viewModel.playerController.getCurrentPosition()
            delay(500)
        }
    }

    // 2. 黑胶唱片旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "coverRotation"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 背景：超大模糊封面渲染
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(currentSong?.albumArtUri).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(60.dp).graphicsLayer { alpha = 0.45f },
            contentScale = ContentScale.Crop
        )

        // 底部渐变遮罩（确保进度条和按钮清晰）
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.2f),
            1f to MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
        )))

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部导航：收起按钮
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "收起", Modifier.size(32.dp)) }
                Text("正在播放", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "更多") }
            }

            Spacer(Modifier.weight(1f))

            // 核心 UI：黑胶封面
            Box(
                modifier = Modifier.size(300.dp).graphicsLayer {
                    if (isPlaying) rotationZ = rotation
                    shadowElevation = 40.dp.toPx()
                    shape = CircleShape
                    clip = true
                }
            ) {
                AsyncImage(
                    model = currentSong?.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.weight(1f))

            // 歌曲信息卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentSong?.title ?: "未知歌曲", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentSong?.artist ?: "未知歌手", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

                    Spacer(Modifier.height(32.dp))

                    // 进度条
                    Slider(
                        value = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { viewModel.playerController.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(formatDuration(currentPosition), style = MaterialTheme.typography.labelMedium)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(Modifier.height(24.dp))

                    // 播放控制
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                            Icon(Icons.Rounded.Shuffle, null, tint = if (shuffleMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        IconButton(onClick = { viewModel.playerController.skipToPrevious() }, Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp))
                        }
                        FilledIconButton(onClick = { viewModel.playerController.togglePlayPause() }, Modifier.size(72.dp)) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(40.dp))
                        }
                        IconButton(onClick = { viewModel.playerController.skipToNext() }, Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                            Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}