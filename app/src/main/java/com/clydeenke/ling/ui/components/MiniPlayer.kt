package com.clydeenke.ling.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel

@Composable
fun MiniPlayer(
    viewModel     : MusicViewModel,
    onExpandClick : () -> Unit
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()

    val currentSong = song ?: return

    // 浮动卡片样式（类似 Apple 的 mini player）
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier       = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 16.dp,
                    shape        = RoundedCornerShape(18.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onExpandClick),
            color          = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圆角正方形专辑封面（不是圆形！）
                AsyncImage(
                    model              = currentSong.albumArtUri,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))  // 圆角正方形
                )

                Spacer(Modifier.width(12.dp))

                // 歌曲信息（带切换动画）
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier    = Modifier.weight(1f),
                    label       = "songInfo"
                ) { s ->
                    Column {
                        Text(
                            text     = s.title,
                            style    = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text     = s.artist,
                            style    = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 播放/暂停
                IconButton(onClick = { viewModel.playerController.togglePlayPause() }) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint               = MaterialTheme.colorScheme.onSurface,
                        modifier           = Modifier.size(28.dp)
                    )
                }

                // 下一首
                IconButton(onClick = { viewModel.playerController.skipToNext() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint               = MaterialTheme.colorScheme.onSurface,
                        modifier           = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))
            }
        }
    }
}