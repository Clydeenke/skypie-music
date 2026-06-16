package com.yulight.skypie.data.remote

// ── 歌曲数据模型 ──────────────────────────────────────────────────────────────

data class OnlineSong(
    val id       : String,
    val title    : String,
    val artist   : String,
    val album    : String,
    val duration : Int,       // 秒
    val coverUrl : String,
    val source   : MusicSource
)

enum class MusicSource(val displayName: String) {
    KUGOU("小蓝"),
    KUWO("小黄")
}

// ── UI 状态 ───────────────────────────────────────────────────────────────────

sealed class DownloadState {
    object Idle    : DownloadState()
    object Loading : DownloadState()
    object Done    : DownloadState()
    data class Error(val msg: String) : DownloadState()
}

sealed class PlayState {
    object Idle      : PlayState()
    object Resolving : PlayState()
    object Playing   : PlayState()
    data class Error(val msg: String) : PlayState()
}

// ── 音质选项 ──────────────────────────────────────────────────────────────────

sealed class AudioQuality(
    val title  : String,
    val level  : String,
    val bitRate: String,
    val desc   : String
) {
    data object Standard : AudioQuality("标准音质", "standard", "128k", "适合移动网络")
    data object High     : AudioQuality("高品质",   "high",     "320k", "音质清晰细节丰富")
    data object Lossless : AudioQuality("无损音质", "lossless", "FLAC", "母带原音重现")
}

// ── 榜单定义 ──────────────────────────────────────────────────────────────────

data class RankInfo(val name: String, val id: Int)

val KUWO_RANKS = listOf(
    RankInfo("热歌榜",  16),
    RankInfo("新歌榜",  17),
    RankInfo("飙升榜",  93),
    RankInfo("抖音榜", 158),
)