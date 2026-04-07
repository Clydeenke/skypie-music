package com.yulight.skypie.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.viewmodel.MusicViewModel
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId   : Long,
    viewModel    : MusicViewModel,
    onBack       : () -> Unit,
) {
    // 订阅歌单详情（实时响应）
    val playlist by viewModel.getPlaylistDetail(playlistId)
        .collectAsStateWithLifecycle(initialValue = null)

    // 所有歌曲（用于"添加歌曲"弹窗）
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()

    // 当前播放中的歌曲
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    // 控制"添加歌曲"底部弹窗
    var showAddSheet by remember { mutableStateOf(false) }

    // 歌单被删除时自动返回
    LaunchedEffect(playlist) {
        if (playlist == null && playlistId != 0L) {
            // 这里不直接 onBack()，等待 Room 第一次 emit 排除初始 null 状态
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = playlist?.name ?: "歌单",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 22.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (playlist != null) {
                            Text(
                                text  = "${playlist!!.songs.size} 首 · ${formatTotalDuration(playlist!!.songs.sumOf { it.duration })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 播放全部按钮
                    val hasSongs = (playlist?.songs?.isNotEmpty() == true)
                    TextButton(
                        onClick  = {
                            playlist?.songs?.takeIf { it.isNotEmpty() }?.let { songs ->
                                viewModel.playSong(songs, 0)
                            }
                        },
                        enabled  = hasSongs
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.PlayArrow,
                            contentDescription = "播放全部",
                            modifier           = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("播放全部")
                    }
                }
            )
        },
        floatingActionButton = {
            // 底部加 80dp 偏移，避免被悬浮迷你播放条遮挡
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                FloatingActionButton(
                    onClick        = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Add,
                        contentDescription = "添加歌曲",
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
    ) { paddingValues ->
        val songs = playlist?.songs.orEmpty()

        if (songs.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector        = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier           = Modifier.size(64.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text  = "歌单还是空的",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text  = "点击右下角 + 添加歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier.padding(paddingValues),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items = songs, key = { it.id }) { song ->
                    PlaylistSongItem(
                        song             = song,
                        isPlaying        = currentSong?.id == song.id,
                        onPlay           = { viewModel.playSong(songs, songs.indexOf(song)) },
                        onRemove         = { viewModel.removeSongFromPlaylist(playlistId, song.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // ── 添加歌曲底部弹窗 ──────────────────────────────────────────────────────
    if (showAddSheet) {
        val addedIds = remember(playlist) { playlist?.songs?.map { it.id }?.toSet() ?: emptySet() }

        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(
                text     = "添加歌曲",
                style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(items = allSongs, key = { it.id }) { song ->
                    val alreadyAdded = song.id in addedIds
                    AddSongItem(
                        song         = song,
                        alreadyAdded = alreadyAdded,
                        onAdd        = {
                            if (!alreadyAdded) {
                                viewModel.addSongToPlaylist(playlistId, song.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlaylistSongItem —— 歌单详情页的歌曲行
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaylistSongItem(
    song     : Song,
    isPlaying: Boolean,
    onPlay   : () -> Unit,
    onRemove : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else           MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = song.albumArtUri,
            contentDescription = "封面",
            modifier           = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale       = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color    = if (isPlaying) MaterialTheme.colorScheme.primary
                else           MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = song.artist,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 移除按钮
        IconButton(onClick = onRemove) {
            Icon(
                imageVector        = Icons.Rounded.RemoveCircleOutline,
                contentDescription = "从歌单移除",
                tint               = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AddSongItem —— "添加歌曲"弹窗中的歌曲行
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddSongItem(
    song        : Song,
    alreadyAdded: Boolean,
    onAdd       : () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyAdded, onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = song.albumArtUri,
            contentDescription = "封面",
            modifier           = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale       = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.bodyLarge,
                color    = if (alreadyAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else              MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (alreadyAdded) 0.3f else 1f
                ),
                maxLines = 1
            )
        }
        if (alreadyAdded) {
            Text(
                text  = "已添加",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        } else {
            Icon(
                imageVector        = Icons.Rounded.Add,
                contentDescription = "添加",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

/** 格式化总时长：1小时32分钟 / 45分钟 */
private fun formatTotalDuration(totalMs: Long): String {
    val totalMin = totalMs / 60_000
    val hours    = totalMin / 60
    val minutes  = totalMin % 60
    return buildString {
        if (hours > 0) append("${hours}小时")
        append("${minutes}分钟")
    }
}

