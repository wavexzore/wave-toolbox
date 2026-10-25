package com.wavexzore.toolbox.core.accessibility.diagnostics

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.wavexzore.toolbox.core.accessibility.action.WindowTreeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多屏诊断（主屏支持 / 副屏实验性）。
 *
 * 每块 display 输出：id、名称、真实尺寸、刷新率、状态、窗口数与窗口归属、按 displayId 取树结果。
 * 说明：副屏动作能力尚未在真实物理副屏验收；本诊断只证明「取树/窗口枚举」路径，
 * 不把 overlay display 的可见性当作动作支持。
 */
@Singleton
class DisplayDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val treeProvider: WindowTreeProvider,
) {

    data class WindowProbe(
        val id: Int,
        val type: Int,
        val rootPkg: String?,
        val focused: Boolean,
        val active: Boolean,
        val bounds: String?,
    )

    data class TreeProbe(
        val ok: Boolean,
        val rootPkg: String?,
        val nodes: Int = 0,
        val truncated: Boolean = false,
        val windowType: Int = -1,
        val bounds: String? = null,
        val message: String? = null,
    )

    data class DisplayProbe(
        val id: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
        val refreshRate: Float,
        val state: Int,
        val rotation: Int,
        val primary: Boolean,
        val support: String,
        val windows: List<WindowProbe>,
        val tree: TreeProbe,
    )

    data class Report(
        val displays: List<DisplayProbe>,
        val note: String,
    )

    fun snapshot(): Report {
        val displays = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.displays
            ?.sortedBy { it.displayId }
            .orEmpty()
        val probes = displays.map { display ->
            val (width, height) = realSize(display)
            val metrics = android.util.DisplayMetrics()
            runCatching { display.getMetrics(metrics) }
            val windows = windowsOf(display.displayId)
            DisplayProbe(
                id = display.displayId,
                name = display.name,
                width = width,
                height = height,
                densityDpi = metrics.densityDpi,
                refreshRate = display.refreshRate,
                state = display.state,
                rotation = display.rotation,
                primary = display.displayId == Display.DEFAULT_DISPLAY,
                support = supportOf(display),
                windows = windows,
                tree = probeTree(display.displayId),
            )
        }
        return Report(
            displays = probes,
            note = "主屏=已支持；副屏=实验性（取树按 displayId，动作未在真实副屏验收）。",
        )
    }

    private fun probeTree(displayId: Int): TreeProbe {
        if (SelectToSpeakService.instance == null) {
            return TreeProbe(ok = false, rootPkg = null, message = "无障碍服务未连接")
        }
        val picked = runCatching {
            treeProvider.currentTree(
                pkg = null,
                displayId = displayId,
                // 主屏允许 rootInActiveWindow 兜底；非主屏保持 fail-safe，禁止跨屏回退。
                allowForeignFallback = displayId == Display.DEFAULT_DISPLAY,
            )
        }.getOrElse { t ->
            return TreeProbe(ok = false, rootPkg = null, message = "取树异常: ${t.message}")
        } ?: return TreeProbe(
            ok = false,
            rootPkg = null,
            message = "未取到树（该屏无焦点窗口或无障碍不可见）",
        )
        if (picked.displayId != displayId) {
            return TreeProbe(
                ok = false,
                rootPkg = picked.rootPkg,
                message = "跨屏树已拒绝: 返回 display=${picked.displayId}",
            )
        }
        return TreeProbe(
            ok = true,
            rootPkg = picked.rootPkg,
            nodes = picked.tree.nodeCount,
            truncated = picked.tree.truncated,
            windowType = picked.windowType,
            bounds = picked.windowBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
        )
    }

    private fun windowsOf(displayId: Int): List<WindowProbe> {
        val service = SelectToSpeakService.instance ?: return emptyList()
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        return windows.mapNotNull { window ->
            val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.displayId else 0
            if (id != displayId) return@mapNotNull null
            WindowProbe(
                id = window.id,
                type = window.type,
                rootPkg = runCatching { window.root?.packageName?.toString() }.getOrNull(),
                focused = window.isFocused,
                active = window.isActive,
                bounds = boundsOf(window),
            )
        }
    }

    private fun boundsOf(window: AccessibilityWindowInfo): String? {
        val rect = android.graphics.Rect()
        return runCatching {
            window.getBoundsInScreen(rect)
            "${rect.left},${rect.top},${rect.right},${rect.bottom}"
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun realSize(display: Display): Pair<Int, Int> {
        val point = android.graphics.Point()
        runCatching { display.getRealSize(point) }
        if (point.x > 0 && point.y > 0) return point.x to point.y
        val metrics = android.util.DisplayMetrics()
        runCatching { display.getRealMetrics(metrics) }
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun supportOf(display: Display): String = when {
        display.displayId == Display.DEFAULT_DISPLAY -> "主屏 · 支持"
        (display.flags and Display.FLAG_PRIVATE) != 0 -> "虚拟/私有屏 · 实验性"
        else -> "副屏 · 实验性"
    }

    companion object {
        fun displayStateText(state: Int): String = when (state) {
            Display.STATE_OFF -> "off"
            Display.STATE_ON -> "on"
            Display.STATE_DOZE -> "doze"
            Display.STATE_DOZE_SUSPEND -> "doze-suspend"
            Display.STATE_ON_SUSPEND -> "on-suspend"
            Display.STATE_UNKNOWN -> "unknown"
            else -> "state=$state"
        }

        fun windowTypeText(type: Int): String = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "ime"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "a11y-overlay"
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split-divider"
            else -> "type=$type"
        }
    }
}
