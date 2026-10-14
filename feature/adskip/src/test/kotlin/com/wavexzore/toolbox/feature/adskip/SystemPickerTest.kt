package com.wavexzore.toolbox.feature.adskip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-226 系统选择器豁免：名单判定（相册-发送被误点修复）。 */
class SystemPickerTest {

    @Test
    fun `intentresolver 豁免`() {
        assertTrue(SystemPicker.exempt("com.android.intentresolver"))
    }

    @Test
    fun `普通应用不豁免`() {
        assertFalse(SystemPicker.exempt("com.miui.gallery"))
        assertFalse(SystemPicker.exempt("com.tencent.mm"))
        assertFalse(SystemPicker.exempt("com.wavexzore.toolbox"))
        assertFalse(SystemPicker.exempt(""))
    }

    @Test
    fun `名单初始仅 intentresolver`() {
        assertEquals(setOf("com.android.intentresolver"), SystemPicker.PKGS)
    }
}
