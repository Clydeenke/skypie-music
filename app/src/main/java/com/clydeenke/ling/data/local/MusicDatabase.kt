package com.clydeenke.ling.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.clydeenke.ling.data.local.dao.PlaylistDao
import com.clydeenke.ling.data.local.dao.ScanFolderDao
import com.clydeenke.ling.data.local.dao.SongDao
import com.clydeenke.ling.data.local.entity.PlaylistEntity
import com.clydeenke.ling.data.local.entity.PlaylistSongCrossRef
import com.clydeenke.ling.data.local.entity.ScanFolderEntity
import com.clydeenke.ling.data.local.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        ScanFolderEntity::class,
        PlaylistEntity::class,        // 歌单表
        PlaylistSongCrossRef::class   // 歌单-歌曲关联表
    ],
    version      = 3,          // 版本 +1，AppModule 已配置 fallbackToDestructiveMigration
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao()       : SongDao
    abstract fun scanFolderDao() : ScanFolderDao
    abstract fun playlistDao()   : PlaylistDao   // 新增
}