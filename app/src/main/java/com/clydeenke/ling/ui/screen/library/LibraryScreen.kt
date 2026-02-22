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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()         // 占住状态栏，不留大片空白
    ) {
        // ── 紧凑标题行（替代 LargeTopAppBar）─────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text     = "聆动音乐",
                style    = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                color    = MaterialTheme.colorScheme.onBackground
            )
            if (isScanning) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // ── 搜索框 ──────────────────────────────────────────────────────
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder   = { Text("搜索歌曲、歌手、专辑") },
            leadingIcon   = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon  = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
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

        Spacer(Modifier.height(8.dp))

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
                            modifier  = Modifier.padding(start = 72.dp),
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
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style    = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${song.artist} · ${song.album}",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatDuration(song.duration),
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
                if (isScanning) "请稍候"
                else "前往「文件夹」选项卡添加音乐目录，再点扫描",
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