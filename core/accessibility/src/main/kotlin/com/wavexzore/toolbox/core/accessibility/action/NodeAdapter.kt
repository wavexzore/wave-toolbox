package com.wavexzore.toolbox.core.accessibility.action

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备侧 [NodeView] 适配（T-007）：包装 [AccessibilityNodeInfo]，按需读属性（内部持有原始节点，
 * 用于 performAction）。节点不主动 recycle（API 33 起废弃；单事件树生命周期内保持有效）。
 */
class AndroidNodeView internal constructor(
    internal val source: AccessibilityNodeInfo,
    override val depth: Int,
    override val bounds: Rect,
    override val isVisible: Boolean,
) : NodeView {

    internal val childViews = ArrayList<NodeView>(4)
    internal var parent: AndroidNodeView? = null

    override val text: String? get() = source.text?.toString()
    override val contentDescription: String? get() = source.contentDescription?.toString()
    override val viewId: String? get() = source.viewIdResourceName
    override val className: String? get() = source.className?.toString()
    override val isClickable: Boolean get() = source.isClickable
    override val isEditable: Boolean get() = source.isEditable
    override val isScrollable: Boolean get() = source.isScrollable
    override val drawingOrder: Int? get() = source.drawingOrder.takeIf { it >= 0 }
    override val children: List<NodeView> get() = childViews

    fun describe(): String =
        "cls=${className?.substringAfterLast('.')} text=${text?.take(16)} desc=${contentDescription?.take(16)} " +
            "b=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
}

/** 节点树构建（深度/节点数上限防 ANR）。 */
object NodeAdapter {

    const val MAX_DEPTH = 64
    const val MAX_NODES = 2500

    data class Tree(
        val root: AndroidNodeView,
        val nodeCount: Int,
        val truncated: Boolean,
        val screenWidth: Int,
        val screenHeight: Int,
    )

    fun fromRoot(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): Tree {
        val counter = intArrayOf(0)
        var truncated = false

        fun build(source: AccessibilityNodeInfo, depth: Int, parent: AndroidNodeView?): AndroidNodeView? {
            if (counter[0] >= MAX_NODES) {
                truncated = true
                return null
            }
            counter[0]++
            val raw = android.graphics.Rect()
            source.getBoundsInScreen(raw)
            val bounds = Rect(raw.left, raw.top, raw.right, raw.bottom)
            val onScreen = bounds.width > 0 && bounds.height > 0 &&
                bounds.right > 0 && bounds.left < screenWidth &&
                bounds.bottom > 0 && bounds.top < screenHeight
            val view = AndroidNodeView(source, depth, bounds, source.isVisibleToUser && onScreen)
            view.parent = parent
            if (depth < MAX_DEPTH) {
                val childCount = runCatching { source.childCount }.getOrDefault(0)
                for (index in 0 until childCount) {
                    val child = source.getChild(index) ?: continue
                    build(child, depth + 1, view)?.let { view.childViews.add(it) }
                }
            }
            return view
        }

        val rootView = build(root, 0, null)!!
        return Tree(rootView, counter[0], truncated, screenWidth, screenHeight)
    }

    /** 祖先链（由近到远，不含自身）。 */
    fun ancestorsNearestFirst(view: AndroidNodeView): List<NodeView> {
        val out = ArrayList<NodeView>(8)
        var cursor = view.parent
        while (cursor != null) {
            out.add(cursor)
            cursor = cursor.parent
        }
        return out
    }
}

/** 当前活动窗口树提供器（服务未连接/窗口缺失时返回 null）。 */
@Singleton
class WindowTreeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Picked(val tree: NodeAdapter.Tree, val displayId: Int, val windowType: Int, val rootPkg: String?)

    /**
     * 取目标包所在窗口的树；多显示器（如背屏）时按 [displayId]/[pkg] 精确选择：
     * 1. displayId+pkg 命中的 focused/active 应用窗口；
     * 2. 退化为 pkg 命中 / focused 窗口；
     * 3. 最后退化为 rootInActiveWindow。
     */
    fun currentTree(pkg: String? = null, displayId: Int? = null): Picked? {
        val service = SelectToSpeakService.instance ?: return null
        val metrics = context.resources.displayMetrics

        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val applicationWindows = windows.filter { window ->
            val root = runCatching { window.root }.getOrNull()
            root != null && (window.type == AccessibilityWindowInfo.TYPE_APPLICATION || root.packageName != null)
        }

        fun pick(predicate: (AccessibilityWindowInfo) -> Boolean): AccessibilityWindowInfo? =
            applicationWindows.firstOrNull { window ->
                runCatching {
                    predicate(window) &&
                        (displayId == null || displayIdOf(window) == displayId) &&
                        (pkg == null || window.root?.packageName?.toString() == pkg)
                }.getOrDefault(false)
            }

        val window = pick { it.isFocused }
            ?: pick { it.isActive }
            ?: windows.firstOrNull { runCatching { it.root?.packageName?.toString() == pkg && (displayId == null || displayIdOf(it) == displayId) }.getOrDefault(false) }

        val root = window?.let { runCatching { it.root }.getOrNull() }
            ?: runCatching { service.rootInActiveWindow }.getOrNull()
            ?: return null

        val tree = runCatching { NodeAdapter.fromRoot(root, metrics.widthPixels, metrics.heightPixels) }.getOrNull() ?: return null
        return Picked(tree, window?.let(::displayIdOf) ?: 0, window?.type ?: -1, runCatching { root.packageName?.toString() }.getOrNull())
    }

    /** `AccessibilityWindowInfo.displayId` 仅 API 30+；低版本回退 0（主屏）。 */
    private fun displayIdOf(window: AccessibilityWindowInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.displayId else 0
}

