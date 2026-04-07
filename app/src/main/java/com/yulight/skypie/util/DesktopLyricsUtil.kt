package com.yulight.skypie.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.yulight.skypie.service.DesktopLyricsService

/**
 * 桌面歌词工具函数
 *
 * 这里用的是 Kotlin 扩展函数（Extension Function）——
 * 意思是给 Context 这个已有的类"加方法"，不需要继承它。
 * 调用时写 context.startDesktopLyrics() 就行，跟普通方法一样。
 */

/** 开启桌面歌词（如果没有悬浮窗权限会先跳授权页） */
fun Context.startDesktopLyrics() {
    if (!Settings.canDrawOverlays(this)) {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        return
    }
    startService(
        Intent(this, DesktopLyricsService::class.java)
            .apply { action = DesktopLyricsService.ACTION_SHOW }
    )
}

/** 关闭桌面歌词 */
fun Context.stopDesktopLyrics() {
    startService(
        Intent(this, DesktopLyricsService::class.java)
            .apply { action = DesktopLyricsService.ACTION_HIDE }
    )
}

/** 判断悬浮窗权限是否已授权 */
fun Context.hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)