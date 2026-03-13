package com.clydeenke.ling.service

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

    private var currentQueue : List<Song> = emptyList()
    private var currentIndex : Int        = 0

    private val _isPlaying    = MutableStateFlow(false)
    private val _currentSong  = MutableStateFlow<Song?>(null)
    private val _shuffleMode  = MutableStateFlow(false)
    private val _repeatMode   = MutableStateFlow(Player.REPEAT_MODE_ALL)
    // 当前是否在播放在线流（用于UI判断，比如不显示删除按钮）
    private val _isOnlineMode   = MutableStateFlow(false)
    private val _onlineLrcText  = MutableStateFlow("")   // 在线播放时的歌词文本

    val isPlaying    : StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentSong  : StateFlow<Song?>   = _currentSong.asStateFlow()
    val shuffleMode  : StateFlow<Boolean> = _shuffleMode.asStateFlow()
    val repeatMode   : StateFlow<Int>     = _repeatMode.asStateFlow()
    val isOnlineMode : StateFlow<Boolean> = _isOnlineMode.asStateFlow()
    val onlineLrcText: StateFlow<String>  = _onlineLrcText.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
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

    private fun Song.toMediaItem(): MediaItem {
        val artUri = albumArtUri?.let { Uri.parse(it) }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _isOnlineMode.value  = false
        _currentStreamUrl    = null
        currentQueue = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }
        mediaController?.run {
            setMediaItems(items, currentIndex, 0L)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            play()
        }
        _currentSong.value = songs.getOrNull(currentIndex)
    }

    fun restoreQueue(songs: List<Song>, startIndex: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        _isOnlineMode.value = false
        currentQueue = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)
        val items = songs.map { it.toMediaItem() }
        mediaController?.run {
            setMediaItems(items, currentIndex, positionMs)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
        _currentSong.value = songs.getOrNull(currentIndex)
    }

    /**
     * 在线流媒体播放：直接给一个 HTTP URL，不需要下载
     * ExoPlayer / Media3 原生支持 HTTP 音频流
     */
    private var _currentStreamUrl: String? = null
    fun getCurrentStreamUrl(): String? = _currentStreamUrl

    fun playOnlineStream(
        streamUrl : String,
        title     : String,
        artist    : String,
        coverUrl  : String = "",
        songId    : String = "online",
        lrcText   : String = ""
    ) {
        _isOnlineMode.value  = true
        _onlineLrcText.value = lrcText
        _currentStreamUrl    = streamUrl

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else null)
            .build()

        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId("online_$songId")
            .setMediaMetadata(metadata)
            .build()

        // 用一个虚拟 Song 让迷你/全屏播放器正常显示歌曲信息
        val fakeSong = Song(
            id          = -1L,
            title       = title,
            artist      = artist,
            album       = "在线播放",
            uri         = streamUrl,
            albumArtUri = coverUrl.ifBlank { null },
            filePath    = "",
            folderPath  = "",
            duration    = 0L,
            size        = 0L,
            dateAdded   = 0L
        )
        currentQueue = listOf(fakeSong)
        currentIndex = 0
        _currentSong.value = fakeSong

        mediaController?.run {
            setMediaItem(item)
            prepare()
            play()
        }
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

    fun seekTo(ms: Long)  { mediaController?.seekTo(ms) }
    fun togglePlayPause() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun toggleShuffle()   { mediaController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
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
    fun getDuration()       : Long = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
    fun getCurrentQueueSize(): Int = currentQueue.size
    fun getCurrentIndex()   : Int  = currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
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