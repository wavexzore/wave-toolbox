package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 规则包 codec 用例（T-003）：解析/校验/隔离契约。 */
class AdSkipPackCodecTest {

    private val validPack = """
        {
          schemaVersion: 1,
          pkg: "com.example.app",
          packVersion: 2,
          groups: [
            { id: "a#splash", mode: "splash", matches: ['[text*="跳过"]'], keywords: ["跳过 3s"], actionMaximum: 1 },
          ],
          unknownField: true,
        }
    """.trimIndent()

    @Test
    fun parsesValidPackAndIgnoresUnknownFields() {
        val pack = AdSkipPackCodec.parse(validPack)
        assertEquals("com.example.app", pack.pkg)
        assertEquals(2, pack.packVersion)
        assertEquals(1, pack.groups.size)
        assertEquals(AdSkipGroup.MODE_SPLASH, pack.groups[0].mode)
        assertEquals(listOf("跳过 3s"), pack.groups[0].keywords)
    }

    @Test
    fun defaultsApplied() {
        val pack = AdSkipPackCodec.parse("""{ pkg: "p", groups: [ { id: "g" } ] }""")
        val group = pack.groups.single()
        assertEquals(AdSkipGroup.MODE_SPLASH, group.mode)
        assertEquals(800L, group.actionCd)
        assertEquals(1, group.actionMaximum)
        assertTrue(group.enabled)
    }

    @Test
    fun blankPkgFails() {
        assertFailsWith<IllegalArgumentException> { AdSkipPackCodec.parse("""{ pkg: " ", groups: [] }""") }
    }

    @Test
    fun unsupportedSchemaVersionFails() {
        assertFailsWith<IllegalArgumentException> {
            AdSkipPackCodec.parse("""{ schemaVersion: 9, pkg: "p", groups: [] }""")
        }
    }

    @Test
    fun unknownModeFails() {
        assertFailsWith<IllegalArgumentException> {
            AdSkipPackCodec.parse("""{ pkg: "p", groups: [ { id: "g", mode: "wat" } ] }""")
        }
    }

    @Test
    fun badFileIsolatedByTryCatchContract() {
        // 坏文件抛错 → loader 逐文件捕获；好文件仍可解析（隔离契约）
        val good = runCatching { AdSkipPackCodec.parse(validPack) }.getOrNull()
        val bad = runCatching { AdSkipPackCodec.parse("not json5 at all {{{") }.getOrNull()
        assertEquals("com.example.app", good?.pkg)
        assertEquals(null, bad)
    }

    @Test
    fun positionAndPathParsed() {
        val pack = AdSkipPackCodec.parse(
            """
            { pkg: "p", groups: [
              { id: "g1", path: "0/2/1" },
              { id: "g2", position: { xPercent: 92, yPercent: 8 } },
            ]}
            """.trimIndent(),
        )
        assertEquals("0/2/1", pack.groups[0].path)
        assertEquals(92f, pack.groups[1].position?.xPercent)
    }
}
