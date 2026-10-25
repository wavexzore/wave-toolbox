package com.wavexzore.toolbox.core.rules.matcher

import com.wavexzore.toolbox.core.rules.model.VersionRange

/**
 * 版本匹配（纯逻辑）：`exclude` 优先；`include` 非空时以 include 为准；否则做区间比较。
 *
 * 目标版本未知时默认 **fail-closed**（约束不通过）：写明了版本范围的规则不应在无法确认
 * 版本时生效，否则会把新版行为套到旧版上。确实需要「版本未知也执行」的调用方显式传
 * [MissingVersionPolicy.ALLOW]。
 */
object VersionMatcher {

    /** 目标版本为 null 时的策略。 */
    enum class MissingVersionPolicy {
        /** 无法确认版本 → 不匹配（默认）。 */
        DENY,

        /** 无法确认版本 → 跳过版本约束（旧行为，需显式开启）。 */
        ALLOW,
    }

    fun allows(
        range: VersionRange<Int>?,
        version: Int?,
        missingVersionPolicy: MissingVersionPolicy = MissingVersionPolicy.DENY,
    ): Boolean {
        if (range == null || range.isEmpty) return true
        if (version == null) return missingVersionPolicy == MissingVersionPolicy.ALLOW
        if (range.exclude.contains(version)) return false
        if (range.include.isNotEmpty()) return range.include.contains(version)
        if (range.min != null && version < range.min) return false
        if (range.max != null && version > range.max) return false
        return true
    }

    fun allowsVersionName(
        range: VersionRange<String>?,
        version: String?,
        missingVersionPolicy: MissingVersionPolicy = MissingVersionPolicy.DENY,
    ): Boolean {
        if (range == null || range.isEmpty) return true
        if (version.isNullOrBlank()) return missingVersionPolicy == MissingVersionPolicy.ALLOW
        if (range.exclude.any { compareVersionNames(version, it) == 0 }) return false
        if (range.include.isNotEmpty()) {
            return range.include.any { compareVersionNames(version, it) == 0 }
        }
        if (range.min != null && compareVersionNames(version, range.min) < 0) return false
        if (range.max != null && compareVersionNames(version, range.max) > 0) return false
        return true
    }

    /**
     * 版本名比较（semver 优先、兼容非规范版本号）：
     * - 数字段按数值比较（`1.10 > 1.9`），缺失段补 0（`8.0 == 8.0.0`）；
     * - `-` 后为预发布标识：**预发布 < 正式版**（`1.0.0-rc.1 < 1.0.0`）；
     * - 预发布标识中纯数字按数值比较，数字标识低于字母标识（`1.0.0-1 < 1.0.0-alpha`）；
     * - `+` 后为构建元数据，不参与比较（`1.0.0+build.5 == 1.0.0+build.1`）；
     * - 非数字标识按字典序比较，并容忍 `v` 前缀与 `_` 分隔。
     */
    fun compareVersionNames(a: String, b: String): Int {
        val left = ParsedVersion.parse(a)
        val right = ParsedVersion.parse(b)
        val core = compareCore(left.core, right.core)
        if (core != 0) return core
        return comparePrerelease(left.prerelease, right.prerelease)
    }

    private data class ParsedVersion(val core: List<String>, val prerelease: List<String>) {
        companion object {
            fun parse(raw: String): ParsedVersion {
                var text = raw.trim()
                // 容忍 tag 形态的 v/V 前缀（v1.2.3），仅在后面是数字时剥离。
                if (text.length > 1 && (text[0] == 'v' || text[0] == 'V') && text[1].isDigit()) {
                    text = text.substring(1)
                }
                // 构建元数据不参与优先级比较。
                text = text.substringBefore('+')
                val dash = text.indexOf('-')
                val corePart = if (dash >= 0) text.substring(0, dash) else text
                val prePart = if (dash >= 0) text.substring(dash + 1) else ""
                val core = corePart.split('.', '_').ifEmpty { listOf("0") }
                val prerelease = if (prePart.isEmpty()) emptyList() else prePart.split('.')
                return ParsedVersion(core, prerelease)
            }
        }
    }

    private fun compareCore(a: List<String>, b: List<String>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            // 缺失段按 0 处理：8.0 与 8.0.0 等价。
            val sa = a.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: "0"
            val sb = b.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: "0"
            val cmp = compareIdentifier(sa, sb, numericFirst = true)
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun comparePrerelease(a: List<String>, b: List<String>): Int {
        // 无预发布标识 = 正式版，优先级高于任何预发布版。
        if (a.isEmpty() && b.isEmpty()) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val sa = a.getOrNull(i) ?: return -1 // 前缀相同，段数少者更低
            val sb = b.getOrNull(i) ?: return 1
            val cmp = compareIdentifier(sa, sb, numericFirst = false)
            if (cmp != 0) return cmp
        }
        return 0
    }

    /**
     * @param numericFirst 数字标识与字母标识比较时，数字是否更大。
     *   核心段保持旧的「数字段优先」直觉；预发布段遵循 semver：数字更小。
     */
    private fun compareIdentifier(a: String, b: String, numericFirst: Boolean): Int {
        val na = a.toLongOrNull()
        val nb = b.toLongOrNull()
        if (na != null && nb != null) return na.compareTo(nb)
        if (na != null) return if (numericFirst) 1 else -1
        if (nb != null) return if (numericFirst) -1 else 1
        return a.compareTo(b)
    }
}
