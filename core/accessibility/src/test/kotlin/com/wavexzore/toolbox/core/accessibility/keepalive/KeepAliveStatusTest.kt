package com.wavexzore.toolbox.core.accessibility.keepalive

import kotlin.test.Test
import kotlin.test.assertEquals

/** 常驻通知状态文案（保活/无障碍组合）。 */
class KeepAliveStatusTest {

    @Test
    fun `engine off wins over service state`() {
        assertEquals("引擎已关闭", keepAliveStatusText(engineEnabled = false, serviceEnabled = true))
        assertEquals("引擎已关闭", keepAliveStatusText(engineEnabled = false, serviceEnabled = false))
    }

    @Test
    fun `engine on distinguishes service connection`() {
        assertEquals(
            "引擎已开启 · 无障碍已连接",
            keepAliveStatusText(engineEnabled = true, serviceEnabled = true),
        )
        assertEquals(
            "引擎已开启 · 等待无障碍服务",
            keepAliveStatusText(engineEnabled = true, serviceEnabled = false),
        )
    }
}
