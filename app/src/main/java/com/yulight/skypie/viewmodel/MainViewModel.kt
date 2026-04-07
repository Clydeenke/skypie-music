package com.yulight.skypie.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import com.yulight.skypie.domain.lyrics.DesktopLyricsPrefs
import androidx.lifecycle.viewModelScope
import com.yulight.skypie.data.repository.MusicRepository
import com.yulight.skypie.data.repository.PlaylistRepository
import com.yulight.skypie.domain.model.Playlist
import com.yulight.skypie.domain.model.ScanFolder
import com.yulight.skypie.domain.model.ScanLog
import com.yulight.skypie.domain.model.Song
import com.yulight.skypie.service.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val PREFS_PLAYER  = "skypie_player"
private const val KEY_LAST_SONG = "last_song_id"
private const val KEY_LAST_POS  = "last_position_ms"

private const val KEY_TOTAL_LISTENED_MS = "total_listened_ms"

/** 歌曲列表排序方式 */
enum class SortOrder {
    TITLE,       // 按标题 A→Z
    ARTIST,      // 按艺术家 A→Z
    DURATION,    // 按时长 长→短
    DATE_ADDED   // 按添加时间 新→旧（默认）
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    application                  : Application,
    private val repository       : MusicRepository,
    private val playlistRepository: PlaylistRepository,  // 歌单仓库
    val playerController         : PlayerController,
    val lyricsPrefs               : DesktopLyricsPrefs
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_PLAYER, 0)
    private val app   = application

    // ── 打开全屏播放器事件 ────────────────────────────────────────────────────
    private val _openPlayerEvent = MutableStateFlow(false)
    val openPlayerEvent: StateFlow<Boolean> = _openPlayerEvent.asStateFlow()
    fun requestOpenPlayer() { _openPlayerEvent.value = true  }
    fun consumeOpenPlayer() { _openPlayerEvent.value = false }

    // ── 累计听歌时长（毫秒，持久化到 SharedPreferences） ─────────────────────────
    private val _totalListenedMs = MutableStateFlow(prefs.getLong(KEY_TOTAL_LISTENED_MS, 0L))
    val totalListenedMs: StateFlow<Long> = _totalListenedMs.asStateFlow()

    // ── 多选模式状态（供 SharedPlayerContainer 隐藏迷你播放条） ──────────────────
    private val _isMultiSelectActive = MutableStateFlow(false)
    val isMultiSelectActive: StateFlow<Boolean> = _isMultiSelectActive.asStateFlow()
    fun setMultiSelectActive(active: Boolean) { _isMultiSelectActive.value = active }

    // ── 搜索状态 ──────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── 排序状态 ──────────────────────────────────────────────────────────────
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    // ── 歌曲列表（支持搜索 + 排序，二者任一变化都触发重组） ──────────────────
    val songs: StateFlow<List<Song>> = combine(
        _searchQuery.debounce(300),
        _sortOrder
    ) { q, sort -> q to sort }
        .flatMapLatest { (q, sort) ->
            val base = if (q.isBlank()) repository.getAllSongs() else repository.searchSongs(q)
            base.map { list ->
                when (sort) {
                    SortOrder.TITLE      -> list.sortedBy      { it.title.lowercase()  }
                    SortOrder.ARTIST     -> list.sortedBy      { it.artist.lowercase() }
                    SortOrder.DURATION   -> list.sortedByDescending { it.duration      }
                    SortOrder.DATE_ADDED -> list.sortedByDescending { it.dateAdded     }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 文件夹列表 ────────────────────────────────────────────────────────────
    val folders: StateFlow<List<ScanFolder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 扫描状态 ──────────────────────────────────────────────────────────────
    val isScanning : StateFlow<Boolean>       = repository.isScanning
    val scanLogs   : StateFlow<List<ScanLog>> = repository.scanLogs

    // ── 无效文件 ──────────────────────────────────────────────────────────────
    private val _invalidFiles    = MutableStateFlow<List<java.io.File>>(emptyList())
    private val _isCheckingFiles = MutableStateFlow(false)
    val invalidFiles    : StateFlow<List<java.io.File>> = _invalidFiles.asStateFlow()
    val isCheckingFiles : StateFlow<Boolean>            = _isCheckingFiles.asStateFlow()

    // ── 歌单列表（响应式，歌单增删改时自动刷新） ─────────────────────────────
    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── 启动时恢复上次播放 ────────────────────────────────────────────────────
    init {
        viewModelScope.launch {
            songs.first { it.isNotEmpty() }.let { list ->
                val lastId  = prefs.getLong(KEY_LAST_SONG, -1L)
                val lastPos = prefs.getLong(KEY_LAST_POS,   0L)
                if (lastId == -1L) return@let
                val idx = list.indexOfFirst { it.id == lastId }
                if (idx == -1) return@let
                playerController.restoreQueue(list, idx, lastPos)
            }
        }
    }

    // ── 基础操作 ──────────────────────────────────────────────────────────────
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun refresh() { viewModelScope.launch { repository.syncMusic() } }
    fun addFolder(uriString: String, displayPath: String) {
        viewModelScope.launch { repository.addFolder(uriString, displayPath) }
    }
    fun removeFolder(folder: ScanFolder) {
        viewModelScope.launch { repository.removeFolder(folder) }
    }
    fun setFolderEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch { repository.setFolderEnabled(id, enabled) }
    }

    // ── 播放控制 ──────────────────────────────────────────────────────────────
    fun playSong(songs: List<Song>, index: Int) {
        playerController.playQueue(songs, index)
        prefs.edit()
            .putLong(KEY_LAST_SONG, songs[index].id)
            .putLong(KEY_LAST_POS,  0L)
            .apply()
    }

    /** 将歌曲插入到当前播放的紧下一首 */
    fun playNext(song: Song) {
        playerController.playNext(song)
    }

    fun savePlaybackProgress() {
        val song = playerController.currentSong.value ?: return
        val pos  = playerController.getCurrentPosition()
        // 仅在实际播放时累积时长（每 5 秒调用一次，加 5 秒）
        val addMs    = if (playerController.isPlaying.value) 5_000L else 0L
        val newTotal = _totalListenedMs.value + addMs
        _totalListenedMs.value = newTotal
        prefs.edit()
            .putLong(KEY_LAST_SONG, song.id)
            .putLong(KEY_LAST_POS,  pos)
            .putLong(KEY_TOTAL_LISTENED_MS, newTotal)
            .apply()
    }

    // ── 歌单操作 ──────────────────────────────────────────────────────────────

    /** 获取单个歌单详情，供详情页订阅（Flow，随数据库变化实时更新） */
    fun getPlaylistDetail(playlistId: Long): Flow<Playlist?> =
        playlistRepository.getPlaylistDetail(playlistId)

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name.trim()) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.addSongToPlaylist(playlistId, songId) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.removeSongFromPlaylist(playlistId, songId) }
    }

    // ── 删除歌曲 ──────────────────────────────────────────────────────────────
    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioFile = java.io.File(song.filePath)
                if (audioFile.exists()) audioFile.delete()
                val base = song.filePath.substringBeforeLast(".")
                java.io.File("$base.jpg").takeIf { it.exists() }?.delete()
                java.io.File("$base.lrc").takeIf { it.exists() }?.delete()
                java.io.File("$base.krc").takeIf { it.exists() }?.delete()
                android.media.MediaScannerConnection.scanFile(app, arrayOf(song.filePath), null, null)
                repository.deleteSong(song)
            } catch (e: Exception) {
                android.util.Log.e("ViewModel", "删除失败: ${e.message}")
            }
        }
    }

    // ── 扫描无效文件 ──────────────────────────────────────────────────────────
    fun scanInvalidFiles() {
        viewModelScope.launch {
            _isCheckingFiles.value = true
            val invalid = withContext(Dispatchers.IO) {
                val musicDir  = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val audioExts = setOf("mp3", "flac", "aac", "ogg", "m4a", "wav")
                val result    = mutableListOf<java.io.File>()
                musicDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in audioExts }
                    .forEach { file ->
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(file.absolutePath)
                            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                ?.toLongOrNull() ?: 0L
                            if (dur < 1000) result.add(file)
                        } catch (_: Exception) {
                            result.add(file)
                        } finally {
                            mmr.release()
                        }
                    }
                result
            }
            _invalidFiles.value    = invalid
            _isCheckingFiles.value = false
        }
    }

    fun deleteInvalidFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _invalidFiles.value.forEach { file ->
                file.delete()
                java.io.File(file.absolutePath.substringBeforeLast(".") + ".jpg").delete()
                java.io.File(file.absolutePath.substringBeforeLast(".") + ".lrc").delete()
                android.media.MediaScannerConnection.scanFile(
                    app, arrayOf(file.absolutePath), null, null
                )
            }
            _invalidFiles.value = emptyList()
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun cleanOldPrivateFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                java.io.File(app.filesDir, "covers").deleteRecursively()
                java.io.File(app.filesDir, "lyrics").deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}