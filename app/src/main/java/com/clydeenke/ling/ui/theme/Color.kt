package com.clydeenke.ling.ui.theme

import androidx.compose.ui.graphics.Color

// 聆 - 品牌基础色，即使不支持动态取色也能用这套
val 主色调紫= Color(0xFF7C5CBF)
val 主色调紫浅 = Color(0xFF9C7FD4)
val 背景深色 = Color(0xFF0D0D14)
val 背景次深 = Color(0xFF16161F)
val 玻璃白 = Color(0x1AFFFFFF) // 15% 透明白，玻璃底色
val 玻璃描边 = Color(0x33FFFFFF) // 20% 透明白，玻璃边框

// 渐变色组合，用于播放器背景
val 渐变色组合 = listOf(
    listOf(Color(0xFF1A0533), Color(0xFF0D1F4A)),
    listOf(Color(0xFF1F0A0A), Color(0xFF0A1F1F)),
    listOf(Color(0xFF0A1F0A), Color(0xFF1A1A0A)),
    listOf(Color(0xFF0A0A1F), Color(0xFF1F0A1A)),
)