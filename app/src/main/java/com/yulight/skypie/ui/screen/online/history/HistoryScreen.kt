package com.yulight.skypie.ui.screen.online.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yulight.skypie.BuildConfig
import com.yulight.skypie.data.remote.AudioQuality
import com.yulight.skypie.data.remote.MusicSource
import com.yulight.skypie.data.remote.OnlineSong
import com.yulight.skypie.data.repository.OnlineMusicRepository
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.service.PlayerController
import com.yulight.skypie.ui.screen.online.favorites.FavoritesEntryPoint
import com.yulight.skypie.util.FavoriteSong
import com.yulight.skypie.util.HistoryManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(HistoryManager.getHistory(context)) }
    val scope = rememberCoroutineScope()

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, FavoritesEntryPoint::class.java)
    }
    val playerController = remember { entryPoint.playerController() }
    val repository = remember { entryPoint.repository() }
    val isPlaying by playerController.isPlaying.collectAsState()
    val currentSong by playerController.currentSong.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBackIosNew, "返回")
            }
            Text(
                text = "播放历史",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                Text(
                    text = "清除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        HistoryManager.clearHistory(context)
                        history = emptyList()
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${history.size} 首",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有播放记录", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                itemsIndexed(history) { index, song ->
                    val isCurrentSong = currentSong?.title == song.title && currentSong?.artist == song.artist

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    playHistorySong(history, index, playerController, repository)
                                    onOpenPlayer()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = song.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrentSong) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrentSong && isPlaying) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (index < history.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun playHistorySong(
    history: List<FavoriteSong>,
    index: Int,
    playerController: PlayerController,
    repository: OnlineMusicRepository
) {
    val apiBase = BuildConfig.DEFAULT_API_URL
    if (apiBase.isBlank()) return

    val fav = history[index]
    val onlineSong = withContext(Dispatchers.IO) {
        OnlineSong(
            id = fav.songId, title = fav.title, artist = fav.artist, album = "",
            duration = fav.duration, coverUrl = fav.coverUrl,
            source = when (fav.source) { "kuwo" -> MusicSource.KUWO; "kugou" -> MusicSource.KUGOU; "netease" -> MusicSource.NETEASE; "qq" -> MusicSource.QQ; else -> MusicSource.KUWO }
        )
    }

    val streamUrl = withContext(Dispatchers.IO) {
        try { repository.resolvePlayUrl(apiBase, onlineSong, AudioQuality.Standard) } catch (_: Exception) { null }
    }

    if (streamUrl.isNullOrBlank()) return

    withContext(Dispatchers.IO) { playerController.urlCache[onlineSong.id] = streamUrl }

    val songObjs = history.mapIndexed { i, s ->
        val u = if (i == index) streamUrl else withContext(Dispatchers.IO) { playerController.urlCache[history[i].songId] ?: "placeholder_${s.songId}" }
        Song(
            id = i.toLong() + 1, title = s.title, artist = s.artist, album = "",
            duration = s.duration.toLong(), uri = u, albumArtUri = s.coverUrl,
            size = 0L, dateAdded = System.currentTimeMillis(), folderPath = "", filePath = u
        )
    }
    playerController.playQueue(songObjs, index)
}
