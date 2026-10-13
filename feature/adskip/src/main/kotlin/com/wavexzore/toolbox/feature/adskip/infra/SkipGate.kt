package com.wavexzore.toolbox.feature.adskip.infra

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup

/**
 * 区域守卫链（T-201 等价迁移自 tryOnce 的弹窗守卫；D-205）：
 *
 * - `splash`（仅开屏）模式：弹窗式窗口（几何）或弹窗类别（Dialog/PopupWindow）→ 拦截；
 * - `any`（全屏蔽）模式：不拦截（Q-206 已决议=保持现状放行）；
 * - 拦截原因回传（window/class），供一次性日志。
 */
class SkipGate(private val classifier: PopupClassifier) {

    fun blockReason(pkg: String, activityClass: String?, mode: String): String? {
        if (mode == AdSkipGroup.MODE_ANY) return null
        if (classifier.isPopupWindow(pkg)) return "window"
        if (activityClass != null && classifier.isPopupWindowClass(activityClass)) return "class"
        return null
    }
}
