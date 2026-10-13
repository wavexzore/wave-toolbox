package com.wavexzore.toolbox.feature.adskip

/** 规则包组读取源（解耦 [AdSkipPackLoader]，便于工厂单测注入假数据）。 */
fun interface PackGroupsSource {
    /** 已 enabled 过滤并按 order 排序的组。 */
    fun groupsFor(pkg: String): List<AdSkipGroup>
}
