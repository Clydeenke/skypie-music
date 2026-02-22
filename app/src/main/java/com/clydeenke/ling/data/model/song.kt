package com.clydeenke.ling.domain.model

data class Song(
    val id          : Long,
    val title       : String,
    val artist      : String,
    val album       : String,
    val duration    : Long,     // 毫秒
    val uri         : String,
    val albumArtUri : String?,
    val size        : Long,
    val dateAdded   : Long,
    val folderPath  : String    // 记录来自哪个文件夹
)