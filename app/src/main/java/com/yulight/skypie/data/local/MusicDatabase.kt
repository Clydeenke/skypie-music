package com.yulight.skypie.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yulight.skypie.data.local.dao.PlaylistDao
import com.yulight.skypie.data.local.dao.ScanFolderDao
import com.yulight.skypie.data.local.dao.SongDao
import com.yulight.skypie.data.local.entity.PlaylistEntity
import com.yulight.skypie.data.local.entity.PlaylistSongCrossRef
import com.yulight.skypie.data.local.entity.ScanFolderEntity
import com.yulight.skypie.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        ScanFolderEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun scanFolderDao(): ScanFolderDao
    abstract fun playlistDao(): PlaylistDao
}