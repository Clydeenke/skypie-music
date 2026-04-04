package com.clydeenke.ling.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 歌单-歌曲 多对多关联表
 *
 * 注意：不对 SongEntity 添加外键约束，因为歌曲可能被外部删除（如直接删文件），
 * 避免 Room 因外键约束报错。孤立引用在 PlaylistRepository 中过滤处理。
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity     = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns  = ["playlistId"],
            onDelete   = ForeignKey.CASCADE   // 删歌单时联动删除所有关联记录
        )
    ],
    indices = [Index("songId"), Index("playlistId")]
)
data class PlaylistSongCrossRef(
    val playlistId : Long,
    val songId     : Long,
    val addedAt    : Long = System.currentTimeMillis()
)