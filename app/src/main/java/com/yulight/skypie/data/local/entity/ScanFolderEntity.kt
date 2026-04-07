package com.yulight.skypie.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yulight.skypie.domain.model.ScanFolder

@Entity(tableName = "scan_folders")
data class ScanFolderEntity(
    @PrimaryKey(autoGenerate = true) val id : Int = 0,
    val uriString   : String,
    val displayPath : String,
    val songCount   : Int     = 0,
    val isEnabled   : Boolean = true
) {
    fun toDomain() = ScanFolder(id, uriString, displayPath, songCount, isEnabled)
}