package com.wavexzore.toolbox.feature.adskip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 开屏跳过规则包 schema v2（v1 兼容：缺省=旧语义；与 `[基座]` RawRule 字段子集对齐 + adskip 专属扩展）。
 *
 * v2 新增（Q-208 已批）：
 * - `zone`：生效区域 `entry|activity|popup|any`（缺省由 `mode` 推导：splash→entry、any→any）；
 * - `settleMs`：activity 区域稳定期（0~2000，默认 350）；
 * - `popupAware`：弹窗窗口是否放行（null=按运行模式裁决：splash 拦、any 放行；显式值恒定）；
 * - `suppressGeneric`：本组命中活动页时压制通用 keyword/vid 回退（默认 false）。
 *
 * v1 死字段接线（D-209）：`activityIds` 参与准入（非空时要求当前活动匹配）、`actionCd` 组级最小间隔、
 * `delayMs` 组级年轻窗口延迟（默认 150=旧值）。
 *
 * 语义要点（不变）：`matches` 复用基座选择器语法、`keywords` 并入关键词、`actionCd/actionMaximum`
 * 按「应用 + 实例」作用域、`path/position` 最后兜底、未知字段忽略、坏文件隔离（loader 层）。
 */
@Serializable
data class AdSkipPackFile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val pkg: String,
    val packVersion: Int = 1,
    val groups: List<AdSkipGroup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Serializable
data class AdSkipGroup(
    val id: String,
    val name: String = "",
    val order: Int = 0,
    val enabled: Boolean = true,
    /** v1 字段；v2 起接线：非空时要求当前活动命中（前缀/后缀/全等）。 */
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    /** `splash` | `any`。 */
    val mode: String = MODE_SPLASH,
    /** v2：生效区域；null=由 mode 推导。 */
    val zone: String? = null,
    /** 选择器（基座语法，OR 语义）。 */
    val matches: List<String> = emptyList(),
    /** 本应用追加关键词（OR 语义，并入默认关键词）。 */
    val keywords: List<String> = emptyList(),
    /** 树路径兜底：`0/2/1`（逐层按 drawingOrder 排序后的子索引）。 */
    val path: String? = null,
    /** 坐标兜底（px 或 %，二选一）。 */
    @SerialName("position") val position: AdSkipPosition? = null,
    /** 组级最小间隔毫秒（v2 起生效；内置声明值多 ≤1200，受全局冷却支配→节奏不变）。 */
    val actionCd: Long = 800,
    /** 同实例最大执行次数。 */
    val actionMaximum: Int = 1,
    /** 组级年轻窗口延迟毫秒（v2 起生效；默认 150=旧值）。 */
    val delayMs: Long = 150,
    /** v2：activity 区域稳定期毫秒。 */
    val settleMs: Long = 350,
    /** v2：弹窗窗口放行（三态；null=按运行模式裁决）。 */
    val popupAware: Boolean? = null,
    /** v2：本组命中活动页时压制通用 keyword/vid 回退。 */
    val suppressGeneric: Boolean = false,
) {
    /** 生效区域（缺省推导，v1 等价）。 */
    val effectiveZone: String
        get() = zone ?: if (mode == MODE_ANY) ZONE_ANY else ZONE_ENTRY

    /** 弹窗窗口是否放行（null 时按运行模式：splash 拦、any 放行——Q-206 已决议）。 */
    fun popupAllowed(runMode: String): Boolean = popupAware ?: (runMode == MODE_ANY)

    companion object {
        const val MODE_SPLASH = "splash"
        const val MODE_ANY = "any"

        const val ZONE_ENTRY = "entry"
        const val ZONE_ACTIVITY = "activity"
        const val ZONE_POPUP = "popup"
        const val ZONE_ANY = "any"

        val VALID_ZONES = setOf(ZONE_ENTRY, ZONE_ACTIVITY, ZONE_POPUP, ZONE_ANY)
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
            require(group.zone == null || group.zone in AdSkipGroup.VALID_ZONES) {
                "未知 zone=${group.zone}"
            }
            require(group.settleMs in 0..2000) { "settleMs 越界=${group.settleMs}" }
        }
        return pack
    }
}
