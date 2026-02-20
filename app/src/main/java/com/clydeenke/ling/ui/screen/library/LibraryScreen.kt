package com.clydeenke.ling.ui.screens.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel

/**
 * 歌曲库主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. 顶部栏：支持点击搜索变身输入框
        TopAppBar(
            title = {
                AnimatedContent(
                    targetState = searchExpanded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { inSearch ->
                    if (inSearch) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.search(it) },
                            placeholder = { Text("搜索歌曲、歌手") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Text(
                            "Ling",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search, "搜索")
                }
                IconButton(onClick = { viewModel.scanMusic() }) {
                    Icon(Icons.Rounded.Refresh, "刷新")
                }
            }
        )

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        // 2. 列表内容
        if (uiState.songs.isEmpty() && !uiState.isLoading) {
            EmptyState()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) { // 留出迷你播放条的空间
                items(
                    items = uiState.songs,
                    key = { it.id } // 性能优化核心：告诉系统每行 ID 是唯一的
                ) { song ->
                    SongItem(
                        song = song,
                        isPlaying = viewModel.playerController.currentSong.collectAsState().value?.id == song.id,
                        onClick = { viewModel.playSong(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun SongItem(song: Song, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面图：使用 Coil 库异步加载
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} • ${song.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }

        if (isPlaying) {
            Icon(Icons.Rounded.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Text(formatDuration(song.duration), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EmptyState() {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Text("库中空空如也", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline)
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}