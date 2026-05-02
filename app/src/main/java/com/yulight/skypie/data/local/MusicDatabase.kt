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
        PlaylistEntity::class,        // 歌单表
        PlaylistSongCrossRef::class   // 歌单-歌曲关联表
    ],
<<<<<<< HEAD
    version      = 3,
=======
    version      = 3,          // 版本 +1，AppModule 已配置 fallbackToDestructiveMigration
>>>>>>> origin/master
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao()       : SongDao
    abstract fun scanFolderDao() : ScanFolderDao
<<<<<<< HEAD
    abstract fun playlistDao()   : PlaylistDao  
=======
    abstract fun playlistDao()   : PlaylistDao   // 新增
>>>>>>> origin/master
}