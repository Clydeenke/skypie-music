package com.yulight.skypie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    viewModel : MusicViewModel,
    onDismiss : () -> Unit,
) {
    val controller   = viewModel.playerController
    val currentSong  by controller.currentSong.collectAsStateWithLifecycle()
    val currentIndex = controller.getCurrentIndex()
    val queueSize    = controller.getCurrentQueueSize()

    // 从 PlayerController 读取完整队列
    val songs = remember(currentSong) {
        (0 until queueSize).mapNotNull { controller.getSongAt(it) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && songs.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor    = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle        = null,
    ) {
        // ── 当前播放歌曲头部 ──────────────────────────────────────────────────
        currentSong?.let { song ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model              = song.albumArtUri,
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = song.title,
                        style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text  = "${song.artist} · ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ── 队列标题栏 ────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = "${currentIndex + 1} / $queueSize",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text      = "播放队列",
                style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier  = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { viewModel.clearQueue(); onDismiss() }) {
                    Text("清除", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        // ── 队列列表 ──────────────────────────────────────────────────────────
        LazyColumn(
            state          = listState,
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                QueueSongItem(
                    song      = song,
                    isCurrent = index == currentIndex,
                    onPlay    = { controller.playAtIndex(index); onDismiss() },
                    onRemove  = { viewModel.removeFromQueue(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueSongItem(
    song      : Song,
    isCurrent : Boolean,
    onPlay    : () -> Unit,
    onRemove  : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else           MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                ),
                color    = if (isCurrent) MaterialTheme.colorScheme.primary
                else           MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "${song.artist} · ${song.album}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector        = Icons.Rounded.Remove,
                contentDescription = "从队列移除",
                modifier           = Modifier.size(18.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}