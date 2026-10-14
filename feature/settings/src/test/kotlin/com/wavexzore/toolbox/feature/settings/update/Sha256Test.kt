package com.wavexzore.toolbox.feature.settings.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** [Sha256] 已知向量（T-306）。 */
class Sha256Test {

    @Test
    fun `空输入与已知向量`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".toByteArray()))
    }

    @Test
    fun `文件摘要与字节一致`() {
        val file = File.createTempFile("sha", ".bin")
        try {
            file.writeBytes("abc".toByteArray())
            assertEquals(Sha256.hex("abc".toByteArray()), Sha256.hex(file))
        } finally {
            file.delete()
        }
    }
}
