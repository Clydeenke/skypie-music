package com.yulight.skypie.domain.model

/**
 * 歌单领域模型，同时用于列表页（songs.size 作歌曲数）和详情页
 */
data class Playlist(
    val id       : Long,
    val name     : String,
    val createdAt: Long,
    val songs    : List<Song>   // 空列表表示歌单为空
)