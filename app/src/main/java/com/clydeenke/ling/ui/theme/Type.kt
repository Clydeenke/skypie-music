package com.clydeenke.ling.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 定义全局字体样式
val Typography = Typography(
    // 用于：播放页的大歌名
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,    // 加粗
        fontSize = 28.sp,               // 大字体
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp       // 稍微收紧字间距，更有高级感
    ),

    // 用于：歌手名、列表主要文字
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // 用于：播放时间（03:20）、小提示文字
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)