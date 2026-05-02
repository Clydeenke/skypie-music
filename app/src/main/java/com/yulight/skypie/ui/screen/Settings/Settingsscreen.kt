package com.yulight.skypie.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yulight.skypie.util.hasOverlayPermission
import com.yulight.skypie.util.startDesktopLyrics
import com.yulight.skypie.util.stopDesktopLyrics
import com.yulight.skypie.viewmodel.MusicViewModel

private const val PREFS_NAME  = "skypie_settings"
private const val KEY_API_URL = "api_url"

@Composable
fun SettingsScreen(
    viewModel    : MusicViewModel,
    onBack       : () -> Unit = {},
    onOpenFolders: () -> Unit = {}
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs          = remember { context.getSharedPreferences(PREFS_NAME, 0) }

    var showApiDialog      by remember { mutableStateOf(false) }
    var apiUrl             by remember { mutableStateOf(prefs.getString(KEY_API_URL, "") ?: "") }
    var apiInput           by remember { mutableStateOf(apiUrl) }
    var showInvalidDialog  by remember { mutableStateOf(false) }
    var showCleanOldDialog by remember { mutableStateOf(false) }
    var showPermDialog     by remember { mutableStateOf(false) }

    // ── 桌面歌词状态（从 Hilt 单例读，不再用本地 SharedPrefs） ───────────────
    val lyricsPrefs    = viewModel.lyricsPrefs
    val lyricsEnabled  by lyricsPrefs.isEnabled.collectAsStateWithLifecycle()
    val isLocked       by lyricsPrefs.isLocked.collectAsStateWithLifecycle()
    val bgEnabled      by lyricsPrefs.bgEnabled.collectAsStateWithLifecycle()
    val bgAlpha        by lyricsPrefs.bgAlpha.collectAsStateWithLifecycle()
    val bgWidth        by lyricsPrefs.bgWidth.collectAsStateWithLifecycle()

    // 是否有悬浮窗权限（ON_RESUME 时重新检查）
    var hasOverlayPerm by remember { mutableStateOf(context.hasOverlayPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasPerm = context.hasOverlayPermission()
                hasOverlayPerm = nowHasPerm
                // 从系统授权页返回且之前想开启 → 自动启动
                if (nowHasPerm && lyricsPrefs.isEnabled.value) context.startDesktopLyrics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val invalidFiles    by viewModel.invalidFiles.collectAsStateWithLifecycle()
    val isCheckingFiles by viewModel.isCheckingFiles.collectAsStateWithLifecycle()
    LaunchedEffect(invalidFiles) { if (invalidFiles.isNotEmpty()) showInvalidDialog = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // 顶部标题栏
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("设置", style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        // ── 本地音乐 ──────────────────────────────────────────────────────────
        SettingsGroupLabel("本地音乐")
        SettingsCard {
            SettingsItem(Icons.Rounded.Folder, "管理文件夹", "添加或移除本地音乐目录", onOpenFolders)
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
            SettingsItem(Icons.Rounded.DeleteSweep, "清理无效文件",
                if (isCheckingFiles) "检测中…" else "扫描并删除无法播放的残缺音频") {
                if (!isCheckingFiles) viewModel.scanInvalidFiles()
            }
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
            SettingsItem(Icons.Rounded.CleaningServices, "清理旧缓存文件",
                "删除旧版本遗留的封面/歌词缓存") { showCleanOldDialog = true }
        }

        Spacer(Modifier.height(24.dp))

        // ── 在线音源 ──────────────────────────────────────────────────────────
        SettingsGroupLabel("在线音源")
        SettingsCard {
            SettingsItem(Icons.Rounded.Language, "API 接口地址",
                apiUrl.ifEmpty { "未设置，点击输入" }) { apiInput = apiUrl; showApiDialog = true }
        }

        Spacer(Modifier.height(24.dp))

        // ── 桌面歌词 ──────────────────────────────────────────────────────────
        SettingsGroupLabel("桌面歌词")
        SettingsCard {

            // 总开关
            SettingsSwitchItem(
                icon    = Icons.Rounded.Lyrics,
                title   = "显示桌面歌词",
                summary = when {
                    lyricsEnabled && hasOverlayPerm  -> "已开启 · 解锁后点击歌词可调节样式"
                    lyricsEnabled && !hasOverlayPerm -> "需要授权悬浮窗权限"
                    else -> "在其他应用上方显示当前歌词"
                },
                checked  = lyricsEnabled && hasOverlayPerm,
                enabled  = true,  // 开关本身永远可点
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        if (context.hasOverlayPermission()) {
                            lyricsPrefs.setEnabled(true)
                            context.startDesktopLyrics()
                        } else {
                            showPermDialog = true
                        }
                    } else {
                        lyricsPrefs.setEnabled(false)
                        context.stopDesktopLyrics()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)

            // 锁定位置（Switch，未开启时灰色不可操作）
            SettingsSwitchItem(
                icon    = Icons.Rounded.Lock,
                title   = "锁定歌词位置",
                summary = if (isLocked) "锁定中，点击穿透，无法拖动" else "解锁，可拖动，点击歌词调节样式",
                checked  = isLocked,
                enabled  = lyricsEnabled && hasOverlayPerm,
                onCheckedChange = { lyricsPrefs.setLocked(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)

            // 背景开关（未开启时灰色）
            SettingsSwitchItem(
                icon    = Icons.Rounded.InvertColors,
                title   = "显示背景",
                summary = if (bgEnabled) "半透明胶囊背景" else "纯文字 + 阴影，无背景",
                checked  = bgEnabled,
                enabled  = lyricsEnabled && hasOverlayPerm,
                onCheckedChange = { lyricsPrefs.setBgEnabled(it) }
            )

            // 背景透明度 + 宽度（只在背景开启 + 桌面歌词开启时显示）
            AnimatedVisibility(visible = bgEnabled && lyricsEnabled && hasOverlayPerm) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsSliderItem(Icons.Rounded.Opacity, "背景透明度",
                        "${(bgAlpha * 100).toInt()}%", bgAlpha, 0.1f..1f) { lyricsPrefs.setBgAlpha(it) }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsSliderItem(Icons.Rounded.SwapHoriz, "背景宽度",
                        "${(bgWidth * 100).toInt()}%", bgWidth, 0.3f..1f) { lyricsPrefs.setBgWidth(it) }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Spacer(Modifier.height(80.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    // ── 弹窗 ──────────────────────────────────────────────────────────────────

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            icon  = { Icon(Icons.Rounded.SecurityUpdate, null) },
            title = { Text("需要悬浮窗权限") },
            text  = { Text("桌面歌词需要「显示在其他应用上层」权限。\n\n点击「去授权」后，在系统设置里找到饼音，打开该权限，然后返回即可自动生效。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermDialog = false
                    // setEnabled(true) 先记录意图，授权回来后 ON_RESUME 里会启动
                    lyricsPrefs.setEnabled(true)
                    context.startDesktopLyrics()  // 内部检测无权限会跳转系统设置
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = { showPermDialog = false }) { Text("取消") }
            }
        )
    }

    if (showApiDialog) {
        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title = { Text("API 接口地址") },
            text  = {
                Column {
                    Text("输入你的音乐 API 地址，例如：\nhttps://your-api.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(value = apiInput, onValueChange = { apiInput = it },
                        placeholder = { Text("https://") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    apiUrl = apiInput.trim(); prefs.edit { putString(KEY_API_URL, apiUrl) }; showApiDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showApiDialog = false }) { Text("取消") } }
        )
    }

    if (showInvalidDialog && invalidFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showInvalidDialog = false },
            title = { Text("发现 ${invalidFiles.size} 个无效文件") },
            text  = {
                Column {
                    Text("以下文件无法播放（残缺或损坏），是否全部删除？",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp))
                    invalidFiles.take(5).forEach {
                        Text("• ${it.name}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (invalidFiles.size > 5) Text("… 还有 ${invalidFiles.size - 5} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteInvalidFiles(); showInvalidDialog = false }) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showInvalidDialog = false }) { Text("取消") } }
        )
    }

    if (showCleanOldDialog) {
        AlertDialog(
            onDismissRequest = { showCleanOldDialog = false },
            title = { Text("清理旧缓存") },
            text  = { Text("将删除旧版本遗留在 App 内部的封面和歌词缓存文件，不影响音频和音乐库。") },
            confirmButton = {
                TextButton(onClick = { viewModel.cleanOldPrivateFiles(); showCleanOldDialog = false }) { Text("确认清理") }
            },
            dismissButton = { TextButton(onClick = { showCleanOldDialog = false }) { Text("取消") } }
        )
    }
}

// ── 小工具组件 ────────────────────────────────────────────────────────────────

@Composable private fun SettingsGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable private fun SettingsItem(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp))
    }
}

@Composable private fun SettingsSwitchItem(
    icon: ImageVector, title: String, summary: String,
    checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f  // 禁用时遵循 Material 规范变灰
    Row(modifier = Modifier.fillMaxWidth()
        .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable private fun SettingsSliderItem(icon: ImageVector, title: String, summary: String,
                                           value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange,
            modifier = Modifier.padding(start = 36.dp))
    }
}