package com.clydeenke.ling.data.repository

import androidx.room.withTransaction
import com.clydeenke.ling.data.local.MediaScanner
import com.clydeenke.ling.data.local.MusicDatabase
import com.clydeenke.ling.data.local.entity.ScanFolderEntity
import com.clydeenke.ling.data.local.entity.SongEntity
import com.clydeenke.ling.domain.model.ScanFolder
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val database     : MusicDatabase,
    private val mediaScanner : MediaScanner
) {
    private val songDao       = database.songDao()
    private val scanFolderDao = database.scanFolderDao()

    private val _scanLogs   = MutableStateFlow<List<ScanLog>>(emptyList())
    val scanLogs: StateFlow<List<ScanLog>> = _scanLogs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun getAllSongs(): Flow<List<Song>> =
        songDao.getAllSongs().map { it.map { e -> e.toDomain() } }

    fun searchSongs(query: String): Flow<List<Song>> =
        songDao.searchSongs(query).map { it.map { e -> e.toDomain() } }

    fun getAllFolders(): Flow<List<ScanFolder>> =
        scanFolderDao.getAllFolders().map { it.map { e -> e.toDomain() } }

    suspend fun addFolder(uriString: String, displayPath: String) = withContext(Dispatchers.IO) {
        scanFolderDao.insert(ScanFolderEntity(uriString = uriString, displayPath = displayPath))
    }

    suspend fun removeFolder(folder: ScanFolder) = withContext(Dispatchers.IO) {
        scanFolderDao.delete(ScanFolderEntity(folder.id, folder.uriString, folder.displayPath, folder.songCount, folder.isEnabled))
    }

    suspend fun setFolderEnabled(id: Int, enabled: Boolean) = withContext(Dispatchers.IO) {
        scanFolderDao.setEnabled(id, enabled)
    }

    // ✅ 从数据库删除单首歌曲
    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.deleteById(song.id)
    }

    suspend fun syncMusic() = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true
        _scanLogs.value   = emptyList()

        val log: (ScanLog) -> Unit = { entry ->
            _scanLogs.update { it + entry }
        }

        try {
            val enabledFolders = scanFolderDao.getEnabledFolders()
            val folderUris     = enabledFolders.map { it.uriString }
            log(ScanLog(message = "已启用文件夹：${enabledFolders.size} 个"))
            val songs = mediaScanner.scan(folderUris, log)

            database.withTransaction {
                songDao.clearAll()
                songDao.insertAll(songs.map { SongEntity.fromDomain(it) })
                enabledFolders.forEach { folder ->
                    val count = songs.count { it.folderPath.startsWith(folder.displayPath) }
                    scanFolderDao.updateSongCount(folder.id, count)
                }
            }
            log(ScanLog(level = ScanLog.Level.SUCCESS, message = "同步完成，共 ${songs.size} 首"))
        } catch (e: Exception) {
            log(ScanLog(level = ScanLog.Level.ERROR, message = "同步失败：${e.localizedMessage}"))
        } finally {
            _isScanning.value = false
        }
    }
}