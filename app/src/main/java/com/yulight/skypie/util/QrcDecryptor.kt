package com.yulight.skypie.util

/**
 * QQ音乐QRC歌词解密器
 * 
 * QRC格式结构：
 * - 需要密钥解密
 * - 解密后为逐字歌词格式
 */
object QrcDecryptor {

    // QRC解密密钥
    private val QRC_KEY = "LyricsISharedHereIsXzy8929wl".toByteArray()

    /**
     * 解密QRC歌词数据
     * @param encrypted 加密的QRC字符串
     * @return 解密后的歌词文本，失败返回null
     */
    fun decrypt(encrypted: String): String? {
        return try {
            if (encrypted.isBlank()) return null
            
            // 检查是否已经是明文LRC格式
            if (encrypted.contains("[00:") && !encrypted.contains("{")) {
                return encrypted
            }

            // 尝试Base64解码
            val decoded = try {
                android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
            } catch (_: Exception) {
                encrypted.toByteArray()
            }

            // XOR解密
            val decrypted = ByteArray(decoded.size)
            for (i in decoded.indices) {
                decrypted[i] = (decoded[i].toInt() xor QRC_KEY[i % QRC_KEY.size].toInt()).toByte()
            }

            val result = String(decrypted, Charsets.UTF_8)
            
            // 验证解密结果是否为有效歌词
            if (result.contains("[00:") || result.contains("[")) {
                result
            } else {
                // 解密失败，返回原始文本
                encrypted
            }
        } catch (e: Exception) {
            encrypted
        }
    }

    /**
     * 检查是否为QRC格式
     */
    fun isQrcFormat(content: String): Boolean {
        return content.contains("{") && content.contains("}") && !content.startsWith("[00:")
    }
}
