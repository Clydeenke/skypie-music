plugins {
    // 调用在 toml 文件里定义的插件
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    // 【关键】必须和文件夹路径一致
    namespace = "com.clydeenke.ling"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clydeenke.ling"
        minSdk = 26       // 最低支持 Android 8.0 (保证音频特性的稳定性)
        targetSdk = 35    // 目标版本
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 开启正式版混淆（压缩代码）
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        // 使用 Java 17 标准编译
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true // 开启 Compose 界面功能
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 基础核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // UI 界面库 (Compose BOM 自动管理版本)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt 依赖注入 (自动化工具)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler) // KSP 负责在编译时生成代码
    implementation(libs.hilt.navigation.compose)

    // Room 数据库 (存歌曲数据)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Media3 播放引擎 (核心组件)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // 图片加载 (显示封面)
    implementation(libs.coil.compose)

    // 协程 (后台扫描音乐不卡顿)
    implementation(libs.kotlinx.coroutines.android)
}