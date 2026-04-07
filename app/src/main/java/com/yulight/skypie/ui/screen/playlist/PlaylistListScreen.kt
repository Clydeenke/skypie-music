package com.yulight.skypie.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.domain.model.Playlist
import com.yulight.skypie.viewmodel.MusicViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    viewModel      : MusicViewModel,
    onBack         : () -> Unit,
    onOpenPlaylist : (Long) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // ── 新建歌单对话框状态 ──────────────────────────────────────────────────
    var showCreateDialog  by remember { mutableStateOf(false) }
    var newPlaylistName   by remember { mutableStateOf("")    }

    // ── 删除确认对话框状态 ──────────────────────────────────────────────────
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text       = "歌单",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 22.sp
                        )
                        Text(
                            text  = "${playlists.size} 个歌单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 底部加 80dp 偏移，避免被悬浮迷你播放条遮挡
            Box(modifier = Modifier.padding(bottom = 80.dp)) {
                FloatingActionButton(
                    onClick        = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Add,
                        contentDescription = "新建歌单",
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
    ) { paddingValues ->
        if (playlists.isEmpty()) {
            // 空状态提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector        = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier           = Modifier.size(64.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text  = "还没有歌单",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text  = "点击右下角 + 创建第一个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier.padding(paddingValues),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist  = playlist,
                        onClick   = { onOpenPlaylist(playlist.id) },
                        onDelete  = { playlistToDelete = playlist }
                    )
                }
                // 底部留白，避免 FAB 遮挡最后一项
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // ── 新建歌单对话框 ────────────────────────────────────────────────────────
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName  = ""
            },
            title   = { Text("新建歌单") },
            text    = {
                OutlinedTextField(
                    value         = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label         = { Text("歌单名称") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName)
                                newPlaylistName  = ""
                                showCreateDialog = false
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick  = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName  = ""
                            showCreateDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        newPlaylistName  = ""
                    }
                ) { Text("取消") }
            }
        )
    }

    // ── 删除歌单确认对话框 ────────────────────────────────────────────────────
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title   = { Text("删除歌单") },
            text    = { Text("确定删除「${playlist.name}」吗？歌单内的歌曲不会被删除。") },
            confirmButton   = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlaylistCard —— 歌单列表卡片
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PlaylistCard(
    playlist : Playlist,
    onClick  : () -> Unit,
    onDelete : () -> Unit,
) {
    val dateStr = remember(playlist.createdAt) {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            .format(Date(playlist.createdAt))
    }
    // 用歌单第一首歌的封面作缩略图
    val thumbnailUri = playlist.songs.firstOrNull()?.albumArtUri

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面缩略图
        Box(
            modifier        = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUri != null) {
                AsyncImage(
                    model          = thumbnailUri,
                    contentDescription = "歌单封面",
                    contentScale   = ContentScale.Crop,
                    modifier       = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector        = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier           = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 歌单信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = playlist.name,
                style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "${playlist.songs.size} 首 · $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 长按/右键删除（用小图标按钮实现，简单直观）
        IconButton(onClick = onDelete) {
            Icon(
                imageVector        = Icons.Rounded.Add, // 替换为 DeleteOutline（见下方说明）
                contentDescription = "删除歌单",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}