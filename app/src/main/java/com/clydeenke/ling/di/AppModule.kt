package com.clydeenke.ling.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.clydeenke.ling.data.local.MusicDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Ling 全局依赖注入中心 (AppModule)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 【核心引擎】提供 ExoPlayer
     */
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        // 1. 定义音频属性：告诉系统这是音乐播放，并启用自动处理焦点
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 2. 构建播放器并注入属性
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // 第二个参数 true 表示自动处理焦点
            .build()
    }
    /**
     * 【记忆中心】提供 Room 数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "ling_music_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 【存取接口】提供 SongDao
     */
    @Provides
    @Singleton
    fun provideSongDao(database: MusicDatabase) = database.songDao()
}