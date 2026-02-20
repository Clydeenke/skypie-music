// 项目根目录的构建配置文件
plugins {
    // 这里的 apply false 表示：在根目录声明插件版本，但不在这里立即执行
    // 具体的安装由 app 目录下的 build.gradle.kts 完成

    alias(libs.plugins.android.application) apply false // 安卓应用插件
    alias(libs.plugins.kotlin.android) apply false     // Kotlin 编程语言插件
    alias(libs.plugins.kotlin.compose) apply false     // Compose 界面编译器
    alias(libs.plugins.hilt) apply false               // Hilt 依赖注入插件
    alias(libs.plugins.ksp) apply false                // KSP 注解处理插件
}