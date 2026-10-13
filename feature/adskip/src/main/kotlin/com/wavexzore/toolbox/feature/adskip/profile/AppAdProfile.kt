package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup

/**
 * 特定应用识别器 SPI（T-202；D-204/D-216）。
 *
 * 准入原则：**pack（数据）表达不了才写代码**——状态相关、跨窗口、需抑制通用回退、多步骤等。
 * 产出物与 pack 同模型（[AdSkipGroup]），由 `AdProfileFactory` 合并进唯一管线（零重复实现）。
 */
interface AppAdProfile {

    /** 归属包名（可多包；同包多 profile 注册 → 启动 fail-fast）。 */
    val pkgs: Set<String>

    /** 应用级规则（与 pack 同模型；同 id 全等时本处优先）。 */
    fun groups(): List<AdSkipGroup> = emptyList()

    /** 追加关键词（并入全局用户关键词之后）。 */
    fun keywordExtra(): List<String> = emptyList()

    /** 追加 vid 词（并入默认 `skip/jump/close`）。 */
    fun vidExtra(): List<String> = emptyList()

    /** 区域策略覆写（缺省=默认；T-203 消费）。 */
    fun zonePolicy(): ZonePolicy = ZonePolicy.DEFAULT

    /** 该页面需要抑制的候选选择器（默认用于抑制通用 keyword/vid 来源；F4）。 */
    fun suppressOn(activity: String?): List<String> = emptyList()

    /** provider 顺序覆写（null=默认顺序；未列出者禁用；T-203 消费）。 */
    fun providerOrder(): List<String>? = null
}

/** 区域策略（最小集；缺省允许 entry 与声明区域）。 */
data class ZonePolicy(
    val allowEntry: Boolean = true,
    val allowDeclared: Boolean = true,
) {
    companion object {
        val DEFAULT = ZonePolicy()
    }
}
