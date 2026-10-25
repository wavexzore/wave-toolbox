package com.wavexzore.toolbox.core.rules.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 规则组（统一模型：通用规则 + adskip 规则包共用；README §6.1 扩展）。 */
@Serializable
data class RawRuleGroup(
    val id: String,
    val name: String = "",
    val order: Int = 0,
    val enabled: Boolean = true,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val apps: List<RawApp> = emptyList(),
    @Serializable(with = IntVersionRangeSerializer::class)
    val versionCode: VersionRange<Int>? = null,
    @Serializable(with = StringVersionRangeSerializer::class)
    val versionName: VersionRange<String>? = null,
    val rules: List<RawRule> = emptyList(),
    /** 组级活动页约束（adskip 组使用；通用组用 rule 级约束）。 */
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    // ---- 统一模型：adskip 组级字段（通用规则缺省忽略） ----
    /** `generic` | `adskip`。 */
    val kind: String = KIND_GENERIC,
    /** 广告跳过运行模式：`splash` | `any`。 */
    val mode: String = MODE_SPLASH,
    /** 生效区域；null=由 [mode] 推导。 */
    val zone: String? = null,
    /** 组级选择器（OR 语义；adskip 组使用）。 */
    val matches: List<String> = emptyList(),
    /** 组级追加关键词（adskip 组使用）。 */
    val keywords: List<String> = emptyList(),
    /** 树路径兜底：`0/2/1`。 */
    val path: String? = null,
    /** 组级坐标兜底（px 或 %）。 */
    @Serializable(with = RulePositionSerializer::class)
    val position: RulePosition? = null,
    /** 组级最小间隔毫秒（adskip 组使用）。 */
    val actionCd: Long = 800,
    /** 同实例最大执行次数（adskip 组使用）。 */
    val actionMaximum: Int = 1,
    /** 组级年轻窗口延迟毫秒（adskip 组使用）。 */
    val delayMs: Long = 150,
    /** activity 区域稳定期毫秒（adskip 组使用）。 */
    val settleMs: Long = 350,
    /** 弹窗窗口放行三态（adskip 组使用；null=按运行模式）。 */
    val popupAware: Boolean? = null,
    /** 命中活动页时压制通用 keyword/vid 回退（adskip 组使用）。 */
    val suppressGeneric: Boolean = false,
    /** 规则包版本（adskip 组使用）。 */
    val packVersion: Int = 1,
    /** 来源标记（`builtin` / `user:<name>`）：仅运行时使用，不写入规则 JSON。 */
    @Transient
    val source: String = SOURCE_BUILTIN,
) {
    /** 生效区域（缺省推导，v1 等价）。 */
    val effectiveZone: String
        get() = zone ?: if (mode == MODE_ANY) ZONE_ANY else ZONE_ENTRY

    /** 弹窗窗口是否放行（null 时按运行模式：splash 拦、any 放行）。 */
    fun popupAllowed(runMode: String): Boolean = popupAware ?: (runMode == MODE_ANY)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2

        const val KIND_GENERIC = "generic"
        const val KIND_ADSKIP = "adskip"

        const val MODE_SPLASH = "splash"
        const val MODE_ANY = "any"

        const val ZONE_ENTRY = "entry"
        const val ZONE_ACTIVITY = "activity"
        const val ZONE_POPUP = "popup"
        const val ZONE_ANY = "any"

        val VALID_ZONES = setOf(ZONE_ENTRY, ZONE_ACTIVITY, ZONE_POPUP, ZONE_ANY)

        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_USER_PREFIX = "user:"
    }
}

