package com.wavexzore.toolbox.feature.adskip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 开屏跳过规则包 schema v1（T-001 冻结；与 `[基座]` RawRule 字段子集对齐 + adskip 专属扩展）。
 *
 * 语义要点：
 * - `matches` 复用基座选择器语法（`[text*="跳过"]`、`@[vid="close"]`），命中后同样走过滤/排序；
 * - `keywords` 为该应用追加关键词（并入全局默认关键词）；
 * - `mode`: `splash`（仅开屏窗口期）/ `any`（任意界面弹窗）；
 * - `actionCd`/`actionMaximum` 按「应用 + activity 实例」作用域；
 * - `path`（drawingOrder+childCount 逐层索引）与 `position`（像素或百分比）为最后兜底；
 * - 未知字段忽略；坏文件隔离（loader 层）。
 */
@Serializable
data class AdSkipPackFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val pkg: String,
    val packVersion: Int = 1,
    val groups: List<AdSkipGroup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class AdSkipGroup(
    val id: String,
    val name: String = "",
    val order: Int = 0,
    val enabled: Boolean = true,
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    /** `splash` | `any`。 */
    val mode: String = MODE_SPLASH,
    /** 选择器（基座语法，OR 语义）。 */
    val matches: List<String> = emptyList(),
    /** 本应用追加关键词（OR 语义，并入默认关键词）。 */
    val keywords: List<String> = emptyList(),
    /** 树路径兜底：`0/2/1`（逐层按 drawingOrder 排序后的子索引）。 */
    val path: String? = null,
    /** 坐标兜底（px 或 %，二选一）。 */
    @SerialName("position") val position: AdSkipPosition? = null,
    /** 同实例冷却毫秒。 */
    val actionCd: Long = 800,
    /** 同实例最大执行次数。 */
    val actionMaximum: Int = 1,
    /** 年轻窗口延迟毫秒（尺寸未稳期）。 */
    val delayMs: Long = 150,
) {
    companion object {
        const val MODE_SPLASH = "splash"
        const val MODE_ANY = "any"
    }
}

@Serializable
data class AdSkipPosition(
    val x: Int? = null,
    val y: Int? = null,
    val xPercent: Float? = null,
    val yPercent: Float? = null,
)

/** 纯解析（JSON5 → 模型 + 语义校验；供 loader 与单测复用）。 */
object AdSkipPackCodec {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun parse(text: String): AdSkipPackFile {
        val element = li.songe.json5.Json5.parseToJsonElement(text)
        val pack = json.decodeFromJsonElement(AdSkipPackFile.serializer(), element)
        require(pack.pkg.isNotBlank()) { "pkg 为空" }
        require(pack.schemaVersion <= AdSkipPackFile.CURRENT_SCHEMA_VERSION) {
            "schemaVersion=${pack.schemaVersion} 高于支持版本"
        }
        pack.groups.forEach { group ->
            require(group.id.isNotBlank()) { "group.id 为空" }
            require(group.mode == AdSkipGroup.MODE_SPLASH || group.mode == AdSkipGroup.MODE_ANY) {
                "未知 mode=${group.mode}"
            }
        }
        return pack
    }
}
