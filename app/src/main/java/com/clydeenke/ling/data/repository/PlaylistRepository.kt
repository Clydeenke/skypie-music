package com.clydeenke.ling.data.repository

import com.clydeenke.ling.data.local.MusicDatabase
import com.clydeenke.ling.data.local.entity.PlaylistEntity
import com.clydeenke.ling.data.local.entity.PlaylistSongCrossRef
import com.clydeenke.ling.domain.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌单数据仓库
 * 直接注入 MusicDatabase 以复用同一个数据库实例，无需在 AppModule 单独 provide PlaylistDao
 */
@Singleton
class PlaylistRepository @Inject constructor(db: MusicDatabase) {

    private val dao = db.playlistDao()

    // ── 查询 ──────────────────────────────────────────────────────────────────

    /** 所有歌单（含歌曲列表，用于列表页 + 详情页共用） */
    fun getAllPlaylists(): Flow<List<Playlist>> =
        dao.getAllPlaylistsWithSongs().map { list ->
            list.map { pws ->
                Playlist(
                    id        = pws.playlist.id,
                    name      = pws.playlist.name,
                    createdAt = pws.playlist.createdAt,
                    songs     = pws.songs.map { it.toDomain() }   // SongEntity 扩展函数
                )
            }
        }

    /** 单个歌单详情（详情页订阅使用），歌单不存在时 emit null */
    fun getPlaylistDetail(playlistId: Long): Flow<Playlist?> =
        dao.getPlaylistWithSongs(playlistId).map { pws ->
            pws?.let {
                Playlist(
                    id        = it.playlist.id,
                    name      = it.playlist.name,
                    createdAt = it.playlist.createdAt,
                    songs     = it.songs.map { song -> song.toDomain() }
                )
            }
        }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlistId: Long) =
        dao.deletePlaylist(PlaylistEntity(id = playlistId, name = ""))

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) =
        dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        dao.removeSongFromPlaylist(playlistId, songId)
}