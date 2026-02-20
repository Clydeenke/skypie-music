package com.clydeenke.ling.data.repository

import com.clydeenke.ling.data.local.MediaScanner
import com.clydeenke.ling.data.local.MusicDatabase
import com.clydeenke.ling.data.local.entity.SongEntity
import com.clydeenke.ling.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐数据仓库
 * 作用：协调媒体扫描器和数据库，是全 App 唯一的数据出口
 */
@Singleton
class MusicRepository @Inject constructor(
    private val database: MusicDatabase,
    private val mediaScanner: MediaScanner
) {
    private val songDao = database.songDao()

    // 1. 获取所有歌曲：把数据库里的“实体”转为界面用的“模型”
    fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { list ->
            list.map { it.toDomain() }
        }
    }

    // 2. 搜索歌曲：同样带实时刷新功能
    fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query).map { list ->
            list.map { it.toDomain() }
        }
    }

    // 3. 同步音乐：先扫描，再清空旧数据，最后存入新数据
    // withContext(Dispatchers.IO) 确保这套动作在后台运行，不卡手机界面
    suspend fun syncMusic() = withContext(Dispatchers.IO) {
        val scannedSongs = mediaScanner.scanMusic()
        songDao.clearAll() // 重新扫描前清理旧账
        songDao.insertAll(scannedSongs.map { SongEntity.fromDomain(it) })
    }
}