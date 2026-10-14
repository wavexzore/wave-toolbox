package com.wavexzore.toolbox.feature.settings.update

import java.io.File
import java.security.MessageDigest

/** 流式 SHA-256（T-306；下载校验单一实现）。 */
object Sha256 {

    /** 文件十六进制摘要（小写）。 */
    fun hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
    }

    /** 字节数组摘要（测试向量用）。 */
    fun hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { b ->
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4])
            append(HEX[v and 0x0f])
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
