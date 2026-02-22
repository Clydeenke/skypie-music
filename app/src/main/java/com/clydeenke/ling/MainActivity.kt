package com.clydeenke.ling

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.clydeenke.ling.service.PlayerController // 确保此路径与你的接口位置完全一致
import com.clydeenke.ling.ui.theme.LingTheme // 统一使用项目命名
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    // 适配 Android 16 (API 35+) 的媒体权限逻辑
    private val requiredPermission
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 2026 规范：如果权限被拒，可以在此处通过 UI 发送事件通知用户功能受限
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 启动页加载
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 2. 沉浸式边缘到边缘适配
        enableEdgeToEdge()

        // 3. 运行时权限检查
        checkAndRequestPermissions()

        // 4. 初始化音频连接
        playerController.connect()

        // 5. 渲染 UI
        setContent {
            LingTheme {
                MainNavigation()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, requiredPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(requiredPermission)
        }
    }

    override fun onDestroy() {
        // 先断开控制器连接，避免内存泄漏
        playerController.release()
        super.onDestroy()
    }
}