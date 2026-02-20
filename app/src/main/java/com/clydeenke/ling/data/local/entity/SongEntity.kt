package com.clydeenke.ling.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clydeenke.ling.domain.model.Song

// @Entity 告诉 Room：在手机里创建一个名叫 "songs" 的表格
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey             // 身份证号：这首歌在数据库里的唯一标识
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArtUri: String?,
    val size: Long,
    val dateAdded: Long
) {
    /**
     * 将“数据库里的歌”变成“界面上能用的歌”
     */
    fun toDomain(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        uri = uri,
        albumArtUri = albumArtUri,
        size = size,
        dateAdded = dateAdded
    )

    companion object {
        /**
         * 将“界面上新扫描到的歌”打包成“准备存入数据库的格式”
         */
        fun fromDomain(song: Song): SongEntity = SongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            uri = song.uri,
            albumArtUri = song.albumArtUri,
            size = song.size,
            dateAdded = song.dateAdded
        )
    }
}