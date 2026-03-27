package com.clydeenke.ling.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.animateFloatAsState

// ─────────────────────────────────────────────────────────────────────────────
// 全局动画常量
// ─────────────────────────────────────────────────────────────────────────────

/** 舒缓贝塞尔曲线：先快后慢，像纸张飘落，适用于抽屉、卡片等大面积动画 */
private val RelaxedEasing = CubicBezierEasing(0.25f, 0.85f, 0.2f, 1.0f)

/** 侧边栏宽度 */
private val DrawerWidth = 260.dp

/** 左边缘触发区域：关闭状态下只有这个宽度内可以开始右滑 */
private val EdgeTriggerWidth = 56.dp

// ─────────────────────────────────────────────────────────────────────────────
// LibraryScreen —— 本地音乐库主界面
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    hazeState: HazeState,
    /** 点击歌曲条目，传出列表快照和点击索引 */
    onSongClick: (songs: List<Song>, index: Int) -> Unit,
    /** 点击当前正在播放的歌曲，打开全屏播放器 */
    onOpenPlayer: () -> Unit = {},
    /** 侧边栏"云端"点击 */
    onOpenOnlineSearch: () -> Unit = {},
    /** 侧边栏"设置"点击 */
    onNavigateToSettings: () -> Unit = {},
    /** 侧边栏本地子项：专辑 */
    onNavigateToAlbums: () -> Unit = {},
    /** 侧边栏本地子项：艺术家 */
    onNavigateToArtists: () -> Unit = {},
    /** 侧边栏本地子项：歌单 */
    onNavigateToPlaylists: () -> Unit = {},
    /** 下拉刷新，触发本地扫描 */
    onRefresh: () -> Unit = {},
) {
    // ── ViewModel 数据订阅 ────────────────────────────────────────────────
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentSong by viewModel.playerController.currentSong.collectAsStateWithLifecycle()

    // ── UI 本地状态 ───────────────────────────────────────────────────────
    var isSearchExpanded by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    // 侧边栏"本地音乐"子项是否展开（点击云端时自动折叠）
    var isLocalExpanded by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { DrawerWidth.toPx() }
    val edgeTriggerPx = with(density) { EdgeTriggerWidth.toPx() }

    // ── 抽屉偏移量（支持跟手拖拽 + 自动补间两种模式） ────────────────────
    val dragOffset = remember { Animatable(0f) }

    // isDrawerOpen 状态切换时，自动补间到目标位置
    LaunchedEffect(isDrawerOpen) {
        val target = if (isDrawerOpen) drawerWidthPx else 0f
        if (dragOffset.value != target) {
            dragOffset.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 650, easing = RelaxedEasing)
            )
        }
    }

    // 所有视觉效果绑定同一个进度值，保证完全同步
    // fraction = 0f 抽屉关闭，fraction = 1f 抽屉完全打开
    val fraction = (dragOffset.value / drawerWidthPx).coerceIn(0f, 1f)
    val contentScale = 1f - 0.06f * fraction    // 主内容最多缩小至 94%
    val contentRadius: Dp = 32.dp * fraction     // 圆角随开启程度增大

    // ─────────────────────────────────────────────────────────────────────
    // 根容器：铺满全屏，作为抽屉背景层
    // ─────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .hazeSource(state = hazeState)
            .pointerInput(isDrawerOpen) {
                /*
                 * ── 手势处理说明 ────────────────────────────────────────────
                 *
                 * 旧问题：detectHorizontalDragGestures 覆盖全屏并无条件消费
                 * 所有触摸事件，与 LazyColumn 竖向滚动产生冲突，松手时卡住。
                 *
                 * 新方案（awaitEachGesture 底层控制）：
                 * 1. 抽屉关闭时，仅在屏幕左边 EdgeTriggerWidth 范围内才开始监听
                 * 2. 累积位移：水平 > 垂直 × 1.5 才判断为横向并消费事件
                 *    → 竖向为主时直接退出，LazyColumn 正常接管
                 * 3. VelocityTracker 记录速度，支持快速甩手开关
                 */
                val velocityTracker = VelocityTracker()

                awaitEachGesture {
                    // 等待按下（不消费，子控件同样可以收到）
                    val down = awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.resetTracking()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    // 抽屉关闭且不在边缘区域 → 直接放行，不处理
                    if (!isDrawerOpen && down.position.x > edgeTriggerPx) return@awaitEachGesture

                    var isHorizontalGesture = false
                    var accX = 0f
                    var accY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break

                        if (!change.pressed) {
                            // ── 手指抬起：决定最终状态 ──────────────────────
                            if (isHorizontalGesture) {
                                val vx = velocityTracker.calculateVelocity().x
                                isDrawerOpen = when {
                                    vx > 500f  -> true   // 快速右甩 → 打开
                                    vx < -500f -> false  // 快速左甩 → 关闭
                                    // 静止松手：超过 30% 开，否则关
                                    else -> dragOffset.value > drawerWidthPx * 0.3f
                                }
                            }
                            break
                        }

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        accX += dx
                        accY += dy
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (!isHorizontalGesture) {
                            val threshold = with(density) { 8.dp.toPx() }
                            val horizontal = abs(accX) > abs(accY) * 1.5f && abs(accX) > threshold
                            val vertical   = abs(accY) > abs(accX) * 1.5f && abs(accY) > threshold
                            when {
                                horizontal -> isHorizontalGesture = true
                                // 确认是竖向 → 退出，交给 LazyColumn
                                vertical   -> break
                            }
                            continue
                        }

                        // 确认横向后才消费事件
                        change.consume()
                        val newOffset = (dragOffset.value + dx).coerceIn(0f, drawerWidthPx)
                        scope.launch { dragOffset.snapTo(newOffset) }
                    }
                }
            }
    ) {
        // ── 侧边栏内容（在主内容下方渲染） ───────────────────────────────
        DrawerContent(
            isLocalExpanded = isLocalExpanded,
            onToggleLocal = { isLocalExpanded = !isLocalExpanded },
            onNavigateToAlbums = { isDrawerOpen = false; onNavigateToAlbums() },
            onNavigateToArtists = { isDrawerOpen = false; onNavigateToArtists() },
            onNavigateToPlaylists = { isDrawerOpen = false; onNavigateToPlaylists() },
            onOpenOnlineSearch = {
                isLocalExpanded = false  // 切到云端时折叠本地子项
                isDrawerOpen = false
                onOpenOnlineSearch()
            },
            onNavigateToSettings = { isDrawerOpen = false; onNavigateToSettings() }
        )

        // ── 主内容区（跟随抽屉移动 + 缩放 + 动态圆角） ───────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragOffset.value
                    scaleX = contentScale
                    scaleY = contentScale
                    // shadowElevation 在 graphicsLayer 内设置，投影会跟随 shape 正确渲染
                    shadowElevation = 24f * fraction
                    shape = RoundedCornerShape(contentRadius)
                    clip = true
                    transformOrigin = TransformOrigin(0f, 0.5f) // 以左边缘为缩放原点
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 顶部导航栏
                TopBar(
                    isDrawerOpen = isDrawerOpen,
                    isSearchExpanded = isSearchExpanded,
                    searchQuery = searchQuery,
                    onToggleDrawer = { isDrawerOpen = !isDrawerOpen },
                    onSearchToggle = { isSearchExpanded = it },
                    onSearchQueryChange = viewModel::setSearchQuery
                )

                // 歌曲列表
                PullToRefreshBox(
                    isRefreshing = isScanning,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            items = songs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            Box(modifier = Modifier.animateItem()) {
                                SongListItem(
                                    song = song,
                                    isCurrentlyPlaying = currentSong?.id == song.id,
                                    onClick = {
                                        if (currentSong?.id == song.id) onOpenPlayer()
                                        else onSongClick(songs, index)
                                    },
                                    onLongClick = { songToDelete = song }
                                )
                            }
                        }
                    }
                }
            }

            // 遮罩层：抽屉打开时点击主内容区关闭抽屉
            if (isDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isDrawerOpen = false }
                        )
                )
            }
        }
    }

    // ── 删除歌曲确认弹窗 ─────────────────────────────────────────────────
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("删除歌曲") },
            text = { Text("确定移除「${song.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSong(song); songToDelete = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerContent —— 侧边栏内容
//
// 布局结构：
//   饼音
//   ─────────
//   ▼ 本地音乐（可展开/折叠）
//       专辑 / 艺术家 / 歌单
//   ─────────
//   ☁ 云端
//   ⚙ 设置
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerContent(
    isLocalExpanded: Boolean,
    onToggleLocal: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onOpenOnlineSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(DrawerWidth)
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // App 名称
        Text(
            text = "饼音",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ── 本地音乐（可展开的分组标题） ──────────────────────────────────
        DrawerSectionHeader(
            icon = Icons.Rounded.LibraryMusic,
            title = "本地音乐",
            isExpanded = isLocalExpanded,
            onClick = onToggleLocal
        )

        // 子项展开/折叠动画
        AnimatedVisibility(
            visible = isLocalExpanded,
            enter = expandVertically(tween(350, easing = RelaxedEasing)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(300, easing = RelaxedEasing)) + fadeOut(tween(200))
        ) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Spacer(modifier = Modifier.height(4.dp))
                DrawerSubItem(Icons.Rounded.Album, "专辑", onNavigateToAlbums)
                DrawerSubItem(Icons.Rounded.Person, "艺术家", onNavigateToArtists)
                DrawerSubItem(Icons.Rounded.QueueMusic, "歌单", onNavigateToPlaylists)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        DrawerMenuItem(Icons.Rounded.CloudQueue, "云端", onOpenOnlineSearch)
        Spacer(modifier = Modifier.height(4.dp))
        DrawerMenuItem(Icons.Rounded.Settings, "设置", onNavigateToSettings)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerSectionHeader —— 可展开分组的标题行（带旋转箭头）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerSectionHeader(
    icon: ImageVector,
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = RelaxedEasing),
        label = "arrowRotation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = if (isExpanded) "折叠" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = arrowRotation }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerSubItem —— 本地分组下的缩进子项（专辑/艺术家/歌单）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerSubItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerMenuItem —— 侧边栏顶级菜单项（云端、设置）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopBar —— 顶部导航栏
// 左：汉堡/关闭  中：页面标题  右：搜索框
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    isDrawerOpen: Boolean,
    isSearchExpanded: Boolean,
    searchQuery: String,
    onToggleDrawer: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp)
    ) {
        // 搜索展开时隐藏汉堡按钮，防止布局拥挤
        if (!isSearchExpanded) {
            IconButton(
                onClick = onToggleDrawer,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Crossfade(
                    targetState = isDrawerOpen,
                    animationSpec = tween(350),
                    label = "menuIconCrossfade"
                ) { open ->
                    if (open) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭菜单",
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "打开菜单",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // 标题：搜索展开或抽屉打开时淡出
        AnimatedVisibility(
            visible = !isSearchExpanded && !isDrawerOpen,
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "音乐库",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            )
        }

        // 右侧搜索框
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            IntegratedSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                isExpanded = isSearchExpanded,
                onToggle = onSearchToggle
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IntegratedSearchBar —— 可展开搜索框
// 折叠态：一个搜索图标；展开态：全宽输入框 + 清除按钮
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntegratedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val widthFraction by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.12f,
        animationSpec = tween(600, easing = RelaxedEasing),
        label = "searchBarWidth"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(400),
        label = "searchContentAlpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // 背景胶囊
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
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

        // 内容行
        Row(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "搜索",
                tint = if (isExpanded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            // 宽度超过 40% 才渲染文字部分，避免过渡期挤压闪烁
            if (widthFraction > 0.4f) {
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).alpha(contentAlpha),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索歌曲...",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                            .copy(alpha = 0.5f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (isExpanded) {
                    IconButton(
                        onClick = {
                            if (query.isNotEmpty()) onQueryChange("") else onToggle(false)
                        },
                        modifier = Modifier.alpha(contentAlpha)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = if (query.isNotEmpty()) "清除" else "关闭搜索",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SongListItem —— 单首歌曲列表行
// 播放中：放大 + 高亮背景 + 波形图标
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongListItem(
    song: Song,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isCurrentlyPlaying) 1.03f else 1f,
        animationSpec = tween(400, easing = RelaxedEasing),
        label = "songItemScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Rounded.MusicNote),
                error = rememberVectorPainter(Icons.Rounded.MusicNote)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "正在播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}