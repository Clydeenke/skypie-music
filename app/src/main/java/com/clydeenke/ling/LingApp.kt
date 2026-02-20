package com.clydeenke.ling

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用程序入口类
 * 作用：它是 Hilt 依赖注入的“电源开关”，必须在 AndroidManifest 中注册
 */
@HiltAndroidApp // 告诉 Hilt：从这里开始生成所有的零件代码
class LingApp : Application()