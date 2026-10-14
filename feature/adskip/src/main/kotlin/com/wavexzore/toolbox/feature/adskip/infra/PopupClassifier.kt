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
        if (sw <= 0 || sh <= 0) return false
        // 贴合屏幕（含 2% 圆角/状态栏容差；HyperOS 启动动画后段实测 15,21,1193,2579/1208x2624）
        val nearFull = b.left <= sw * NEAR_FULL_TOLERANCE && b.top <= sh * NEAR_FULL_TOLERANCE &&
            b.right >= sw * (1 - NEAR_FULL_TOLERANCE) && b.bottom >= sh * (1 - NEAR_FULL_TOLERANCE)
        if (nearFull) return false
        val wr = b.width / sw.toFloat()
        val hr = b.height / sh.toFloat()
        if (isUniformlyScaled(wr, hr, quirks.animationScaleFloor)) return false
        return true
    }

    fun isPopupWindowClass(cls: String?): Boolean = isPopupWindowClassOf(cls)

    companion object {
        /** 贴合屏幕容差（比例）：圆角/状态栏内缩不至于判为弹窗，F1 弹窗（顶内缩 5.5%）仍拦。 */
        const val NEAR_FULL_TOLERANCE = 0.02f
    }
}

/**
 * 启动动画窗口（等比缩放）：
 * - 小比例（两轴 < floor 且接近）——F2 实测 0.26；
 * - 大比例近全屏（两轴 ≥0.85 且两轴差 ≤3%）——HyperOS 启动动画后段实测 0.975/0.975。
 */
internal fun isUniformlyScaled(widthRatio: Float, heightRatio: Float, floor: Float = 0.8f): Boolean =
    (widthRatio < floor && heightRatio < floor && abs(widthRatio - heightRatio) <= 0.15f) ||
        (widthRatio >= 0.85f && heightRatio >= 0.85f && abs(widthRatio - heightRatio) <= 0.03f)

/** 弹窗式窗口类别：框架 Dialog / PopupWindow（F3：只认这两种精确类别）。 */
internal fun isPopupWindowClassOf(cls: String?): Boolean =
    cls == "android.app.Dialog" || (cls?.contains("PopupWindow") ?: false)
