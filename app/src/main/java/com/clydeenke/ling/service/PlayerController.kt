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
    // ✅ 用 ListenableFuture 持有 future，只调用一次 buildAsync
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _isPlaying       = MutableStateFlow(false)
    private val _currentSong     = MutableStateFlow<Song?>(null)
    private val _currentPosition = MutableStateFlow(0L)
    private val _shuffleMode     = MutableStateFlow(false)
    private val _repeatMode      = MutableStateFlow(Player.REPEAT_MODE_OFF)

    val isPlaying      : StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentSong    : StateFlow<Song?>   = _currentSong.asStateFlow()
    val currentPosition: StateFlow<Long>    = _currentPosition.asStateFlow()
    val shuffleMode    : StateFlow<Boolean> = _shuffleMode.asStateFlow()
    val repeatMode     : StateFlow<Int>     = _repeatMode.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            _currentSong.value = item?.localConfiguration?.tag as? Song
        }
        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleMode.value = enabled
        }
        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
        }
    }

    fun connect() {
        val token = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        // ✅ 只调用一次 buildAsync，在回调里用同一个 future.get()
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    mediaController = future.get()
                    mediaController?.addListener(listener)
                } catch (e: Exception) {
                    // 连接失败：服务未启动，忽略即可，播放时会重试
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val items = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setTag(song)
                .build()
        }
        mediaController?.run {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
        _currentSong.value = songs.getOrNull(startIndex)
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val p = mediaController ?: return
        if (p.currentPosition > 3000) p.seekTo(0)
        else p.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        mediaController?.seekTo(ms)
    }

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
    fun getDuration(): Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

    fun release() {
        mediaController?.release()
        controllerFuture?.cancel(false)
        mediaController    = null
        controllerFuture   = null
    }
}