package com.clydeenke.ling.ui

/**
 * 界面路由定义
 * 作用：统一管理所有页面的“名字”，防止代码里出现乱七八糟的字符串
 */
object Screen {
    const val LIBRARY = "library"   // 歌曲列表页
    const val ALBUMS = "albums"     // 专辑分类页
    const val ARTISTS = "artists"   // 歌手分类页
    const val SETTINGS = "settings" // 设置页
    const val PLAYER = "player"     // 全屏播放页
}