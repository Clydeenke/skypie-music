package com.clydeenke.ling.ui.components

import android.os.Build
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.clydeenke.ling.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlayerAnchor { Mini, Full }

private object SharedKeys {
    const val CONTAINER  = "player_container"
    const val ALBUM_ART  = "player_album_art"
    const val SONG_INFO  = "player_song_info"
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun SharedPlayerContainer(
    viewModel : MusicViewModel,
    modifier  : Modifier = Modifier
) {
    val density   = LocalDensity.current
    val song      by viewModel.playerController.currentSong.collectAsStateWithLifecycle()
    val coroutine = rememberCoroutineScope()

    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // ✅ 新构造函数：snapAnimationSpec + decayAnimationSpec
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue        = PlayerAnchor.Mini,
            positionalThreshold = { total -> total * 0.4f },
            velocityThreshold   = { with(density) { 300.dp.toPx() } },
            snapAnimationSpec   = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness    = Spring.StiffnessMediumLow
            ),
            decayAnimationSpec  = exponentialDecay(frictionMultiplier = 1.5f)
        )
    }

    // ✅ updateAnchors 只传一个参数
    LaunchedEffect(containerHeightPx) {
        if (containerHeightPx > 0f) {
            draggableState.updateAnchors(
                DraggableAnchors {
                    PlayerAnchor.Mini at 0f
                    PlayerAnchor.Full at -containerHeightPx
                }
            )
        }
    }

    // ✅ 用 .offset 属性，不用 requireOffset()
    val progress by remember {
        derivedStateOf {
            if (containerHeightPx == 0f) return@derivedStateOf 0f
            val off = draggableState.offset
            if (off.isNaN()) return@derivedStateOf 0f
            (-off / containerHeightPx).coerceIn(0f, 1f)
        }
    }

    if (song == null) return

    SharedTransitionLayout(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
    ) {
        AnimatedContent(
            targetState  = draggableState.currentValue,
            transitionSpec = { fadeIn(tween(0)) togetherWith fadeOut(tween(0)) },
            label        = "playerState"
        ) { anchor ->
            when (anchor) {
                PlayerAnchor.Mini -> MiniPlayerShared(
                    viewModel               = viewModel,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    draggableState          = draggableState,
                    onExpand                = { coroutine.launch { draggableState.animateTo(PlayerAnchor.Full) } }
                )
                PlayerAnchor.Full -> FullPlayerShared(
                    viewModel               = viewModel,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    progress                = progress,
                    draggableState          = draggableState,
                    onCollapse              = { coroutine.launch { draggableState.animateTo(PlayerAnchor.Mini) } }
                )
            }
        }
    }
}

// ─── MiniPlayer ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
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
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ 参数名已改为 sharedContentState
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .sharedBounds(
                            sharedContentState      = rememberSharedContentState(SharedKeys.SONG_INFO),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform         = { _, _ -> tween(300) }
                        )
                ) {
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

                IconButton(
                    onClick  = { viewModel.playerController.skipToPrevious() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, null,
                        modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(
                    onClick  = { viewModel.playerController.togglePlayPause() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier           = Modifier.size(28.dp),
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick  = { viewModel.playerController.skipToNext() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, null,
                        modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ─── FullPlayer ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
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

    var currentMs    by remember { mutableLongStateOf(0L) }
    var durationMs   by remember { mutableLongStateOf(1L) }
    var isDragging   by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                currentMs  = viewModel.playerController.getCurrentPosition()
                durationMs = viewModel.playerController.getDuration().coerceAtLeast(1L)
            }
            delay(500)
        }
    }

    val displayProgress = if (isDragging) dragProgress
    else (currentMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val albumScale by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0.88f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label         = "scale"
    )

    val blurAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .fillMaxSize()
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
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

            if (blurAlpha > 0f && song?.albumArtUri != null) {
                BlurredBackground(
                    imageUrl = song!!.albumArtUri!!,
                    alpha    = blurAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null,
                            modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "正在播放",
                        style     = MaterialTheme.typography.titleLarge,
                        color     = MaterialTheme.colorScheme.onBackground,
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // ✅ sharedElement 参数名 sharedContentState + graphicsLayer
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

                Spacer(Modifier.height(28.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedBounds(
                            sharedContentState      = rememberSharedContentState(SharedKeys.SONG_INFO),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform         = { _, _ -> tween(300) }
                        )
                ) {
                    Text(song?.title ?: "",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(song?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(20.dp))

                Slider(
                    value                 = displayProgress,
                    onValueChange         = { v -> isDragging = true; dragProgress = v; currentMs = (v * durationMs).toLong() },
                    onValueChangeFinished = { viewModel.playerController.seekTo((dragProgress * durationMs).toLong()); isDragging = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentMs),  style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.playerController.toggleShuffle() }) {
                        Icon(Icons.Rounded.Shuffle, null,
                            tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp))
                    }
                    IconButton(onClick = { viewModel.playerController.skipToPrevious() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(32.dp))
                    }
                    FilledIconButton(
                        onClick  = { viewModel.playerController.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.playerController.skipToNext() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = { viewModel.playerController.toggleRepeat() }) {
                        Icon(
                            when (repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne; else -> Icons.Rounded.Repeat },
                            null,
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── 高斯模糊背景 ──────────────────────────────────────────────────────────────
@Composable
private fun BlurredBackground(imageUrl: String, alpha: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {
        Image(
            painter            = rememberAsyncImagePainter(imageUrl),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(80f, 80f, TileMode.Mirror)
                    }
                }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}