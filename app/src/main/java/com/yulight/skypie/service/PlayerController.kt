package com.yulight.skypie.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.data.remote.OnlineSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
@androidx.media3.common.util.UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture : ListenableFuture<MediaController>? = null
    private var mediaController  : MediaController? = null
    private val queueMutex = Mutex()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)
    val currentQueue : StateFlow<List<Song>> = _currentQueue.asStateFlow()
    val currentIndex : StateFlow<Int>        = _currentIndex.asStateFlow()
    val songLrcMap = mutableMapOf<String, String>()  // key: song.id 转为 String

    // 在线播放：存储原始歌曲列表、URL缓存、切歌回调
    var onlineSongs: List<OnlineSong> = emptyList()
    val urlCache = mutableMapOf<String, String>()  // songId -> streamUrl
    var onSongTransition: ((newIndex: Int) -> Unit)? = null
    private var isUpdatingUrl = false  // 标志：正在更新URL，忽略transition

    private val _isPlaying    = MutableStateFlow(false)
    private val _currentSong  = MutableStateFlow<Song?>(null)
    private val _repeatMode   = MutableStateFlow(Player.REPEAT_MODE_ALL)
    // 当前播放模式：0=循环全部, 1=单曲循环, 2=随机播放
    private val _playMode     = MutableStateFlow(0)
    // 当前是否在播放在线流（用于UI判断，比如不显示删除按钮）
    private val _isOnlineMode   = MutableStateFlow(false)
    private val _onlineLrcText  = MutableStateFlow("")   // 在线播放时的歌词文本

    val isPlaying    : StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentSong  : StateFlow<Song?>   = _currentSong.asStateFlow()
    val repeatMode   : StateFlow<Int>     = _repeatMode.asStateFlow()
    val playMode     : StateFlow<Int>     = _playMode.asStateFlow()
    val isOnlineMode : StateFlow<Boolean> = _isOnlineMode.asStateFlow()
    val onlineLrcText: StateFlow<String>  = _onlineLrcText.asStateFlow()

    /**
     * 更新在线歌词显示（供外部调用，如实时加载歌词）
     */
    fun updateOnlineLrcText(text: String) {
        _onlineLrcText.value = text
    }

    private var _currentStreamUrl: String? = null
    fun getCurrentStreamUrl(): String? = _currentStreamUrl

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            // 开始播放时预取下一首歌曲的 URL
            if (isPlaying && _isOnlineMode.value) {
                preFetchNextUrl()
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // 在线播放遇到错误时暂停，防止崩溃
            if (_isOnlineMode.value) {
                mediaController?.pause()
            }
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            // 正在更新URL时忽略transition，避免重复跳歌
            if (isUpdatingUrl) return
            val mediaId = item?.mediaId
            if (!mediaId.isNullOrEmpty()) {
                // 用歌曲唯一ID查找，不用位置索引
                val song = _currentQueue.value.find { it.id.toString() == mediaId }
                if (song != null) {
                    _currentSong.value = song
                    _currentIndex.value = _currentQueue.value.indexOf(song)
                    // 更新在线播放的 streamUrl 和歌词
                    if (_isOnlineMode.value) {
                        _currentStreamUrl = song.uri
                        // 通过歌曲信息匹配原始在线歌曲ID，不依赖索引对齐
                        val originalId = onlineSongs.find { it.title == song.title && it.artist == song.artist }?.id ?: ""
                        _onlineLrcText.value = songLrcMap[song.id.toString()]
                            ?: songLrcMap[originalId]
                            ?: ""
                        // 如果是 placeholder URI，暂停播放等待 URL 解析完成
                        if (song.uri.startsWith("placeholder_")) {
                            mediaController?.pause()
                        }
                        // 通知外部切歌（用于按需解析URL和歌词）
                        onSongTransition?.invoke(_currentIndex.value)
                        // 预取下一首的 URL
                        preFetchNextUrl()
                    }
                }
            }
        }
        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }
    }

    /**
     * 预取下一首歌曲的 URL（后台静默加载）
     */
    private fun preFetchNextUrl() {
        val nextIndex = _currentIndex.value + 1
        if (nextIndex >= _currentQueue.value.size) {
            // 队列末尾，循环模式下预取第一首
            if (_repeatMode.value == Player.REPEAT_MODE_ALL && _currentQueue.value.isNotEmpty()) {
                val firstSong = _currentQueue.value[0]
                if (firstSong.uri.startsWith("placeholder_")) {
                    onSongTransition?.invoke(0)
                }
            }
            return
        }
        val nextSong = _currentQueue.value[nextIndex]
        // 如果不是 placeholder，跳过
        if (!nextSong.uri.startsWith("placeholder_")) return
        // 通知外部解析 URL
        onSongTransition?.invoke(nextIndex)
    }

    fun connect() {
        val token  = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    mediaController = future.get()
                    mediaController?.apply {
                        repeatMode = Player.REPEAT_MODE_ALL
                        addListener(listener)
                    }
                } catch (_: Exception) {}
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun Song.toMediaItem(index: Int = 0, isOnline: Boolean = false): MediaItem {
        val artUri = albumArtUri?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())  // 用歌曲唯一ID，不用位置索引
            .setMediaMetadata(metadata)
            .build()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, lrcText: String = "") {
        if (songs.isEmpty()) return
        val startIdx = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[startIdx]
        val isOnline = startSong.uri.startsWith("http")
        _isOnlineMode.value = isOnline
        _currentStreamUrl   = if (isOnline) startSong.uri else null
        if (isOnline && lrcText.isNotBlank()) {
            songLrcMap[startSong.id.toString()] = lrcText
        }
        _onlineLrcText.value = if (isOnline) (songLrcMap[startSong.id.toString()] ?: "") else ""
        _currentQueue.value = songs
        _currentIndex.value = startIdx
        val items = songs.mapIndexed { index, song -> song.toMediaItem(index, isOnline) }
        mediaController?.run {
            clearMediaItems()
            setMediaItems(items, _currentIndex.value, 0L)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            play()
        }
        _currentSong.value = songs.getOrNull(_currentIndex.value)
        // 记录播放历史
        if (isOnline) {
            try {
                val onlineSongs = this.onlineSongs
                val matched = onlineSongs?.firstOrNull { it.id.toLongOrNull()?.plus(1) == startSong.id || it.title == startSong.title }
                if (matched != null) {
                    val favSong = com.yulight.skypie.util.FavoriteSong(
                        songId = matched.id, title = matched.title, artist = matched.artist,
                        coverUrl = matched.coverUrl, source = matched.source.name.lowercase(),
                        duration = matched.duration
                    )
                    com.yulight.skypie.util.HistoryManager.addHistory(context, favSong)
                    com.yulight.skypie.util.CacheManager.addCache(context, favSong)
                }
            } catch (_: Exception) {}
        }
    }

    fun restoreQueue(songs: List<Song>, startIndex: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val isOnline = songs[idx].uri.startsWith("http")
        _isOnlineMode.value = isOnline
        _currentStreamUrl   = if (isOnline) songs[idx].uri else null
        _onlineLrcText.value = if (isOnline) (songLrcMap[songs[idx].id.toString()] ?: "") else ""
        _currentQueue.value = songs
        _currentIndex.value = idx
        val items = songs.mapIndexed { index, song -> song.toMediaItem(index, isOnline) }
        mediaController?.run {
            // 如果已经在播放同一首歌，只更新UI状态，不重新prepare
            if (currentMediaItemIndex == _currentIndex.value && isPlaying) {
                _currentSong.value = songs.getOrNull(_currentIndex.value)
                return
            }
            clearMediaItems()
            setMediaItems(items, _currentIndex.value, positionMs)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
        _currentSong.value = songs.getOrNull(_currentIndex.value)
    }

    fun skipToNext() {
        val mc = mediaController ?: return
        if (_currentQueue.value.isEmpty()) return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else {
            mc.seekTo(0, 0L)
            mc.play()
            _currentSong.value = _currentQueue.value.firstOrNull()
            _currentIndex.value = 0
        }
    }

    fun skipToPrevious() {
        val mc = mediaController ?: return
        if (_currentQueue.value.isEmpty()) return
        // 始终切到上一首（绕过 ExoPlayer 内置的3秒判断）
        val prevIndex = (mc.currentMediaItemIndex - 1).coerceAtLeast(0)
        _currentIndex.value = prevIndex
        _currentSong.value = _currentQueue.value.getOrNull(prevIndex)
        mc.seekTo(prevIndex, 0L)
    }

    fun seekTo(ms: Long) {
        // 限制最大位置为 duration - 1000ms，避免触发歌曲结束
        val maxPos = getDuration() - 1000
        val target = if (maxPos > 0) ms.coerceAtMost(maxPos) else ms
        mediaController?.seekTo(target)
    }
    fun togglePlayPause() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }

    /**
     * 切换播放模式：循环全部(0) → 单曲循环(1) → 随机播放(2) → 循环全部(0)
     */
    fun togglePlayMode() {
        val newMode = (_playMode.value + 1) % 3
        _playMode.value = newMode
        mediaController?.let {
            when (newMode) {
                0 -> { // 循环全部
                    it.repeatMode = Player.REPEAT_MODE_ALL
                    it.shuffleModeEnabled = false
                }
                1 -> { // 单曲循环
                    it.repeatMode = Player.REPEAT_MODE_ONE
                    it.shuffleModeEnabled = false
                }
                2 -> { // 随机播放
                    it.repeatMode = Player.REPEAT_MODE_ALL
                    it.shuffleModeEnabled = true
                }
            }
        }
    }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    fun getDuration()       : Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
    fun getCurrentQueueSize(): Int = _currentQueue.value.size
    fun getCurrentIndex()   : Int  = _currentIndex.value.coerceIn(0, (_currentQueue.value.size - 1).coerceAtLeast(0))
    fun getSongAt(index: Int): Song? = _currentQueue.value.getOrNull(index)

    /**
     * 更新队列中指定位置的 MediaItem URI（用于在线歌曲按需解析URL后更新）
     * @param forceResume 如果为 true，即使之前暂停了也恢复播放（用于 placeholder 解析完成后）
     */
    fun updateMediaItemUrl(index: Int, newUrl: String, forceResume: Boolean = false) {
        val mc = mediaController ?: return
        if (index < 0 || index >= mc.mediaItemCount) return
        // 如果是当前正在播放的歌曲，需要特殊处理
        val isCurrentItem = (index == _currentIndex.value)
        // 设置标志，避免触发transition回调导致跳歌
        isUpdatingUrl = true
        try {
            // 构建新的 MediaItem
            val song = _currentQueue.value.getOrNull(index) ?: return
            val artUri = song.albumArtUri?.let { Uri.parse(it) }
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(artUri)
                .build()
            val newItem = MediaItem.Builder()
                .setUri(newUrl)
                .setMediaId(song.id.toString())  // 用歌曲唯一ID
                .setMediaMetadata(metadata)
                .build()
            // 原子替换 MediaItem，避免 remove+add 的竞态问题
            mc.replaceMediaItem(index, newItem)
            // 只有当前歌曲才需要恢复播放状态
            if (isCurrentItem && forceResume) {
                mc.seekTo(index, 0L)
                mc.play()
            }
            // 同步更新 currentQueue
            _currentQueue.value = _currentQueue.value.toMutableList().apply {
                set(index, song.copy(uri = newUrl))
            }
        } finally {
            isUpdatingUrl = false
        }
    }

    fun playAtIndex(index: Int) {
        val mc = mediaController ?: return
        if (_currentQueue.value.isEmpty()) return
        val target = index.coerceIn(0, _currentQueue.value.lastIndex)
        mc.seekTo(target, 0L)
        mc.play()
        _currentSong.value = _currentQueue.value.getOrNull(target)
        _currentIndex.value = target
    }

    /**
     * 将指定歌曲插入到当前播放位置的紧下一首
     * 不中断当前播放，其余队列顺序不变
     */
    fun playNext(song: Song) {
        val mc = mediaController ?: return
        val existingIndex = _currentQueue.value.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            mc.removeMediaItem(existingIndex)
            _currentQueue.value = _currentQueue.value.toMutableList().apply { removeAt(existingIndex) }
            if (existingIndex < _currentIndex.value) _currentIndex.value--
        }
        val insertIndex = (_currentIndex.value + 1).coerceIn(0, _currentQueue.value.size)
        mc.addMediaItem(insertIndex, song.toMediaItem())
        _currentQueue.value = _currentQueue.value.toMutableList().apply {
            add(insertIndex.coerceIn(0, size), song)
        }
    }

    /**
     * 将歌曲插入下一首，返回原始位置（-1 表示不在队列中）
     */
    fun playNextWithPosition(song: Song): Int {
        val mc = mediaController ?: return -1
        val existingIndex = _currentQueue.value.indexOfFirst { it.id == song.id }
        if (existingIndex >= 0) {
            mc.removeMediaItem(existingIndex)
            _currentQueue.value = _currentQueue.value.toMutableList().apply { removeAt(existingIndex) }
            if (existingIndex < _currentIndex.value) _currentIndex.value--
        }
        val insertIndex = (_currentIndex.value + 1).coerceIn(0, _currentQueue.value.size)
        mc.addMediaItem(insertIndex, song.toMediaItem())
        _currentQueue.value = _currentQueue.value.toMutableList().apply {
            add(insertIndex.coerceIn(0, size), song)
        }
        return existingIndex
    }

    /**
     * 恢复歌曲到原位置（originalIndex >= 0），或移除（originalIndex == -1）
     */
    suspend fun restoreFromPlayNext(song: Song, originalIndex: Int) {
        queueMutex.withLock {
            val mc = mediaController ?: return@withLock
            val currentIdx = _currentQueue.value.indexOfFirst { it.id == song.id }
            if (currentIdx < 0) return@withLock

            if (originalIndex < 0) {
                mc.removeMediaItem(currentIdx)
                _currentQueue.value = _currentQueue.value.toMutableList().apply { removeAt(currentIdx) }
                if (currentIdx < _currentIndex.value) _currentIndex.value--
            } else {
                mc.removeMediaItem(currentIdx)
                _currentQueue.value = _currentQueue.value.toMutableList().apply { removeAt(currentIdx) }
                val targetIdx = originalIndex.coerceAtMost(_currentQueue.value.size)
                mc.addMediaItem(targetIdx, song.toMediaItem())
                _currentQueue.value = _currentQueue.value.toMutableList().apply {
                    add(targetIdx.coerceAtMost(size), song)
                }
                if (currentIdx < _currentIndex.value && targetIdx >= _currentIndex.value) _currentIndex.value--
                else if (currentIdx >= _currentIndex.value && targetIdx < _currentIndex.value) _currentIndex.value++
            }
        }
    }

    suspend fun removeFromQueue(songId: String) {
        queueMutex.withLock {
            val mc = mediaController ?: return@withLock
            val index = _currentQueue.value.indexOfFirst { it.id.toString() == songId }
            if (index < 0) return@withLock
            val isCurrentSong = (index == _currentIndex.value)
            mc.removeMediaItem(index)
            _currentQueue.value = _currentQueue.value.toMutableList().apply { removeAt(index) }
            if (isCurrentSong) {
                if (_currentQueue.value.isEmpty()) {
                    _currentIndex.value = 0
                    _currentSong.value = null
                } else {
                    if (_currentIndex.value >= _currentQueue.value.size) {
                        _currentIndex.value = _currentQueue.value.lastIndex
                    }
                    _currentSong.value = _currentQueue.value.getOrNull(_currentIndex.value)
                }
            } else if (index < _currentIndex.value) {
                _currentIndex.value--
            }
        }
    }

    fun clearQueue() {
        val mc = mediaController ?: return
        mc.clearMediaItems()
        _currentQueue.value = emptyList()
        _currentIndex.value = 0
    }

    suspend fun moveInQueue(from: Int, to: Int) {
        queueMutex.withLock {
            val mc = mediaController ?: return@withLock
            if (from < 0 || from >= _currentQueue.value.size || to < 0 || to >= _currentQueue.value.size) return@withLock
            mc.moveMediaItem(from, to)
            _currentQueue.value = _currentQueue.value.toMutableList().apply {
                val item = removeAt(from)
                add(to, item)
            }
            // 更新 currentIndex
            _currentIndex.value = when {
                _currentIndex.value == from -> to
                from < _currentIndex.value && to >= _currentIndex.value -> _currentIndex.value - 1
                from > _currentIndex.value && to <= _currentIndex.value -> _currentIndex.value + 1
                else -> _currentIndex.value
            }
        }
    }

    fun release() {
        mediaController?.release()
        controllerFuture?.cancel(false)
        mediaController  = null
        controllerFuture = null
    }
}