package com.clydeenke.ling.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    // 没有正在播放的歌曲时不显示
    val currentSong = song ?: return

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandClick),
        color          = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            AsyncImage(
                model              = currentSong.albumArtUri,
                contentDescription = null,
                modifier           = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = currentSong.title,
                    style    = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text     = currentSong.artist,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 上一首
            IconButton(onClick = { viewModel.playerController.skipToPrevious() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }

            // 播放 / 暂停
            IconButton(onClick = { viewModel.playerController.togglePlayPause() }) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }

            // 下一首
            IconButton(onClick = { viewModel.playerController.skipToNext() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
            }
        }
    }
}