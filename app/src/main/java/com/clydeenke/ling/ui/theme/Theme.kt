package com.clydeenke.ling.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary      = YunBlue80,
    secondary    = YunBlueGrey80,
    background   = TgBgDark,
    surface      = TgSurface,
    onPrimary    = Color.Black,
    onBackground = Color.White,
    onSurface    = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary      = YunBlue40,
    secondary    = YunBlueGrey40,
    background   = Color.White,
    surface      = TgBgLight,
    onPrimary    = Color.White,
    onBackground = Color.Black,
    onSurface    = Color.Black,
)

@Composable
fun LingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 动态取色逻辑：Android 12+ 会根据壁纸自动调整颜色
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Android 16 强制 edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}