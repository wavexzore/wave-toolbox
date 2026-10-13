package com.wavexzore.toolbox.core.rules.matcher

import com.wavexzore.toolbox.core.rules.model.VersionRange

/**
 * 版本匹配（纯逻辑）：`exclude` 优先；`include` 非空时以 include 为准；否则做区间比较。
 * 版本号缺失（null）时约束跳过（fail-open，文档见 04 §11）。
 */
object VersionMatcher {

    fun allows(range: VersionRange<Int>?, version: Int?): Boolean {
        if (range == null || range.isEmpty) return true
        if (version == null) return true
        if (range.exclude.contains(version)) return false
        if (range.include.isNotEmpty()) return range.include.contains(version)
        if (range.min != null && version < range.min) return false
        if (range.max != null && version > range.max) return false
        return true
    }

    fun allowsVersionName(range: VersionRange<String>?, version: String?): Boolean {
        if (range == null || range.isEmpty) return true
        if (version == null) return true
        if (range.exclude.any { compareVersionNames(version, it) == 0 }) return false
        if (range.include.isNotEmpty()) {
            return range.include.any { compareVersionNames(version, it) == 0 }
        }
        if (range.min != null && compareVersionNames(version, range.min) < 0) return false
        if (range.max != null && compareVersionNames(version, range.max) > 0) return false
        return true
    }

    /** 数字段语义化比较：`1.10 > 1.9`；非数字段按字典序。 */
    fun compareVersionNames(a: String, b: String): Int {
        val pa = a.split('.', '-', '_', '+')
        val pb = b.split('.', '-', '_', '+')
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val sa = pa.getOrNull(i)
            val sb = pb.getOrNull(i)
            if (sa == null) return -1
            if (sb == null) return 1
            val na = sa.toLongOrNull()
            val nb = sb.toLongOrNull()
            val cmp = if (na != null && nb != null) na.compareTo(nb) else sa.compareTo(sb)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
