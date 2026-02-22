package com.clydeenke.ling.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.clydeenke.ling.data.local.dao.ScanFolderDao
import com.clydeenke.ling.data.local.dao.SongDao
import com.clydeenke.ling.data.local.entity.ScanFolderEntity
import com.clydeenke.ling.data.local.entity.SongEntity

@Database(
    entities = [SongEntity::class, ScanFolderEntity::class],
    version  = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun scanFolderDao(): ScanFolderDao
}