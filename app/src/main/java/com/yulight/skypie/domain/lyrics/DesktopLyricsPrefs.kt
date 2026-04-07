package com.yulight.skypie.domain.lyrics

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopLyricsPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("skypie_desktop_lyrics", 0)

    // ── 是否已开启（用户意图，有权限且打开 = true） ───────────────────────────
    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    fun setEnabled(on: Boolean) { _isEnabled.value = on; save(KEY_ENABLED, on) }

    // ── 锁定状态 ──────────────────────────────────────────────────────────────
    private val _isLocked = MutableStateFlow(prefs.getBoolean(KEY_LOCKED, true))
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()
    fun setLocked(locked: Boolean) { _isLocked.value = locked; save(KEY_LOCKED, locked) }

    // ── 字号（sp）12~24 ───────────────────────────────────────────────────────
    private val _fontSize = MutableStateFlow(prefs.getFloat(KEY_FONT_SIZE, 14f))
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()
    fun setFontSize(sp: Float) { _fontSize.value = sp; save(KEY_FONT_SIZE, sp) }

    // ── 文字颜色（0 = 跟随 Monet） ────────────────────────────────────────────
    private val _colorArgb = MutableStateFlow(prefs.getInt(KEY_COLOR, 0))
    val colorArgb: StateFlow<Int> = _colorArgb.asStateFlow()
    fun setColor(argb: Int) { _colorArgb.value = argb; save(KEY_COLOR, argb) }

    // ── 背景透明度 ────────────────────────────────────────────────────────────
    private val _bgAlpha = MutableStateFlow(prefs.getFloat(KEY_BG_ALPHA, 0.55f))
    val bgAlpha: StateFlow<Float> = _bgAlpha.asStateFlow()
    fun setBgAlpha(alpha: Float) { _bgAlpha.value = alpha; save(KEY_BG_ALPHA, alpha) }

    // ── 背景开关 ──────────────────────────────────────────────────────────────
    private val _bgEnabled = MutableStateFlow(prefs.getBoolean(KEY_BG_ENABLED, true))
    val bgEnabled: StateFlow<Boolean> = _bgEnabled.asStateFlow()
    fun setBgEnabled(on: Boolean) { _bgEnabled.value = on; save(KEY_BG_ENABLED, on) }

    // ── 背景宽度（屏幕宽度比例 0.3~1.0） ─────────────────────────────────────
    private val _bgWidth = MutableStateFlow(prefs.getFloat(KEY_BG_WIDTH, 0.85f))
    val bgWidth: StateFlow<Float> = _bgWidth.asStateFlow()
    fun setBgWidth(fraction: Float) { _bgWidth.value = fraction; save(KEY_BG_WIDTH, fraction) }

    private fun save(key: String, v: Float)   = prefs.edit().putFloat(key, v).apply()
    private fun save(key: String, v: Int)     = prefs.edit().putInt(key, v).apply()
    private fun save(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    companion object {
        private const val KEY_ENABLED   = "is_enabled"
        private const val KEY_LOCKED    = "is_locked"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_COLOR     = "text_color"
        private const val KEY_BG_ALPHA  = "bg_alpha"
        private const val KEY_BG_ENABLED= "bg_enabled"
        private const val KEY_BG_WIDTH  = "bg_width"

        val PRESET_COLORS = listOf(
            0,
            Color.White.toArgb(),
            0xFFFFE066.toInt(),
            0xFF80DEEA.toInt(),
            0xFFCE93D8.toInt(),
        )
    }
}