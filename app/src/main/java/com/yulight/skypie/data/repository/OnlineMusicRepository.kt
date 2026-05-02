package com.yulight.skypie.data.repository

import com.yulight.skypie.data.remote.AudioQuality
import com.yulight.skypie.data.remote.MusicSource
import com.yulight.skypie.data.remote.OnlineMusicApi
import com.yulight.skypie.data.remote.OnlineSong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线音乐仓库层
 *
 * ViewModel 只和这一层交互，不直接依赖 [OnlineMusicApi]。
 * 未来如果要加缓存、分页、或切换数据源，只改这里。
 */
@Singleton
class OnlineMusicRepository @Inject constructor(
    private val api: OnlineMusicApi
) {

    // ── 搜索 ──────────────────────────────────────────────────────────────────

    suspend fun search(keyword: String, source: MusicSource): List<OnlineSong> = when (source) {
<<<<<<< HEAD
        MusicSource.KUWO  -> api.searchKuwo(keyword).distinctBy { it.id }
        MusicSource.KUGOU -> api.searchKugou(keyword).distinctBy { it.id }
=======
        MusicSource.KUWO  -> api.searchKuwo(keyword)
        MusicSource.KUGOU -> api.searchKugou(keyword)
>>>>>>> origin/master
    }

    // ── 榜单 ──────────────────────────────────────────────────────────────────

    suspend fun fetchRank(rankId: Int, page: Int): List<OnlineSong> =
<<<<<<< HEAD
        api.fetchKuwoRank(rankId, page).distinctBy { it.id }
=======
        api.fetchKuwoRank(rankId, page)
>>>>>>> origin/master

    // ── 播放链接（需要用户配置 API 地址） ─────────────────────────────────────

    /**
     * 解析播放链接
     * @return 流媒体 URL，null 表示解析失败或无此音质
     */
    suspend fun resolvePlayUrl(
        apiBase : String,
        song    : OnlineSong,
        quality : AudioQuality
    ): String? = when (song.source) {
        MusicSource.KUWO  -> api.resolveKuwoPlayUrl(apiBase, song.id, quality.level)
        MusicSource.KUGOU -> api.resolveKugouPlayUrl(apiBase, song.id, quality.level)
    }

    // ── 歌词 ──────────────────────────────────────────────────────────────────

    suspend fun fetchLyric(song: OnlineSong): String = when (song.source) {
        MusicSource.KUWO  -> api.fetchKuwoLyric(song.id)
        MusicSource.KUGOU -> api.fetchKugouLyric(song.id)
    }
}