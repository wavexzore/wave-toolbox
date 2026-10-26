package com.wavexzore.toolbox.core.accessibility.action

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
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
    val source: AccessibilityNodeInfo,
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
    override val isChecked: Boolean get() = source.isChecked
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
            val hasArea = bounds.width > 0 || bounds.height > 0
            val onScreen = hasArea &&
                bounds.right > 0 && bounds.left < screenWidth &&
                bounds.bottom > 0 && bounds.top < screenHeight
            val degenerate = bounds.width <= 0 || bounds.height <= 0
            // B-205：退化节点豁免——Compose 实测按钮 height=0 且 clickable（市场种果树「关闭」，
            // bounds [1077,2564][1179,2564]）；框架 isVisibleToUser 不可靠时，
            // 可点击且非全退化的节点仍视为可见（全退化 [0,0,0,0] 与旧行为一致）。
            val visible = onScreen && (source.isVisibleToUser || (degenerate && source.isClickable))
            val view = AndroidNodeView(source, depth, bounds, visible)
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

/**
 * 目标包取树时的活动窗回退守卫（纯逻辑，JVM 可测）：
 * 请求的是其它包时，只允许回退到「能确认归属且非工具箱自身」的活动窗。
 * 自身窗口会让「自动跳过」等文案命中跳过关键词、把特性开关点掉；归属未知时同样
 * 放弃（宁可不点，不误点）。
 */
internal fun allowsActiveWindowFallback(
    requestedPkg: String?,
    activeRootPkg: String?,
    selfPkg: String,
): Boolean = requestedPkg == null || requestedPkg == selfPkg ||
    (activeRootPkg != null && activeRootPkg != selfPkg)

/**
 * 是否允许用 `rootInActiveWindow` 兜底取树。
 * 显式请求非主屏时不允许：该 API 返回的是当前焦点屏，可能正是主屏。
 */
internal fun allowsActiveRootFallback(displayId: Int?): Boolean = displayId == null || displayId == 0

