package com.yulight.skypie.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌基准色（TG 蓝风格）
val YunBlue80      = Color(0xFFADC6FF)
val YunBlueGrey80  = Color(0xFFBEC6DC)
val YunBlue40      = Color(0xFF1A73E8) // 经典 TG 蓝
val YunBlueGrey40  = Color(0xFF4A5568)

// 氛围控制层
val GlassWhite     = Color(0x33FFFFFF)
val GlassBlack     = Color(0x66000000)

// TG 风格背景色
val TgBgLight      = Color(0xFFF1F3F4)
val TgBgDark       = Color(0xFF1C2433) // 深航海蓝
val TgSurface      = Color(0xFF212D3B)

// ── 主题色板 ──────────────────────────────────────────────────────────────────
data class ThemeColor(
    val name: String,
    val light: Color,
    val dark: Color,
    val lightSecondary: Color,
    val darkSecondary: Color
)

val themeColors = listOf(
    ThemeColor("蓝紫", Color(0xFF6750A4), Color(0xFFD0BCFF), Color(0xFF625B71), Color(0xFFCCC2DC)),
    ThemeColor("蓝色", Color(0xFF0061A4), Color(0xFFD0BCFF), Color(0xFF535F70), Color(0xFFB8C9E8)),
    ThemeColor("青色", Color(0xFF006A6A), Color(0xFFA0CFCF), Color(0xFF4A6363), Color(0xFFB0CDCD)),
    ThemeColor("绿色", Color(0xFF386A20), Color(0xFFA4D579), Color(0xFF55624C), Color(0xFFB8CCA5)),
    ThemeColor("黄色", Color(0xFF7D5700), Color(0xFFFFDEA1), Color(0xFF6D5E3F), Color(0xFFEDC673)),
    ThemeColor("橙色", Color(0xFF8B5000), Color(0xFFFFB68C), Color(0xFF7A5C3F), Color(0xFFFFDBB8)),
    ThemeColor("红色", Color(0xFFBA1A1A), Color(0xFFFFB4AB), Color(0xFF93000A), Color(0xFFFFB4AB)),
    ThemeColor("粉色", Color(0xFF984061), Color(0xFFFFB1C8), Color(0xFF7D5260), Color(0xFFFFB1C8)),
)