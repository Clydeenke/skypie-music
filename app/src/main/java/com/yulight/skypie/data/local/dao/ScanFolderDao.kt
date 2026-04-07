package com.yulight.skypie.data.local.dao

import androidx.room.*
import com.yulight.skypie.data.local.entity.ScanFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanFolderDao {
    @Query("SELECT * FROM scan_folders ORDER BY displayPath ASC")
    fun getAllFolders(): Flow<List<ScanFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ScanFolderEntity): Long

    @Delete
    suspend fun delete(folder: ScanFolderEntity)

    @Query("UPDATE scan_folders SET songCount = :count WHERE id = :id")
    suspend fun updateSongCount(id: Int, count: Int)

    @Query("UPDATE scan_folders SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("SELECT * FROM scan_folders WHERE isEnabled = 1")
    suspend fun getEnabledFolders(): List<ScanFolderEntity>
}