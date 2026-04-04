package com.clydeenke.ling.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌单表：存储用户创建的歌单基础信息
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)