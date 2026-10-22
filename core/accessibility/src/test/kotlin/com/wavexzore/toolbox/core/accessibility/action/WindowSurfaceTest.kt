package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

/** D-235 窗口形态分类：主窗口 / Dialog / 浮窗（2026-10-22 浮窗误点修复）。 */
class WindowSurfaceTest {

    private val screenW = 1208
    private val screenH = 2624

    @Test
    fun fullScreenActivityWindowIsMain() {
        val b = Rect(0, 0, screenW, screenH)
        assertEquals(SurfaceKind.MAIN, classifySurface(WINDOW_TYPE_APPLICATION, b, screenW, screenH))
    }

    @Test
    fun statusBarInsetStillMain() {
        // HyperOS 实测主窗 0,1,1208,2623 / 0,10,1206,2625
        assertEquals(
            SurfaceKind.MAIN,
            classifySurface(WINDOW_TYPE_APPLICATION, Rect(0, 10, 1206, 2625), screenW, screenH),
        )
    }

    @Test
    fun smallActivityWindowIsDialog() {
        // 小睡眠隐私弹窗 0,2763,1208,5183（跨屏偏移、92% 高）
        assertEquals(
            SurfaceKind.DIALOG,
            classifySurface(WINDOW_TYPE_APPLICATION, Rect(0, 2763, 1208, 5183), screenW, screenH),
        )
    }

    @Test
    fun smallNonActivityWindowIsFloating() {
        // 腾讯会议实测 13 节点小窗：PopupWindow/OVERLAY 在无障碍层归 TYPE_SYSTEM
        assertEquals(
            SurfaceKind.FLOATING,
            classifySurface(3, Rect(142, 734, 1066, 1972), screenW, screenH),
        )
    }

    @Test
    fun fullScreenSystemWindowIsMain() {
        // 全屏系统窗（如通知栏全屏展开）不按浮窗处理，避免扩大拦截面
        assertEquals(
            SurfaceKind.MAIN,
            classifySurface(3, Rect(0, 0, screenW, screenH), screenW, screenH),
        )
    }

    @Test
    fun unknownWindowTypeKeepsLegacyMain() {
        assertEquals(SurfaceKind.MAIN, classifySurface(-1, Rect(100, 100, 500, 500), screenW, screenH))
    }

    @Test
    fun missingBoundsKeepsLegacyMain() {
        assertEquals(SurfaceKind.MAIN, classifySurface(WINDOW_TYPE_APPLICATION, null, screenW, screenH))
    }
}
