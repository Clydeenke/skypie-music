package com.yulight.skypie.ui.screen.online.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yulight.skypie.data.remote.KUWO_RANKS
import com.yulight.skypie.data.remote.OnlineSong
import com.yulight.skypie.data.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineHomeViewModel @Inject constructor(
    private val repository: OnlineMusicRepository
) : ViewModel() {

    // 每个榜单的歌曲列表（索引对应榜单索引）
    private val _rankSongsMap = MutableStateFlow<Map<Int, List<OnlineSong>>>(emptyMap())
    val rankSongsMap: StateFlow<Map<Int, List<OnlineSong>>> = _rankSongsMap.asStateFlow()

    private val _rankLoading = MutableStateFlow(true)
    val rankLoading: StateFlow<Boolean> = _rankLoading.asStateFlow()

    init {
        loadAllRanks()
    }

    private fun loadAllRanks() {
        viewModelScope.launch {
            _rankLoading.value = true
            try {
                val songsMap = mutableMapOf<Int, List<OnlineSong>>()
                KUWO_RANKS.forEachIndexed { index, rank ->
                    // 使用缓存方法
                    val songs = repository.fetchRankSongsWithCache(rank.id, 1, 3)
                    songsMap[index] = songs
                }
                _rankSongsMap.value = songsMap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _rankLoading.value = false
            }
        }
    }
}
