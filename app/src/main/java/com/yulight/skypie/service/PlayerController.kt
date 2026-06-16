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
import javax.inject.Inject
import javax.inject.Singleton
@androidx.media3.common.util.UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture : ListenableFuture<MediaController>? = null
    private var mediaController  : MediaController? = null

    private var currentQueue : List<Song> = emptyList()
    private var currentIndex : Int        = 0
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
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            // 正在更新URL时忽略transition，避免重复跳歌
            if (isUpdatingUrl) return
            val mediaId = item?.mediaId
            if (!mediaId.isNullOrEmpty()) {
                // 用歌曲唯一ID查找，不用位置索引
                val song = currentQueue.find { it.id.toString() == mediaId }
                if (song != null) {
                    _currentSong.value = song
                    currentIndex = currentQueue.indexOf(song)
                    // 更新在线播放的 streamUrl 和歌词
                    if (_isOnlineMode.value) {
                        _currentStreamUrl = song.uri
                        _onlineLrcText.value = songLrcMap[song.id.toString()] ?: ""
                        // 通知外部切歌（用于按需解析URL和歌词）
                        onSongTransition?.invoke(currentIndex)
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
        val nextIndex = currentIndex + 1
        if (nextIndex >= currentQueue.size) {
            // 队列末尾，循环模式下预取第一首
            if (_repeatMode.value == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                val firstSong = currentQueue[0]
                if (firstSong.uri.startsWith("placeholder_")) {
                    onSongTransition?.invoke(0)
                }
            }
            return
        }
        val nextSong = currentQueue[nextIndex]
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
        val isOnline = songs[startIdx].uri.startsWith("http")
        _isOnlineMode.value = isOnline
        _currentStreamUrl   = if (isOnline) songs[startIdx].uri else null
        // 存储歌词到 map
        if (isOnline && lrcText.isNotBlank()) {
            songLrcMap[songs[startIdx].id.toString()] = lrcText
        }
        _onlineLrcText.value = if (isOnline) (songLrcMap[songs[startIdx].id.toString()] ?: "") else ""
        currentQueue = songs
        currentIndex = startIdx
        val items = songs.mapIndexed { index, song -> song.toMediaItem(index, isOnline) }
        mediaController?.run {
            clearMediaItems()
            setMediaItems(items, currentIndex, 0L)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            play()
        }
        _currentSong.value = songs.getOrNull(currentIndex)
    }

    fun restoreQueue(songs: List<Song>, startIndex: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val isOnline = songs[idx].uri.startsWith("http")
        _isOnlineMode.value = isOnline
        _currentStreamUrl   = if (isOnline) songs[idx].uri else null
        _onlineLrcText.value = if (isOnline) (songLrcMap[songs[idx].id.toString()] ?: "") else ""
        currentQueue = songs
        currentIndex = idx
        val items = songs.mapIndexed { index, song -> song.toMediaItem(index, isOnline) }
        mediaController?.run {
            clearMediaItems()
            setMediaItems(items, currentIndex, positionMs)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
        _currentSong.value = songs.getOrNull(currentIndex)
    }

    fun skipToNext() {
        val mc = mediaController ?: return
        if (currentQueue.isEmpty()) return
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else {
            mc.seekTo(0, 0L)
            mc.play()
            _currentSong.value = currentQueue.firstOrNull()
            currentIndex = 0
        }
    }

    fun skipToPrevious() {
        val mc = mediaController ?: return
        if (currentQueue.isEmpty()) return
        when {
            mc.currentPosition > 3000 -> mc.seekTo(0L)
            mc.hasPreviousMediaItem() -> mc.seekToPreviousMediaItem()
            else -> {
                val last = currentQueue.lastIndex
                mc.seekTo(last, 0L)
                mc.play()
                _currentSong.value = currentQueue.lastOrNull()
                currentIndex = last
            }
        }
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
    fun getCurrentQueueSize(): Int = currentQueue.size
    fun getCurrentIndex()   : Int  = currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
    fun getSongAt(index: Int): Song? = currentQueue.getOrNull(index)

    /**
     * 更新队列中指定位置的 MediaItem URI（用于在线歌曲按需解析URL后更新）
     * @param forceResume 如果为 true，即使之前暂停了也恢复播放（用于 placeholder 解析完成后）
     */
    fun updateMediaItemUrl(index: Int, newUrl: String, forceResume: Boolean = false) {
        val mc = mediaController ?: return
        if (index < 0 || index >= mc.mediaItemCount) return
        // 如果是当前正在播放的歌曲，需要特殊处理
        val isCurrentItem = (index == currentIndex)
        // 设置标志，避免触发transition回调导致跳歌
        isUpdatingUrl = true
        try {
            // 构建新的 MediaItem
            val song = currentQueue.getOrNull(index) ?: return
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
            // 替换 MediaItem（不 seekTo，让 ExoPlayer 自然播放）
            mc.removeMediaItem(index)
            mc.addMediaItem(index, newItem)
            // 只有当前歌曲才需要恢复播放状态
            if (isCurrentItem && forceResume) {
                mc.seekTo(index, mc.currentPosition)
                mc.play()
            }
            // 同步更新 currentQueue
            currentQueue = currentQueue.toMutableList().apply {
                set(index, song.copy(uri = newUrl))
            }
        } finally {
            isUpdatingUrl = false
        }
    }

    fun playAtIndex(index: Int) {
        val mc = mediaController ?: return
        if (currentQueue.isEmpty()) return
        val target = index.coerceIn(0, currentQueue.lastIndex)
        mc.seekTo(target, 0L)
        mc.play()
        _currentSong.value = currentQueue.getOrNull(target)
        currentIndex = target
    }

    /**
     * 将指定歌曲插入到当前播放位置的紧下一首
     * 不中断当前播放，其余队列顺序不变
     */
    fun playNext(song: Song) {
        val mc = mediaController ?: return
        // 插入位置 = 当前索引 + 1，边界保护：不超过列表末尾
        val insertIndex = (mc.currentMediaItemIndex + 1).coerceIn(0, mc.mediaItemCount)
        mc.addMediaItem(insertIndex, song.toMediaItem())
        // 同步更新内存队列，保证 getSongAt() / getCurrentIndex() 等查询准确
        currentQueue = currentQueue.toMutableList().apply {
            add(insertIndex.coerceIn(0, size), song)
        }
    }

    fun removeFromQueue(index: Int) {
        val mc = mediaController ?: return
        if (index < 0 || index >= currentQueue.size) return
        mc.removeMediaItem(index)
        currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
        // 如果移除的是当前之前的歌，currentIndex 要往前移一位
        if (index < currentIndex) currentIndex--
    }

    fun clearQueue() {
        val mc = mediaController ?: return
        mc.clearMediaItems()
        currentQueue = emptyList()
        currentIndex = 0
    }

    fun release() {
        mediaController?.release()
        controllerFuture?.cancel(false)
        mediaController  = null
        controllerFuture = null
    }
}