/** 当前活动窗口树提供器（服务未连接/窗口缺失时返回 null）。 */
@Singleton
class WindowTreeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Picked(
        val tree: NodeAdapter.Tree,
        val displayId: Int,
        val windowType: Int,
        val rootPkg: String?,
        /** 窗口外框（D-235；用于主窗口/Dialog/浮窗判定，不按 root 节点内容判定）。 */
        val windowBounds: Rect? = null,
        val isActive: Boolean = false,
        val isFocused: Boolean = false,
        val windowId: Int = -1,
    )

    /** 不建树的窗口元数据（D-235：先判形态再决定是否扫描，避免浮窗场景白建 2500 节点）。 */
    data class WindowInfo(
        val displayId: Int,
        val windowType: Int,
        val rootPkg: String?,
        val bounds: Rect?,
        val isActive: Boolean,
        val isFocused: Boolean,
        val windowId: Int,
    )

    private class Selected(val window: AccessibilityWindowInfo?, val root: AccessibilityNodeInfo)

    /**
     * 取目标包所在窗口的树；多显示器（如背屏）时按 [displayId]/[pkg] 精确选择：
     * 1. displayId+pkg 命中的 focused/active 应用窗口；
     * 2. 退化为 pkg 命中 / focused 窗口；
     * 3. 最后退化为 rootInActiveWindow（仅 [allowForeignFallback] 为 true 时）。
     *
     * [allowForeignFallback] = false 时只返回 root 包名严格等于 [pkg] 的窗口树，
     * 供自动点击链使用（李跳跳同款「窗口身份优先」约束，避免旧目标扫描新前台）。
     */
    fun currentTree(
        pkg: String? = null,
        displayId: Int? = null,
        allowForeignFallback: Boolean = true,
    ): Picked? {
        val selected = selectWindow(pkg, displayId, allowForeignFallback) ?: return null
        val info = infoOf(selected)
        // 兜底身份校验：返回值必须属于请求的显示器，跨屏树一律放弃。
        if (displayId != null && info.displayId != displayId) return null
        val (width, height) = displaySize(info.displayId)
        val tree = runCatching {
            NodeAdapter.fromRoot(selected.root, width, height)
        }.getOrNull() ?: return null
        return Picked(
            tree = tree,
            displayId = info.displayId,
            windowType = info.windowType,
            rootPkg = info.rootPkg,
            windowBounds = info.bounds,
            isActive = info.isActive,
            isFocused = info.isFocused,
            windowId = info.windowId,
        )
    }

    /**
     * 指定显示器的树（仅诊断使用，不参与自动点击链）。
     *
     * `getWindows()` 只返回服务所在显示器（通常是主屏）的窗口；背屏等副屏窗口位于
     * `getWindowsOnAllDisplays()`。这里按 displayId 精确读取，允许选取该屏非
     * focused/active 的应用窗口，但**绝不**回退到其它显示器。
     */
    fun currentTreeForDisplay(displayId: Int): Picked? {
        val service = SelectToSpeakService.instance ?: return null
        val windows = windowsOnDisplay(service, displayId)
        val applicationWindows = windows.filter { window ->
            val root = runCatching { window.root }.getOrNull()
            root != null && (window.type == AccessibilityWindowInfo.TYPE_APPLICATION || root.packageName != null)
        }
        val window = applicationWindows.firstOrNull { it.isFocused }
            ?: applicationWindows.firstOrNull { it.isActive }
            ?: applicationWindows.firstOrNull()
            ?: return null
        if (displayIdOf(window) != displayId) return null
        val root = runCatching { window.root }.getOrNull() ?: return null
        val info = infoOf(Selected(window, root))
        val (width, height) = displaySize(info.displayId)
        val tree = runCatching { NodeAdapter.fromRoot(root, width, height) }.getOrNull() ?: return null
        return Picked(
            tree = tree,
            displayId = info.displayId,
            windowType = info.windowType,
            rootPkg = info.rootPkg,
            windowBounds = info.bounds,
            isActive = info.isActive,
            isFocused = info.isFocused,
            windowId = info.windowId,
        )
    }

    private fun windowsOnDisplay(
        service: SelectToSpeakService,
        displayId: Int,
    ): List<AccessibilityWindowInfo> {
        val all = runCatching { service.windowsOnAllDisplays[displayId] }.getOrNull()
        if (!all.isNullOrEmpty()) return all
        return runCatching { service.windows }.getOrNull().orEmpty()
            .filter { displayIdOf(it) == displayId }
    }

    /**
     * 目标包当前窗口的元数据（不建树；D-235）。
     *
     * 与 [currentTree] 共用同一选窗口径，供自动点击链「先判形态、后建树」：
     * 浮窗/弹窗上下文可在建树前被拦下（保持旧路径的 CPU 语义）。
     */
    fun windowInfo(
        pkg: String? = null,
        displayId: Int? = null,
        allowForeignFallback: Boolean = true,
    ): WindowInfo? = selectWindow(pkg, displayId, allowForeignFallback)?.let(::infoOf)

    private fun selectWindow(
        pkg: String?,
        displayId: Int?,
        allowForeignFallback: Boolean,
    ): Selected? {
        val service = SelectToSpeakService.instance ?: return null

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

        val activeRoot = runCatching { service.rootInActiveWindow }.getOrNull()
        // 指定了非主屏时禁止跨屏回退：无障碍服务可能看不到某些副屏（如 overlay display），
        // rootInActiveWindow 会返回主屏窗口；拿主屏树当副屏用会导致坐标/归属全错。
        val crossDisplayFallbackAllowed = allowsActiveRootFallback(displayId)
        val fallbackRoot = if (allowForeignFallback && crossDisplayFallbackAllowed) {
            // 误操作修复（2026-10-19）：为其它包取树时，禁止活动窗回退到工具箱自身。
            // 目标 App 已切到后台/自身包时，rootInActiveWindow 会返回工具箱 UI——其中
            // 「自动跳过」等自建文案会命中跳过关键词，导致把自己特性开关点掉。
            activeRoot?.takeIf { candidate ->
                allowsActiveWindowFallback(
                    requestedPkg = pkg,
                    activeRootPkg = runCatching { candidate.packageName?.toString() }.getOrNull(),
                    selfPkg = context.packageName,
                )
            }
        } else {
            null
        }
        val root = window?.let { runCatching { it.root }.getOrNull() }
            ?: fallbackRoot
            ?: return null
        return Selected(window, root)
    }

    private fun infoOf(selected: Selected): WindowInfo {
        val window = selected.window
        return WindowInfo(
            displayId = window?.let(::displayIdOf) ?: 0,
            windowType = window?.type ?: -1,
            rootPkg = runCatching { selected.root.packageName?.toString() }.getOrNull(),
            bounds = windowBounds(window, selected.root),
            isActive = window?.isActive ?: false,
            isFocused = window?.isFocused ?: false,
            windowId = window?.id ?: -1,
        )
    }

    private fun windowBounds(window: AccessibilityWindowInfo?, root: AccessibilityNodeInfo): Rect? {
        val raw = android.graphics.Rect()
        val ok = runCatching {
            if (window != null) window.getBoundsInScreen(raw) else root.getBoundsInScreen(raw)
        }.isSuccess
        return if (ok) Rect(raw.left, raw.top, raw.right, raw.bottom) else null
    }

    /**
     * pkg 的 focused/active 应用窗口根节点边界（用于区分"全屏 Activity 窗口"与"小弹窗"）；
     * 取不到返回 null。
     */
    fun focusedWindowBounds(pkg: String): com.wavexzore.toolbox.core.selector.Rect? {
        val service = SelectToSpeakService.instance ?: return null
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val window = windows.firstOrNull { w ->
            runCatching {
                (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && displayIdOf(w) == 0) &&
                    (w.isFocused || w.isActive) && w.root?.packageName?.toString() == pkg
            }.getOrDefault(false)
        } ?: return null
        val root = runCatching { window.root }.getOrNull() ?: return null
        val b = runCatching { android.graphics.Rect().also { root.getBoundsInScreen(it) } }.getOrNull() ?: return null
        return com.wavexzore.toolbox.core.selector.Rect(b.left, b.top, b.right, b.bottom)
    }

    /**
     * pkg 是否处于前台：存在该包的 focused/active 应用窗口（主屏），且无其它应用窗口占焦。
     * 服务未连接/窗口未知时保守返回 true（沿用旧行为）。用于避免"后台窗口按坐标点到前台应用"。
     */
    fun isAppForeground(pkg: String): Boolean {
        val service = SelectToSpeakService.instance ?: return true
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && displayIdOf(it) == 0 }
        if (windows.isEmpty()) return true
        val otherFocused = windows.any { w ->
            runCatching {
                (w.isFocused || w.isActive) && w.root?.packageName?.toString()?.let { it != pkg } == true
            }.getOrDefault(false)
        }
        return !otherFocused
    }

    /** `AccessibilityWindowInfo.displayId` 仅 API 30+；低版本回退 0（主屏）。 */
    private fun displayIdOf(window: AccessibilityWindowInfo): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.displayId else 0

    /**
     * 指定显示器的像素尺寸（用于节点屏内可见性判定）。
     *
     * 主屏/取不到时回退 `resources.displayMetrics`；副屏（背屏、`overlay_display_devices`）
     * 必须用该 Display 的真实尺寸，否则按主屏宽度会把副屏右侧节点误判为不可见。
     */
    @Suppress("DEPRECATION")
    private fun displaySize(displayId: Int): Pair<Int, Int> {
        val fallback = context.resources.displayMetrics
        if (displayId == 0) return fallback.widthPixels to fallback.heightPixels
        val display = runCatching {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(displayId)
        }.getOrNull() ?: return fallback.widthPixels to fallback.heightPixels
        val metrics = DisplayMetrics()
        val ok = runCatching { display.getRealMetrics(metrics) }.isSuccess
        return if (ok && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            metrics.widthPixels to metrics.heightPixels
        } else {
            fallback.widthPixels to fallback.heightPixels
        }
    }
}

