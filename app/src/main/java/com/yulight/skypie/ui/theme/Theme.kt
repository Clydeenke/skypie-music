package com.yulight.skypie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalThemeIndex = compositionLocalOf { mutableIntStateOf(0) }

@Composable
fun skypieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("skypie_settings", 0) }

    val themeIndexState = remember { mutableIntStateOf(prefs.getInt("theme_color_index", 0)) }
    val themeIndex by themeIndexState
    // themeIndex 0=跟随系统, 1+=对应 themeColors[0], themeColors[1]...
    val selectedTheme = if (themeIndex > 0) themeColors[themeIndex - 1] else themeColors[0]

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeIndex == 0 -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = selectedTheme.dark, secondary = selectedTheme.darkSecondary,
            background = TgBgDark, surface = TgSurface,
            onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White,
        )
        else -> lightColorScheme(
            primary = selectedTheme.light, secondary = selectedTheme.lightSecondary,
            background = Color.White, surface = TgBgLight,
            onPrimary = Color.White, onBackground = Color.Black, onSurface = Color.Black,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalThemeIndex provides themeIndexState) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
