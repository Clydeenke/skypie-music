package com.clydeenke.ling.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 全局应用模块
 * 作用：提供全 App 共享的单例零件（如播放器引擎）
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供 ExoPlayer 实例
     * 这里的 @Singleton 确保整个 App 运行期间只有一个播放引擎在工作
     * 这样你在后台听歌时，切回前台看到的进度才是实时同步的
     */
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).build()
    }

    // 提示：数据库的 provideDatabase 已经在 DatabaseModule.kt 中定义过了
    // 保持职责单一，AppModule 以后可以放更多通用的第三方工具
}