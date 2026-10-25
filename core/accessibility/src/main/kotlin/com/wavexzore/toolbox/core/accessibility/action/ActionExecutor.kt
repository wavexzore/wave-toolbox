package com.wavexzore.toolbox.core.accessibility.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.rules.model.RawRule
import com.wavexzore.toolbox.core.rules.model.RuleAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 动作执行器（T-007，04 §5.5）：统一执行与防误触。
 *
 * 执行链：`performAction` →（失败且兜底开启）手势坐标 → 结果对象；
 * 全部动作在 `AppDispatchers.action` 单线程串行（防手势互斥、顺序错乱）。
 */
@Singleton
class ActionExecutor @Inject constructor(
    private val dispatchers: AppDispatchers,
    settingsRepository: SettingsRepository,
) {

    data class Result(
        val ok: Boolean,
        val mechanism: String,
        val node: String? = null,
        val reason: String? = null,
    )

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var fallbackEnabled = SettingsRepository.DEFAULT_CLICK_FALLBACK

    init {
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            settingsRepository.clickFallback.collect { fallbackEnabled = it }
        }
    }

    suspend fun execute(rule: RawRule, target: AndroidNodeView?, screenWidth: Int, screenHeight: Int): Result =
        withContext(dispatchers.action) {
            val service = SelectToSpeakService.instance ?: return@withContext Result(false, "none", reason = "服务未连接")
            when (rule.action) {
                RuleAction.NONE -> Result(true, "none")
                RuleAction.BACK -> Result(service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK), "back")
                RuleAction.CLICK -> click(service, target, screenWidth, screenHeight, long = false)
                RuleAction.LONG_CLICK -> click(service, target, screenWidth, screenHeight, long = true)
                RuleAction.CLICK_CENTER -> target?.let { view ->
                    val center = ClickResolver.centerOf(view.bounds)
                        ?: return@withContext Result(false, "gesture.center", reason = "零面积")
                    Result(tap(service, center.first, center.second, screenWidth, screenHeight), "gesture.center", view.describe())
                } ?: Result(false, "gesture.center", reason = "无目标节点")
                RuleAction.POSITION -> position(service, rule, screenWidth, screenHeight)
                RuleAction.SCROLL_FORWARD -> scroll(target, forward = true)
                RuleAction.SCROLL_BACKWARD -> scroll(target, forward = false)
            }
        }

    private suspend fun click(
        service: AccessibilityService,
        target: AndroidNodeView?,
        screenWidth: Int,
        screenHeight: Int,
        long: Boolean,
    ): Result {
        if (target == null) return Result(false, "performAction", reason = "无目标节点")
        val resolution = ClickResolver.resolve(target, NodeAdapter.ancestorsNearestFirst(target), screenWidth)
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        return when (resolution) {
            is ClickResolver.Resolution.Direct -> {
                val view = resolution.view
                val performed = (view as? AndroidNodeView)?.source?.performAction(action) == true
                if (performed) {
                    return Result(true, if (long) "performAction.long" else "performAction", view.describeOrNull())
                }
                if (!fallbackEnabled) return Result(false, "performAction", reason = "performAction=false 且兜底关闭")
                val center = ClickResolver.centerOf(view.bounds) ?: return Result(false, "performAction", reason = "兜底无坐标")
                val ok = gesture(service, center.first, center.second, if (long) LONG_PRESS_MS else TAP_MS, screenWidth, screenHeight)
                Result(ok, if (long) "gesture.long" else "gesture", view.describeOrNull())
            }
            is ClickResolver.Resolution.CenterTap -> {
                if (!fallbackEnabled) return Result(false, "gesture", reason = "兜底关闭")
                val ok = gesture(service, resolution.x, resolution.y, if (long) LONG_PRESS_MS else TAP_MS, screenWidth, screenHeight)
                Result(ok, "gesture.center", target.describe())
            }
            ClickResolver.Resolution.NoTarget -> Result(false, "none", reason = "无可用目标（零面积）")
        }
    }

    private suspend fun position(service: AccessibilityService, rule: RawRule, screenWidth: Int, screenHeight: Int): Result {
        val pos = rule.position ?: return Result(false, "gesture.position", reason = "position 缺失")
        val x = pos.x ?: pos.xPercent?.let { (screenWidth * it / 100f).roundToInt() }
            ?: return Result(false, "gesture.position", reason = "x 缺失")
        val y = pos.y ?: pos.yPercent?.let { (screenHeight * it / 100f).roundToInt() }
            ?: return Result(false, "gesture.position", reason = "y 缺失")
        val ok = gesture(service, x, y, TAP_MS, screenWidth, screenHeight)
        return Result(ok, "gesture.position", "($x,$y)")
    }

    private fun scroll(target: AndroidNodeView?, forward: Boolean): Result {
        var cursor: AndroidNodeView? = target
        while (cursor != null && !(cursor.isScrollable && cursor.isVisible)) cursor = cursor.parent
        val scrollable = cursor ?: return Result(false, "scroll", reason = "无滚动容器")
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val ok = scrollable.source.performAction(action)
        return Result(ok, if (forward) "scroll.forward" else "scroll.backward", scrollable.describe())
    }

    private suspend fun tap(
        service: AccessibilityService,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean = gesture(service, x, y, TAP_MS, screenWidth, screenHeight)

    private suspend fun gesture(
        service: AccessibilityService,
        x: Int,
        y: Int,
        durationMs: Long,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean =
        withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                // 坐标属于目标窗口所在显示器；不能再用主屏 metrics 截断副屏坐标。
                val maxX = (screenWidth - 1).coerceAtLeast(0)
                val maxY = (screenHeight - 1).coerceAtLeast(0)
                val safeX = if (screenWidth > 0) x.coerceIn(0, maxX) else x
                val safeY = if (screenHeight > 0) y.coerceIn(0, maxY) else y
                val path = Path().apply { moveTo(safeX.toFloat(), safeY.toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                    .build()
                val dispatched = service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resumeWith(kotlin.Result.success(true))
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resumeWith(kotlin.Result.success(false))
                        }
                    },
                    handler,
                )
                if (!dispatched && cont.isActive) cont.resumeWith(kotlin.Result.success(false))
            }
        } ?: false

    private fun Any.describeOrNull(): String? = (this as? AndroidNodeView)?.describe() ?: toString()

    companion object {
        /** 轻触按下-抬起时长（pre-check 定稿：60ms 接近系统点击手感）。 */
        const val TAP_MS = 60L

        /** 长按按下-抬起时长。 */
        const val LONG_PRESS_MS = 600L

        private const val GESTURE_TIMEOUT_MS = 1500L
    }
}
