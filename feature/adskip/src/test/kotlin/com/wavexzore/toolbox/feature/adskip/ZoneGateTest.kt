package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** T-208：区域门闸（zone/popupAware/settle）独立语义锁定。 */
class ZoneGateTest {

    private fun group(
        zone: String? = null,
        popupAware: Boolean? = null,
        settleMs: Long = 350,
        mode: String = AdSkipGroup.MODE_SPLASH,
    ) = AdSkipGroup(id = "g", zone = zone, popupAware = popupAware, settleMs = settleMs, mode = mode)

    @Test
    fun activityZoneRequiresKnownActivityAndSettle() {
        val g = group(zone = AdSkipGroup.ZONE_ACTIVITY)
        assertFalse(ZoneGate.allows(g, popupContext = false, activity = null, activityAt = 0, now = 10_000))
        assertFalse(ZoneGate.allows(g, popupContext = false, activity = "com.a.Main", activityAt = 1_000, now = 1_200))
        assertTrue(ZoneGate.allows(g, popupContext = false, activity = "com.a.Main", activityAt = 1_000, now = 1_400))
    }

    @Test
    fun popupZoneOnlyInPopupContext() {
        val g = group(zone = AdSkipGroup.ZONE_POPUP)
        assertTrue(ZoneGate.allows(g, popupContext = true, activity = "com.a.Main", activityAt = 0, now = 0))
        assertFalse(ZoneGate.allows(g, popupContext = false, activity = "com.a.Main", activityAt = 0, now = 0))
    }

    @Test
    fun popupAwareOnlyInPopupContext() {
        val g = group(popupAware = true)
        assertTrue(ZoneGate.allows(g, popupContext = true, activity = null, activityAt = 0, now = 0))
        assertTrue(ZoneGate.allows(g, popupContext = false, activity = null, activityAt = 0, now = 0))
        val notAware = group(popupAware = false)
        assertFalse(ZoneGate.allows(notAware, popupContext = true, activity = null, activityAt = 0, now = 0))
        assertTrue(ZoneGate.allows(notAware, popupContext = false, activity = null, activityAt = 0, now = 0))
    }

    @Test
    fun entryAndAnyPassWithoutConstraints() {
        assertTrue(
            ZoneGate.allows(group(zone = AdSkipGroup.ZONE_ENTRY), popupContext = false, activity = null, activityAt = 0, now = 0),
        )
        assertTrue(
            ZoneGate.allows(group(zone = AdSkipGroup.ZONE_ANY, mode = AdSkipGroup.MODE_ANY), popupContext = false, activity = null, activityAt = 0, now = 0),
        )
    }
}
