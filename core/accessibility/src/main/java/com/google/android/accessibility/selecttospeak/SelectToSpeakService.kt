package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.wavexzore.toolbox.core.accessibility.action.RuleEngine
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveService
import com.wavexzore.toolbox.core.accessibility.keepalive.OverlayKeepAlive
import com.wavexzore.toolbox.core.accessibility.pipeline.AnrGuard
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.accessibility.pipeline.toA11yEvent
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 引擎无障碍服务。
 *
 * **类名是硬约束（D-011）**：`com.google.android.accessibility.selecttospeak.SelectToSpeakService`。
 * 微信 8.0.x 按服务组件全限定名白名单发布节点树；改名即空树（407B）。类文件必须真实位于
 * `com/google/android/accessibility/selecttospeak/` 包路径下，manifest `android:name` 与此全限定名一致。
 *
 * T-003 范围：注册、静态配置、开关观测、连接日志；事件处理管线在 T-004。
 */
@AndroidEntryPoint
class SelectToSpeakService : AccessibilityService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var pipeline: EventPipeline

    @Inject
    lateinit var anrGuard: AnrGuard

    @Inject
    lateinit var ruleEngine: RuleEngine

    @Inject
    lateinit var featureRegistry: FeatureRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** T-009：1×1 overlay 保活（第二重，与 FGS 独立）。 */
    private val overlayKeepAlive by lazy { OverlayKeepAlive(this) }

    /** 引擎总开关镜像（来自 DataStore；T-004 起决定事件是否进入管线）。 */
    @Volatile
    var engineEnabled: Boolean = SettingsRepository.DEFAULT_ENGINE_ENABLED
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        applyStaticServiceInfo()
        // 幂等；后续重复连接仅覆盖一次收集
        serviceScope.launch {
            settingsRepository.engineEnabled.collect { engineEnabled = it }
        }
        serviceScope.launch {
            // T-004：管线出线事件交 ANR 守卫（系统无响应对话框自动点「等待」）
            pipeline.events.collect { anrGuard.onEvent(it) }
        }
        // T-007：规则引擎（事件 → 匹配 → 动作）
        ruleEngine.start()
        // T-010：特性 SPI 事件分发（注册表；开关过滤在表内）
        featureRegistry.start()
        // T-009：保活（overlay + 前台常驻通知）
        overlayKeepAlive.attach()
        runCatching { KeepAliveService.start(this) }
            .onFailure { Log.w(TAG, "keepalive start deferred: ${it::class.simpleName}") }
        Log.i(
            TAG,
            "onServiceConnected: info=[${describeServiceInfo()}], engineEnabled=$engineEnabled",
        )
    }

    private fun applyStaticServiceInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    private fun describeServiceInfo(): String {
        val info = serviceInfo ?: return "null"
        return "eventTypes=0x${info.eventTypes.toString(16)},flags=0x${info.flags.toString(16)},timeout=${info.notificationTimeout}"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            eventCount.incrementAndGet()
            pipeline.offer(event.toA11yEvent())
        } catch (t: Throwable) {
            // 永不把异常抛回系统回调线程
            Log.e(TAG, "onAccessibilityEvent failed", t)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        isConnected = false
        instance = null
        ruleEngine.stop()
        featureRegistry.stop()
        overlayKeepAlive.detach()
        serviceScope.cancel()
        Log.i(TAG, "onDestroy: totalEvents=${eventCount.get()}")
        super.onDestroy()
    }

    companion object {
        const val TAG = "WaveA11y"

        /** 当前连接实例（进程内单例；AnrGuard/动作执行者使用）。 */
        @Volatile
        var instance: SelectToSpeakService? = null
            private set

        /** 连接状态镜像（进程内观测用；权威状态走 AccessibilityStateMonitor）。 */
        @Volatile
        var isConnected: Boolean = false
            private set

        /** 累计事件计数（C-005 冒烟的辅助证据）。 */
        val eventCount = AtomicLong()
    }
}
