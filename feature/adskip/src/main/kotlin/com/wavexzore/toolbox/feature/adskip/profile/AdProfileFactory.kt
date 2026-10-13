package com.wavexzore.toolbox.feature.adskip.profile

import com.wavexzore.toolbox.feature.adskip.AdSkipGroup
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 识别器工厂（T-202；D-217）：解析 / 回落 [GenericProfile] / 与 pack 合并 / 缓存。
 *
 * 合并规则（单测锁定）：同 `id` 全等=profile 整组替换 pack 组；不同 id=concat 后稳定排序
 * （order 升序；平局：pack 先、profile 后，再按 id 字典序）。
 */
@Singleton
class AdProfileFactory @Inject constructor(
    private val registry: AdProfileRegistry,
    private val loader: com.wavexzore.toolbox.feature.adskip.PackGroupsSource,
) {

    private val generic = GenericProfile()
    private val cache = ConcurrentHashMap<String, AppAdProfile>()

    /** pkg → profile（未命中回落通用；缓存上限 512，满后清空重建）。 */
    fun profile(pkg: String): AppAdProfile {
        cache[pkg]?.let { return it }
        if (cache.size >= CACHE_LIMIT) cache.clear()
        return cache.getOrPut(pkg) { registry.resolve(pkg) ?: generic }
    }

    /** pack ∪ profile 合并（enabled 过滤后；同 id 冲突 profile 优先）。 */
    fun mergedGroups(pkg: String): List<AdSkipGroup> {
        val packGroups = loader.groupsFor(pkg) // 已 enabled 过滤 + order 排序
        val profileGroups = profile(pkg).groups().filter { it.enabled }
        if (profileGroups.isEmpty()) return packGroups
        if (packGroups.isEmpty()) return profileGroups.sortedWith(orderComparator)

        val byId = LinkedHashMap<String, Pair<AdSkipGroup, Int>>()
        for (g in packGroups) byId[g.id] = g to ORIGIN_PACK
        for (g in profileGroups) byId[g.id] = g to ORIGIN_PROFILE // 同 id 替换（profile 优先）
        return byId.values
            .sortedWith(compareBy({ it.first.order }, { it.second }, { it.first.id }))
            .map { it.first }
    }

    private val orderComparator = compareBy<AdSkipGroup>({ it.order }, { it.id })

    private companion object {
        const val CACHE_LIMIT = 512
        const val ORIGIN_PACK = 0
        const val ORIGIN_PROFILE = 1
    }
}
