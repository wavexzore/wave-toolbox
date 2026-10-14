package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** D-223：无可用组时的通用兜底（修复 v1→重构回归，未适配应用恢复 keyword/vid 语义）。 */
class GroupFallbackTest {

    @Test
    fun emptyActiveResolvesToGeneric() {
        val resolved = GroupFallback.resolve(emptyList())
        assertEquals(1, resolved.size)
        val g = resolved.single()
        assertEquals(GroupFallback.GENERIC_ID, g.id)
        assertEquals(AdSkipGroup.ZONE_ENTRY, g.effectiveZone)
        assertEquals(AdSkipGroup.MODE_SPLASH, g.mode)
        assertTrue(g.matches.isEmpty() && g.path == null && g.position == null)
        assertFalse(g.suppressGeneric)
        assertTrue(g.activityIds.isEmpty() && g.excludeActivityIds.isEmpty())
    }

    @Test
    fun nonEmptyActivePassesThrough() {
        val groups = listOf(AdSkipGroup(id = "a"), AdSkipGroup(id = "b"))
        assertSame(groups, GroupFallback.resolve(groups))
    }

    @Test
    fun genericActiveInBothRunModes() {
        // GroupGating：splash 模式要求组 mode=splash（默认），any 模式不过滤
        assertTrue(GroupGating.active(GroupFallback.generic, AdSkipGroup.MODE_SPLASH, "com.p.MainActivity"))
        assertTrue(GroupGating.active(GroupFallback.generic, AdSkipGroup.MODE_ANY, "com.p.MainActivity"))
    }

    @Test
    fun genericZoneEntryAllowedOutsidePopupButNotInPopup() {
        assertTrue(
            ZoneGate.allows(GroupFallback.generic, popupContext = false, activity = "com.p.Main", activityAt = 0, now = 0),
        )
        // 弹窗上下文（splash 模式）：兜底组非 popupAware → 不参与（popup-skip 语义由 tryOnce 前置处理）
        assertFalse(
            ZoneGate.allows(GroupFallback.generic, popupContext = true, activity = "com.p.Main", activityAt = 0, now = 0),
        )
    }
}
