package com.clydeenke.ling.domain.model

/**
 * 歌曲数据模型
 * 作用：这是在整个 App 界面（UI）层流转的歌曲信息
 */
data class Song(
    val id: Long,             // 数据库 ID 或系统媒体 ID
    val title: String,        // 歌名
    val artist: String,       // 歌手
    val album: String,        // 专辑
    val duration: Long,       // 时长（毫秒）
    val uri: String,          // 歌曲文件的实际路径
    val albumArtUri: String?, // 专辑封面的图片路径（可能没有，所以带问号）
    val size: Long,           // 文件大小（用来过滤掉太小的音频）
    val dateAdded: Long       // 扫描到的时间
)