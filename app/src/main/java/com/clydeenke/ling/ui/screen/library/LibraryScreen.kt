package com.clydeenke.ling.ui.screen.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.clydeenke.ling.domain.model.Playlist
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel
import com.clydeenke.ling.viewmodel.SortOrder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.abs
// ─────────────────────────────────────────────────────────────────────────────
// 全局动画常量
// ─────────────────────────────────────────────────────────────────────────────

private val RelaxedEasing    = CubicBezierEasing(0.25f, 0.85f, 0.2f, 1.0f)
private val DrawerWidth      = 260.dp
private val EdgeTriggerWidth = 56.dp

// ─────────────────────────────────────────────────────────────────────────────
// LibraryScreen —— 本地音乐库主界面
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel             : MusicViewModel,
    hazeState             : HazeState,
    onSongClick           : (songs: List<Song>, index: Int) -> Unit,
    onOpenPlayer          : () -> Unit    = {},
    onOpenOnlineSearch    : () -> Unit    = {},
    onNavigateToSettings  : () -> Unit    = {},
    onNavigateToAlbums    : () -> Unit    = {},
    onNavigateToArtists   : () -> Unit    = {},
    onNavigateToPlaylists : () -> Unit    = {},
    onRefresh             : () -> Unit    = {},
) {
    // ── ViewModel 数据订阅 ────────────────────────────────────────────────
    val songs           by viewModel.songs.collectAsStateWithLifecycle()
    val searchQuery     by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning      by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentSong     by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val sortOrder       by viewModel.sortOrder.collectAsStateWithLifecycle()
    val playlists       by viewModel.playlists.collectAsStateWithLifecycle()
    val totalListenedMs by viewModel.totalListenedMs.collectAsStateWithLifecycle()

    // ── UI 状态 ───────────────────────────────────────────────────────────
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isDrawerOpen     by remember { mutableStateOf(false) }
    var isLocalExpanded  by remember { mutableStateOf(true)  }

    // ── 底部菜单状态 ──────────────────────────────────────────────────────
    var optionsSong   by remember { mutableStateOf<Song?>(null) }  // 触发歌曲操作菜单
    var showSortSheet by remember { mutableStateOf(false) }

    // ── 多选状态 ──────────────────────────────────────────────────────────
    var isMultiSelect          by remember { mutableStateOf(false)              }
    var selectedIds            by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMultiPlaylistSheet by remember { mutableStateOf(false)              }

    // 同步多选状态到 ViewModel，供 SharedPlayerContainer 隐藏迷你播放条
    LaunchedEffect(isMultiSelect) { viewModel.setMultiSelectActive(isMultiSelect) }

    // 多选模式下系统返回键 = 退出多选
    BackHandler(enabled = isMultiSelect) {
        isMultiSelect = false
        selectedIds   = emptySet()
    }

    // ── 抽屉手势 ──────────────────────────────────────────────────────────
    val scope         = rememberCoroutineScope()
    val density       = LocalDensity.current
    val drawerWidthPx = with(density) { DrawerWidth.toPx() }
    val edgeTriggerPx = with(density) { EdgeTriggerWidth.toPx() }
    val dragOffset    = remember { Animatable(0f) }

    LaunchedEffect(isDrawerOpen) {
        val target = if (isDrawerOpen) drawerWidthPx else 0f
        if (dragOffset.value != target) {
            dragOffset.animateTo(target, tween(650, easing = RelaxedEasing))
        }
    }

    val fraction      = (dragOffset.value / drawerWidthPx).coerceIn(0f, 1f)
    val contentScale  = 1f - 0.06f * fraction
    val contentRadius : Dp = 32.dp * fraction

    // ── 根容器 ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .hazeSource(state = hazeState)
            .pointerInput(isDrawerOpen) {
                val velocityTracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    if (!isDrawerOpen && down.position.x > edgeTriggerPx) return@awaitEachGesture
                    var isHorizontalGesture = false
                    var accX = 0f; var accY = 0f
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (isHorizontalGesture) {
                                val vx = velocityTracker.calculateVelocity().x
                                isDrawerOpen = when {
                                    vx >  500f -> true
                                    vx < -500f -> false
                                    else       -> dragOffset.value > drawerWidthPx * 0.3f
                                }
                            }
                            break
                        }
                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        accX += dx; accY += dy
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (!isHorizontalGesture) {
                            val threshold  = with(density) { 8.dp.toPx() }
                            val horizontal = abs(accX) > abs(accY) * 1.5f && abs(accX) > threshold
                            val vertical   = abs(accY) > abs(accX) * 1.5f && abs(accY) > threshold
                            when { horizontal -> isHorizontalGesture = true; vertical -> break }
                            continue
                        }
                        change.consume()
                        scope.launch { dragOffset.snapTo((dragOffset.value + dx).coerceIn(0f, drawerWidthPx)) }
                    }
                }
            }
    ) {
        // 侧边栏
        DrawerContent(
            isLocalExpanded    = isLocalExpanded,
            onToggleLocal      = { isLocalExpanded = !isLocalExpanded },
            onNavigateToAlbums = { isDrawerOpen = false; onNavigateToAlbums() },
            onNavigateToArtists= { isDrawerOpen = false; onNavigateToArtists() },
            onNavigateToPlaylists = { isDrawerOpen = false; onNavigateToPlaylists() },
            onOpenOnlineSearch = { isLocalExpanded = false; isDrawerOpen = false; onOpenOnlineSearch() },
            onNavigateToSettings = { isDrawerOpen = false; onNavigateToSettings() }
        )

        // 主内容层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX    = dragOffset.value
                    scaleX          = contentScale; scaleY = contentScale
                    shadowElevation = 24f * fraction
                    shape           = RoundedCornerShape(contentRadius)
                    clip            = true
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── 顶部栏：普通模式 ↔ 多选模式，带过渡动画 ─────────────────
                AnimatedContent(
                    targetState = isMultiSelect,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "topBarMode"
                ) { multiSelect ->
                    if (multiSelect) {
                        MultiSelectTopBar(
                            selectedCount = selectedIds.size,
                            totalCount    = songs.size,
                            onSelectAll   = { selectedIds = songs.map { it.id }.toSet() },
                            onCancel      = { isMultiSelect = false; selectedIds = emptySet() }
                        )
                    } else {
                        TopBar(
                            isDrawerOpen       = isDrawerOpen,
                            isSearchExpanded   = isSearchExpanded,
                            searchQuery        = searchQuery,
                            onToggleDrawer     = { isDrawerOpen = !isDrawerOpen },
                            onSearchToggle     = { isSearchExpanded = it },
                            onSearchQueryChange= viewModel::setSearchQuery
                        )
                    }
                }

                // ── 统计栏 ──────────────────────────────────────────────────
                StatsRow(
                    songCount       = songs.size,
                    totalListenedMs = totalListenedMs,
                    sortOrder       = sortOrder,
                    onSortClick     = { showSortSheet = true }
                )

                // ── 歌曲列表 ────────────────────────────────────────────────
                PullToRefreshBox(
                    isRefreshing = isScanning,
                    onRefresh    = onRefresh,
                    modifier     = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding      = PaddingValues(top = 4.dp, bottom = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(items = songs, key = { _, s -> s.id }) { index, song ->
                            Box(modifier = Modifier.animateItem()) {
                                SongListItem(
                                    song               = song,
                                    isCurrentlyPlaying = !isMultiSelect && currentSong?.id == song.id,
                                    isMultiSelectMode  = isMultiSelect,
                                    isSelected         = song.id in selectedIds,
                                    onClick            = {
                                        when {
                                            // 多选模式：点击 = 切换选中
                                            isMultiSelect -> selectedIds =
                                                if (song.id in selectedIds) selectedIds - song.id
                                                else selectedIds + song.id
                                            // 普通模式：当前歌曲 = 打开播放器
                                            currentSong?.id == song.id -> onOpenPlayer()
                                            else -> onSongClick(songs, index)
                                        }
                                    },
                                    onLongClick = {
                                        // 长按进入多选并选中该歌曲
                                        if (!isMultiSelect) {
                                            isMultiSelect = true
                                            selectedIds   = setOf(song.id)
                                        }
                                    },
                                    onPlayNext  = { viewModel.playNext(song) },
                                    onMoreClick = { optionsSong = song }
                                )
                            }
                        }
                    }
                }
            }

            // 抽屉打开时遮罩层
            if (isDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = { isDrawerOpen = false }
                        )
                )
            }

            // ── 多选底部操作栏（从底部滑入，迷你播放条已隐藏） ──────────────
            AnimatedVisibility(
                visible  = isMultiSelect,
                enter    = slideInVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(tween(220)),
                exit     = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MultiSelectBottomBar(
                    selectedCount   = selectedIds.size,
                    onDelete        = {
                        songs.filter { it.id in selectedIds }.forEach { viewModel.deleteSong(it) }
                        isMultiSelect = false; selectedIds = emptySet()
                    },
                    onAddToPlaylist = { showMultiPlaylistSheet = true }
                )
            }
        }
    }

    // ── 歌曲操作底部菜单 ──────────────────────────────────────────────────
    optionsSong?.let { song ->
        SongOptionsSheet(
            song             = song,
            playlists        = playlists,
            onDismiss        = { optionsSong = null },
            onPlayNext       = { viewModel.playNext(song);                     optionsSong = null },
            onAddToPlaylist  = { id -> viewModel.addSongToPlaylist(id, song.id); optionsSong = null },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onDelete         = { viewModel.deleteSong(song);                   optionsSong = null }
        )
    }

    // ── 排序 BottomSheet ──────────────────────────────────────────────────
    if (showSortSheet) {
        SortSheet(
            current   = sortOrder,
            onSelect  = { viewModel.setSortOrder(it); showSortSheet = false },
            onDismiss = { showSortSheet = false }
        )
    }

    // ── 多选"添加到歌单" ──────────────────────────────────────────────────
    if (showMultiPlaylistSheet) {
        PlaylistPickSheet(
            playlists        = playlists,
            onSelect         = { id ->
                selectedIds.forEach { songId -> viewModel.addSongToPlaylist(id, songId) }
                showMultiPlaylistSheet = false; isMultiSelect = false; selectedIds = emptySet()
            },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
            onDismiss        = { showMultiPlaylistSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StatsRow —— 歌曲数 + 累计听歌时长 + 排序按钮
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatsRow(
    songCount       : Int,
    totalListenedMs : Long,
    sortOrder       : SortOrder,
    onSortClick     : () -> Unit,
) {
    val sortLabel = when (sortOrder) {
        SortOrder.DATE_ADDED -> "最近添加"
        SortOrder.TITLE      -> "名称"
        SortOrder.ARTIST     -> "艺术家"
        SortOrder.DURATION   -> "时长"
    }
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧：歌曲数 + 累计时长
        Column {
            Text(
                text  = if (songCount == 0) "暂无歌曲" else "$songCount 首歌曲",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (totalListenedMs > 0L) {
                Text(
                    text  = "累计听了 ${formatDuration(totalListenedMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
        // 右侧：排序按钮
        TextButton(
            onClick        = onSortClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.FilterList,
                contentDescription = "排序",
                modifier           = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text  = sortLabel,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SortSheet —— 排序选择底部弹窗
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current   : SortOrder,
    onSelect  : (SortOrder) -> Unit,
    onDismiss : () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text     = "排序方式",
            style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        val options = listOf(
            SortOrder.DATE_ADDED to "最近添加",
            SortOrder.TITLE      to "名称",
            SortOrder.ARTIST     to "艺术家",
            SortOrder.DURATION   to "时长"
        )
        options.forEach { (order, label) ->
            val selected = order == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(order) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color      = if (selected) MaterialTheme.colorScheme.primary
                    else          MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector        = Icons.Rounded.Check,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SongOptionsSheet —— 歌曲操作底部菜单
// 内含三个视图：主菜单 / 添加到歌单 / 歌曲信息
// ─────────────────────────────────────────────────────────────────────────────
private enum class OptionsView { MAIN, PLAYLIST, INFO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongOptionsSheet(
    song             : Song,
    playlists        : List<Playlist>,
    onDismiss        : () -> Unit,
    onPlayNext       : () -> Unit,
    onAddToPlaylist  : (Long) -> Unit,
    onCreatePlaylist : (String) -> Unit,
    onDelete         : () -> Unit,
) {
    var view            by remember { mutableStateOf(OptionsView.MAIN) }
    var showNewListInput by remember { mutableStateOf(false) }
    var newListName      by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        AnimatedContent(
            targetState  = view,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal)
                    (fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 6 }) togetherWith fadeOut(tween(160))
                else
                    (fadeIn(tween(200)) + slideInVertically(tween(250)) { -it / 6 }) togetherWith fadeOut(tween(160))
            },
            label = "optionsView"
        ) { currentView ->
            when (currentView) {

                // ── 主菜单 ───────────────────────────────────────────────
                OptionsView.MAIN -> Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    // 歌曲预览头
                    Row(
                        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model              = song.albumArtUri,
                            contentDescription = null,
                            modifier           = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale       = ContentScale.Crop,
                            error              = rememberVectorPainter(Icons.Rounded.MusicNote)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text     = song.title,
                                style    = MaterialTheme.typography.titleMedium
                                    .copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text  = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(4.dp))
                    // 操作选项
                    OptionRow(Icons.Rounded.SkipNext,   "下一首播放", onClick = onPlayNext)
                    OptionRow(Icons.Rounded.QueueMusic, "添加到歌单", onClick = { view = OptionsView.PLAYLIST })
                    OptionRow(Icons.Rounded.Info,       "歌曲信息",   onClick = { view = OptionsView.INFO })
                    OptionRow(Icons.Rounded.Delete,     "删除",       isDestructive = true, onClick = onDelete)
                    Spacer(Modifier.navigationBarsPadding())
                }

                // ── 添加到歌单子页 ────────────────────────────────────────
                OptionsView.PLAYLIST -> Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { view = OptionsView.MAIN }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = 180f }
                            )
                        }
                        Text(
                            "添加到歌单",
                            style    = MaterialTheme.typography.titleLarge
                                .copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showNewListInput = !showNewListInput }) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新建")
                        }
                    }
                    // 新建歌单输入框（可收起）
                    AnimatedVisibility(visible = showNewListInput) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value         = newListName,
                                onValueChange = { newListName = it },
                                label         = { Text("歌单名称") },
                                singleLine    = true,
                                modifier      = Modifier.weight(1f),
                                shape         = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick  = {
                                    if (newListName.isNotBlank()) {
                                        onCreatePlaylist(newListName)
                                        newListName      = ""
                                        showNewListInput = false
                                    }
                                },
                                enabled  = newListName.isNotBlank()
                            ) { Text("创建") }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    if (playlists.isEmpty()) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "还没有歌单，点右上角新建",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        playlists.forEach { pl ->
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddToPlaylist(pl.id) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.QueueMusic, null,
                                    modifier = Modifier.size(22.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${pl.songs.size} 首",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding())
                }

                // ── 歌曲信息子页 ──────────────────────────────────────────
                OptionsView.INFO -> Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { view = OptionsView.MAIN }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = 180f }
                            )
                        }
                        Text(
                            "歌曲信息",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    listOf(
                        "标题"   to song.title,
                        "艺术家" to song.artist,
                        "专辑"   to song.album,
                        "时长"   to formatDuration(song.duration),
                        "大小"   to formatFileSize(song.size),
                        "路径"   to (song.filePath.ifBlank { song.uri })
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text  = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                            Text(
                                text     = value,
                                style    = MaterialTheme.typography.bodyMedium,
                                color    = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlaylistPickSheet —— 可复用的歌单选择弹窗（多选模式用）
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickSheet(
    playlists        : List<Playlist>,
    onSelect         : (Long) -> Unit,
    onCreatePlaylist : (String) -> Unit,
    onDismiss        : () -> Unit,
) {
    var newListName by remember { mutableStateOf("") }
    var showInput   by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "添加到歌单",
                style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showInput = !showInput }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }
        AnimatedVisibility(visible = showInput) {
            Row(
                modifier          = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = newListName,
                    onValueChange = { newListName = it },
                    label         = { Text("歌单名称") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            onCreatePlaylist(newListName)
                            newListName = ""; showInput = false
                        }
                    },
                    enabled = newListName.isNotBlank()
                ) { Text("创建") }
            }
        }
        playlists.forEach { pl ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(pl.id) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.QueueMusic, null,
                    modifier = Modifier.size(22.dp),
                    tint     = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                    Text("${pl.songs.size} 首", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MultiSelectTopBar —— 多选模式下的顶部栏
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MultiSelectTopBar(
    selectedCount : Int,
    totalCount    : Int,
    onSelectAll   : () -> Unit,
    onCancel      : () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 8.dp)
    ) {
        // 左：取消按钮
        IconButton(
            onClick  = onCancel,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(Icons.Rounded.Close, "取消多选", modifier = Modifier.size(24.dp))
        }
        // 中：已选数量
        Text(
            text     = "已选 $selectedCount 首",
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.Center)
        )
        // 右：全选
        TextButton(
            onClick  = onSelectAll,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text(
                text  = if (selectedCount == totalCount) "取消全选" else "全选",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MultiSelectBottomBar —— 多选模式底部操作栏
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MultiSelectBottomBar(
    selectedCount   : Int,
    onDelete        : () -> Unit,
    onAddToPlaylist : () -> Unit,
) {
    Surface(
        modifier      = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape         = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color         = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 添加到歌单
            FilledTonalButton(
                onClick  = onAddToPlaylist,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("加入歌单")
            }
            // 删除（强调色）
            FilledTonalButton(
                onClick  = onDelete,
                modifier = Modifier.weight(1f),
                colors   = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("删除 $selectedCount 首")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OptionRow —— 操作菜单的一行（底部菜单内用）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OptionRow(
    icon          : ImageVector,
    label         : String,
    isDestructive : Boolean = false,
    onClick       : () -> Unit,
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error
    else               MaterialTheme.colorScheme.onSurface
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerContent —— 侧边栏（与原来完全相同）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerContent(
    isLocalExpanded      : Boolean,
    onToggleLocal        : () -> Unit,
    onNavigateToAlbums   : () -> Unit,
    onNavigateToArtists  : () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onOpenOnlineSearch   : () -> Unit,
    onNavigateToSettings : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(DrawerWidth)
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text     = "饼音",
            style    = MaterialTheme.typography.headlineLarge.copy(
                fontWeight    = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color         = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        DrawerSectionHeader(
            icon       = Icons.Rounded.LibraryMusic,
            title      = "本地音乐",
            isExpanded = isLocalExpanded,
            onClick    = onToggleLocal
        )
        AnimatedVisibility(
            visible = isLocalExpanded,
            enter   = expandVertically(tween(350, easing = RelaxedEasing)) + fadeIn(tween(250)),
            exit    = shrinkVertically(tween(300, easing = RelaxedEasing)) + fadeOut(tween(200))
        ) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Spacer(Modifier.height(4.dp))
                DrawerSubItem(Icons.Rounded.Album,      "专辑",   onNavigateToAlbums)
                DrawerSubItem(Icons.Rounded.Person,     "艺术家", onNavigateToArtists)
                DrawerSubItem(Icons.Rounded.QueueMusic, "歌单",   onNavigateToPlaylists)
                Spacer(Modifier.height(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        DrawerMenuItem(Icons.Rounded.CloudQueue, "云端", onOpenOnlineSearch)
        Spacer(Modifier.height(4.dp))
        DrawerMenuItem(Icons.Rounded.Settings,  "设置", onNavigateToSettings)
    }
}

@Composable
private fun DrawerSectionHeader(
    icon      : ImageVector,
    title     : String,
    isExpanded: Boolean,
    onClick   : () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue   = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = RelaxedEasing),
        label         = "arrowRotation"
    )
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = if (isExpanded) "折叠" else "展开",
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        )
    }
}

@Composable
private fun DrawerSubItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DrawerMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopBar —— 顶部导航栏（普通模式）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    isDrawerOpen        : Boolean,
    isSearchExpanded    : Boolean,
    searchQuery         : String,
    onToggleDrawer      : () -> Unit,
    onSearchToggle      : (Boolean) -> Unit,
    onSearchQueryChange : (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (!isSearchExpanded) {
            IconButton(onClick = onToggleDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
                Crossfade(targetState = isDrawerOpen, animationSpec = tween(350), label = "menuIcon") { open ->
                    Icon(
                        imageVector        = if (open) Icons.Rounded.Close else Icons.Rounded.Menu,
                        contentDescription = if (open) "关闭菜单" else "打开菜单",
                        modifier           = Modifier.size(26.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible  = !isSearchExpanded && !isDrawerOpen,
            enter    = fadeIn(tween(350)),
            exit     = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text  = "音乐库",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            )
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            IntegratedSearchBar(
                query        = searchQuery,
                onQueryChange= onSearchQueryChange,
                isExpanded   = isSearchExpanded,
                onToggle     = onSearchToggle
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IntegratedSearchBar —— 与原来完全相同
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntegratedSearchBar(
    query        : String,
    onQueryChange: (String) -> Unit,
    isExpanded   : Boolean,
    onToggle     : (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val widthFraction by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0.12f,
        animationSpec = tween(600, easing = RelaxedEasing),
        label         = "searchBarWidth"
    )
    val contentAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(400),
        label         = "searchContentAlpha"
    )
    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .clickable(interactionSource = interactionSource, indication = null) { if (!isExpanded) onToggle(true) }
        )
        Row(
            modifier          = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                Icons.Rounded.Search, "搜索",
                tint     = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            if (widthFraction > 0.4f) {
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value        = query,
                    onValueChange= onQueryChange,
                    modifier     = Modifier.weight(1f).alpha(contentAlpha),
                    textStyle    = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    singleLine   = true,
                    cursorBrush  = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("搜索歌曲...", style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                            inner()
                        }
                    }
                )
                if (isExpanded) {
                    IconButton(
                        onClick  = { if (query.isNotEmpty()) onQueryChange("") else onToggle(false) },
                        modifier = Modifier.alpha(contentAlpha)
                    ) {
                        Icon(Icons.Rounded.Close, if (query.isNotEmpty()) "清除" else "关闭搜索", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SongListItem —— 歌曲行（支持普通模式和多选模式）
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItem(
    song               : Song,
    isCurrentlyPlaying : Boolean,
    isMultiSelectMode  : Boolean,
    isSelected         : Boolean,
    onClick            : () -> Unit,
    onLongClick        : () -> Unit,
    onPlayNext         : () -> Unit,
    onMoreClick        : () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue   = if (isCurrentlyPlaying) 1.02f else 1f,
        animationSpec = tween(400, easing = RelaxedEasing),
        label         = "songItemScale"
    )
    val selectedBg by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label         = "selectedBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isSelected         -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    isCurrentlyPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else               -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 封面 / 多选圆圈
            Box(
                modifier         = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = song.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier           = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale       = ContentScale.Crop,
                    placeholder        = rememberVectorPainter(Icons.Rounded.MusicNote),
                    error              = rememberVectorPainter(Icons.Rounded.MusicNote)
                )
                // 多选模式覆盖层
                androidx.compose.animation.AnimatedVisibility(
                    visible = isMultiSelectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = if (isSelected) 0.45f else 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // 标题 + 艺术家
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = song.title,
                    style    = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    ),
                    color    = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                    else                    MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = song.artist,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧按钮：普通模式显示，多选模式隐藏
            AnimatedVisibility(
                visible = !isMultiSelectMode,
                enter   = fadeIn(tween(150)),
                exit    = fadeOut(tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 下一首播放
                    IconButton(
                        onClick  = onPlayNext,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.SkipNext,
                            contentDescription = "下一首播放",
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    // 更多选项（竖三点）
                    IconButton(
                        onClick  = onMoreClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.MoreVert,
                            contentDescription = "更多选项",
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────────────────────────────────────

/** 格式化时长：1小时32分 / 45分钟 / 30秒 */
private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours    = totalSec / 3600
    val minutes  = (totalSec % 3600) / 60
    val seconds  = totalSec % 60
    return when {
        hours > 0  -> "${hours}小时${minutes}分"
        minutes > 0-> "${minutes}分${seconds}秒"
        else       -> "${seconds}秒"
    }
}

/** 格式化文件大小：MB / KB */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L     -> "%.0f KB".format(bytes / 1_024.0)
    else                -> "$bytes B"
}