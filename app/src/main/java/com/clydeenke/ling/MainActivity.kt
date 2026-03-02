package com.clydeenke.ling

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.clydeenke.ling.service.PlayerController
import com.clydeenke.ling.ui.theme.LingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    private val audioPermission
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()
        playerController.connect()

        setContent {
            LingTheme {
                MainNavigation()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // 音频权限
        val hasAudio = ContextCompat.checkSelfPermission(
            this, audioPermission
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            permissionLauncher.launch(audioPermission)
        }

        // ✅ 完整文件访问权限（用于读取 LRC 歌词文件）
        // Android 11+ 需要单独申请，会跳转到系统设置页面让用户手动开启
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}