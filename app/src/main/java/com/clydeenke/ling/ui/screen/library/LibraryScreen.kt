package com.clydeenke.ling.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel

@Composable
fun LibraryScreen(
    viewModel   : MusicViewModel,
    onSongClick : (songs: List<Song>, index: Int) -> Unit
) {
    val songs       by viewModel.songs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning  by viewModel.isScanning.collectAsStateWithLifecycle()

    // Navigation 已经处理了 statusBarsPadding，这里直接从搜索框开始
    Column(modifier = Modifier.fillMaxSize()) {

        // 搜索框
        LibrarySearchBar(
            query         = searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )

        if (songs.isEmpty()) {
            LibraryEmpty(isScanning = isScanning)
        } else {
            Text(
                text     = "${songs.size} 首歌曲",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongListItem(
                        song    = song,
                        onClick = { onSongClick(songs, index) }
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
        placeholder   = { Text("搜索歌曲、歌手、专辑") },
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

@Composable
private fun SongListItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model              = song.albumArtUri,
            contentDescription = null,
            modifier           = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = song.title,
                style    = MaterialTheme.typography.titleLarge,
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
                Icons.Rounded.MusicNote,
                contentDescription = null,
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
                if (isScanning) "请稍候" else "前往左滑到「文件夹」添加目录，再点扫描",
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