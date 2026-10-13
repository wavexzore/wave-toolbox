package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals

/** T-206：单 App 三态生效解析 + 配置解析/夹取纯函数。 */
class PkgModeTest {

    @Test
    fun perAppModeOverridesGlobalAndLegacy() {
        assertEquals(
            AdSkipGroup.MODE_ANY,
            PkgMode.effective(AdSkipGroup.MODE_SPLASH, mapOf("com.a" to AdSkipGroup.MODE_ANY), setOf("com.a"), "com.a"),
        )
        assertEquals(
            AdSkipConfig.MODE_OFF,
            PkgMode.effective(AdSkipGroup.MODE_SPLASH, mapOf("com.a" to AdSkipConfig.MODE_OFF), emptySet(), "com.a"),
        )
    }

    @Test
    fun legacyDisabledActsAsOff() {
        assertEquals(
            AdSkipConfig.MODE_OFF,
            PkgMode.effective(AdSkipGroup.MODE_ANY, emptyMap(), setOf("com.a"), "com.a"),
        )
    }

    @Test
    fun unsetPackageFollowsGlobal() {
        assertEquals(
            AdSkipGroup.MODE_SPLASH,
            PkgMode.effective(AdSkipGroup.MODE_SPLASH, emptyMap(), emptySet(), "com.a"),
        )
        assertEquals(
            AdSkipGroup.MODE_ANY,
            PkgMode.effective(AdSkipGroup.MODE_ANY, mapOf("com.b" to AdSkipGroup.MODE_ANY), emptySet(), "com.a"),
        )
    }

    @Test
    fun perAppEntriesParseWithInvalidFiltered() {
        val parsed = AdSkipConfig.parsePerAppModes(
            setOf("com.a=any", "com.b=splash", "bad", "=x", "com.c=nope", "com.d=off"),
        )
        assertEquals(
            mapOf("com.a" to AdSkipGroup.MODE_ANY, "com.b" to AdSkipGroup.MODE_SPLASH, "com.d" to AdSkipConfig.MODE_OFF),
            parsed,
        )
    }

    @Test
    fun entryWindowClamped() {
        assertEquals(AdSkipConfig.MIN_ENTRY_WINDOW_MS, AdSkipConfig.clampEntryWindow(500))
        assertEquals(AdSkipConfig.MAX_ENTRY_WINDOW_MS, AdSkipConfig.clampEntryWindow(99_999))
        assertEquals(8_000L, AdSkipConfig.clampEntryWindow(8_000))
    }
}
