package com.yulight.skypie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    previousContent: @Composable () -> Unit,
    currentContent: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    var offsetX by remember { mutableFloatStateOf(0f) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current

    val maxDragPx = with(density) { screenWidth.toPx() }

    val progress = (offsetX / maxDragPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 上一个页面（在底层）
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    val parallax = (1f - progress) * -40f
                    translationX = parallax
                    scaleX = 0.95f + progress * 0.05f
                    scaleY = 0.95f + progress * 0.05f
                }
                .zIndex(0f)
        ) {
            previousContent()
        }

        // 当前页面（手势控制）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .graphicsLayer {
                    val scale = 1f - progress * 0.05f
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = progress * 20f
                }
                .zIndex(1f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            val threshold = maxDragPx * 0.3f

                            if (offsetX > threshold) {
                                // 执行返回
                                scope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = maxDragPx,
                                        animationSpec = tween(250)
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                    onBack()
                                }
                            } else {
                                // 回弹
                                scope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            currentContent()
        }
    }
}
