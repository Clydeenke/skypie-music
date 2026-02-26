package com.clydeenke.ling.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
internal val MiniPlayerHeight = 68.dp
private  val MiniBottomPad    = 24.dp
private  val MiniSidePad      = 14.dp

// FullPlayer layout constants (for pager cover Y anchor)
private val FullTopBar     = 56.dp
private val FullPagerPad   = 4.dp
private val FullBottomCtrl = 291.dp

// ── 动画曲线 ───────────────────────────────────────────────────────────────────
// 点击展开：先快后慢（iOS feel），使用弹簧，fast→resist
private val TapSpring    = spring<Float>(stiffness = 320f, dampingRatio = Spring.DampingRatioNoBouncy)
// 拖动松手落定：CubicBezier ease-out
private val SettleEasing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

// ─────────────────────────────────────────────────────────────────────────────
// SharedPlayerContainer
//
// ┌─ 动效描述（一镜到底）────────────────────────────────────────────────────────┐
// │  胶囊背景: 向上拉时左右+上下一起向外扩展，无阴影，圆角渐消，填满全屏         │
// │  Mini内容: 封面+文字+按钮作为整体，向下位移60dp+淡出(前30% progress)         │
// │            感觉像"收拾好行李下去，再作为全屏出来"                             │
// │  全屏UI:   从Box下方随Box整体上移而自然出现，progress>15%后开始淡入          │
// │  手势:     onDrag = snapTo（1:1跟手，手不离开就不自动对齐）                   │
// │            onDragStopped = settle（速度/距离判断，tween+SettleEasing落定）    │
// │            onTap = spring（TapSpring，先快后阻）                              │
// └─────────────────────────────────────────────────────────────────────────────┘
@Composable
fun SharedPlayerContainer(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val song by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    if (song == null) return

    val density     = LocalDensity.current
    val scope       = rememberCoroutineScope()
    val statusBarPx = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWPx = constraints.maxWidth.toFloat()
        val screenHPx = constraints.maxHeight.toFloat()
        val miniHPx   = with(density) { MiniPlayerHeight.toPx() }
        val botPx     = with(density) { MiniBottomPad.toPx() }
        val sidePx    = with(density) { MiniSidePad.toPx() }

        // animOffset: dragRange = Mini，0 = Full
        val dragRange  = (screenHPx - miniHPx - botPx).coerceAtLeast(1f)
        val animOffset = remember { Animatable(dragRange, Float.VectorConverter) }

        // progress 0=Mini, 1=Full（raw，1:1跟手，不施加easing）
        val progress by remember {
            derivedStateOf { (1f - animOffset.value / dragRange).coerceIn(0f, 1f) }
        }

        val velocityTracker = remember { VelocityTracker() }

        // ── 落定函数（仅在松手后调用） ───────────────────────────────────────
        fun goToSettle(target: Float) {
            scope.launch {
                animOffset.animateTo(target, tween(400, easing = SettleEasing))
            }
        }
        fun goToTap(target: Float) {
            scope.launch {
                animOffset.animateTo(target, TapSpring)
            }
        }
        fun settle(velocityY: Float) {
            val toFull = when {
                velocityY < -600f -> true
                velocityY >  600f -> false
                else              -> progress > 0.45f
            }
            goToSettle(if (toFull) 0f else dragRange)
        }

        // 返回键：动画收起
        BackHandler(enabled = progress > 0f) { goToSettle(dragRange) }

        // ── Mini 手势：区分轻点 vs 拖动 ────────────────────────────────────
        val miniGestureMod = Modifier.pointerInput(dragRange) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch { animOffset.stop() }
                velocityTracker.resetTracking()
                velocityTracker.addPointerInputChange(down)

                var totalDy           = 0f
                var dragging          = false
                var upConsumedByChild = false

                while (true) {
                    val event  = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dy     = change.position.y - change.previousPosition.y
                    totalDy   += dy

                    if (!dragging && abs(totalDy) > viewConfiguration.touchSlop) dragging = true

                    if (dragging) {
                        velocityTracker.addPointerInputChange(change)
                        // onDrag: 纯 snapTo，1:1，不自动对齐
                        scope.launch {
                            animOffset.snapTo((animOffset.value + dy).coerceIn(0f, dragRange))
                        }
                        change.consume()
                    }
                    if (!change.pressed) {
                        upConsumedByChild = change.isConsumed && !dragging
                        break
                    }
                }
                // 松手才落定
                when {
                    dragging           -> settle(velocityTracker.calculateVelocity().y)
                    !upConsumedByChild -> goToTap(0f)  // 轻点：spring 先快后阻
                }
            }
        }

        // ── Full 手势：整个 Column 垂直拖动，与 HorizontalPager 水平轴正交 ─
        val fullGestureMod = Modifier.pointerInput(dragRange) {
            detectVerticalDragGestures(
                onDragStart    = {
                    scope.launch { animOffset.stop() }
                    velocityTracker.resetTracking()
                },
                onVerticalDrag = { change, dy ->
                    velocityTracker.addPointerInputChange(change)
                    scope.launch {
                        animOffset.snapTo((animOffset.value + dy).coerceIn(0f, dragRange))
                    }
                },
                onDragEnd    = { settle(velocityTracker.calculateVelocity().y) },
                onDragCancel = { goToSettle(dragRange) }
            )
        }

        val s = song ?: return@BoxWithConstraints
        val p = progress

        // ══════════════════════════════════════════════════════════════════
        // 背景卡片插值（无阴影，一镜到底无层叠感）
        // 胶囊向上拉时，四个方向同步扩展：
        //   左/右: padding 从 sidePad → 0
        //   上:    Box 整体随 animOffset 上移（自然）
        //   下:    height 从 miniH → screenH（卡片底边也向下扩展到 botPad=0）
        // ══════════════════════════════════════════════════════════════════
        val bgXPx      = sidePx * (1f - p)
        val bgWidthPx  = screenWPx - 2f * sidePx * (1f - p)
        val bgHeightPx = miniHPx + (screenHPx - miniHPx) * p
        val bgCorner   = lerp(34.dp, 0.dp, p)
        val bgWidthDp  = with(density) { bgWidthPx.toDp() }
        val bgHeightDp = with(density) { bgHeightPx.toDp() }

        // ══════════════════════════════════════════════════════════════════
        // Alpha / offset 控制
        //
        // Mini 整体（封面+文字+按钮）：
        //   前 30% progress 内下沉 60dp + 淡出
        //   感觉像"整理好东西下去"，全屏随之出来
        //
        // FullPlayer：
        //   Box 本身随 animOffset 上移，等于全屏从下方涌出
        //   progress > 15% 后叠加 alpha 淡入，控件 > 50% 后淡入
        // ══════════════════════════════════════════════════════════════════
        val miniSinkFrac  = (p / 0.30f).coerceIn(0f, 1f)
        val miniAlpha     = 1f - miniSinkFrac
        val miniSinkPx    = with(density) { 60.dp.toPx() } * miniSinkFrac

        val fullAlpha     = ((p - 0.15f) / 0.25f).coerceIn(0f, 1f)
        val controlsAlpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)

        val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        val primary = MaterialTheme.colorScheme.primary
        val isDark  = isSystemInDarkTheme()

        // ══════════════════════════════════════════════════════════════════
        // 渲染树
        // 外层 Box 无 pointerInput → Mini 态对底层列表完全透明
        // ══════════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animOffset.value.roundToInt()) }
        ) {

            // ── Layer 1: 背景卡片（无阴影） ─────────────────────────────────
            Box(
                modifier = Modifier
                    .offset { IntOffset(bgXPx.roundToInt(), 0) }
                    .width(bgWidthDp)
                    .height(bgHeightDp)
                    .clip(RoundedCornerShape(bgCorner))
                    .background(surface)
            )

            // ── Layer 2: 主题色渐变叠层 ─────────────────────────────────────
            if (p > 0.01f) {
                val a1 = if (isDark) 0.65f else 0.14f
                val a2 = if (isDark) 0.22f else 0.05f
                Box(
                    modifier = Modifier
                        .offset { IntOffset(bgXPx.roundToInt(), 0) }
                        .width(bgWidthDp)
                        .height(bgHeightDp)
                        .clip(RoundedCornerShape(bgCorner))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    primary.copy(alpha = a1 * p),
                                    primary.copy(alpha = a2 * p),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // ── Layer 3: FullPlayer ─────────────────────────────────────────
            // Box 整体上移 = 全屏从下方涌出，无需额外 offset
            // alpha 在 progress > 0.15 后淡入
            if (fullAlpha > 0f) {
                FullPlayer(
                    viewModel       = viewModel,
                    onCollapse      = { goToSettle(dragRange) },
                    gestureModifier = fullGestureMod,
                    controlsAlpha   = controlsAlpha,
                    modifier        = Modifier.graphicsLayer { alpha = fullAlpha }
                )
            }

            // ── Layer 4: MiniPlayer（含封面，整体下沉淡出） ─────────────────
            // 胶囊在屏幕上的固定位置 = screenH - miniH - botPad
            // Box 本地 Y = 上述固定值 - animOffset.value（保持屏幕位置固定）
            // graphicsLayer translateY = miniSinkPx 向下下沉
            if (miniAlpha > 0f) {
                val miniCardY = (screenHPx - miniHPx - botPx - animOffset.value).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, miniCardY) }
                        .fillMaxWidth()
                        .padding(horizontal = MiniSidePad)
                        .graphicsLayer {
                            alpha        = miniAlpha
                            translationY = miniSinkPx
                        }
                ) {
                    MiniPlayer(
                        viewModel   = viewModel,
                        gestureMod  = miniGestureMod
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniPlayer — 胶囊播放条（含封面，整体作为一个单元上下运动）
//
// 封面直接放在 Row 内，progress > 0 时随 miniSinkPx + miniAlpha 整体下沉淡出。
// 不再有"飞升封面"层，消除割裂感。
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MiniPlayer(
    viewModel: MusicViewModel,
    gestureMod: Modifier = Modifier
) {
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerController.isPlaying.collectAsStateWithLifecycle()
    val s          = song ?: return
    val pillBg     = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)

    Row(
        modifier          = gestureMod
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            // 无 shadow（一镜到底，去掉叠层感）
            .clip(RoundedCornerShape(MiniPlayerHeight / 2))
            .background(pillBg)
            .padding(start = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面（与文字按钮一起整体下沉，无需分离）
        AsyncImage(
            model              = s.albumArtUri,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(13.dp))
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = s.title,
                style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = s.artist,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { viewModel.playerController.skipToPrevious() }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { viewModel.playerController.togglePlayPause() }, modifier = Modifier.size(46.dp)) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = { viewModel.playerController.skipToNext() }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.SkipNext, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FullPlayer — 全屏播放界面
//
// 不需要 pagerCoverAlpha 控制，因为飞升封面层已移除。
// Pager 封面直接显示，HorizontalPager 横划切歌正常工作。
// controlsAlpha: progress > 0.5 后渐入，过渡前半段保持清爽。
// gestureModifier（detectVerticalDragGestures）绑整个 Column；
//   HorizontalPager 水平轴正交，Compose 自动区分，互不干扰。
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FullPlayer(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit,
    gestureModifier: Modifier = Modifier,
    controlsAlpha: Float       = 1f,
    modifier: Modifier         = Modifier
) {
    val controller = viewModel.playerController
    val song       by controller.currentSong.collectAsStateWithLifecycle()
    val isPlaying  by controller.isPlaying.collectAsStateWithLifecycle()
    val repeatMode by controller.repeatMode.collectAsStateWithLifecycle()
    val scope      = rememberCoroutineScope()

    val controllerRef by rememberUpdatedState(controller)
    var currentMs   by remember { mutableLongStateOf(0L) }
    var durationMs  by remember { mutableLongStateOf(1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProg   by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isScrubbing) {
                currentMs  = controllerRef.getCurrentPosition()
                durationMs = controllerRef.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    val queueSize  = remember(song) { controller.getCurrentQueueSize().coerceAtLeast(1) }
    val initIdx    = remember(song) { controller.getCurrentIndex().coerceIn(0, queueSize - 1) }
    val pagerState = rememberPagerState(initialPage = initIdx, pageCount = { queueSize })

    LaunchedEffect(song?.id) {
        val target = controller.getCurrentIndex().coerceIn(0, pagerState.pageCount - 1)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.pageCount > 0) {
            val idx = pagerState.currentPage
            if (idx != controller.getCurrentIndex()) controller.playAtIndex(idx)
        }
    }

    var prevPage     by remember { mutableStateOf(pagerState.currentPage) }
    var goingForward by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        goingForward = pagerState.currentPage >= prevPage
        prevPage     = pagerState.currentPage
    }

    val displaySong = controller.getSongAt(pagerState.currentPage) ?: song
    val onBg        = MaterialTheme.colorScheme.onBackground

    Column(
        modifier            = modifier
            .then(gestureModifier)
            .fillMaxSize()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.Start
    ) {
        // 顶栏
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .graphicsLayer { alpha = controlsAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "收起",
                    Modifier.size(30.dp), tint = onBg.copy(alpha = 0.45f))
            }
            Text(
                text      = "N O W  P L A Y I N G",
                style     = MaterialTheme.typography.labelSmall.copy(
                    fontWeight    = FontWeight.Medium,
                    letterSpacing = 3.sp
                ),
                color     = onBg.copy(alpha = 0.30f),
                modifier  = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, "更多",
                    Modifier.size(22.dp), tint = onBg.copy(alpha = 0.45f))
            }
        }

        // 封面 Pager（直接显示，无额外 alpha 控制）
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = FullPagerPad),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageSong = controller.getSongAt(page) ?: song
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model              = pageSong?.albumArtUri,
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 标题
        AnimatedContent(
            targetState    = Pair(displaySong?.title ?: "", goingForward),
            label          = "title",
            transitionSpec = {
                val fwd = targetState.second
                (slideInHorizontally(tween(300)) { if (fwd) it else -it } +
                        fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { if (fwd) -it else it } +
                                fadeOut(tween(180)))
            }
        ) { (title, _) ->
            Text(
                text     = title,
                style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color    = onBg.copy(alpha = controlsAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(5.dp))

        // 歌手
        AnimatedContent(
            targetState    = Pair(displaySong?.artist ?: "", goingForward),
            label          = "artist",
            transitionSpec = {
                val fwd = targetState.second
                (slideInHorizontally(tween(320, delayMillis = 30)) { if (fwd) it else -it } +
                        fadeIn(tween(240, 30))) togetherWith
                        (slideOutHorizontally(tween(260)) { if (fwd) -it else it } +
                                fadeOut(tween(160)))
            }
        ) { (artist, _) ->
            Text(
                text     = artist,
                style    = MaterialTheme.typography.bodyLarge,
                color    = onBg.copy(alpha = 0.45f * controlsAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // 进度条
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = controlsAlpha }
        ) {
            val sliderProg = if (isScrubbing) scrubProg
            else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Slider(
                value                 = sliderProg,
                onValueChange         = { v ->
                    isScrubbing = true; scrubProg = v
                    currentMs = (v * durationMs).toLong()
                },
                onValueChangeFinished = {
                    controllerRef.seekTo((scrubProg * durationMs).toLong())
                    isScrubbing = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = SliderDefaults.colors(
                    thumbColor         = onBg,
                    activeTrackColor   = onBg,
                    inactiveTrackColor = onBg.copy(alpha = 0.12f)
                )
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(currentMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = onBg.copy(alpha = 0.35f))
                Text(formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = onBg.copy(alpha = 0.35f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 播控按钮
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .graphicsLayer { alpha = controlsAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.toggleRepeat() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        else                   -> Icons.Rounded.Repeat
                    },
                    null, Modifier.size(24.dp),
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                        MaterialTheme.colorScheme.primary
                    else onBg.copy(alpha = 0.25f)
                )
            }
            IconButton(
                onClick  = {
                    scope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp), tint = onBg)
            }
            FilledIconButton(
                onClick  = { controller.togglePlayPause() },
                modifier = Modifier.size(74.dp),
                shape    = CircleShape,
                colors   = IconButtonDefaults.filledIconButtonColors(containerColor = onBg)
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null, Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.background
                )
            }
            IconButton(
                onClick  = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)
                        )
                    }
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp), tint = onBg)
            }
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun formatMs(ms: Long): String {
    val t = ms / 1000
    return "%d:%02d".format(t / 60, t % 60)
}