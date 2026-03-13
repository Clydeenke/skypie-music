package com.clydeenke.ling.ui.screen.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel
@Composable
fun LibraryScreen(
    viewModel          : MusicViewModel,
    onSongClick        : (songs: List<Song>, index: Int) -> Unit,
    onOpenPlayer       : () -> Unit = {},
    onOpenOnlineSearch : () -> Unit = {},
    onRefresh          : () -> Unit = {}
) {
    val songs       by viewModel.songs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning  by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    // 删除确认弹窗
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    if (songToDelete != null) {
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            icon    = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("删除歌曲") },
            text    = { Text("确定要从本地删除「${songToDelete?.title}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    songToDelete?.let { viewModel.deleteSong(it) }
                    songToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("取消") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier          = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = "音乐库",
                style    = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenOnlineSearch) {
                Icon(
                    imageVector        = Icons.Rounded.CloudQueue,
                    contentDescription = "云端搜索",
                    tint               = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                )
            }
        }

        LibrarySearchBar(
            query         = searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )

        if (songs.isEmpty()) {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh    = onRefresh,
                modifier     = Modifier.fillMaxSize()
            ) {
                LibraryEmpty(isScanning = isScanning)
            }
        } else {
            Text(
                text     = "${songs.size} 首歌曲",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
            )
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh    = onRefresh,
                modifier     = Modifier.fillMaxSize()
            ) {
                LazyColumn(contentPadding = PaddingValues(bottom = 108.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        val isCurrentlyPlaying = currentSong?.id == song.id
                        SongListItem(
                            song               = song,
                            isCurrentlyPlaying = isCurrentlyPlaying,
                            onClick            = {
                                if (isCurrentlyPlaying) onOpenPlayer()
                                else onSongClick(songs, index)
                            },
                            onLongClick        = { songToDelete = song }
                        )
                        if (index < songs.lastIndex) {
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 74.dp),
                                thickness = 0.5.dp,
                                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchBar(
    query         : String,
    onQueryChange : (String) -> Unit,
    modifier      : Modifier = Modifier
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = modifier,
        placeholder   = { Text("搜索歌曲") },
        leadingIcon   = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon  = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = "清空")
                }
            }
        },
        shape      = RoundedCornerShape(28.dp),
        singleLine = true,
        colors     = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItem(
    song               : Song,
    isCurrentlyPlaying : Boolean = false,
    onClick            : () -> Unit,
    onLongClick        : () -> Unit = {}
) {
    val fallbackPainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val context         = LocalContext.current

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick     = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier           = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            placeholder        = fallbackPainter,
            error              = fallbackPainter,
            contentScale       = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.titleMedium,
                color    = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "${song.artist} · ${song.album}",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text  = formatDuration(song.duration),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryEmpty(isScanning: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.MusicNote, null,
                modifier = Modifier.size(64.dp),
                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isScanning) "正在扫描中…" else "没有找到歌曲",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isScanning) "请稍候" else "前往右滑到「设置」添加目录，再点扫描",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}