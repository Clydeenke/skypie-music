package com.yulight.skypie.ui.screen.online.rank

import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yulight.skypie.data.remote.*
import com.yulight.skypie.ui.screen.online.search.OnlineSearchViewModel

@Composable
fun OnlineRankScreen(
    rankIndex: Int = 0,
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    viewModel: OnlineSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // 初始化时设置榜单索引
    LaunchedEffect(rankIndex) {
        viewModel.setRankIndex(rankIndex)
    }

    val rankSongs by viewModel.rankSongs.collectAsStateWithLifecycle()
    val rankLoading by viewModel.rankLoading.collectAsStateWithLifecycle()
    val rankLoadingMore by viewModel.rankLoadingMore.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val playStates by viewModel.playStates.collectAsStateWithLifecycle()

    val rankInfo = KUWO_RANKS.getOrNull(rankIndex)
    val firstSong = rankSongs.firstOrNull()

    fun showNoApiToast() = Toast.makeText(
        context,
        "请先在「设置 → API 接口地址」中配置音源",
        Toast.LENGTH_LONG
    ).show()

    var songForDownload by remember { mutableStateOf<OnlineSong?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部大封面 + 信息
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (firstSong?.coverUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = firstSong.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.Rounded.ArrowBack, "返回", tint = Color.White)
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (firstSong?.coverUrl?.isNotBlank() == true) {
                        AsyncImage(
                            model = firstSong.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = rankInfo?.name ?: "榜单",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (firstSong != null) {
                            Text(
                                text = firstSong.title,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = firstSong.artist,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 歌曲列表（半透明圆角框）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                if (rankLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        itemsIndexed(rankSongs, key = { _, song -> song.id }) { index, song ->
                            if (index == rankSongs.lastIndex) {
                                SideEffect { viewModel.loadMoreRank() }
                            }

                            OnlineRankSongItem(
                                index = index + 1,
                                song = song,
                                downloadState = downloadStates[song.id] ?: DownloadState.Idle,
                                playState = playStates[song.id] ?: PlayState.Idle,
                                onPlay = { viewModel.play(rankSongs, index, onOpenPlayer, ::showNoApiToast) },
                                onDownload = { songForDownload = song }
                            )

                            if (index < rankSongs.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 50.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }

                        if (rankLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 下载音质选择
    songForDownload?.let { song ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { songForDownload = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text("选择音质下载：${song.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                listOf(AudioQuality.Standard, AudioQuality.High, AudioQuality.Lossless).forEach { q ->
                    ListItem(
                        headlineContent = { Text(q.title) },
                        supportingContent = { Text("${q.bitRate} | ${q.desc}") },
                        trailingContent = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.download(song, q,
                                    onNoApi = { Toast.makeText(context, "请先在「设置 → API 接口地址」中配置音源", Toast.LENGTH_LONG).show() },
                                    onQueued = { Toast.makeText(context, "已加入后台下载队列", Toast.LENGTH_SHORT).show() },
                                    onComplete = { }
                                )
                                songForDownload = null
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineRankSongItem(
    index: Int,
    song: OnlineSong,
    downloadState: DownloadState,
    playState: PlayState,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))

        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            AsyncImage(song.coverUrl, null, Modifier.matchParentSize())
            if (playState is PlayState.Resolving) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (playState is PlayState.Playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        when (downloadState) {
            is DownloadState.Idle -> IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Download, "下载", tint = MaterialTheme.colorScheme.primary) }
            is DownloadState.Loading -> Box(Modifier.size(36.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            is DownloadState.Done -> Box(Modifier.size(36.dp), Alignment.Center) { Icon(Icons.Rounded.CheckCircle, "完成", tint = MaterialTheme.colorScheme.primary) }
            is DownloadState.Error -> IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.ErrorOutline, "重试", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
