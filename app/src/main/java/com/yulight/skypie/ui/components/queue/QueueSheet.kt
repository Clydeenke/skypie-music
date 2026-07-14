package com.yulight.skypie.ui.components.queue

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ══════════════════════════════════════════════════════════════════════════════
// QueueSheet — 播放队列底部弹窗
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
) {
    val controller = viewModel.playerController
    val currentQueue by controller.currentQueue.collectAsState()
    val currentSong by controller.currentSong.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()

    val currentSongId = currentSong?.id
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── 动态背景色（从封面取色） ──
    var bgColor by remember { mutableStateOf(Color(0xFF1C1B1F)) }
    val context = LocalContext.current
    LaunchedEffect(currentSong?.albumArtUri) {
        val uri = currentSong?.albumArtUri
        if (uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val req = ImageRequest.Builder(context).data(uri).size(64).allowHardware(false).build()
                    val result = context.imageLoader.execute(req)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = Palette.Builder(bitmap).generate()
                            bgColor = Color(palette.getDarkMutedColor(0xFF1C1B1F.toInt())).copy(alpha = 0.95f)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // 判断背景亮度，决定文字颜色
    val isBgDark = remember(bgColor) {
        val luminance = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
        luminance < 0.5f
    }
    val onBgColor = if (isBgDark) Color.White else Color(0xFF1C1B1F)
    val onBgVariant = if (isBgDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)

    // 背景色动画过渡
    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(600),
        label = "sheetBg",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = animatedBgColor,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── 拖拽手柄 ──
            QueueDragHandle(onBgVariant = onBgVariant)

            // ── 当前播放（在标题上方） ──
            currentSong?.let { song ->
                NowPlayingSection(song = song, isPlaying = isPlaying, onTogglePlay = { controller.togglePlayPause() }, onBgColor = onBgColor, onBgVariant = onBgVariant)
            }

            // ── 播放队列标题 ──
            QueueTitle(onBgColor = onBgColor)

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // ── 队列列表 ──
            QueueList(
                queue = currentQueue,
                currentSongId = currentSongId,
                bgColor = bgColor,
                onBgColor = onBgColor,
                onBgVariant = onBgVariant,
                onRemove = { song ->
                    viewModel.removeFromQueue(songId = song.id.toString())
                },
                onPlay = { index -> controller.playAtIndex(index) },
                onMove = { from, to -> scope.launch { viewModel.playerController.moveInQueue(from, to) } },
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QueueDragHandle — 拖拽手柄
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QueueDragHandle(onBgVariant: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(onBgVariant.copy(alpha = 0.4f))
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QueueTitle — 标题
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QueueTitle(onBgColor: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("播放队列", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = onBgColor)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NowPlayingSection — 当前播放
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NowPlayingSection(song: Song, isPlaying: Boolean, onTogglePlay: () -> Unit, onBgColor: Color, onBgVariant: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = onBgColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = onBgVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = onBgColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QueueList — 队列列表
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QueueList(
    queue: List<Song>,
    currentSongId: Long?,
    bgColor: Color,
    onBgColor: Color,
    onBgVariant: Color,
    onRemove: (Song) -> Unit,
    onPlay: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    if (queue.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().background(bgColor).padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("队列为空", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 半圆弧度分割线
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(top = 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)))
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 40.dp),
        modifier = Modifier.fillMaxWidth().background(bgColor)
    ) {
        items(queue.size, key = { queue[it].id }) { index ->
            val song = queue[index]
            ReorderableItem(reorderableState, key = song.id) { isDragging ->
                QueueItemRow(
                    scope = this,
                    song = song,
                    bgColor = bgColor,
                    onBgColor = onBgColor,
                    onBgVariant = onBgVariant,
                    isCurrent = song.id == currentSongId,
                    isDragging = isDragging,
                    onDelete = { onRemove(song) },
                    onPlay = { onPlay(index) },
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragStopped = {},
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// QueueItemRow — 单行（SwipeToDismiss + 内容）
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemRow(
    scope: ReorderableCollectionItemScope,
    song: Song,
    bgColor: Color,
    onBgColor: Color,
    onBgVariant: Color,
    isCurrent: Boolean,
    isDragging: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // 纯色背景，无图标
            Box(modifier = Modifier.fillMaxSize())
        },
    ) {
        val surfaceBg = bgColor.copy(alpha = 0.85f)
        val rowBg by animateColorAsState(
            targetValue = when {
                isDragging -> Color(
                    red = (bgColor.red + 0.12f).coerceAtMost(1f),
                    green = (bgColor.green + 0.12f).coerceAtMost(1f),
                    blue = (bgColor.blue + 0.12f).coerceAtMost(1f),
                    alpha = 0.95f
                )
                isCurrent -> Color(
                    red = (bgColor.red + MaterialTheme.colorScheme.primary.red * 0.15f).coerceAtMost(1f),
                    green = (bgColor.green + MaterialTheme.colorScheme.primary.green * 0.15f).coerceAtMost(1f),
                    blue = (bgColor.blue + MaterialTheme.colorScheme.primary.blue * 0.15f).coerceAtMost(1f),
                    alpha = 0.95f
                )
                else -> surfaceBg
            },
            animationSpec = tween(200),
            label = "rowBg",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
                .clickable { onPlay() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else onBgColor,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(song.artist, color = onBgVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Default.DragHandle, contentDescription = "拖拽排序", tint = onBgVariant,
                modifier = Modifier.size(20.dp).then(
                    with(scope) {
                        Modifier.draggableHandle(onDragStarted = { onDragStarted() }, onDragStopped = { onDragStopped() })
                    }
                ),
            )
        }
    }
}


