package com.wavexzore.toolbox.core.common.text

/**
 * 类名语义判定（R-209 公共化：归一 AdSkipFeature/RuleEngine/AndroidTaskEnvironment 三处副本）。
 *
 * [isActivityLike] 采用「必须带包名点号」的严格版（原 AdSkipFeature 语义）：
 * 抽屉 DrawerLayout、弹窗 FrameLayout、裸类名 Main 等框架/视图窗口返回 false。
 * 行为影响：RuleEngine / AndroidTaskEnvironment 对裸类名将收紧（旧版会把 "Main" 当 Activity）。
 */
object ClassNames {

    /** 真实 Activity 类名（非框架组件/窗口类）。 */
    fun isActivityLike(cls: String?): Boolean {
        if (cls.isNullOrBlank() || !cls.contains('.')) return false
        if (cls.endsWith("Window") || cls.endsWith("WindowView") || cls.endsWith("PopupWindow")) return false
        return !cls.startsWith("android.") &&
            !cls.startsWith("androidx.") &&
            !cls.startsWith("android.support.") &&
            !cls.startsWith("com.android.internal.") &&
            !cls.startsWith("com.google.android.material.") &&
            !cls.startsWith("com.android.systemui.")
    }
}
