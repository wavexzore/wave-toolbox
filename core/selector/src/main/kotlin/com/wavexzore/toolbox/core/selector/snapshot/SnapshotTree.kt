package com.wavexzore.toolbox.core.selector.snapshot

import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.Rect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 快照树：JSON fixtures 与真实树导出的统一载体（04 §5.2 测试与 T-010 检查器采集用）。
 *
 * JSON 节点字段：text/desc/vid/class/bounds[l,t,r,b]/clickable/visible/editable/scrollable/drawingOrder/children。
 */
@Serializable
data class SnapshotNodeDto(
    val text: String? = null,
    val desc: String? = null,
    val vid: String? = null,
    @SerialName("class") val className: String? = null,
    val bounds: List<Int> = listOf(0, 0, 0, 0),
    val clickable: Boolean = false,
    val visible: Boolean = true,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val drawingOrder: Int? = null,
    val children: List<SnapshotNodeDto> = emptyList(),
)

class SnapshotNode(
    override val text: String?,
    override val contentDescription: String?,
    override val viewId: String?,
    override val className: String?,
    override val bounds: Rect,
    override val isClickable: Boolean,
    override val isVisible: Boolean,
    override val isEditable: Boolean,
    override val isScrollable: Boolean,
    override val depth: Int,
    override val drawingOrder: Int?,
    override val children: List<NodeView>,
) : NodeView

object SnapshotTree {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun fromJson(text: String): SnapshotNode =
        fromDto(json.decodeFromString(SnapshotNodeDto.serializer(), text))

    fun fromDto(dto: SnapshotNodeDto, depth: Int = 0): SnapshotNode {
        if (dto.bounds.size != 4) {
            throw IllegalArgumentException("bounds must be [left, top, right, bottom], got ${dto.bounds}")
        }
        return SnapshotNode(
            text = dto.text,
            contentDescription = dto.desc,
            viewId = dto.vid,
            className = dto.className,
            bounds = Rect(dto.bounds[0], dto.bounds[1], dto.bounds[2], dto.bounds[3]),
            isClickable = dto.clickable,
            isVisible = dto.visible,
            isEditable = dto.editable,
            isScrollable = dto.scrollable,
            depth = depth,
            drawingOrder = dto.drawingOrder,
            children = dto.children.map { fromDto(it, depth + 1) },
        )
    }

    fun toJson(root: NodeView): String =
        json.encodeToString(SnapshotNodeDto.serializer(), toDto(root))

    fun toDto(node: NodeView): SnapshotNodeDto = SnapshotNodeDto(
        text = node.text,
        desc = node.contentDescription,
        vid = node.viewId,
        className = node.className,
        bounds = listOf(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom),
        clickable = node.isClickable,
        visible = node.isVisible,
        editable = node.isEditable,
        scrollable = node.isScrollable,
        drawingOrder = node.drawingOrder,
        children = node.children.map(::toDto),
    )
}

/** 设备侧快照采集器接口（T-010 由真实树导出 fixtures 落库）。 */
interface SnapshotCollector {
    /** 导出当前活动窗口树为 JSON；无窗口时返回 null。 */
    fun collectJson(): String?
}
