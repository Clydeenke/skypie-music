package com.yulight.skypie.ui.overlay

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yulight.skypie.domain.lyrics.DesktopLyricsPrefs
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

@Composable
fun DesktopLyricsOverlay(
    text             : String,
    hasLyrics        : Boolean,
    isLocked         : Boolean,
    showControlPanel : Boolean,
    showPanelAbove   : Boolean,   // Service 根据 windowY 决定浮层方向
    fontSize         : Float,
    colorArgb        : Int,
    bgAlpha          : Float,
    bgEnabled        : Boolean,
    onLock           : () -> Unit,
    onTogglePanel    : () -> Unit,
    onDrag           : (dx: Float, dy: Float) -> Unit,
    onFontSizeChange : (Float) -> Unit,
    onColorChange    : (Int) -> Unit,
) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        dynamicDarkColorScheme(LocalContext.current)
    else darkColorScheme()

    val textColor = if (colorArgb == 0) colorScheme.primary else Color(colorArgb)

    MaterialTheme(colorScheme = colorScheme) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // 浮层出现在上方时，先渲染浮层（Column 里靠上）
            if (showPanelAbove) {
                ControlPanel(
                    visible          = !isLocked && showControlPanel,
                    expandFrom       = ExpandFrom.Bottom,   // 从下往上展开（贴近歌词）
                    colorScheme      = colorScheme,
                    fontSize         = fontSize,
                    colorArgb        = colorArgb,
                    onLock           = onLock,
                    onFontSizeChange = onFontSizeChange,
                    onColorChange    = onColorChange,
                )
            }

            // 歌词条（始终在这里，位置不受浮层影响）
            AnimatedVisibility(
                visible = hasLyrics && text.isNotEmpty(),
                enter   = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it },
                exit    = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            ) {
                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 3 })
                            .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 })
                    },
                    label    = "lyric_line",
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (bgEnabled) Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colorScheme.surfaceVariant.copy(alpha = bgAlpha))
                            else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .pointerInput(isLocked) {
                            if (isLocked) return@pointerInput
                            // ── 区分 tap 和拖动 ──────────────────────────────
                            // 原理：记录 DOWN 时的位置，UP 时如果位移 < touchSlop → tap
                            //       MOVE 时如果位移 > touchSlop → 拖动，消费事件
                            awaitEachGesture {
                                val down     = awaitFirstDown()
                                val startPos = down.position
                                var isDrag   = false

                                while (true) {
                                    val event  = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (!change.pressed) {
                                        // 手指抬起
                                        if (!isDrag) onTogglePanel()
                                        break
                                    }

                                    // 用当前位置和 DOWN 位置对比判断是否拖动
                                    val dx = change.position.x - startPos.x
                                    val dy = change.position.y - startPos.y
                                    if (!isDrag && (abs(dx) > viewConfiguration.touchSlop ||
                                                abs(dy) > viewConfiguration.touchSlop)) {
                                        isDrag = true
                                    }

                                    if (isDrag) {
                                        // positionChange() = 相对上一个事件的增量，用于 WindowManager 坐标更新
                                        val delta = change.positionChange()
                                        onDrag(delta.x, delta.y)
                                        change.consume()
                                    }
                                }
                            }
                        }
                ) { line ->
                    Text(
                        text       = line,
                        color      = textColor,
                        fontSize   = fontSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        textAlign  = TextAlign.Center,
                        style      = LocalTextStyle.current.copy(
                            shadow = Shadow(
                                color      = Color.Black.copy(alpha = 0.55f),
                                offset     = Offset(0f, 2f),
                                blurRadius = 10f
                            )
                        )
                    )
                }
            }

            // 浮层出现在下方时，后渲染（Column 里靠下）
            if (!showPanelAbove) {
                ControlPanel(
                    visible          = !isLocked && showControlPanel,
                    expandFrom       = ExpandFrom.Top,   // 从上往下展开（贴近歌词）
                    colorScheme      = colorScheme,
                    fontSize         = fontSize,
                    colorArgb        = colorArgb,
                    onLock           = onLock,
                    onFontSizeChange = onFontSizeChange,
                    onColorChange    = onColorChange,
                )
            }
        }
    }
}

private enum class ExpandFrom { Top, Bottom }

@Composable
private fun ControlPanel(
    visible          : Boolean,
    expandFrom       : ExpandFrom,
    colorScheme      : ColorScheme,
    fontSize         : Float,
    colorArgb        : Int,
    onLock           : () -> Unit,
    onFontSizeChange : (Float) -> Unit,
    onColorChange    : (Int) -> Unit,
) {
    val enter = fadeIn(tween(180)) + expandVertically(
        tween(180),
        expandFrom = if (expandFrom == ExpandFrom.Top) Alignment.Top else Alignment.Bottom
    )
    val exit = fadeOut(tween(130)) + shrinkVertically(
        tween(130),
        shrinkTowards = if (expandFrom == ExpandFrom.Top) Alignment.Top else Alignment.Bottom
    )

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            // 字号 -
            IconButton(
                onClick  = { onFontSizeChange((fontSize - 1f).coerceIn(12f, 24f)) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Rounded.Remove, null,
                    tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }

            Text("${fontSize.toInt()}", color = colorScheme.onSurfaceVariant,
                fontSize = 12.sp, modifier = Modifier.widthIn(min = 22.dp),
                textAlign = TextAlign.Center)

            // 字号 +
            IconButton(
                onClick  = { onFontSizeChange((fontSize + 1f).coerceIn(12f, 24f)) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Rounded.Add, null,
                    tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }

            Spacer(Modifier.width(3.dp))
            Box(Modifier.width(0.5.dp).height(14.dp)
                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            Spacer(Modifier.width(3.dp))

            // 颜色色块（pointerInput 只在歌词文字上，这里 clickable 正常工作）
            DesktopLyricsPrefs.PRESET_COLORS.forEach { argb ->
                val isSelected   = argb == colorArgb
                val displayColor = if (argb == 0) colorScheme.primary else Color(argb)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isSelected) 17.dp else 13.dp)
                        .clip(RoundedCornerShape(50))
                        .background(displayColor)
                        .clickable { onColorChange(argb) }
                )
            }

            Spacer(Modifier.width(3.dp))
            Box(Modifier.width(0.5.dp).height(14.dp)
                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            Spacer(Modifier.width(3.dp))

            // 锁定按钮（点了要去设置才能解锁）
            IconButton(onClick = onLock, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Lock, "锁定",
                    tint = colorScheme.error.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp))
            }
        }
    }
}