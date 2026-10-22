package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-226 系统界面豁免：名单判定（相册-发送被误点修复；2026-10-19 收入系统设置、桌面/AOD）。 */
class SystemPickerTest {

    @Test
    fun `intentresolver 豁免`() {
        assertTrue(SystemPicker.exempt("com.android.intentresolver"))
    }

    @Test
    fun `系统设置豁免`() {
        // 无障碍服务/特性开关位于系统设置，禁止自动点击「关闭」。
        assertTrue(SystemPicker.exempt("com.android.settings"))
    }

    @Test
    fun `息屏显示豁免`() {
        assertTrue(SystemPicker.exempt("com.miui.aod"))
    }

    @Test
    fun `桌面包按运行时集合豁免`() {
        val home = setOf("com.miui.home", "com.google.android.apps.nexuslauncher")
        assertTrue(SystemPicker.exempt("com.miui.home", home))
        assertTrue(SystemPicker.exempt("com.google.android.apps.nexuslauncher", home))
        assertFalse(SystemPicker.exempt("com.tencent.mm", home))
        assertFalse(SystemPicker.exempt("com.miui.home", emptySet()))
    }

    @Test
    fun `普通应用不豁免`() {
        assertFalse(SystemPicker.exempt("com.miui.gallery"))
        assertFalse(SystemPicker.exempt("com.tencent.mm"))
        assertFalse(SystemPicker.exempt("com.wavexzore.toolbox"))
        assertFalse(SystemPicker.exempt(""))
    }

    @Test
    fun `系统 UI 宿主豁免`() {
        // 通知栏/锁屏/系统插件不是广告；真机日志显示通用兜底仍在扫描。
        assertTrue(SystemPicker.exempt("com.android.systemui"))
        assertTrue(SystemPicker.exempt("miui.systemui.plugin"))
    }

    @Test
    fun `静态名单含选择器、设置与系统 UI`() {
        assertEquals(
            setOf(
                "com.android.intentresolver",
                "com.android.settings",
                "com.android.systemui",
                "miui.systemui.plugin",
            ),
            SystemPicker.PKGS,
        )
    }
}
