package com.wavexzore.toolbox.core.accessibility.action

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2026-10-19 误操作修复：目标包取树时禁止回退到工具箱自身窗口。 */
class WindowFallbackGuardTest {

    private val self = "com.wavexzore.toolbox"

    @Test
    fun `requesting another package never falls back to self window`() {
        assertFalse(allowsActiveWindowFallback(requestedPkg = "com.tencent.mm", activeRootPkg = self, selfPkg = self))
    }

    @Test
    fun `requesting another package may use another app window`() {
        assertTrue(allowsActiveWindowFallback(requestedPkg = "com.tencent.mm", activeRootPkg = "com.miui.home", selfPkg = self))
    }

    @Test
    fun `no package request keeps active window fallback`() {
        assertTrue(allowsActiveWindowFallback(requestedPkg = null, activeRootPkg = self, selfPkg = self))
    }

    @Test
    fun `requesting self keeps active window fallback`() {
        assertTrue(allowsActiveWindowFallback(requestedPkg = self, activeRootPkg = self, selfPkg = self))
    }

    @Test
    fun `unknown active root package blocks foreign fallback`() {
        // 归属不明时宁可放弃：无法证明不是工具箱自身窗口。
        assertFalse(allowsActiveWindowFallback(requestedPkg = "com.tencent.mm", activeRootPkg = null, selfPkg = self))
    }

    @Test
    fun `non primary display never falls back to active root`() {
        // rootInActiveWindow 只代表当前焦点屏，可能是主屏；副屏取树不能跨屏回退。
        assertFalse(allowsActiveRootFallback(displayId = 2))
        assertTrue(allowsActiveRootFallback(displayId = 0))
        assertTrue(allowsActiveRootFallback(displayId = null))
    }
}
