package com.clydeenke.ling.viewmodel

import androidx.lifecycle.ViewModel
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository     : MusicRepository,
    val         playerController : PlayerController
) : ViewModel() {

    // ── 搜索状态 ──
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 歌曲列表（带 300ms 搜索防抖）──
    // 2026 最优写法：保持 Flow 链式的简洁性
    val songs: StateFlow<List<Song>> = _searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) repository.getAllSongs()
            else             repository.searchSongs(q)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── 文件夹列表 ──
    val folders: StateFlow<List<ScanFolder>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── 扫描状态 ──
    val isScanning : StateFlow<Boolean> = repository.isScanning
    val scanLogs   : StateFlow<List<ScanLog>> = repository.scanLogs

    // ── 基础操作方法 ──
    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun refresh() {
        viewModelScope.launch { repository.syncMusic() }
    }

    fun addFolder(uriString: String, displayPath: String) {
        viewModelScope.launch { repository.addFolder(uriString, displayPath) }
    }

    fun removeFolder(folder: ScanFolder) {
        viewModelScope.launch { repository.removeFolder(folder) }
    }

    fun setFolderEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch { repository.setFolderEnabled(id, enabled) }
    }

    // ── 播放控制 ──
    fun playSong(songs: List<Song>, index: Int) {
        playerController.playQueue(songs, index)
    }
}