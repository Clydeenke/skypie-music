package com.yulight.skypie.data.model

data class DownloadTask(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String = "",
    val streamUrl: String = "",
    val lrcText: String = "",
    val status: DownloadStatus = DownloadStatus.Queued,
    val progress: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val errorMessage: String = ""
)

enum class DownloadStatus {
    Queued,
    Downloading,
    Done,
    Failed,
    Cancelled
}
