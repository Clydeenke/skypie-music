package com.clydeenke.ling.data.local.dao

import androidx.room.*
import com.clydeenke.ling.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲增删改查接口
 * 作用：定义我们要对数据库做的所有动作
 */
@Dao
interface SongDao {

    // 1. 获取所有歌曲：按添加时间倒序（最新的在上面）
    // Flow 的意思是：只要数据库一变，界面会自动跟着刷新
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    // 2. 按专辑找歌
    @Query("SELECT * FROM songs WHERE album = :album ORDER BY title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    // 3. 按歌手找歌
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    // 4. 搜索功能：只要歌名或歌手名里包含这个词就行
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    // 5. 存入歌曲：如果 ID 重复了，就覆盖掉旧的 (REPLACE)
    // suspend 表示：这是耗时操作，要在后台悄悄运行，不卡界面
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    // 6. 删掉所有歌曲：通常在用户点击“重新扫描”时使用
    @Query("DELETE FROM songs")
    suspend fun clearAll()
}