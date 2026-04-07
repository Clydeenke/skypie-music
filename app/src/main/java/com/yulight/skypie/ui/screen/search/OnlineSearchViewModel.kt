package com.yulight.skypie.ui.screen.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.yulight.skypie.data.remote.AudioQuality
import com.yulight.skypie.data.remote.DownloadState
import com.yulight.skypie.data.remote.KUWO_RANKS
import com.yulight.skypie.data.remote.MusicSource
import com.yulight.skypie.data.remote.OnlineSong
import com.yulight.skypie.data.remote.PlayState
import com.yulight.skypie.data.repository.OnlineMusicRepository
import com.yulight.skypie.service.PlayerController
import com.yulight.skypie.worker.MusicDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREFS_SETTINGS = "skypie_settings"
private const val KEY_API_URL    = "api_url"

@OptIn(FlowPreview::class)
@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    application            : Application,
    private val repository : OnlineMusicRepository,
    val playerController   : PlayerController
) : AndroidViewModel(application) {

    private val app = application

    // ── API 地址（从 SharedPreferences 实时读取，用户在设置里改了立刻生效） ──
    val apiBase: String
        get() = app.getSharedPreferences(PREFS_SETTINGS, 0)
            .getString(KEY_API_URL, "") ?: ""

    // ── 搜索 ──────────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 每个 Tab 独立维护搜索结果状态
    private val _kuwoResults  = MutableStateFlow<List<OnlineSong>>(emptyList())
    private val _kugouResults = MutableStateFlow<List<OnlineSong>>(emptyList())
    val kuwoResults : StateFlow<List<OnlineSong>> = _kuwoResults.asStateFlow()
    val kugouResults: StateFlow<List<OnlineSong>> = _kugouResults.asStateFlow()

    private val _kuwoSearching  = MutableStateFlow(false)
    private val _kugouSearching = MutableStateFlow(false)
    val kuwoSearching : StateFlow<Boolean> = _kuwoSearching.asStateFlow()
    val kugouSearching: StateFlow<Boolean> = _kugouSearching.asStateFlow()

    private val _kuwoError  = MutableStateFlow<String?>(null)
    private val _kugouError = MutableStateFlow<String?>(null)
    val kuwoError : StateFlow<String?> = _kuwoError.asStateFlow()
    val kugouError: StateFlow<String?> = _kugouError.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** 搜索指定 Tab（0=酷我, 1=酷狗） */
    fun search(tabIndex: Int) {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            when (tabIndex) {
                0 -> {
                    _kuwoSearching.value = true; _kuwoError.value = null; _kuwoResults.value = emptyList()
                    try { _kuwoResults.value = repository.search(q, MusicSource.KUWO) }
                    catch (e: Exception) { _kuwoError.value = "搜索失败：${e.message}" }
                    finally { _kuwoSearching.value = false }
                }
                1 -> {
                    _kugouSearching.value = true; _kugouError.value = null; _kugouResults.value = emptyList()
                    try { _kugouResults.value = repository.search(q, MusicSource.KUGOU) }
                    catch (e: Exception) { _kugouError.value = "搜索失败：${e.message}" }
                    finally { _kugouSearching.value = false }
                }
            }
        }
    }

    // ── 榜单 ──────────────────────────────────────────────────────────────────

    private val _currentRankIndex = MutableStateFlow(0)
    val currentRankIndex: StateFlow<Int> = _currentRankIndex.asStateFlow()

    private val _rankSongs    = MutableStateFlow<List<OnlineSong>>(emptyList())
    private val _rankLoading  = MutableStateFlow(true)
    private val _rankPage     = MutableStateFlow(1)
    private val _rankLoadingMore = MutableStateFlow(false)
    val rankSongs      : StateFlow<List<OnlineSong>> = _rankSongs.asStateFlow()
    val rankLoading    : StateFlow<Boolean>          = _rankLoading.asStateFlow()
    val rankLoadingMore: StateFlow<Boolean>          = _rankLoadingMore.asStateFlow()

    init {
        // 榜单切换时自动重新加载
        viewModelScope.launch {
            _currentRankIndex.collect { index ->
                _rankLoading.value = true
                _rankPage.value    = 1
                _rankSongs.value   = emptyList()
                _rankSongs.value   = repository.fetchRank(KUWO_RANKS[index].id, 1)
                _rankLoading.value = false
            }
        }
    }

    fun setRankIndex(index: Int) { _currentRankIndex.value = index }

    fun loadMoreRank() {
        if (_rankLoadingMore.value || _rankLoading.value) return
        viewModelScope.launch {
            _rankLoadingMore.value = true
            delay(300)
            try {
                val newSongs = repository.fetchRank(
                    KUWO_RANKS[_currentRankIndex.value].id,
                    _rankPage.value + 1
                )
                if (newSongs.isNotEmpty()) {
                    _rankSongs.value = _rankSongs.value + newSongs
                    _rankPage.value++
                }
            } catch (_: Exception) {}
            delay(1000)
            _rankLoadingMore.value = false
        }
    }

    // ── 播放状态 ──────────────────────────────────────────────────────────────

    private val _playStates = MutableStateFlow<Map<String, PlayState>>(emptyMap())
    val playStates: StateFlow<Map<String, PlayState>> = _playStates.asStateFlow()

    private var currentPlayingId: String? = null

    fun play(song: OnlineSong, onOpenPlayer: () -> Unit, onNoApi: () -> Unit) {
        if (apiBase.isBlank()) { onNoApi(); return }
        if (currentPlayingId == song.id) { onOpenPlayer(); return }
        viewModelScope.launch {
            updatePlayState(song.id, PlayState.Resolving)
            currentPlayingId?.let { updatePlayState(it, PlayState.Idle) }
            currentPlayingId = song.id
            try {
                val streamUrl = repository.resolvePlayUrl(apiBase, song, AudioQuality.Standard)
                if (streamUrl.isNullOrBlank()) {
                    updatePlayState(song.id, PlayState.Error("获取链接失败"))
                    currentPlayingId = null; return@launch
                }
                val lrcText = repository.fetchLyric(song)
                playerController.playOnlineStream(
                    streamUrl = streamUrl, title = song.title, artist = song.artist,
                    coverUrl  = song.coverUrl, songId = song.id, lrcText = lrcText
                )
                updatePlayState(song.id, PlayState.Playing)
                onOpenPlayer()
            } catch (e: Exception) {
                updatePlayState(song.id, PlayState.Error(e.message ?: "播放失败"))
                currentPlayingId = null
            }
        }
    }

    // ── 下载状态 ──────────────────────────────────────────────────────────────

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    fun download(
        song      : OnlineSong,
        quality   : AudioQuality,
        onNoApi   : () -> Unit,
        onQueued  : () -> Unit,
        onComplete: () -> Unit
    ) {
        if (apiBase.isBlank()) { onNoApi(); return }
        viewModelScope.launch {
            updateDownloadState(song.id, DownloadState.Loading)
            try {
                val playUrl = repository.resolvePlayUrl(apiBase, song, quality)
                if (playUrl.isNullOrBlank()) {
                    updateDownloadState(song.id, DownloadState.Error("该音质暂时不可用"))
                    return@launch
                }
                val lrcText = repository.fetchLyric(song)
                val inputData = workDataOf(
                    "title"    to song.title,
                    "artist"   to song.artist,
                    "playUrl"  to playUrl,
                    "coverUrl" to song.coverUrl,
                    "lrcText"  to lrcText
                )
                WorkManager.getInstance(app).enqueue(
                    OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                        .setInputData(inputData)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        ).build()
                )
                updateDownloadState(song.id, DownloadState.Done)
                onQueued()
                onComplete()
            } catch (e: Exception) {
                updateDownloadState(song.id, DownloadState.Error(e.message ?: "未知错误"))
            }
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun updatePlayState(id: String, state: PlayState) {
        _playStates.value = _playStates.value.toMutableMap().also { it[id] = state }
    }

    private fun updateDownloadState(id: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().also { it[id] = state }
    }
}