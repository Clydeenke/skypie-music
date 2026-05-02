package com.yulight.skypie.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val AppleEasing  = CubicBezierEasing(0.32f, 0.94f, 0.60f, 1.0f)
internal val SettleEasing = CubicBezierEasing(0.2f,  0.0f,  0f,    1.0f)
internal val TapSpring    = spring<Float>(stiffness = 320f, dampingRatio = Spring.DampingRatioNoBouncy)

internal val MiniPlayerHeight = 68.dp
internal val MiniBottomPad    = 24.dp
internal val MiniSidePad      = 14.dp
internal val FullPagerPad     = 4.dp

internal data class AlbumPalette(
    val color1: Color = Color.Transparent,
    val color2: Color = Color.Transparent,
    val color3: Color = Color.Transparent
)