package com.clydeenke.ling.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clydeenke.ling.data.repository.MusicRepository
import com.clydeenke.ling.domain.model.Song
import com.clydeenke.ling.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI 状态包：把界面上所有要显示的变量打包，方便 Compose 一次性读取
 */
data class MusicUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
    val playerController: PlayerController // UI 层可以直接通过它观察播放状态
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeSongs()             // 启动时开始监听歌曲列表
        playerController.connect() // 启动时连接播放服务
    }

    @OptIn(FlowPreview::class)
    private fun observeSongs() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // 极客优化：打字停顿 300 毫秒后再搜索，省电省资源
                .flatMapLatest { query ->
                    if (query.isBlank()) repository.getAllSongs() else repository.searchSongs(query)
                }
                .collect { songs ->
                    _uiState.update { it.copy(songs = songs, isLoading = false) }
                }
        }
    }

    // 搜索：更新查询词
    fun search(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    // 扫描：触发本地文件同步
    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.syncMusic()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "扫描失败：${e.message}", isLoading = false) }
            }
        }
    }

    // 播放：把当前列表塞进播放队列，并播放选中的那首
    fun playSong(song: Song) {
        val songs = _uiState.value.songs
        val index = songs.indexOf(song)
        playerController.playQueue(songs, if (index >= 0) index else 0)
    }

    override fun onCleared() {
        super.onCleared()
        playerController.disconnect() // 销毁时断开连接，防止内存泄漏
    }
}