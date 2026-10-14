package com.wavexzore.toolbox.feature.settings.update

/**
 * 更新 tag 与本地 versionName 的语义化比较（T-303；设计 04 §5.3）。
 *
 * 规则（单一实现，禁止别处重复比较）：
 * - 剥 `v`/`V` 前缀；仅接受 2~4 段纯数字；段长不等按 0 补齐（`1.0` == `1.0.0`）；
 * - `+build` 元数据忽略；`-pre` 后缀仅当三段完全相等时生效（先行版低于同版本正式版）；
 * - 非法输入返回 null / 视为「不更新」。
 */
object SemverCompare {

    /** `"v1.2.3"` → `[1,2,3]`；非法 → null。 */
    fun parse(tag: String): IntArray? {
        val core = tag.trim().substringBefore('+').substringBefore('-').trim()
        val body = core.removePrefix("v").removePrefix("V")
        if (body.isEmpty()) return null
        val parts = body.split('.')
        if (parts.size !in 2..4) return null
        val numbers = IntArray(parts.size)
        for (i in parts.indices) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n < 0) return null
            numbers[i] = n
        }
        return numbers
    }

    /** latest 严格新于 current 才为 true；相等/更旧/非法均为 false。 */
    fun isNewer(latestTag: String, currentVersionName: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(currentVersionName) ?: return false
        val cmp = compare(latest, current)
        if (cmp != 0) return cmp > 0
        // 三段相等：仅「本地是先行版、latest 是正式版」才算更新
        return hasPrerelease(currentVersionName) && !hasPrerelease(latestTag)
    }

    private fun compare(a: IntArray, b: IntArray): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return if (av > bv) 1 else -1
        }
        return 0
    }

    private fun hasPrerelease(tag: String): Boolean = tag.substringBefore('+').contains('-')
}
