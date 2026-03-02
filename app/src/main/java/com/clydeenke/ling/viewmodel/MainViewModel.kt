package com.clydeenke.ling.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clydeenke.ling.data.repository.MusicRepository
import com.clydeenke.ling.domain.model.ScanFolder
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREFS_PLAYER   = "ling_player"
private const val KEY_LAST_SONG  = "last_song_id"
private const val KEY_LAST_POS   = "last_position_ms"

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    application              : Application,
    private val repository   : MusicRepository,
    val playerController     : PlayerController
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_PLAYER, 0)

    // ── 打开全屏播放器事件（单次触发） ────────────────────────────────────────
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
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    // ── 文件夹列表 ────────────────────────────────────────────────────────────
    val folders: StateFlow<List<ScanFolder>> = repository.getAllFolders()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── 扫描状态 ──────────────────────────────────────────────────────────────
    val isScanning : StateFlow<Boolean>       = repository.isScanning
    val scanLogs   : StateFlow<List<ScanLog>> = repository.scanLogs

    // ── 启动时恢复上次播放 ────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            // 等歌曲列表第一次加载完成
            songs.first { it.isNotEmpty() }.let { list ->
                val lastId  = prefs.getLong(KEY_LAST_SONG, -1L)
                val lastPos = prefs.getLong(KEY_LAST_POS,   0L)
                if (lastId == -1L) return@let
                val idx = list.indexOfFirst { it.id == lastId }
                if (idx == -1) return@let
                // 恢复队列但不自动播放（暂停状态，用户点播放才开始）
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
        // 记录这首歌，下次启动恢复
        prefs.edit()
            .putLong(KEY_LAST_SONG, songs[index].id)
            .putLong(KEY_LAST_POS, 0L)
            .apply()
    }

    // 定期保存播放进度（在 Navigation 里每 5 秒调一次即可）
    fun savePlaybackProgress() {
        val song = playerController.currentSong.value ?: return
        val pos  = playerController.getCurrentPosition()
        prefs.edit()
            .putLong(KEY_LAST_SONG, song.id)
            .putLong(KEY_LAST_POS, pos)
            .apply()
    }
}