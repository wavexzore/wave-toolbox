package com.wavexzore.toolbox.feature.adskip

/**
 * 单 App 生效模式解析（T-206 纯函数）：
 * 优先级 = 单 App 三态 > 旧禁用集合（迁移兼容，"禁用"等价 off） > 全局模式。
 */
internal object PkgMode {

    const val OFF = "off"
    const val FOLLOW = "follow"

    fun effective(
        globalMode: String,
        perApp: Map<String, String>,
        legacyDisabled: Set<String>,
        pkg: String,
    ): String = perApp[pkg]
        ?: if (pkg in legacyDisabled) OFF else globalMode
}
