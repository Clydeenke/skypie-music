package com.yulight.skypie.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.yulight.skypie.MainActivity
import com.yulight.skypie.R
import com.yulight.skypie.domain.lyrics.DesktopLyricsPrefs
import com.yulight.skypie.util.startDesktopLyrics
import com.yulight.skypie.util.stopDesktopLyrics
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var exoPlayer: ExoPlayer
    @Inject lateinit var lyricsPrefs: DesktopLyricsPrefs

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        // 自定义命令标识，代替原来的 Intent Action
        const val COMMAND_LYRICS_TOGGLE = "com.yulight.skypie.COMMAND_LYRICS_TOGGLE"
    }

    override fun onCreate() {
        super.onCreate()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 1. 构建 Session 并注入 Callback
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(LyricsSessionCallback()) // 设置回调！核心逻辑在此
            .build()

        // 2. 监听歌词状态变化 → 动态更新 Media3 系统的自定义布局（系统会自动刷新通知栏 UI）
        serviceScope.launch {
            combine(lyricsPrefs.isEnabled, lyricsPrefs.isLocked) { enabled, locked ->
                enabled to locked
            }.collect { (enabled, locked) ->
                mediaSession?.let { session ->
                    // 构建最新状态的按钮
                    val newButton = buildLyricsCommandButton(enabled, locked)
                    // 提交给系统，系统立刻刷新通知栏和锁屏界面
                    session.setCustomLayout(ImmutableList.of(newButton))
                }
            }
        }
    }

    /**
     * 根据当前状态，构建一个系统可识别的 CommandButton。
     * Media3 强制要求使用本地 Drawable ID，不再支持 Bitmap 传输。
     */
    private fun buildLyricsCommandButton(enabled: Boolean, locked: Boolean): CommandButton {
        // 判断当前状态决定使用哪个图标 (你需要自行在 res/drawable 准备好这三个图标)
        val iconResId = when {
            !enabled -> R.drawable.ic_lyrics_off      // 状态：关闭
            locked -> R.drawable.ic_lyrics_locked   // 状态：已锁定
            else -> R.drawable.ic_lyrics_on         // 状态：开启未锁
        }

        val displayName = when {
            !enabled -> "开启歌词"
            locked -> "解锁歌词"
            else -> "关闭歌词"
        }

        return CommandButton.Builder()
            .setDisplayName(displayName)
            .setIconResId(iconResId) // 强制使用资源 ID
            .setSessionCommand(SessionCommand(COMMAND_LYRICS_TOGGLE, Bundle.EMPTY))
            .build()
    }

    /**
     * 你的核心逻辑完美保留
     */
    private fun handleLyricsToggle() {
        val enabled = lyricsPrefs.isEnabled.value
        val locked = lyricsPrefs.isLocked.value
        when {
            !enabled -> startDesktopLyrics()               // 关 → 开
            enabled && locked -> lyricsPrefs.setLocked(false) // 锁定 → 解锁
            enabled && !locked -> stopDesktopLyrics()         // 开 → 关
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        super.onDestroy()
    }

    // ── 2026 Media3 新架构：通过 Callback 处理自定义按钮和点击事件 ──────────────────────────────────

    private inner class LyricsSessionCallback : MediaSession.Callback {

        // 客户端/系统连接时，告诉系统我们支持哪些自定义命令，以及初始按钮长什么样
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // 声明支持我们自定义的歌词切换命令
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_LYRICS_TOGGLE, Bundle.EMPTY))
                .build()

            // 获取当前按钮状态
            val initialButton = buildLyricsCommandButton(
                lyricsPrefs.isEnabled.value,
                lyricsPrefs.isLocked.value
            )

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(ImmutableList.of(initialButton)) // 把按钮发给系统
                .build()
        }

        // 当用户点击通知栏上的按钮时，Media3 会回调到这里
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == COMMAND_LYRICS_TOGGLE) {

                handleLyricsToggle() // 执行你的业务逻辑

                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // 处理媒体控件按钮（上一首、下一首等）
        override fun onMediaButtonEvent(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaButtonEvent: android.content.Intent
        ): Boolean {
            val intent = mediaButtonEvent
            if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
                val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (event?.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS 
                    && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    // 上一首：始终切到上一首（绕过 ExoPlayer 内置的3秒判断）
                    val prevIndex = (exoPlayer.currentMediaItemIndex - 1).coerceAtLeast(0)
                    exoPlayer.seekTo(prevIndex, 0L)
                    return true
                }
            }
            return super.onMediaButtonEvent(session, controller, mediaButtonEvent)
        }
    }
}