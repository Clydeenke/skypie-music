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

    // ── 榜单缓存（Singleton级别，跨ViewModel持久） ──
    private val rankCache = mutableMapOf<Int, List<OnlineSong>>()
    private val rankSongsCache = mutableMapOf<Int, List<OnlineSong>>()

    // ── 搜索 ──────────────────────────────────────────────────────────────────

    suspend fun search(keyword: String, source: MusicSource, page: Int = 1): List<OnlineSong> = when (source) {
        MusicSource.KUWO    -> api.searchKuwo(keyword, page).distinctBy { it.id }
        MusicSource.KUGOU   -> api.searchKugou(keyword, page).distinctBy { it.id }
        MusicSource.NETEASE -> api.searchNetease(keyword, page).distinctBy { it.id }
        MusicSource.QQ      -> api.searchQQ(keyword, page).distinctBy { it.id }
    }

    // ── 榜单（带缓存） ────────────────────────────────────────────────────────

    suspend fun fetchRankWithCache(rankId: Int, page: Int = 1): List<OnlineSong> {
        // 只缓存第一页
        if (page == 1) {
            rankCache[rankId]?.let { return it }
        }
        val songs = api.fetchKuwoRank(rankId, page).distinctBy { it.id }
        if (page == 1 && songs.isNotEmpty()) {
            rankCache[rankId] = songs
        }
        return songs
    }

    suspend fun fetchRankSongsWithCache(rankId: Int, page: Int = 1, take: Int = 3): List<OnlineSong> {
        val cacheKey = rankId * 100 + page
        rankSongsCache[cacheKey]?.let { return it }
        val songs = fetchRankWithCache(rankId, page).take(take)
        if (songs.isNotEmpty()) {
            rankSongsCache[cacheKey] = songs
        }
        return songs
    }

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
        MusicSource.KUWO    -> api.resolveKuwoPlayUrl(apiBase, song.id, quality.level)
        MusicSource.KUGOU   -> api.resolveKugouPlayUrl(apiBase, song.id, quality.level)
        MusicSource.NETEASE -> api.resolveNeteasePlayUrl(apiBase, song.id, quality.level)
        MusicSource.QQ      -> api.resolveQQPlayUrl(apiBase, song.id, quality.level)
    }

    // ── 歌词 ──────────────────────────────────────────────────────────────────

    suspend fun fetchLyric(song: OnlineSong): String = when (song.source) {
        MusicSource.KUWO    -> api.fetchKuwoLyric(song.id)
        MusicSource.KUGOU   -> api.fetchKugouLyric(song.id)
        MusicSource.NETEASE -> api.fetchNeteaseLyric(song.id)
        MusicSource.QQ      -> api.fetchQQLyric(song.id)
    }
}