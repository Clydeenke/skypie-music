package com.clydeenke.ling.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 自定义深色配色方案（不支持动态取色时的回退方案）
private val 深色配色方案 = darkColorScheme(
    primary = 主色调紫浅,
    onPrimary = Color(0xFF1A0040),
    primaryContainer = Color(0xFF3D007A),
    background = 背景深色,
    surface = 背景次深,
    surfaceVariant = Color(0xFF1E1E2A),
    onBackground = Color(0xFFE8E0FF),
    onSurface = Color(0xFFE8E0FF),
)

@Composable
fun 聆主题(
    深色模式: Boolean = isSystemInDarkTheme(),
    // 动态取色，Android 12 (API 31) 以上支持
    动态取色: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 支持动态取色且用户开启了的话，优先用系统壁纸的颜色
        动态取色 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (深色模式) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 不支持动态取色时，用我们自定义的深色方案
        深色模式 -> 深色配色方案
        else -> 深色配色方案 // 这个App设计以深色为主
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏透明，让内容延伸到状态栏后面，高级感的关键
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !深色模式
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = 字体排版,
        content = content
    )
}