package com.wavexzore.toolbox.feature.settings.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ReleaseParser] 规则锁定（T-304；fixture 取自 _recon/00 真实响应形状）。 */
class ReleaseParserTest {

    private val realSha = "685e70e413b47615e08d442cab6f4a31364192d81d15cbde5709d5ec937fde1f"

    private fun fixture(
        tag: String = "v1.0.0",
        body: String = "首个版本\n\nSHA-256: $realSha",
        assets: String = """[{"name":"wave-toolbox-v1.0.0.apk","browser_download_url":"https://github.com/wavexzore/wave-toolbox/releases/download/v1.0.0/wave-toolbox-v1.0.0.apk","size":14417825,"digest":"sha256:$realSha"}]""",
        htmlUrl: String = "https://github.com/wavexzore/wave-toolbox/releases/tag/v1.0.0",
    ) = """
        {
          "tag_name": "$tag",
          "name": "wave工具箱 $tag",
          "draft": false,
          "prerelease": false,
          "body": ${escape(body)},
          "published_at": "2026-10-14T06:28:16Z",
          "html_url": "$htmlUrl",
          "assets": $assets
        }
    """.trimIndent()

    private fun escape(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `真实形状样本解析全字段`() {
        val info = assertNotNull(ReleaseParser.parse(fixture()))
        assertEquals("v1.0.0", info.tag)
        assertEquals("2026-10-14T06:28:16Z", info.publishedAt)
        assertTrue(info.notes.contains("首个版本"))
        val asset = assertNotNull(info.asset)
        assertEquals("wave-toolbox-v1.0.0.apk", asset.name)
        assertEquals(14417825L, asset.size)
        assertEquals("sha256:$realSha", asset.digest)
    }

    @Test
    fun `api digest 缺失时正文兜底`() {
        val body = "更新说明\nSHA-256：$realSha\n其他行"
        val assets = """[{"name":"wave-toolbox-v1.0.0.apk","browser_download_url":"https://x","size":10}]"""
        val info = assertNotNull(ReleaseParser.parse(fixture(body = body, assets = assets)))
        assertEquals("sha256:$realSha", info.asset?.digest)
    }

    @Test
    fun `api digest 合法时优先于正文`() {
        val other = "a".repeat(64)
        val info = assertNotNull(ReleaseParser.parse(fixture(body = "SHA-256: $other")))
        assertEquals("sha256:$realSha", info.asset?.digest)
    }

    @Test
    fun `digest 非法且正文无则 null`() {
        val assets = """[{"name":"wave-toolbox-v1.0.0.apk","browser_download_url":"https://x","size":10,"digest":"md5:abc"}]"""
        val info = assertNotNull(ReleaseParser.parse(fixture(body = "无摘要行", assets = assets)))
        assertNull(info.asset?.digest)
    }

    @Test
    fun `tag 缺失或非法返回 null`() {
        assertNull(ReleaseParser.parse(fixture(tag = "")))
        assertNull(ReleaseParser.parse(fixture(tag = "release-1")))
        assertNull(ReleaseParser.parse("not json"))
    }

    @Test
    fun `无 apk 资产时为成功但 asset 为空`() {
        val assets = """[{"name":"source.zip","browser_download_url":"https://x","size":10}]"""
        val info = assertNotNull(ReleaseParser.parse(fixture(assets = assets)))
        assertNull(info.asset)
    }

    @Test
    fun `精确命名资产优先`() {
        val assets = """
            [{"name":"other.apk","browser_download_url":"https://other","size":1},
             {"name":"wave-toolbox-v1.0.0.apk","browser_download_url":"https://exact","size":2}]
        """.trimIndent()
        val info = assertNotNull(ReleaseParser.parse(fixture(assets = assets)))
        assertEquals("https://exact", info.asset?.url)
    }

    @Test
    fun `回落到首个 apk`() {
        val assets = """[{"name":"legacy-build.apk","browser_download_url":"https://legacy","size":3}]"""
        val info = assertNotNull(ReleaseParser.parse(fixture(assets = assets)))
        assertEquals("https://legacy", info.asset?.url)
    }

    @Test
    fun `notes 超长码点安全截断`() {
        val long = buildString {
            repeat(1000) { append("A🎯") } // 1000 组代理对，共 3000 码元
        }
        val info = assertNotNull(ReleaseParser.parse(fixture(body = long + "\nSHA-256: $realSha")))
        assertTrue(info.notes.endsWith("…"))
        assertTrue(info.notes.length <= UpdateConfig.NOTES_MAX_CHARS + 1)
        // 结尾无孤立高代理
        assertTrue(!Character.isHighSurrogate(info.notes[info.notes.length - 2]))
    }

    @Test
    fun `短 notes 不截断`() {
        val info = assertNotNull(ReleaseParser.parse(fixture(body = "短说明")))
        assertEquals("短说明", info.notes)
    }
}
