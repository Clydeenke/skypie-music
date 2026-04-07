package com.yulight.skypie.domain.model

data class ScanFolder(
    val id          : Int,
    val uriString   : String,   // SAF URI
    val displayPath : String,   // 用户看到的路径
    val songCount   : Int = 0,
    val isEnabled   : Boolean = true
)