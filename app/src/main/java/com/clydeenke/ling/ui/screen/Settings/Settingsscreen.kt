package com.clydeenke.ling.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

private const val PREFS_NAME = "ling_settings"
private const val KEY_API_URL = "api_url"

@Composable
fun SettingsScreen(
    onOpenFolders: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, 0) }

    // API 地址输入框状态
    var showApiDialog by remember { mutableStateOf(false) }
    var apiUrl        by remember { mutableStateOf(prefs.getString(KEY_API_URL, "") ?: "") }
    var apiInput      by remember { mutableStateOf(apiUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // 大标题（和 LibraryScreen 风格一致）
        Text(
            text     = "设置",
            style    = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )

        // ── 本地音乐分组 ──────────────────────────────────────────────────────
        SettingsGroupLabel("本地音乐")
        SettingsCard {
            SettingsItem(
                icon    = Icons.Rounded.Folder,
                title   = "管理文件夹",
                summary = "添加或移除本地音乐目录",
                onClick = onOpenFolders
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── 在线音源分组 ──────────────────────────────────────────────────────
        SettingsGroupLabel("在线音源")
        SettingsCard {
            SettingsItem(
                icon    = Icons.Rounded.Language,
                title   = "API 接口地址",
                summary = apiUrl.ifEmpty { "未设置，点击输入" },
                onClick = { apiInput = apiUrl; showApiDialog = true }
            )
        }

        Spacer(Modifier.height(32.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    // ── API 地址输入对话框 ────────────────────────────────────────────────────
    if (showApiDialog) {
        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title   = { Text("API 接口地址") },
            text    = {
                Column {
                    Text(
                        "输入你的音乐 API 地址，例如：\nhttps://your-api.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value         = apiInput,
                        onValueChange = { apiInput = it },
                        placeholder   = { Text("https://") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiUrl = apiInput.trim()
                    prefs.edit { putString(KEY_API_URL, apiUrl) }
                    showApiDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showApiDialog = false }) { Text("取消") }
            }
        )
    }
}

// ── 小工具 Composable ─────────────────────────────────────────────────────────

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsItem(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    title  : String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector        = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier           = Modifier.size(20.dp)
        )
    }
}