/** 规则包（adskip 导入/导出格式；内部统一为 [RawRuleGroup] + [kind]=adskip）。 */
@Serializable
data class RawRulePack(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val pkg: String,
    val packVersion: Int = 1,
    val groups: List<RawRuleGroup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/** 组内目标应用（pkg 为键；可带应用级版本约束与开关）。 */
@Serializable
data class RawApp(
    val id: String,
    val enabled: Boolean = true,
    @Serializable(with = IntVersionRangeSerializer::class)
    val versionCode: VersionRange<Int>? = null,
    @Serializable(with = StringVersionRangeSerializer::class)
    val versionName: VersionRange<String>? = null,
)

/** 规则条目（README §6.1 schema + schemaVersion/note）。 */
@Serializable
data class RawRule(
    val id: String,
    val name: String = "",
    val order: Int = 0,
    val enabled: Boolean = true,
    val activityIds: List<String> = emptyList(),
    val excludeActivityIds: List<String> = emptyList(),
    @Serializable(with = IntVersionRangeSerializer::class)
    val versionCode: VersionRange<Int>? = null,
    @Serializable(with = StringVersionRangeSerializer::class)
    val versionName: VersionRange<String>? = null,
    val matches: List<String> = emptyList(),
    val anyMatches: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val action: RuleAction = RuleAction.CLICK,
    @Serializable(with = RulePositionSerializer::class)
    val position: RulePosition? = null,
    val actionCd: Long = 500,
    val actionMaximum: Int? = null,
    val note: String? = null,
)

@Serializable
enum class RuleAction {
    @SerialName("click")
    CLICK,

    @SerialName("longClick")
    LONG_CLICK,

    /** 点击命中节点中心（强制手势，跳过 performAction）。 */
    @SerialName("clickCenter")
    CLICK_CENTER,

    /** 按规则 position 坐标点击（px 或 %）。 */
    @SerialName("position")
    POSITION,

    @SerialName("back")
    BACK,

    @SerialName("scrollForward")
    SCROLL_FORWARD,

    @SerialName("scrollBackward")
    SCROLL_BACKWARD,

    @SerialName("none")
    NONE,
}

/**
 * 坐标：px（数字）与百分比（`"95%"` 字符串）二选一；运行时由执行器换算（T-007）。
 */
data class RulePosition(
    val x: Int? = null,
    val y: Int? = null,
    val xPercent: Float? = null,
    val yPercent: Float? = null,
)

/** 版本区间：`{min,max,include,exclude}` 对象形式或 `[min,max,include,exclude]` 数组形式。 */
data class VersionRange<T>(
    val min: T? = null,
    val max: T? = null,
    val include: List<T> = emptyList(),
    val exclude: List<T> = emptyList(),
) {
    val isEmpty: Boolean
        get() = min == null && max == null && include.isEmpty() && exclude.isEmpty()
}

// ---------------------------------------------------------------------------
// 自定义序列化（JSON5 经 Json5.parseToJsonElement 后统一为 JsonElement）
// ---------------------------------------------------------------------------

private val versionRangeDescriptor: SerialDescriptor = JsonElement.serializer().descriptor

private fun rangeToJsonElement(min: JsonElement?, max: JsonElement?, include: List<JsonElement>, exclude: List<JsonElement>): JsonElement =
    buildJsonObject {
        if (min != null && min !is JsonNull) put("min", min)
        if (max != null && max !is JsonNull) put("max", max)
        if (include.isNotEmpty()) put("include", buildJsonArray { include.forEach(::add) })
        if (exclude.isNotEmpty()) put("exclude", buildJsonArray { exclude.forEach(::add) })
    }

/** 解析对象/数组两形态为 (min, max, include, exclude) 四元组。 */
private fun parseRangeParts(element: JsonElement): Quadruple<JsonElement?, JsonElement?, List<JsonElement>, List<JsonElement>> {
    return when (element) {
        is JsonObject -> Quadruple(
            element["min"],
            element["max"],
            element["include"]?.let { it as? JsonArray ?: JsonArray(listOf(it)) }?.toList() ?: emptyList(),
            element["exclude"]?.let { it as? JsonArray ?: JsonArray(listOf(it)) }?.toList() ?: emptyList(),
        )
        is JsonArray -> Quadruple(
            element.getOrNull(0),
            element.getOrNull(1),
            element.getOrNull(2)?.let { it as? JsonArray ?: JsonArray(listOf(it)) }?.toList() ?: emptyList(),
            element.getOrNull(3)?.let { it as? JsonArray ?: JsonArray(listOf(it)) }?.toList() ?: emptyList(),
        )
        else -> Quadruple(null, null, emptyList(), emptyList())
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun JsonElement?.asIntOrNull(): Int? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content.toIntOrNull()
        else -> null
    }

private fun JsonElement?.asStringOrNull(): String? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }

object IntVersionRangeSerializer : KSerializer<VersionRange<Int>> {
    override val descriptor: SerialDescriptor = versionRangeDescriptor

    override fun deserialize(decoder: Decoder): VersionRange<Int> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val (min, max, include, exclude) = parseRangeParts(element)
        return VersionRange(
            min = min.asIntOrNull(),
            max = max.asIntOrNull(),
            include = include.mapNotNull { it.asIntOrNull() },
            exclude = exclude.mapNotNull { it.asIntOrNull() },
        )
    }

    override fun serialize(encoder: Encoder, value: VersionRange<Int>) {
        (encoder as JsonEncoder).encodeJsonElement(
            rangeToJsonElement(
                value.min?.let(::JsonPrimitive),
                value.max?.let(::JsonPrimitive),
                value.include.map(::JsonPrimitive),
                value.exclude.map(::JsonPrimitive),
            ),
        )
    }
}

