package com.wavexzore.toolbox.feature.settings.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [UpdateConfig] 常量与文件名清理（T-311）。 */
class UpdateConfigTest {

    @Test
    fun `合法文件名保持不变`() {
        assertEquals("wave-toolbox-v1.0.0.apk", UpdateConfig.sanitizeForFileName("wave-toolbox-v1.0.0.apk"))
    }

    @Test
    fun `非法字符替换为横线`() {
        assertEquals("a-b-c-.apk", UpdateConfig.sanitizeForFileName("a b/c?.apk"))
        assertEquals("中文-1.apk", UpdateConfig.sanitizeForFileName("中文 1.apk"))
    }

    @Test
    fun `空名回落默认`() {
        assertEquals("update.apk", UpdateConfig.sanitizeForFileName(""))
    }

    @Test
    fun `UA 模板含版本与 SDK`() {
        assertEquals("wave-toolbox/1.0.0 (Android 33)", UpdateConfig.userAgent("1.0.0", 33))
    }

    @Test
    fun `关键常量`() {
        assertEquals(24L * 60 * 60 * 1000, UpdateConfig.THROTTLE_MS)
        assertEquals(200L * 1024 * 1024, UpdateConfig.MAX_APK_BYTES)
        assertEquals(2_000, UpdateConfig.NOTES_MAX_CHARS)
        assertEquals(1L * 1024 * 1024, UpdateConfig.API_READ_CAP_BYTES)
        assertTrue(UpdateConfig.API_LATEST_URL.endsWith("/releases/latest"))
        assertEquals("wave-toolbox-", UpdateConfig.ASSET_NAME_PREFIX)
    }
}
