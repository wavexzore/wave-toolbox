package com.wavexzore.toolbox.feature.adskip.infra

import kotlin.math.abs

/** 纯矩形（与 android.graphics.Rect 解耦，便于单测）。 */
data class WindowBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

/**
 * 弹窗判别唯一实现（T-201 自 AdSkipFeature 等价迁移）：
 *
 * - 几何：窗口未贴合屏幕四边 → 弹窗式窗口（Dialog/PopupWindow/弹层）；
 * - 动画豁免：应用开关动画期间窗口整体等比缩放（F2），按全屏对待；
 * - 类别：只认 `android.app.Dialog` 与 `*PopupWindow*`（F3：WSC 类名会混入视图类，禁止泛化）；
 * - 取不到窗口时按全屏对待（等价迁移：不拦）。
 */
class PopupClassifier(
    private val quirks: OemQuirks,
    private val windowBounds: (String) -> WindowBounds?,
    private val screenSize: () -> Pair<Int, Int>,
) {

    /** 焦点窗口是否为弹窗式窗口。 */
    fun isPopupWindow(pkg: String): Boolean {
        val b = windowBounds(pkg) ?: return false
        val (sw, sh) = screenSize()
        val full = b.left <= 0 && b.top <= 0 && b.right >= sw && b.bottom >= sh
        if (full) return false
        val wr = if (sw > 0) b.width / sw.toFloat() else 0f
        val hr = if (sh > 0) b.height / sh.toFloat() else 0f
        if (isUniformlyScaled(wr, hr, quirks.animationScaleFloor)) return false
        return true
    }

    fun isPopupWindowClass(cls: String?): Boolean = isPopupWindowClassOf(cls)
}

/** 等比缩放（两轴接近且都明显小于屏）→ 开关动画窗口。 */
internal fun isUniformlyScaled(widthRatio: Float, heightRatio: Float, floor: Float = 0.8f): Boolean =
    widthRatio < floor && heightRatio < floor && abs(widthRatio - heightRatio) <= 0.15f

/** 弹窗式窗口类别：框架 Dialog / PopupWindow（F3：只认这两种精确类别）。 */
internal fun isPopupWindowClassOf(cls: String?): Boolean =
    cls == "android.app.Dialog" || (cls?.contains("PopupWindow") ?: false)
