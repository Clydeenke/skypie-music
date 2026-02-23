package com.clydeenke.ling.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── 展开状态 ──────────────────────────────────────────────────────────────────
enum class PlayerAnchor { Mini, Full }

// ── SharedElement 键 ──────────────────────────────────────────────────────────
private object SharedKeys {
    const val CONTAINER  = "player_container"
    const val ALBUM_ART  = "player_album_art"
    const val SONG_TITLE = "player_song_title"
}

// ── 固定锚点像素值 ────────────────────────────────────────────────────────────
// Mini 位置 = 0f（相对于容器顶部的偏移）
// Full 位置 = -(容器总高度 - miniBarHeight)，即全屏展开时的负偏移量
const val MINI_BAR_HEIGHT_DP = 68 // 胶囊条高度估算（dp）

/**
 * 入口组件。
 *
 * 提升到 Navigation 顶层 Box 的最后（最高 z-order），确保全屏时覆盖标题栏。
 * BoxWithConstraints 一次性获取高度，不再在 onSizeChanged 里高频更新锚点。
 */
@Composable
fun SharedPlayerContainer(
    viewModel : MusicViewModel,
    modifier  : Modifier = Modifier
) {
    val density   = LocalDensity.current
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val coroutine = rememberCoroutineScope()

    if (song == null) return

    // ✅ BoxWithConstraints 一次性拿到屏幕高度，不再高频 onSizeChanged
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val miniBarPx    = with(density) { MINI_BAR_HEIGHT_DP.dp.toPx() }

        // ✅ 锚点在这里一次性确定，不再动态更新
        val draggableState = remember(fullHeightPx) {
            AnchoredDraggableState(
                initialValue        = PlayerAnchor.Mini,
                anchors             = DraggableAnchors {
                    PlayerAnchor.Mini at 0f
                    PlayerAnchor.Full at -(fullHeightPx - miniBarPx)
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                velocityThreshold   = { with(density) { 300.dp.toPx() } },
                snapAnimationSpec   = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                decayAnimationSpec  = exponentialDecay(frictionMultiplier = 1.5f)
            )
        }

        // ✅ progress 通过 derivedStateOf 隔离重组——只有 progress 变化时
        //    才触发消费方重组，不会因为其他状态导致整棵树抖动
        val progress by remember {
            derivedStateOf {
                val offset   = draggableState.offset
                val maxTravel = fullHeightPx - miniBarPx
                if (offset.isNaN() || maxTravel == 0f) 0f
                else (-offset / maxTravel).coerceIn(0f, 1f)
            }
        }

        val isExpanded = draggableState.currentValue == PlayerAnchor.Full

        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState  = isExpanded,
                transitionSpec = { fadeIn(tween(0)) togetherWith fadeOut(tween(0)) },
                label        = "playerState"
            ) { expanded ->
                val avScope = this

                if (!expanded) {
                    // ── Mini 胶囊（底部区域） ────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        MiniPlayerShared(
                            viewModel               = viewModel,
                            sharedTransitionScope   = this@SharedTransitionLayout,
                            animatedVisibilityScope = avScope,
                            draggableState          = draggableState,
                            onExpand                = {
                                coroutine.launch { draggableState.animateTo(PlayerAnchor.Full) }
                            }
                        )
                    }
                } else {
                    // ── Full 全屏（覆盖一切） ────────────────────────────
                    FullPlayerShared(
                        viewModel               = viewModel,
                        sharedTransitionScope   = this@SharedTransitionLayout,
                        animatedVisibilityScope = avScope,
                        progress                = progress,
                        draggableState          = draggableState,
                        onCollapse              = {
                            coroutine.launch { draggableState.animateTo(PlayerAnchor.Mini) }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniPlayer（胶囊条）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MiniPlayerShared(
    viewModel               : MusicViewModel,
    sharedTransitionScope   : SharedTransitionScope,
    animatedVisibilityScope : AnimatedVisibilityScope,
    draggableState          : AnchoredDraggableState<PlayerAnchor>,
    onExpand                : () -> Unit,
    modifier                : Modifier = Modifier
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .sharedBounds(
                    sharedContentState      = rememberSharedContentState(SharedKeys.CONTAINER),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform         = { _, _ ->
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    },
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                )
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
                .anchoredDraggable(state = draggableState, orientation = Orientation.Vertical)
                .clickable(onClick = onExpand)
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(MINI_BAR_HEIGHT_DP.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 封面（sharedElement）
                AsyncImage(
                    model              = song?.albumArtUri,
                    contentDescription = "封面",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(44.dp)
                        .sharedElement(
                            sharedContentState      = rememberSharedContentState(SharedKeys.ALBUM_ART),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform         = { _, _ ->
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                            }
                        )
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(Modifier.width(12.dp))

                // 文字（weight 在外层，按钮固定右侧）
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = song?.title ?: "",
                        style    = MaterialTheme.typography.titleLarge,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = song?.artist ?: "",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.playerController.skipToPrevious() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { viewModel.playerController.togglePlayPause() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { viewModel.playerController.skipToNext() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FullPlayer（全屏播放器）
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FullPlayerShared(
    viewModel               : MusicViewModel,
    sharedTransitionScope   : SharedTransitionScope,
    animatedVisibilityScope : AnimatedVisibilityScope,
    progress                : Float,
    draggableState          : AnchoredDraggableState<PlayerAnchor>,
    onCollapse              : () -> Unit,
    modifier                : Modifier = Modifier
) {
    val song        by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying   by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val repeatMode  by viewModel.playerController.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.playerController.shuffleMode.collectAsStateWithLifecycle()
    val isDark       = isSystemInDarkTheme()

    // ✅ Predictive Back：滑动返回时触发收起
    BackHandler(enabled = true, onBack = onCollapse)

    // ── 进度条状态（完全隔离，不触发外层重组）────────────────────
    // 用单独 rememberUpdatedState 包住 ViewModel 引用，避免闭包捕获问题
    val controllerRef by rememberUpdatedState(viewModel.playerController)
    var currentMs    by remember { mutableLongStateOf(0L) }
    var durationMs   by remember { mutableLongStateOf(1L) }
    // ✅ 进度条解耦：isDragging / dragProgress 只在 Slider 内部用
    //    Slider 用独立的本地状态，不影响父层 progress（封面/背景）的重组
    var isDragging   by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                currentMs  = controllerRef.getCurrentPosition()
                durationMs = controllerRef.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    // ── 封面缩放（播放/暂停切换，tween 无弹跳）──────────────────
    val albumScale by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0.88f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label         = "albumScale"
    )

    // ── 预测性返回缩放（progress 从 1 → 0 时，容器整体缩小）────
    // 当用户下拉（progress 减小）时，容器从 1f 缩小到 0.88f，产生遇见式返回感
    val containerScale by animateFloatAsState(
        targetValue   = 0.88f + progress * 0.12f,  // progress=1 → 1f；progress=0 → 0.88f
        animationSpec = tween(0),                   // 跟手，无延迟
        label         = "containerScale"
    )

    // ── 主色调渐变背景（替代高斯模糊，零性能开销）──────────────
    // progress > 30% 时渐显背景
    val bgAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary

    // 亮色/暗色模式下用不同的透明度和深度
    val gradientColors = if (isDark) {
        listOf(
            primaryColor.copy(alpha = 0.85f * bgAlpha),
            primaryColor.copy(alpha = 0.50f * bgAlpha),
            MaterialTheme.colorScheme.background.copy(alpha = 1f)
        )
    } else {
        listOf(
            primaryColor.copy(alpha = 0.30f * bgAlpha),
            primaryColor.copy(alpha = 0.12f * bgAlpha),
            MaterialTheme.colorScheme.background.copy(alpha = 1f)
        )
    }

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxSize()
                // ✅ 预测性返回缩放：整个容器跟随 progress 收缩
                .graphicsLayer {
                    scaleX         = containerScale
                    scaleY         = containerScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    // 收缩时加圆角，模拟系统遇见式返回
                    shape          = RoundedCornerShape((1f - progress) * 24.dp.toPx())
                    clip           = true
                }
                .sharedBounds(
                    sharedContentState      = rememberSharedContentState(SharedKeys.CONTAINER),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform         = { _, _ ->
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    },
                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                )
                .clip(RoundedCornerShape(0.dp))
                .anchoredDraggable(state = draggableState, orientation = Orientation.Vertical)
        ) {
            // 层1：纯色背景基底
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            // 层2：主色流光渐变（替代高斯模糊，GPU 友好）
            if (bgAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = gradientColors,
                                startY = 0f,
                                endY   = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }

            // 层3：内容
            // ✅ 布局重组：封面偏上，信息/按钮整体靠下，填满底部空白
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))

                // 顶栏
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "收起",
                            Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "正在播放",
                        style     = MaterialTheme.typography.titleLarge,
                        color     = MaterialTheme.colorScheme.onBackground,
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.MoreVert, "更多", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // 封面区域：weight(1f) 让它撑满顶部剩余空间，保持封面垂直居中偏上
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                ) {
                    AsyncImage(
                        model              = song?.albumArtUri,
                        contentDescription = "专辑封面",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(1f)
                            .sharedElement(
                                sharedContentState      = rememberSharedContentState(SharedKeys.ALBUM_ART),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform         = { _, _ ->
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                }
                            )
                            .graphicsLayer {
                                scaleX          = albumScale
                                scaleY          = albumScale
                                shadowElevation = 32f * albumScale
                                shape           = RoundedCornerShape(20.dp)
                                clip            = true
                            }
                    )
                }

                // ── 歌名 + 收藏 ───────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .sharedBounds(
                                sharedContentState      = rememberSharedContentState(SharedKeys.SONG_TITLE),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform         = { _, _ -> tween(300) }
                            )
                    ) {
                        Text(
                            text     = song?.title ?: "",
                            style    = MaterialTheme.typography.headlineMedium,
                            color    = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text     = song?.artist ?: "",
                            style    = MaterialTheme.typography.bodyLarge,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.FavoriteBorder, "收藏",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── 进度条（完全解耦，独立 key 隔离重组）────────────
                // ✅ key("slider") 让 Slider 在自己的重组范围里运行
                //    拖动时只有这个 key 块重组，不影响封面/背景/歌名
                key("slider") {
                    val displayProgress = if (isDragging) dragProgress
                    else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)

                    Slider(
                        value                 = displayProgress,
                        onValueChange         = { v ->
                            isDragging   = true
                            dragProgress = v
                            currentMs    = (v * durationMs).toLong()
                        },
                        onValueChangeFinished = {
                            controllerRef.seekTo((dragProgress * durationMs).toLong())
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = SliderDefaults.colors(
                            thumbColor       = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMs(currentMs),  style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 控制按钮 ──────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle, "随机",
                            tint     = if (shuffleMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick  = { viewModel.playerController.skipToPrevious() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(32.dp))
                    }
                    FilledIconButton(
                        onClick  = { viewModel.playerController.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(
                        onClick  = { viewModel.playerController.skipToNext() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(32.dp))
                    }
                    IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                        Icon(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                else                   -> Icons.Rounded.Repeat
                            },
                            "循环",
                            tint     = if (repeatMode != Player.REPEAT_MODE_OFF)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // ✅ 底部 Spacer：撑满底部导航栏高度 + 额外余量，消除空白感
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── 工具 ──────────────────────────────────────────────────────────────────────
private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}