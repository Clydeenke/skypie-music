package com.yulight.skypie.util

import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

/**
 * 酷狗KRC歌词解密器
 * 
 * KRC格式：
 * - 前4字节：签名 "KCRE" (0x4B 0x43 0x52 0x45)
 * - 后续字节：XOR加密 + zlib压缩
 */
object KrcDecryptor {

    // KRC解密密钥
    private val KRC_KEY = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 126,
        94, 51, 100, 39, 49, 118, 105, 105
    )

    /**
     * 解密KRC歌词数据
     * @param encrypted 加密的KRC字节数组
     * @return 解密后的歌词文本，失败返回null
     */
    fun decrypt(encrypted: ByteArray): String? {
        return try {
            // 检查签名 "KCRE"
            if (encrypted.size < 4) return null
            if (encrypted[0] != 0x4B.toByte() || encrypted[1] != 0x43.toByte() ||
                encrypted[2] != 0x52.toByte() || encrypted[3] != 0x45.toByte()) {
                // 不是KRC格式，可能是纯文本LRC
                return String(encrypted)
            }

            // 跳过前4字节签名
            val encryptedData = encrypted.copyOfRange(4, encrypted.size)

            // XOR解密
            val decrypted = ByteArray(encryptedData.size)
            for (i in encryptedData.indices) {
                decrypted[i] = (encryptedData[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
            }

            // zlib解压
            decompress(decrypted)
        } catch (e: Exception) {
            // 解密失败，返回原始文本
            try { String(encrypted) } catch (_: Exception) { null }
        }
    }

    /**
     * 解密KRC歌词字符串
     * @param encrypted 加密的KRC字符串
     * @return 解密后的歌词文本
     */
    fun decryptString(encrypted: String): String {
        return decrypt(encrypted.toByteArray()) ?: encrypted
    }

    /**
     * zlib解压
     */
    private fun decompress(compressed: ByteArray): String {
        val inflater = InflaterInputStream(ByteArrayInputStream(compressed))
        val buffer = ByteArray(1024)
        val output = StringBuilder()
        var bytesRead: Int
        while (inflater.read(buffer).also { bytesRead = it } != -1) {
            output.append(String(buffer, 0, bytesRead))
        }
        inflater.close()
        return output.toString()
    }

    /**
     * 检查是否为KRC格式
     */
    fun isKrcFormat(data: ByteArray): Boolean {
        return data.size >= 4 &&
               data[0] == 0x4B.toByte() && // K
               data[1] == 0x43.toByte() && // C
               data[2] == 0x52.toByte() && // R
               data[3] == 0x45.toByte()    // E
    }
}
