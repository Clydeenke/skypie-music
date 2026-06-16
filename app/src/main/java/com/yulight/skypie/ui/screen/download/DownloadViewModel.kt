package com.yulight.skypie.ui.screen.download

import androidx.lifecycle.ViewModel
import com.yulight.skypie.data.model.DownloadStatus
import com.yulight.skypie.data.model.DownloadTask
import com.yulight.skypie.service.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    fun cancelTask(taskId: String) {
        downloadManager.cancel(taskId)
    }

    fun removeTask(taskId: String) {
        downloadManager.removeTask(taskId)
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
    }
}
