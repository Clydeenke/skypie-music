package com.yulight.skypie.domain.lyrics

import android.content.Context
import com.yulight.skypie.service.PlayerController
import com.yulight.skypie.util.LrcLine
import com.yulight.skypie.util.LrcParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌词管理器（Hilt 单例）
 *
 * 职责：
 *   1. 监听当前歌曲变化 → 自动加载对应歌词（内嵌 > 同目录 .lrc）
 *   2. 每 200ms 轮询播放位置 → 暴露当前行索引和文字
 *   3. 统一数据源，供桌面歌词 Service 和全屏播放器共用，避免重复加载
 */
@Singleton
class LyricsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerController: PlayerController
) {
    // 单例持有自己的协程作用域，随 App 进程存活
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── 对外暴露的状态 ────────────────────────────────────────────────────────

    /** 当前歌曲的全部歌词行 */
    private val _lrcLines = MutableStateFlow<List<LrcLine>>(emptyList())
    val lrcLines: StateFlow<List<LrcLine>> = _lrcLines.asStateFlow()

    /** 当前高亮行的索引，-1 表示暂无 */
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 当前行文字（空字符串 = 无歌词或歌曲未开始） */
    val currentLine: StateFlow<String> = combine(_lrcLines, _currentIndex) { lines, idx ->
        lines.getOrNull(idx)?.text ?: ""
    }.stateIn(scope, SharingStarted.Eagerly, "")

    /** 当前歌曲是否有可用歌词 */
    val hasLyrics: StateFlow<Boolean> = _lrcLines
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ── 内部逻辑 ──────────────────────────────────────────────────────────────

    init {
        // 歌曲切换 → 重新加载歌词（collectLatest 保证旧任务被取消）
        scope.launch {
            playerController.currentSong.collectLatest { song ->
                _currentIndex.value = -1
                _lrcLines.value = if (song != null) {
                    withContext(Dispatchers.IO) {
                        val parent = File(song.filePath).parent ?: ""
                        LrcParser.loadForSong(
                            folderPath = parent,
                            title      = song.title,
                            filePath   = song.filePath,
                            artist     = song.artist
                        ) ?: emptyList()
                    }
                } else emptyList()
            }
        }

        // 每 200ms 更新当前行（只在播放时运行）
        scope.launch {
            while (isActive) {
                val lines = _lrcLines.value
                if (playerController.isPlaying.value && lines.isNotEmpty()) {
                    // ExoPlayer.currentPosition 必须在主线程读取
                    val posMs = withContext(Dispatchers.Main) {
                        playerController.getCurrentPosition()
                    }
                    val idx = lines.indexOfLast { it.timeMs <= posMs }
                    if (idx != _currentIndex.value) {
                        _currentIndex.value = idx
                    }
                }
                delay(200)
            }
        }
    }
}