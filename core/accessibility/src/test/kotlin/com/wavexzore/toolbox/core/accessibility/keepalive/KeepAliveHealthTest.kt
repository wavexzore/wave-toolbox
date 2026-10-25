package com.wavexzore.toolbox.core.accessibility.keepalive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 保活体检文案（OEM 指引 / 摘要）。 */
class KeepAliveHealthTest {

    @Test
    fun `known manufacturers map to actionable hints`() {
        assertTrue(KeepAliveHealth.oemHint("samsung").contains("三星"))
        assertTrue(KeepAliveHealth.oemHint("Xiaomi").contains("省电策略"))
        assertTrue(KeepAliveHealth.oemHint("HUAWEI").contains("应用启动管理"))
        assertTrue(KeepAliveHealth.oemHint("OPPO").contains("后台运行"))
        assertTrue(KeepAliveHealth.oemHint("vivo").contains("后台耗电管理"))
    }

    @Test
    fun `unknown manufacturer falls back to generic hint`() {
        assertTrue(KeepAliveHealth.oemHint("Google").contains("自启动"))
    }

    @Test
    fun `healthy snapshot reports best effort normal`() {
        val snapshot = snapshot(warnings = emptyList())
        assertTrue(snapshot.healthy)
        assertEquals("链路正常 · 同进程 best-effort", snapshot.summary)
    }

    @Test
    fun `warning is surfaced in summary`() {
        val snapshot = snapshot(warnings = listOf("电池优化未豁免，后台可能被冻结"))
        assertEquals("电池优化未豁免，后台可能被冻结", snapshot.summary)
    }

    private fun snapshot(warnings: List<String>) = KeepAliveHealth.Snapshot(
        engineEnabled = true,
        keepAliveEnabled = true,
        foregroundServiceRunning = true,
        accessibilityEnabled = true,
        batteryOptimizationIgnored = true,
        bootAutostart = true,
        manufacturer = "Samsung",
        oemHint = KeepAliveHealth.oemHint("Samsung"),
        warnings = warnings,
    )
}
