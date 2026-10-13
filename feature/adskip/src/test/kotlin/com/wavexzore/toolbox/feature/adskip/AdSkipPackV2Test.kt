package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-204：schema v2 解析/校验/v1 兼容 + 死字段接线（GroupGating / 组级 cd）。 */
class AdSkipPackV2Test {

    @Test
    fun v2FieldsParse() {
        val pack = AdSkipPackCodec.parse(
            """
            {
              schemaVersion: 2,
              pkg: "com.app.v2",
              groups: [{
                id: "g1", mode: "splash",
                zone: "activity",
                activityIds: ["com.app.DetailActivity"],
                settleMs: 500,
                popupAware: true,
                suppressGeneric: true,
                actionCd: 2000,
                delayMs: 300,
                matches: ['[text="关闭"]'],
              }]
            }
            """.trimIndent(),
        )
        val g = pack.groups.single()
        assertEquals("activity", g.effectiveZone)
        assertEquals(500, g.settleMs)
        assertEquals(true, g.popupAware)
        assertTrue(g.suppressGeneric)
        assertTrue(g.popupAllowed(AdSkipGroup.MODE_SPLASH))
    }

    @Test
    fun v1PackKeepsLegacySemantics() {
        val pack = AdSkipPackCodec.parse(
            """
            {
              schemaVersion: 1,
              pkg: "com.app.v1",
              groups: [
                { id: "s", mode: "splash", keywords: ["跳过"] },
                { id: "p", mode: "any", matches: ['@[vid="close"]'] },
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, pack.schemaVersion)
        val splash = pack.groups[0]
        val popup = pack.groups[1]
        // zone 由 mode 推导：splash→entry、any→any
        assertEquals(AdSkipGroup.ZONE_ENTRY, splash.effectiveZone)
        assertEquals(AdSkipGroup.ZONE_ANY, popup.effectiveZone)
        // popupAware 缺省按运行模式：splash 拦、any 放行（Q-206）
        assertFalse(splash.popupAllowed(AdSkipGroup.MODE_SPLASH))
        assertTrue(splash.popupAllowed(AdSkipGroup.MODE_ANY))
        // 死字段默认=旧值
        assertEquals(800, splash.actionCd)
        assertEquals(150, splash.delayMs)
        assertEquals(350, splash.settleMs)
        assertNull(splash.popupAware)
    }

    @Test
    fun invalidFieldsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            AdSkipPackCodec.parse("""{ pkg: "a", groups: [{ id: "g", zone: "unknown" }] }""")
        }
        assertFailsWith<IllegalArgumentException> {
            AdSkipPackCodec.parse("""{ pkg: "a", groups: [{ id: "g", settleMs: 5000 }] }""")
        }
        assertFailsWith<IllegalArgumentException> {
            AdSkipPackCodec.parse("""{ schemaVersion: 3, pkg: "a" }""")
        }
    }

    @Test
    fun activityIdsGateOnlyWhenActivityKnown() {
        val g = AdSkipGroup(id = "g", activityIds = listOf("com.app.Splash"))
        assertTrue(GroupGating.active(g, AdSkipGroup.MODE_SPLASH, "com.app.SplashActivity"))
        assertFalse(GroupGating.active(g, AdSkipGroup.MODE_SPLASH, "com.app.MainActivity"))
        // 活动未知（null）时不判定
        assertTrue(GroupGating.active(g, AdSkipGroup.MODE_SPLASH, null))
        // exclude 仍然优先
        val ex = AdSkipGroup(id = "x", excludeActivityIds = listOf("com.app.Player"))
        assertFalse(GroupGating.active(ex, AdSkipGroup.MODE_ANY, "com.app.PlayerActivity"))
    }

    @Test
    fun splashModeStillDropsAnyGroup() {
        val g = AdSkipGroup(id = "g", mode = AdSkipGroup.MODE_ANY)
        assertFalse(GroupGating.active(g, AdSkipGroup.MODE_SPLASH, "com.app.MainActivity"))
        assertTrue(GroupGating.active(g, AdSkipGroup.MODE_ANY, "com.app.MainActivity"))
    }
}
