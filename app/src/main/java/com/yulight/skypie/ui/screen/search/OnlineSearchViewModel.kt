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
import com.yulight.skypie.domain.model.Song
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

    // 搜索分页状态
    private val _kuwoPage = MutableStateFlow(1)
    private val _kugouPage = MutableStateFlow(1)
    private val _kuwoLoadingMore = MutableStateFlow(false)
    private val _kugouLoadingMore = MutableStateFlow(false)
    private val _kuwoLoadedPages = mutableSetOf<Int>()
    private val _kugouLoadedPages = mutableSetOf<Int>()
    val kuwoLoadingMore: StateFlow<Boolean> = _kuwoLoadingMore.asStateFlow()
    val kugouLoadingMore: StateFlow<Boolean> = _kugouLoadingMore.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** 搜索指定 Tab（0=酷我, 1=酷狗），已加载则跳过 */
    fun search(tabIndex: Int) {
        val q = _searchQuery.value.trim()
        if (q.isBlank()) return
        // 检查是否已加载过相同关键词
        val alreadyLoaded = when (tabIndex) {
            0 -> _kuwoResults.value.isNotEmpty() && _searchQuery.value == _lastKuwoQuery
            1 -> _kugouResults.value.isNotEmpty() && _searchQuery.value == _lastKugouQuery
            else -> false
        }
        if (alreadyLoaded) return

        viewModelScope.launch {
            when (tabIndex) {
                0 -> {
                    _kuwoSearching.value = true; _kuwoError.value = null
                    _kuwoResults.value = emptyList()
                    _kuwoPage.value = 1
                    _kuwoLoadedPages.clear()
                    try {
                        val results = repository.search(q, MusicSource.KUWO)
                        _kuwoResults.value = results
                        _kuwoLoadedPages.add(1)
                        _lastKuwoQuery = q
                    }
                    catch (e: Exception) { _kuwoError.value = "搜索失败：${e.message}" }
                    finally { _kuwoSearching.value = false }
                }
                1 -> {
                    _kugouSearching.value = true; _kugouError.value = null
                    _kugouResults.value = emptyList()
                    _kugouPage.value = 1
                    _kugouLoadedPages.clear()
                    try {
                        val results = repository.search(q, MusicSource.KUGOU)
                        _kugouResults.value = results
                        _kugouLoadedPages.add(1)
                        _lastKugouQuery = q
                    }
                    catch (e: Exception) { _kugouError.value = "搜索失败：${e.message}" }
                    finally { _kugouSearching.value = false }
                }
            }
        }
    }

    // 记录上次搜索的关键词，用于判断是否需要重新搜索
    private var _lastKuwoQuery = ""
    private var _lastKugouQuery = ""

    fun loadMoreKuwo() {
        if (_kuwoLoadingMore.value || _kuwoSearching.value) return
        val nextPage = _kuwoPage.value + 1
        if (_kuwoLoadedPages.contains(nextPage)) return
        viewModelScope.launch {
            _kuwoLoadingMore.value = true
            delay(1000)
            try {
                val q = _searchQuery.value.trim()
                val newSongs = repository.search(q, MusicSource.KUWO, nextPage)
                if (newSongs.isNotEmpty()) {
                    _kuwoResults.value = _kuwoResults.value + newSongs
                    _kuwoPage.value = nextPage
                    _kuwoLoadedPages.add(nextPage)
                    // 同步更新 PlayerController 的在线歌曲列表
                    if (playerController.isOnlineMode.value) {
                        playerController.onlineSongs = _kuwoResults.value
                        prefetchNewSongsLyrics(newSongs)
                    }
                }
            } catch (_: Exception) {}
            _kuwoLoadingMore.value = false
        }
    }

    fun loadMoreKugou() {
        if (_kugouLoadingMore.value || _kugouSearching.value) return
        val nextPage = _kugouPage.value + 1
        if (_kugouLoadedPages.contains(nextPage)) return
        viewModelScope.launch {
            _kugouLoadingMore.value = true
            delay(1000)
            try {
                val q = _searchQuery.value.trim()
                val newSongs = repository.search(q, MusicSource.KUGOU, nextPage)
                if (newSongs.isNotEmpty()) {
                    _kugouResults.value = _kugouResults.value + newSongs
                    _kugouPage.value = nextPage
                    _kugouLoadedPages.add(nextPage)
                    // 同步更新 PlayerController 的在线歌曲列表
                    if (playerController.isOnlineMode.value) {
                        playerController.onlineSongs = _kugouResults.value
                        prefetchNewSongsLyrics(newSongs)
                    }
                }
            } catch (_: Exception) {}
            _kugouLoadingMore.value = false
        }
    }

    // ── 榜单 ──────────────────────────────────────────────────────────────────

    private val _currentRankIndex = MutableStateFlow(0)
    val currentRankIndex: StateFlow<Int> = _currentRankIndex.asStateFlow()

    private val _rankSongs    = MutableStateFlow<List<OnlineSong>>(emptyList())
    private val _rankLoading  = MutableStateFlow(true)
    private val _rankPage     = MutableStateFlow(1)
    private val _rankLoadingMore = MutableStateFlow(false)
    private val _rankLoadedPages = mutableSetOf<Int>()  // 已加载的页码
    val rankSongs      : StateFlow<List<OnlineSong>> = _rankSongs.asStateFlow()
    val rankLoading    : StateFlow<Boolean>          = _rankLoading.asStateFlow()
    val rankLoadingMore: StateFlow<Boolean>          = _rankLoadingMore.asStateFlow()

    init {
        // 榜单切换时自动重新加载
        viewModelScope.launch {
            _currentRankIndex.collect { index ->
                _rankLoading.value = true
                _rankPage.value    = 1
                _rankLoadedPages.clear()
                _rankSongs.value   = emptyList()
                val songs = repository.fetchRank(KUWO_RANKS[index].id, 1)
                _rankSongs.value = songs
                _rankLoadedPages.add(1)
                _rankLoading.value = false
            }
        }
    }

    fun setRankIndex(index: Int) { _currentRankIndex.value = index }

    fun loadMoreRank() {
        if (_rankLoadingMore.value || _rankLoading.value) return
        val nextPage = _rankPage.value + 1
        if (_rankLoadedPages.contains(nextPage)) return  // 已加载过，跳过
        viewModelScope.launch {
            _rankLoadingMore.value = true
            // 先延迟1秒显示加载动画，再发起请求
            delay(1000)
            try {
                val newSongs = repository.fetchRank(
                    KUWO_RANKS[_currentRankIndex.value].id,
                    nextPage
                )
                if (newSongs.isNotEmpty()) {
                    _rankSongs.value = _rankSongs.value + newSongs
                    _rankPage.value = nextPage
                    _rankLoadedPages.add(nextPage)
                    // 同步更新 PlayerController 的在线歌曲列表
                    if (playerController.isOnlineMode.value) {
                        playerController.onlineSongs = _rankSongs.value
                        // 预取新加载歌曲的歌词
                        prefetchNewSongsLyrics(newSongs)
                    }
                }
            } catch (_: Exception) {}
            _rankLoadingMore.value = false
        }
    }

    /**
     * 预取新加载歌曲的歌词
     */
    private fun prefetchNewSongsLyrics(songs: List<OnlineSong>) {
        viewModelScope.launch {
            for (song in songs) {
                val songId = song.id.toLongOrNull() ?: -1L
                if (playerController.songLrcMap.containsKey(songId)) continue
                try {
                    val lrc = repository.fetchLyric(song)
                    if (lrc.isNotBlank()) {
                        playerController.songLrcMap[songId] = lrc
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ── 播放状态 ──────────────────────────────────────────────────────────────

    private val _playStates = MutableStateFlow<Map<String, PlayState>>(emptyMap())
    val playStates: StateFlow<Map<String, PlayState>> = _playStates.asStateFlow()

    private var currentPlayingId: String? = null

    init {
        // 观察当前歌曲变化，更新高亮
        viewModelScope.launch {
            playerController.currentSong.collect { song ->
                if (song != null && playerController.isOnlineMode.value) {
                    // 从 onlineSongs 中找到对应的 id
                    val onlineSong = playerController.onlineSongs.find { it.id.toLongOrNull() == song.id }
                    if (onlineSong != null && currentPlayingId != onlineSong.id) {
                        currentPlayingId?.let { updatePlayState(it, PlayState.Idle) }
                        currentPlayingId = onlineSong.id
                        updatePlayState(onlineSong.id, PlayState.Playing)
                    }
                }
            }
        }
    }

    fun play(
        songs: List<OnlineSong>,
        index: Int,
        onOpenPlayer: () -> Unit,
        onNoApi: () -> Unit
    ) {
        if (apiBase.isBlank()) { onNoApi(); return }
        val song = songs.getOrNull(index) ?: return
        if (currentPlayingId == song.id) { onOpenPlayer(); return }
        viewModelScope.launch {
            updatePlayState(song.id, PlayState.Resolving)
            currentPlayingId?.let { updatePlayState(it, PlayState.Idle) }
            currentPlayingId = song.id
            try {
                // 只解析点击歌曲的URL
                val streamUrl = repository.resolvePlayUrl(apiBase, song, AudioQuality.Standard)
                if (streamUrl.isNullOrBlank()) {
                    updatePlayState(song.id, PlayState.Error("获取链接失败"))
                    currentPlayingId = null; return@launch
                }
                // 缓存URL
                playerController.urlCache[song.id] = streamUrl
                // 获取歌词
                val lrcText = try { repository.fetchLyric(song) } catch (_: Exception) { "" }
                val songId = song.id.toLongOrNull() ?: -1L
                if (lrcText.isNotBlank()) {
                    playerController.songLrcMap[songId] = lrcText
                }
                // 存储原始歌曲列表
                playerController.onlineSongs = songs
                // 注册切歌回调（按需解析URL）
                playerController.onSongTransition = { newIndex -> resolveNextUrl(newIndex) }
                // 构建队列：只用点击歌曲的URL，其他用空占位
                val songObjs = songs.mapIndexed { i, s ->
                    val url = if (i == index) streamUrl else (playerController.urlCache[songs[i].id] ?: "placeholder_${s.id}")
                    Song(
                        id          = s.id.toLongOrNull() ?: -1L,
                        title       = s.title,
                        artist      = s.artist,
                        album       = s.album,
                        duration    = s.duration.toLong(),
                        uri         = url,
                        albumArtUri = s.coverUrl,
                        size        = 0L,
                        dateAdded   = System.currentTimeMillis(),
                        folderPath  = "",
                        filePath    = url
                    )
                }
                playerController.playQueue(songObjs, index, lrcText)
                updatePlayState(song.id, PlayState.Playing)
                onOpenPlayer()
            } catch (e: Exception) {
                updatePlayState(song.id, PlayState.Error(e.message ?: "播放失败"))
                currentPlayingId = null
            }
        }
    }

    /**
     * 按需解析指定位置歌曲的URL（切歌时调用）
     */
    private fun resolveNextUrl(index: Int) {
        val song = playerController.onlineSongs.getOrNull(index) ?: return
        val songId = song.id
        // 如果已缓存则跳过
        if (playerController.urlCache.containsKey(songId)) return
        viewModelScope.launch {
            try {
                val url = repository.resolvePlayUrl(apiBase, song, AudioQuality.Standard)
                if (!url.isNullOrBlank()) {
                    playerController.urlCache[songId] = url
                    playerController.updateMediaItemUrl(index, url)
                }
                // 获取歌词
                val lrc = try { repository.fetchLyric(song) } catch (_: Exception) { "" }
                val longId = songId.toLongOrNull() ?: -1L
                if (lrc.isNotBlank()) {
                    playerController.songLrcMap[longId] = lrc
                }
            } catch (_: Exception) {}
        }
        // 预取后续几首歌的歌词（后台）
        prefetchNearbyLyrics(index)
    }

    /**
     * 预取附近歌曲的歌词（后台静默加载）
     */
    private fun prefetchNearbyLyrics(currentIndex: Int) {
        val songs = playerController.onlineSongs
        // 预取后续3首歌的歌词
        for (i in 1..3) {
            val idx = currentIndex + i
            if (idx >= songs.size) break
            val song = songs[idx]
            val songId = song.id.toLongOrNull() ?: -1L
            // 已有歌词则跳过
            if (playerController.songLrcMap.containsKey(songId)) continue
            viewModelScope.launch {
                try {
                    val lrc = repository.fetchLyric(song)
                    if (lrc.isNotBlank()) {
                        playerController.songLrcMap[songId] = lrc
                    }
                } catch (_: Exception) {}
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