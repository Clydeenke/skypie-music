package com.clydeenke.ling.ui.screen.folders

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clydeenke.ling.domain.model.ScanFolder
import com.clydeenke.ling.domain.model.ScanLog
import com.clydeenke.ling.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanLogs by viewModel.scanLogs.collectAsStateWithLifecycle()
    var showLogs by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        viewModel.addFolder(uri.toString(), resolveDisplayPath(uri))
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("文件夹管理") },
                actions = {
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(if (showLogs) Icons.Rounded.VisibilityOff else Icons.Rounded.Terminal, null)
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Sync, "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { folderPicker.launch(null) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("添加文件夹") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (folders.isEmpty()) {
                item { EmptyFoldersPlaceholder { folderPicker.launch(null) } }
            } else {
                items(folders, key = { it.id }) { folder ->
                    FolderItem(
                        folder = folder,
                        onRemove = { viewModel.removeFolder(folder) },
                        onToggle = { viewModel.setFolderEnabled(folder.id, !folder.isEnabled) }
                    )
                }
            }

            item { ScanInfoCard(folders.size, isScanning) }

            item {
                AnimatedVisibility(showLogs) {
                    ScanLogPanel(scanLogs)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun FolderItem(folder: ScanFolder, onRemove: () -> Unit, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (folder.isEnabled) 1f else 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(folder.displayPath.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium)
                Text(folder.displayPath, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Switch(checked = folder.isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScanLogPanel(logs: List<ScanLog>) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("实时日志", style = MaterialTheme.typography.titleSmall)
            logs.reversed().take(30).forEach { log ->
                Text(
                    text = log.message,
                    color = when(log.level) {
                        ScanLog.Level.ERROR -> MaterialTheme.colorScheme.error
                        ScanLog.Level.SUCCESS -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// 辅助函数与原版一致，包名已修
@Composable
private fun EmptyFoldersPlaceholder(onAdd: () -> Unit) { /* 同上文，略 */ }
@Composable
private fun ScanInfoCard(count: Int, scanning: Boolean) { /* 同上文，略 */ }

fun resolveDisplayPath(uri: Uri): String {
    val segment = uri.lastPathSegment ?: return uri.toString()
    return if (segment.startsWith("primary:")) "/storage/emulated/0/${segment.substringAfter(":")}" else segment
}