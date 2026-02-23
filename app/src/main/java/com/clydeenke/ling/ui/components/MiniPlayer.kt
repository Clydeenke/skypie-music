package com.clydeenke.ling.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel

@Composable
fun MiniPlayer(
    viewModel     : MusicViewModel,
    onExpandClick : () -> Unit,
    modifier      : Modifier = Modifier
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()

    val currentSong = song ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation    = 10.dp,
                shape        = RoundedCornerShape(28.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor    = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
            .clickable(onClick = onExpandClick)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 封面：以 song.id 为 key，切歌时淡入淡出 ──────────
            AnimatedContent(
                targetState  = currentSong.id,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label        = "miniArt"
            ) { _ ->
                AsyncImage(
                    model              = currentSong.albumArtUri,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Spacer(Modifier.width(12.dp))

            // ── 歌曲信息：weight(1f) 让它撑满剩余空间 ────────────
            // ✅ weight 放在 AnimatedContent 上，确保按钮始终在右边
            AnimatedContent(
                targetState  = currentSong.id,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                modifier     = Modifier.weight(1f),
                label        = "miniInfo"
            ) { _ ->
                Column {
                    Text(
                        text     = currentSong.title,
                        style    = MaterialTheme.typography.titleLarge,
                        color    = MaterialTheme.colorScheme.onSurface,
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
            }

            // ── 控制按钮（固定在最右，不会被歌名挤掉）────────────
            IconButton(
                onClick  = { viewModel.playerController.skipToPrevious() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    modifier           = Modifier.size(22.dp),
                    tint               = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick  = { viewModel.playerController.togglePlayPause() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier           = Modifier.size(28.dp),
                    tint               = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick  = { viewModel.playerController.skipToNext() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    modifier           = Modifier.size(22.dp),
                    tint               = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}