package com.clydeenke.ling.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 音乐后台服务
 * 作用：承载播放引擎，并与手机通知栏、锁屏控制台连接
 */
@AndroidEntryPoint // 只有加了这个，Hilt 才能注入 exoPlayer
class MusicService : MediaSessionService() {

    // 注入播放器引擎：这个零件稍后会在 di 模块里定义
    @Inject
    lateinit var exoPlayer: ExoPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 核心逻辑：把播放器包装成一个“会话”，让系统、耳机线控都能操作它
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    // 当系统或 UI 界面想要控制播放时，把这个会话递出去
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // 释放资源：关掉播放器，防止 App 关了还在偷偷吃电
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}