package com.clydeenke.ling.ui.screen.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable // 🌟 找回长按必须的导包
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable

// 🍎 苹果级缓动：先快后慢，带一点点回弹阻尼感
private val AppleEasing = CubicBezierEasing(0.32f, 0.94f, 0.60f, 1.0f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    hazeState: HazeState,
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    onOpenPlayer: () -> Unit = {},
    onOpenOnlineSearch: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }

    // 🌟 核心删除提示框状态
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .hazeSource(state = hazeState)
    ) {
        // --- 头部设计 (Header) ---
        Box(
            modifier = Modifier
                .zIndex(1f)
                .background(MaterialTheme.colorScheme.surface)
                .clip(RectangleShape)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSearchExpanded,
                    enter = fadeIn(tween(400, 0, AppleEasing)),
                    exit = fadeOut(tween(200, 0, AppleEasing)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "音乐库",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            letterSpacing = (-1).sp
                        )
                    )
                }

                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSearchExpanded,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(150))
                    ) {
                        IconButton(onClick = onOpenOnlineSearch) {
                            Icon(Icons.Rounded.CloudQueue, null, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                if (!isSearchExpanded) {
                    Spacer(Modifier.width(48.dp))
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                IntegratedSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    isExpanded = isSearchExpanded,
                    onToggle = { isSearchExpanded = it }
                )
            }
        }

        // --- 核心列表区 ---
        PullToRefreshBox(
            isRefreshing = isScanning,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    Box(modifier = Modifier.animateItem()) {
                        SongListItem(
                            song = song,
                            isCurrentlyPlaying = currentSong?.id == song.id,
                            onClick = {
                                if (currentSong?.id == song.id) onOpenPlayer()
                                else onSongClick(songs, index)
                            },
                            onLongClick = { songToDelete = song } // 🌟 重新接回长按逻辑
                        )
                    }
                }
            }
        }
    }


    if (songToDelete != null) {
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("删除歌曲") },
            text = { Text("确定要从本地库中移除 \"${songToDelete?.title}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        songToDelete?.let { viewModel.deleteSong(it) }
                        songToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun IntegratedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val widthPercent by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.12f,
        animationSpec = tween(600, 0, AppleEasing),
        label = "searchWidth"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(400, if (isExpanded) 200 else 0, AppleEasing),
        label = "contentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthPercent)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    if (isExpanded) MaterialTheme.colorScheme.surfaceVariant
                    else Color.Transparent
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (!isExpanded) onToggle(true)
                }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(widthPercent)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.requiredSize(24.dp),
                tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            if (widthPercent > 0.4f) {
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha),
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("搜索...", style = TextStyle(fontSize = 16.sp, color = Color.Gray.copy(0.4f)))
                            }
                            innerTextField()
                        }
                    }
                )

                if (isExpanded) {
                    IconButton(
                        onClick = { if (query.isNotEmpty()) onQueryChange("") else onToggle(false) },
                        modifier = Modifier.alpha(contentAlpha)
                    ) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItem(
    song: Song,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit // 🌟 参数找回
) {
    val scale by animateFloatAsState(
        targetValue = if (isCurrentlyPlaying) 1.03f else 1f,
        animationSpec = tween(500, 0, AppleEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .shadow(
                elevation = if (isCurrentlyPlaying) 12.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color.Black.copy(0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(0.15f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(0.45f)
                else Color.Transparent
            )
            .combinedClickable( // 🌟 核心逻辑找回：支持点击和长按
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Rounded.MusicNote)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}