package com.wavexzore.toolbox.core.rules.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

/** 规则组（README §6.1 扩展：组承载 apps 与组级版本约束；规则为组内条目）。 */
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
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
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
        return RulePosition(
            x = obj["x"].asIntOrNull(),
            y = obj["y"].asIntOrNull(),
            xPercent = obj["x"].asPercent(),
            yPercent = obj["y"].asPercent(),
        )
    }

    private fun JsonElement?.asPercent(): Float? {
        val p = this as? JsonPrimitive ?: return null
        if (!p.isString) return null
        return p.content.removeSuffix("%").trim().toFloatOrNull()?.takeIf { p.content.endsWith("%") }
    }

    override fun serialize(encoder: Encoder, value: RulePosition) {
        (encoder as JsonEncoder).encodeJsonElement(
            buildJsonObject {
                value.xPercent?.let { put("x", "${it}%") } ?: value.x?.let { put("x", it) }
                value.yPercent?.let { put("y", "${it}%") } ?: value.y?.let { put("y", it) }
            },
        )
    }
}
