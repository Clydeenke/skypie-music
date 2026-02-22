package com.clydeenke.ling.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.clydeenke.ling.domain.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id      : Long,
    val title       : String,
    val artist      : String,
    val album       : String,
    val duration    : Long,
    val uri         : String,
    val albumArtUri : String?,
    val size        : Long,
    val dateAdded   : Long,
    val folderPath  : String = ""
) {
    fun toDomain() = Song(id, title, artist, album, duration, uri, albumArtUri, size, dateAdded, folderPath)

    companion object {
        fun fromDomain(s: Song) = SongEntity(
            s.id, s.title, s.artist, s.album, s.duration,
            s.uri, s.albumArtUri, s.size, s.dateAdded, s.folderPath
        )
    }
}