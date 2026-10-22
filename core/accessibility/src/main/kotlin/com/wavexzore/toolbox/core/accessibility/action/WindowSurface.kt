package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.Rect

/**
 * 窗口形态分类（D-235；2026-10-22 浮窗误点修复）：
 *
 * 自动点击前先回答「目标树来自哪类窗口」，避免把同包的悬浮窗/面板当成主界面：
 * - [MAIN]：贴合全屏的主窗口（含全屏开屏、全屏广告页）；
 * - [DIALOG]：Activity 窗口但未贴合屏幕（Dialog/模态弹层；Q-206 语义不变）；
 * - [FLOATING]：非 Activity 窗口（PopupWindow/悬浮窗/应用 OVERLAY 等），默认不点。
 *
 * 依据：无障碍层把 `TYPE_APPLICATION`（Activity/Dialog）与 `TYPE_SYSTEM`（应用面板/
 * 悬浮窗/OVERLAY）分开；常量以 [WINDOW_TYPE_APPLICATION] 固化，保持纯 JVM 可测。
 * 取不到窗口/尺寸时按 [MAIN] 处理（沿用旧行为，不扩大拦截面）。
 */
enum class SurfaceKind { MAIN, DIALOG, FLOATING }

/** `AccessibilityWindowInfo.TYPE_APPLICATION`（避免为纯逻辑引入 framework 依赖）。 */
internal const val WINDOW_TYPE_APPLICATION = 1

/** 贴合屏幕容差（与 [PopupClassifier] 同口径：圆角/状态栏内缩）。 */
private const val NEAR_FULL_TOLERANCE = 0.02f

fun classifySurface(
    windowType: Int,
    bounds: Rect?,
    screenWidth: Int,
    screenHeight: Int,
): SurfaceKind {
    if (bounds == null || screenWidth <= 0 || screenHeight <= 0) return SurfaceKind.MAIN
    if (windowType < 0) return SurfaceKind.MAIN
    if (isNearFullWindow(bounds, screenWidth, screenHeight)) return SurfaceKind.MAIN
    return if (windowType == WINDOW_TYPE_APPLICATION) SurfaceKind.DIALOG else SurfaceKind.FLOATING
}

/** 窗框是否贴合屏幕（2% 容差，不按 root 节点内容面积判定）。 */
internal fun isNearFullWindow(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean =
    bounds.left <= screenWidth * NEAR_FULL_TOLERANCE &&
        bounds.top <= screenHeight * NEAR_FULL_TOLERANCE &&
        bounds.right >= screenWidth * (1 - NEAR_FULL_TOLERANCE) &&
        bounds.bottom >= screenHeight * (1 - NEAR_FULL_TOLERANCE)
