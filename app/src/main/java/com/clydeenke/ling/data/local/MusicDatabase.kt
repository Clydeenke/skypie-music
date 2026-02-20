package com.clydeenke.ling.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.clydeenke.ling.data.local.dao.SongDao
import com.clydeenke.ling.data.local.entity.SongEntity

/**
 * 数据库房间 (Room)
 * 作用：这是数据库的本体，负责连接表 (Entity) 和操作接口 (Dao)
 */
@Database(
    entities = [SongEntity::class], // 告诉数据库这里面有哪些表
    version = 1,                    // 版本号，如果以后你增加了新功能改了表，这个数字要加 1
    exportSchema = false            // 不导出数据库结构文件 (个人项目通常设为 false)
)
abstract class MusicDatabase : RoomDatabase() {

    // 暴露出我们的管理员，让外面的人能通过它来增删改查
    abstract fun songDao(): SongDao
}