object StringVersionRangeSerializer : KSerializer<VersionRange<String>> {
    override val descriptor: SerialDescriptor = versionRangeDescriptor

    override fun deserialize(decoder: Decoder): VersionRange<String> {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        val (min, max, include, exclude) = parseRangeParts(element)
        return VersionRange(
            min = min.asStringOrNull(),
            max = max.asStringOrNull(),
            include = include.mapNotNull { it.asStringOrNull() },
            exclude = exclude.mapNotNull { it.asStringOrNull() },
        )
    }

    override fun serialize(encoder: Encoder, value: VersionRange<String>) {
        (encoder as JsonEncoder).encodeJsonElement(
            rangeToJsonElement(
                value.min?.let(::JsonPrimitive),
                value.max?.let(::JsonPrimitive),
                value.include.map(::JsonPrimitive),
                value.exclude.map(::JsonPrimitive),
            ),
        )
    }
}

object RulePositionSerializer : KSerializer<RulePosition> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): RulePosition {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        // 兼容两种写法：
        // - 基座：`{x: 900, y: 120}` / `{x: "95%", y: "5%"}`
        // - adskip：`{xPercent: 95, yPercent: 5}`
        val x = obj["x"]?.asIntOrNull()
        val y = obj["y"]?.asIntOrNull()
        val xPercent = obj["xPercent"]?.asPercentValue() ?: obj["x"]?.asPercentValue()
        val yPercent = obj["yPercent"]?.asPercentValue() ?: obj["y"]?.asPercentValue()
        return RulePosition(x = x, y = y, xPercent = xPercent, yPercent = yPercent)
    }

    private fun JsonElement?.asPercentValue(): Float? =
        when (this) {
            null, is JsonNull -> null
            is JsonPrimitive -> when {
                isString -> content.removeSuffix("%").trim().toFloatOrNull()?.takeIf { content.endsWith("%") }
                else -> content.toFloatOrNull()
            }
            else -> null
        }

    override fun serialize(encoder: Encoder, value: RulePosition) {
        (encoder as JsonEncoder).encodeJsonElement(
            buildJsonObject {
                value.xPercent?.let { put("xPercent", it) } ?: value.x?.let { put("x", it) }
                value.yPercent?.let { put("yPercent", it) } ?: value.y?.let { put("y", it) }
            },
        )
    }
}
