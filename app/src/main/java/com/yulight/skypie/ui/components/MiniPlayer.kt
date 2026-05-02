package com.yulight.skypie.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@Composable
fun MiniPlayer(
    viewModel    : MusicViewModel,
    gestureMod   : Modifier  = Modifier,
    hazeState    : HazeState,
    onQueueClick : () -> Unit = {}
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val s      = song ?: return
    val radius = MiniPlayerHeight / 2
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .shadow(
                elevation    = 12.dp,
                shape        = RoundedCornerShape(radius),
                spotColor    = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.15f),
                ambientColor = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.10f)
            )
            .border(
                width = 0.8.dp,
                color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                shape = RoundedCornerShape(radius)
            )
    ) {
        // 毛玻璃层
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(radius))
                .hazeEffect(
                    state = hazeState,
                    style = dev.chrisbanes.haze.HazeStyle(
                        blurRadius  = 12.dp,
                        noiseFactor = 0.02f,
                        tints       = listOf(dev.chrisbanes.haze.HazeTint(
                            color = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.02f)
                        ))
                    )
                )
        )

        // 顶部高光描边
        Box(modifier = Modifier.matchParentSize().drawBehind {
            val h  = size.height
            val cr = CornerRadius(h / 2f)
            drawRoundRect(
                brush        = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = if (isDark) 0.15f else 0.45f), Color.Transparent),
                    startY = 0f, endY = h * 0.5f
                ),
                cornerRadius = cr,
                style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        })

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面 + 歌名（手势区域）
            Box(
                modifier         = gestureMod.weight(1f).height(MiniPlayerHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model              = s.albumArtUri,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .shadow(2.dp, RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text     = s.title,
                            style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color    = if (isDark) Color.White else Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text     = s.artist,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val iconTint = if (isDark) Color.White else Color.Black

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 播放队列按钮
                IconButton(onClick = onQueueClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.FormatListBulleted, null, Modifier.size(22.dp), tint = iconTint)
                }
                // 播放/暂停
                IconButton(onClick = { viewModel.playerController.togglePlayPause() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier           = Modifier.size(30.dp),
                        tint               = iconTint
                    )
                }
                // 下一首
                IconButton(onClick = { viewModel.playerController.skipToNext() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(22.dp), tint = iconTint)
                }
            }
        }
    }
}