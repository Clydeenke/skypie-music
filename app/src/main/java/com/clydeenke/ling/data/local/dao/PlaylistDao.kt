package com.clydeenke.ling.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.clydeenke.ling.data.local.entity.PlaylistEntity
import com.clydeenke.ling.data.local.entity.PlaylistSongCrossRef
import com.clydeenke.ling.data.local.entity.PlaylistWithSongEntities
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ── 歌单基础 CRUD ─────────────────────────────────────────────────────────

    /** 获取所有歌单（含歌曲列表），响应式，数据变化时自动通知 */
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongEntities>>

    /** 获取单个歌单（含歌曲列表），详情页使用 */
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongEntities?>

    /** 新建歌单，返回新行的 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    /** 删除歌单（关联的 playlist_songs 记录由 CASCADE 自动删除） */
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // ── 歌单-歌曲关联 ─────────────────────────────────────────────────────────

    /** 将歌曲加入歌单；若已存在则忽略（IGNORE 防止重复插入） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    /** 从歌单移除某首歌 */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
}