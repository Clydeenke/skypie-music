// 项目根目录的构建脚本
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Hilt 依赖注入插件
    alias(libs.plugins.hilt) apply false
    // KSP 代码生成，Room和Hilt都需要
    alias(libs.plugins.ksp) apply false
}