package com.clydeenke.ling.service

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.clydeenke.ling.MainActivity
import com.clydeenke.ling.R
import com.clydeenke.ling.domain.lyrics.DesktopLyricsPrefs
import com.clydeenke.ling.domain.lyrics.LyricsManager
import com.clydeenke.ling.ui.overlay.DesktopLyricsOverlay
import com.clydeenke.ling.ui.overlay.ServiceLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class DesktopLyricsService : Service() {

    @Inject lateinit var lyricsManager: LyricsManager
    @Inject lateinit var lyricsPrefs  : DesktopLyricsPrefs

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val serviceScope   = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val showControlPanel = MutableStateFlow(false)
    // true = 浮层出现在歌词上方（窗口靠近屏幕底部时）
    private val showPanelAbove   = MutableStateFlow(false)

    companion object {
        const val ACTION_SHOW = "com.clydeenke.ling.DESKTOP_LYRICS_SHOW"
        const val ACTION_HIDE = "com.clydeenke.ling.DESKTOP_LYRICS_HIDE"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID      = "ling_desktop_lyrics"
        // windowY > 屏幕高度 * 此比例时，浮层改为出现在上方
        private const val PANEL_ABOVE_THRESHOLD = 0.65f
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate(); lifecycleOwner.onStart(); lifecycleOwner.onResume()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 监听锁定状态 → 实时更新 WindowManager flags（Settings 里解锁立刻生效）
        serviceScope.launch {
            lyricsPrefs.isLocked.collect { locked ->
                val view = overlayView ?: return@collect
                windowParams.flags = if (locked) lockedFlags() else unlockedFlags()
                if (locked) showControlPanel.value = false
                runCatching { windowManager.updateViewLayout(view, windowParams) }
            }
        }

        // 监听背景宽度 → 实时更新窗口宽度
        serviceScope.launch {
            lyricsPrefs.bgWidth.collect { fraction ->
                val view = overlayView ?: return@collect
                windowParams.width = (resources.displayMetrics.widthPixels * fraction).toInt()
                runCatching { windowManager.updateViewLayout(view, windowParams) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> { hideOverlay(); stopSelf() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        serviceScope.cancel()
        lifecycleOwner.onPause(); lifecycleOwner.onStop(); lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        lyricsPrefs.setEnabled(true)   // 同步给 MusicService 读取

        val screenWidth = resources.displayMetrics.widthPixels
        windowParams = WindowManager.LayoutParams(
            (screenWidth * lyricsPrefs.bgWidth.value).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (lyricsPrefs.isLocked.value) lockedFlags() else unlockedFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 160
        }

        updatePanelDirection()

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val currentLine  by lyricsManager.currentLine.collectAsState()
                val hasLyrics    by lyricsManager.hasLyrics.collectAsState()
                val isLocked     by lyricsPrefs.isLocked.collectAsState()
                val fontSize     by lyricsPrefs.fontSize.collectAsState()
                val colorArgb    by lyricsPrefs.colorArgb.collectAsState()
                val bgAlpha      by lyricsPrefs.bgAlpha.collectAsState()
                val bgEnabled    by lyricsPrefs.bgEnabled.collectAsState()
                val showPanel    by showControlPanel.collectAsState()
                val panelAbove   by showPanelAbove.collectAsState()

                DesktopLyricsOverlay(
                    text             = currentLine,
                    hasLyrics        = hasLyrics,
                    isLocked         = isLocked,
                    showControlPanel = showPanel,
                    showPanelAbove   = panelAbove,
                    fontSize         = fontSize,
                    colorArgb        = colorArgb,
                    bgAlpha          = bgAlpha,
                    bgEnabled        = bgEnabled,
                    onLock           = { lyricsPrefs.setLocked(true) },
                    onTogglePanel    = {
                        updatePanelDirection()  // 每次打开浮层重新判断方向
                        showControlPanel.value = !showControlPanel.value
                    },
                    onDrag           = { dx, dy -> handleDrag(dx, dy) },
                    onFontSizeChange = { lyricsPrefs.setFontSize(it) },
                    onColorChange    = { lyricsPrefs.setColor(it) },
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, windowParams)
    }

    private fun hideOverlay() {
        showControlPanel.value = false
        lyricsPrefs.setEnabled(false)  // 同步给 MusicService
        overlayView?.let { runCatching { windowManager.removeView(it) }; overlayView = null }
    }

    /** 根据当前窗口 Y 坐标决定浮层出现在上方还是下方 */
    private fun updatePanelDirection() {
        if (!::windowParams.isInitialized) return
        val screenHeight = resources.displayMetrics.heightPixels
        showPanelAbove.value = windowParams.y > screenHeight * PANEL_ABOVE_THRESHOLD
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val view = overlayView ?: return
        windowParams.x += dx.toInt()
        windowParams.y += dy.toInt()
        updatePanelDirection()  // 拖动时实时更新浮层方向
        runCatching { windowManager.updateViewLayout(view, windowParams) }
    }

    private fun lockedFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun unlockedFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_LOW).apply {
                description = "桌面歌词悬浮窗运行时的常驻通知"; setShowBadge(false)
            }.also { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(it) }
        }
    }

    private fun buildNotification(): Notification {
        val openPi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1,
            Intent(this, DesktopLyricsService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("饼音 · 桌面歌词").setContentText("正在显示桌面歌词")
            .setContentIntent(openPi).addAction(0, "关闭", stopPi)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}