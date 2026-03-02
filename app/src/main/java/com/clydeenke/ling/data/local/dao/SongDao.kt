package com.clydeenke.ling.data.local.dao

import androidx.room.*
import com.clydeenke.ling.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<SongEntity>>


    @Query("""
        SELECT * FROM songs
        WHERE title  LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album  LIKE '%' || :q || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(q: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE folderPath = :path ORDER BY title ASC")
    fun getSongsByFolder(path: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int
}