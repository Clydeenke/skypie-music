package com.clydeenke.ling.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clydeenke.ling.data.repository.MusicRepository
import com.clydeenke.ling.domain.model.ScanFolder
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val PREFS_PLAYER  = "ling_player"
private const val KEY_LAST_SONG = "last_song_id"
private const val KEY_LAST_POS  = "last_position_ms"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    application            : Application,
    private val repository : MusicRepository,
    val playerController   : PlayerController
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_PLAYER, 0)
    private val app   = application

    // ── 打开全屏播放器事件 ────────────────────────────────────────────────────
    private val _openPlayerEvent = MutableStateFlow(false)
    val openPlayerEvent: StateFlow<Boolean> = _openPlayerEvent.asStateFlow()
    fun requestOpenPlayer() { _openPlayerEvent.value = true  }
    fun consumeOpenPlayer() { _openPlayerEvent.value = false }

    // ── 搜索状态 ──────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 歌曲列表 ──────────────────────────────────────────────────────────────
    val songs: StateFlow<List<Song>> = _searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) repository.getAllSongs()
            else             repository.searchSongs(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 文件夹列表 ────────────────────────────────────────────────────────────
    val folders: StateFlow<List<ScanFolder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 扫描状态 ──────────────────────────────────────────────────────────────
    val isScanning : StateFlow<Boolean>       = repository.isScanning
    val scanLogs   : StateFlow<List<ScanLog>> = repository.scanLogs

    // ── 无效文件列表（供 UI 展示确认） ────────────────────────────────────────
    private val _invalidFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val invalidFiles: StateFlow<List<java.io.File>> = _invalidFiles.asStateFlow()

    private val _isCheckingFiles = MutableStateFlow(false)
    val isCheckingFiles: StateFlow<Boolean> = _isCheckingFiles.asStateFlow()

    // ── 启动时恢复上次播放 ────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            songs.first { it.isNotEmpty() }.let { list ->
                val lastId  = prefs.getLong(KEY_LAST_SONG, -1L)
                val lastPos = prefs.getLong(KEY_LAST_POS,   0L)
                if (lastId == -1L) return@let
                val idx = list.indexOfFirst { it.id == lastId }
                if (idx == -1) return@let
                playerController.restoreQueue(list, idx, lastPos)
            }
        }
    }

    // ── 基础操作 ──────────────────────────────────────────────────────────────
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun refresh() { viewModelScope.launch { repository.syncMusic() } }
    fun addFolder(uriString: String, displayPath: String) {
        viewModelScope.launch { repository.addFolder(uriString, displayPath) }
    }
    fun removeFolder(folder: ScanFolder) {
        viewModelScope.launch { repository.removeFolder(folder) }
    }
    fun setFolderEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch { repository.setFolderEnabled(id, enabled) }
    }

    // ── 播放控制 ──────────────────────────────────────────────────────────────
    fun playSong(songs: List<Song>, index: Int) {
        playerController.playQueue(songs, index)
        prefs.edit()
            .putLong(KEY_LAST_SONG, songs[index].id)
            .putLong(KEY_LAST_POS, 0L)
            .apply()
    }

    fun savePlaybackProgress() {
        val song = playerController.currentSong.value ?: return
        val pos  = playerController.getCurrentPosition()
        prefs.edit()
            .putLong(KEY_LAST_SONG, song.id)
            .putLong(KEY_LAST_POS, pos)
            .apply()
    }

    // ── 删除歌曲（从数据库 + 删文件 + 同名封面/歌词） ────────────────────────
    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 删音频文件
                val audioFile = java.io.File(song.filePath)
                if (audioFile.exists()) audioFile.delete()

                // 删同名 .jpg 封面
                val jpg = java.io.File(song.filePath.substringBeforeLast(".") + ".jpg")
                if (jpg.exists()) jpg.delete()

                // 删同名 .lrc 歌词
                val lrc = java.io.File(song.filePath.substringBeforeLast(".") + ".lrc")
                if (lrc.exists()) lrc.delete()

                // 通知 MediaStore 更新
                android.media.MediaScannerConnection.scanFile(
                    app, arrayOf(song.filePath), null, null
                )

                // 从数据库删除
                repository.deleteSong(song)
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "删除失败: ${e.message}")
            }
        }
    }

    // ── 扫描无效文件（播放时长为0或文件损坏） ────────────────────────────────
    fun scanInvalidFiles() {
        viewModelScope.launch {
            _isCheckingFiles.value = true
            val invalid = withContext(Dispatchers.IO) {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val audioExts = setOf("mp3", "flac", "aac", "ogg", "m4a", "wav")
                val result = mutableListOf<java.io.File>()
                musicDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in audioExts }
                    .forEach { file ->
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(file.absolutePath)
                            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull() ?: 0L
                            if (dur < 1000) result.add(file) // 时长不足1秒判定为无效
                        } catch (_: Exception) {
                            result.add(file) // 读取失败也是无效
                        } finally {
                            mmr.release()
                        }
                    }
                result
            }
            _invalidFiles.value = invalid
            _isCheckingFiles.value = false
        }
    }

    // ── 删除所有无效文件 ──────────────────────────────────────────────────────
    fun deleteInvalidFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _invalidFiles.value.forEach { file ->
                file.delete()
                // 删同名封面和歌词
                java.io.File(file.absolutePath.substringBeforeLast(".") + ".jpg").delete()
                java.io.File(file.absolutePath.substringBeforeLast(".") + ".lrc").delete()
                android.media.MediaScannerConnection.scanFile(
                    app, arrayOf(file.absolutePath), null, null
                )
            }
            _invalidFiles.value = emptyList()
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    // ── 清理 App 私有目录旧文件（之前版本遗留的） ────────────────────────────
    fun cleanOldPrivateFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                java.io.File(app.filesDir, "covers").deleteRecursively()
                java.io.File(app.filesDir, "lyrics").deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}