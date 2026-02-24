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
    private var controllerFuture : ListenableFuture<MediaController>? = null
    private var mediaController  : MediaController? = null

    // ✅ 保存播放队列，用 mediaId 而不是 tag 来找歌曲
    private var currentQueue : List<Song> = emptyList()
    private var currentIndex : Int        = 0

    private val _isPlaying       = MutableStateFlow(false)
    private val _currentSong     = MutableStateFlow<Song?>(null)
    private val _shuffleMode     = MutableStateFlow(false)
    private val _repeatMode      = MutableStateFlow(Player.REPEAT_MODE_ALL)

    val isPlaying  : StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentSong: StateFlow<Song?>   = _currentSong.asStateFlow()
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()
    val repeatMode : StateFlow<Int>     = _repeatMode.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            // ✅ 关键修复：用 mediaId（song.id.toString()）在本地队列里查歌
            // MediaItem.localConfiguration.tag 跨进程会丢失，不能用
            val mediaId = item?.mediaId
            if (!mediaId.isNullOrEmpty()) {
                val song = currentQueue.find { it.id.toString() == mediaId }
                if (song != null) {
                    _currentSong.value = song
                    currentIndex = currentQueue.indexOf(song)
                }
            }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleMode.value = enabled
        }

        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }
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

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        currentQueue = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)

        val items = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())  // ✅ 用 mediaId，不用 tag
                .build()
        }
        mediaController?.run {
            setMediaItems(items, currentIndex, 0L)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            play()
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
            mc.currentPosition > 3000   -> mc.seekTo(0L)
            mc.hasPreviousMediaItem()   -> {
                mc.seekToPreviousMediaItem()
            }
            else -> {
                val last = currentQueue.lastIndex
                mc.seekTo(last, 0L)
                mc.play()
                _currentSong.value = currentQueue.lastOrNull()
                currentIndex = last
            }
        }
    }

    fun seekTo(ms: Long)    { mediaController?.seekTo(ms) }
    fun togglePlayPause()   { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun toggleShuffle()     { mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat()      {
        mediaController?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else                   -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L
    fun getDuration()       : Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    // ── 供全屏 HorizontalPager 使用的辅助方法 ─────────────────────────────
    fun getCurrentQueueSize(): Int = currentQueue.size

    fun getCurrentIndex(): Int = currentIndex.coerceIn(
        minimumValue = 0,
        maximumValue = (currentQueue.size - 1).coerceAtLeast(0)
    )

    fun getSongAt(index: Int): Song? = currentQueue.getOrNull(index)

    fun playAtIndex(index: Int) {
        val mc = mediaController ?: return
        if (currentQueue.isEmpty()) return
        val target = index.coerceIn(0, currentQueue.lastIndex)
        mc.seekTo(target, 0L)
        mc.play()
        _currentSong.value = currentQueue.getOrNull(target)
        currentIndex = target
    }

    fun release() {
        mediaController?.release()
        controllerFuture?.cancel(false)
        mediaController  = null
        controllerFuture = null
    }
}