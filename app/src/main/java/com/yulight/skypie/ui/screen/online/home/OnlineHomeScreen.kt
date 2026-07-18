package com.yulight.skypie.ui.screen.online.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.memory.MemoryCache
import com.yulight.skypie.data.remote.KUWO_RANKS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_SEARCH_HISTORY = "online_search_history"
private const val KEY_SEARCH_HISTORY = "history"

@Composable
fun OnlineHomeScreen(
    onBack: () -> Unit = {},
    onOpenSearch: (String) -> Unit = {},
    onOpenRank: (Int) -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    viewModel: OnlineHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val rankSongsMap by viewModel.rankSongsMap.collectAsStateWithLifecycle()
    val searchHistory = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        searchHistory.clear()
        searchHistory.addAll(loadSearchHistory(context))
    }

    // 预加载榜单封面图片
    LaunchedEffect(rankSongsMap) {
        if (rankSongsMap.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                rankSongsMap.values.flatten().take(12).forEach { song ->
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            val cacheKey = MemoryCache.Key(song.coverUrl)
                            if (context.imageLoader.memoryCache?.get(cacheKey) != null) return@forEach
                            val request = ImageRequest.Builder(context)
                                .data(song.coverUrl)
                                .size(120)
                                .memoryCacheKey(cacheKey)
                                .diskCacheKey(song.coverUrl)
                                .build()
                            context.imageLoader.execute(request)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        // 顶部标题
        Text(
            text = "在线",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // 搜索框（点击跳转搜索页）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { onOpenSearch("") }
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("搜索歌曲、歌手…") },
                singleLine = true,
                readOnly = true,
                enabled = false,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, "搜索", tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 酷我榜单板块
        Text(
            text = "酷我榜单",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(KUWO_RANKS) { index, rank ->
                val firstSong = rankSongsMap[index]?.firstOrNull()
                OnlineRankCard(
                    rankName = rank.name,
                    coverUrl = firstSong?.coverUrl ?: "",
                    firstSong = firstSong?.title ?: "",
                    onClick = { onOpenRank(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 我的收藏 + 播放历史 + 缓存歌曲
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .clickable { onOpenFavorites() }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("收藏", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    .clickable { onOpenHistory() }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                    .clickable { onOpenCache() }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("缓存", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun OnlineRankCard(
    rankName: String,
    coverUrl: String,
    firstSong: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = rankName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (firstSong.isNotBlank()) {
                Text(
                    text = firstSong,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun loadSearchHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_SEARCH_HISTORY, Context.MODE_PRIVATE)
    val historyStr = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
    return if (historyStr.isBlank()) emptyList()
    else historyStr.split(",").filter { it.isNotBlank() }
}
