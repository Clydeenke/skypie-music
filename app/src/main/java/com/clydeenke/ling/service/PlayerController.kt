package com.clydeenke.ling.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.clydeenke.ling.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _isPlaying       = MutableStateFlow(false)
    private val _currentSong     = MutableStateFlow<Song?>(null)
    private val _currentPosition = MutableStateFlow(0L)
    private val _shuffleMode     = MutableStateFlow(false)
    private val _repeatMode      = MutableStateFlow(Player.REPEAT_MODE_ALL) // 默认全部循环

    val isPlaying      : StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentSong    : StateFlow<Song?>   = _currentSong.asStateFlow()
    val currentPosition: StateFlow<Long>    = _currentPosition.asStateFlow()
    val shuffleMode    : StateFlow<Boolean> = _shuffleMode.asStateFlow()
    val repeatMode     : StateFlow<Int>     = _repeatMode.asStateFlow()

    // 记录当前播放列表，用于手动循环
    private var currentQueue: List<Song> = emptyList()
    private var currentQueueIndex: Int   = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            // ✅ 关键修复：只在非 null 时更新，不因为过渡动画的短暂 null 清空当前歌曲
            val song = item?.localConfiguration?.tag as? Song
            if (song != null) {
                _currentSong.value = song
                currentQueueIndex  = currentQueue.indexOfFirst { it.id == song.id }
                    .coerceAtLeast(0)
            }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleMode.value = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // 播放结束时（队列跑完）手动跳回开头
            if (playbackState == Player.STATE_ENDED) {
                mediaController?.seekTo(0, 0)
                mediaController?.play()
            }
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mc = future.get()
                mediaController = mc
                mc.addListener(listener)
                // 连接成功后同步一次当前状态
                _isPlaying.value  = mc.isPlaying
                _repeatMode.value = mc.repeatMode
                _shuffleMode.value = mc.shuffleModeEnabled
            } catch (e: Exception) {
                // 服务未就绪，播放时会重试
            }
        }, MoreExecutors.directExecutor())
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        currentQueue = songs
        currentQueueIndex = startIndex

        val items = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setTag(song)
                .build()
        }

        // ✅ 先更新 StateFlow，保证 UI 在 mediaController 连接前就能看到歌曲
        _currentSong.value = songs.getOrNull(startIndex)

        mediaController?.run {
            setMediaItems(items, startIndex, 0L)
            repeatMode = Player.REPEAT_MODE_ALL  // 默认全部循环
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipToNext() {
        val mc = mediaController
        if (mc != null) {
            if (mc.hasNextMediaItem()) {
                mc.seekToNextMediaItem()
            } else {
                // ✅ 到队列末尾时，跳回第一首继续播放
                mc.seekTo(0, 0)
                mc.play()
                _currentSong.value = currentQueue.firstOrNull()
                currentQueueIndex  = 0
            }
        }
    }

    fun skipToPrevious() {
        val mc = mediaController ?: return
        if (mc.currentPosition > 3000) {
            mc.seekTo(0)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
        } else {
            // ✅ 已在第一首时，跳到最后一首
            val lastIndex = currentQueue.lastIndex
            if (lastIndex >= 0) {
                mc.seekTo(lastIndex, 0)
                mc.play()
                _currentSong.value   = currentQueue.lastOrNull()
                currentQueueIndex    = lastIndex
            }
        }
    }

    fun seekTo(ms: Long) { mediaController?.seekTo(ms) }

    fun toggleShuffle() {
        mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else                   -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    fun getDuration(): Long        = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    fun release() {
        mediaController?.release()
        controllerFuture?.cancel(false)
        mediaController  = null
        controllerFuture = null
    }
}