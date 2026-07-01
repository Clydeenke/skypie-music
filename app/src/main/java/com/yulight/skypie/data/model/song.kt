package com.yulight.skypie.domain.model

data class Song(
    val id          : Long,
    val title       : String,
    val artist      : String,
    val album       : String,
    val duration    : Long,
    val uri         : String,
    val albumArtUri : String?,
    val size        : Long,
    val dateAdded   : Long,
    val folderPath  : String,
    val filePath    : String = ""
) {
    override fun toString(): String = "$title - $artist"
}