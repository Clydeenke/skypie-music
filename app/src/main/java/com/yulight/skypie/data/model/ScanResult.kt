package com.yulight.skypie.domain.model

data class ScanLog(
    val timestamp  : Long   = System.currentTimeMillis(),
    val level      : Level  = Level.INFO,
    val message    : String
) {
    enum class Level { INFO, WARN, ERROR, SUCCESS }
}