package com.yulight.skypie.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Room 关联查询结果：歌单 + 其包含的所有歌曲 Entity
 * 使用 @Transaction + Junction 实现多对多关联，由 Room 自动处理 JOIN
 */
data class PlaylistWithSongEntities(
    @Embedded
    val playlist: PlaylistEntity,

    @Relation(
        parentColumn = "id",        // PlaylistEntity.id
        entityColumn = "id",        // SongEntity.id（目标实体主键）
        associateBy  = Junction(
            value        = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>     // Room 通过 Junction 自动填充
)