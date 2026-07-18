package com.yulight.skypie.ui.screen.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yulight.skypie.ui.theme.LocalThemeIndex
import com.yulight.skypie.ui.theme.themeColors
import com.yulight.skypie.util.hasOverlayPermission
import com.yulight.skypie.util.startDesktopLyrics
import com.yulight.skypie.util.stopDesktopLyrics
import com.yulight.skypie.viewmodel.MusicViewModel

private const val PREFS_NAME = "skypie_settings"
private const val KEY_API_URL = "api_url"
private const val KEY_3D_COVER = "enable_3d_cover"
private const val KEY_DOWNLOAD_DIR = "download_dir"
private const val KEY_PLAY_QUALITY = "play_quality"
private const val KEY_ENABLE_KARAOKE = "enable_karaoke"
const val DEFAULT_DOWNLOAD_DIR = "SkypieMusic"

private val qualityOptions = listOf(
    "standard" to "标准音质 (128k)",
    "high"     to "高品质 (320k)",
    "lossless" to "无损音质 (FLAC)",
)

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, 0) }

    var showApiDialog by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(prefs.getString(KEY_API_URL, "") ?: "") }
    var apiInput by remember { mutableStateOf(apiUrl) }
    var showInvalidDialog by remember { mutableStateOf(false) }
    var showCleanOldDialog by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    var enable3DCover by remember { mutableStateOf(prefs.getBoolean(KEY_3D_COVER, true)) }
    var enableKaraoke by remember { mutableStateOf(prefs.getBoolean(KEY_ENABLE_KARAOKE, false)) }
    var downloadDir by remember { mutableStateOf(prefs.getString(KEY_DOWNLOAD_DIR, DEFAULT_DOWNLOAD_DIR) ?: DEFAULT_DOWNLOAD_DIR) }
    var playQuality by remember { mutableStateOf(prefs.getString(KEY_PLAY_QUALITY, "standard") ?: "standard") }

    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.lastPathSegment?.let { path ->
            val folderName = path.substringAfterLast(':').substringAfterLast('/')
            if (folderName.isNotBlank()) {
                downloadDir = folderName
                prefs.edit().putString(KEY_DOWNLOAD_DIR, folderName).apply()
            }
        }
    }

    val themeIndexState = LocalThemeIndex.current
    var themeColorIndex by themeIndexState
    var showThemeColors by remember { mutableStateOf(false) }

    val lyricsPrefs = viewModel.lyricsPrefs
    val lyricsEnabled by lyricsPrefs.isEnabled.collectAsStateWithLifecycle()
    val isLocked by lyricsPrefs.isLocked.collectAsStateWithLifecycle()
    val bgEnabled by lyricsPrefs.bgEnabled.collectAsStateWithLifecycle()
    val bgAlpha by lyricsPrefs.bgAlpha.collectAsStateWithLifecycle()
    val bgWidth by lyricsPrefs.bgWidth.collectAsStateWithLifecycle()

    var hasOverlayPerm by remember { mutableStateOf(context.hasOverlayPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasPerm = context.hasOverlayPermission()
                hasOverlayPerm = nowHasPerm
                if (nowHasPerm && lyricsPrefs.isEnabled.value) context.startDesktopLyrics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val invalidFiles by viewModel.invalidFiles.collectAsStateWithLifecycle()
    val isCheckingFiles by viewModel.isCheckingFiles.collectAsStateWithLifecycle()
    LaunchedEffect(invalidFiles) { if (invalidFiles.isNotEmpty()) showInvalidDialog = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 固定标题栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = if (rememberScrollState().value > 0) 4.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("设置", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
            }

            // 可滚动内容
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                SettingsGroupLabel("本地音乐")
                SettingsCard {
                    SettingsItem(Icons.Rounded.Folder, "管理文件夹", "添加或移除本地音乐目录", onOpenFolders)
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsItem(Icons.Rounded.DeleteSweep, "清理无效文件", if (isCheckingFiles) "检测中…" else "扫描并删除无法播放的残缺音频") { if (!isCheckingFiles) viewModel.scanInvalidFiles() }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsItem(Icons.Rounded.CleaningServices, "清理旧缓存文件", "删除旧版本遗留的封面/歌词缓存") { showCleanOldDialog = true }
                }

                Spacer(Modifier.height(24.dp))
                SettingsGroupLabel("在线音源")
                SettingsCard {
                    SettingsItem(Icons.Rounded.Language, "API 接口地址", if (apiUrl.isNotBlank()) "自定义API地址" else "未设置，点击输入") { apiInput = apiUrl; showApiDialog = true }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { folderPickerLauncher.launch(null) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("下载目录", style = MaterialTheme.typography.bodyLarge)
                            Text("Music/$downloadDir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    // ── 云端播放音质 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val currentIndex = qualityOptions.indexOfFirst { it.first == playQuality }.coerceAtLeast(0)
                                val nextIndex = (currentIndex + 1) % qualityOptions.size
                                playQuality = qualityOptions[nextIndex].first
                                prefs.edit().putString(KEY_PLAY_QUALITY, playQuality).apply()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.HighQuality, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("云端播放音质", style = MaterialTheme.typography.bodyLarge)
                            Text(qualityOptions.firstOrNull { it.first == playQuality }?.second ?: "标准音质", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsGroupLabel("外观")
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showThemeColors = !showThemeColors }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("主题颜色", style = MaterialTheme.typography.bodyLarge)
                            Text(if (themeColorIndex == 0) "跟随系统" else themeColors[themeColorIndex - 1].name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showThemeColors) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                    AnimatedVisibility(visible = showThemeColors) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    ThemeCircle(selected = themeColorIndex == 0, onClick = { themeColorIndex = 0; prefs.edit().putInt("theme_color_index", 0).apply() }) {
                                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(colors = listOf(Color(0xFF6750A4), Color(0xFF0061A4), Color(0xFF006A6A)))))
                                    }
                                }
                                items(themeColors.size) { index ->
                                    val theme = themeColors[index]
                                    ThemeCircle(selected = themeColorIndex == index + 1, onClick = { themeColorIndex = index + 1; prefs.edit().putInt("theme_color_index", index + 1).apply() }) {
                                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(theme.light))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsGroupLabel("播放设置")
                SettingsCard {
                    SettingsSwitchItem(icon = Icons.Rounded.ViewInAr, title = "3D 封面效果", summary = if (enable3DCover) "根据手机倾斜角度旋转封面" else "封面固定不动", checked = enable3DCover, onCheckedChange = { enable3DCover = it; prefs.edit().putBoolean(KEY_3D_COVER, it).apply() })
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsSwitchItem(icon = Icons.Rounded.Lyrics, title = "逐字歌词", summary = if (enableKaraoke) "卡拉OK逐字高亮效果（此功能为实验性，部分歌词会出错）" else "普通歌词显示", checked = enableKaraoke, onCheckedChange = { enableKaraoke = it; prefs.edit().putBoolean(KEY_ENABLE_KARAOKE, it).apply() })
                }

                Spacer(Modifier.height(24.dp))
                SettingsGroupLabel("桌面歌词")
                SettingsCard {
                    SettingsSwitchItem(icon = Icons.Rounded.Lyrics, title = "显示桌面歌词", summary = when { lyricsEnabled && hasOverlayPerm -> "已开启"; lyricsEnabled && !hasOverlayPerm -> "需要授权悬浮窗权限"; else -> "在其他应用上方显示当前歌词" }, checked = lyricsEnabled && hasOverlayPerm, enabled = true, onCheckedChange = { wantOn -> if (wantOn) { if (context.hasOverlayPermission()) { lyricsPrefs.setEnabled(true); context.startDesktopLyrics() } else { showPermDialog = true } } else { lyricsPrefs.setEnabled(false); context.stopDesktopLyrics() } })
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsSwitchItem(icon = Icons.Rounded.Lock, title = "锁定歌词位置", summary = if (isLocked) "锁定中" else "解锁，可拖动", checked = isLocked, enabled = lyricsEnabled && hasOverlayPerm, onCheckedChange = { lyricsPrefs.setLocked(it) })
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                    SettingsSwitchItem(icon = Icons.Rounded.InvertColors, title = "显示背景", summary = if (bgEnabled) "半透明胶囊背景" else "纯文字+阴影", checked = bgEnabled, enabled = lyricsEnabled && hasOverlayPerm, onCheckedChange = { lyricsPrefs.setBgEnabled(it) })
                    AnimatedVisibility(visible = bgEnabled && lyricsEnabled && hasOverlayPerm) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                            SettingsSliderItem(Icons.Rounded.Opacity, "背景透明度", "${(bgAlpha * 100).toInt()}%", bgAlpha, 0.1f..1f) { lyricsPrefs.setBgAlpha(it) }
                            HorizontalDivider(modifier = Modifier.padding(start = 52.dp), thickness = 0.5.dp)
                            SettingsSliderItem(Icons.Rounded.SwapHoriz, "背景宽度", "${(bgWidth * 100).toInt()}%", bgWidth, 0.3f..1f) { lyricsPrefs.setBgWidth(it) }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SettingsGroupLabel("其他")
                SettingsCard { SettingsItem(Icons.Rounded.Info, "关于", "饼音 Skypie Music", onOpenAbout) }

                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.height(80.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    // 弹窗
    if (showPermDialog) {
        AlertDialog(onDismissRequest = { showPermDialog = false }, icon = { Icon(Icons.Rounded.SecurityUpdate, null) }, title = { Text("需要悬浮窗权限") }, text = { Text("桌面歌词需要「显示在其他应用上层」权限。") }, confirmButton = { TextButton(onClick = { showPermDialog = false; lyricsPrefs.setEnabled(true); context.startDesktopLyrics() }) { Text("去授权") } }, dismissButton = { TextButton(onClick = { showPermDialog = false }) { Text("取消") } })
    }
    if (showApiDialog) {
        AlertDialog(onDismissRequest = { showApiDialog = false }, title = { Text("自定义API地址") }, text = { Column { Text("输入你的音乐 API 地址，留空则使用默认地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)); OutlinedTextField(value = apiInput, onValueChange = { apiInput = it }, placeholder = { Text("留空使用默认地址") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { TextButton(onClick = { apiUrl = apiInput.trim(); prefs.edit().putString(KEY_API_URL, apiUrl).apply(); showApiDialog = false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { showApiDialog = false }) { Text("取消") } })
    }
    if (showInvalidDialog && invalidFiles.isNotEmpty()) {
        AlertDialog(onDismissRequest = { showInvalidDialog = false }, title = { Text("发现 ${invalidFiles.size} 个无效文件") }, text = { Column { Text("以下文件无法播放，是否全部删除？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)); invalidFiles.take(5).forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }, confirmButton = { TextButton(onClick = { viewModel.deleteInvalidFiles(); showInvalidDialog = false }) { Text("全部删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { showInvalidDialog = false }) { Text("取消") } })
    }
    if (showCleanOldDialog) {
        AlertDialog(onDismissRequest = { showCleanOldDialog = false }, title = { Text("清理旧缓存") }, text = { Text("将删除旧版本遗留的缓存文件。") }, confirmButton = { TextButton(onClick = { viewModel.cleanOldPrivateFiles(); showCleanOldDialog = false }) { Text("确认清理") } }, dismissButton = { TextButton(onClick = { showCleanOldDialog = false }) { Text("取消") } })
    }
}

// 工具组件
@Composable private fun SettingsGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)), modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable private fun SettingsItem(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable private fun SettingsSwitchItem(icon: ImageVector, title: String, summary: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Row(modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 2) }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable private fun SettingsSliderItem(icon: ImageVector, title: String, summary: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)); Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = Modifier.padding(start = 36.dp))
    }
}

@Composable private fun ThemeCircle(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (selected) 1.15f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow), label = "scale")
    Box(modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        content()
        if (selected) { Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp))) }
    }
}
