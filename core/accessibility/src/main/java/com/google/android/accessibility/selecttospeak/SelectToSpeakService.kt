package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 引擎总开关镜像（来自 DataStore；T-004 起决定事件是否进入管线）。 */
    @Volatile
    var engineEnabled: Boolean = SettingsRepository.DEFAULT_ENGINE_ENABLED
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        applyStaticServiceInfo()
        // 幂等；后续重复连接仅覆盖一次收集
        serviceScope.launch {
            settingsRepository.engineEnabled.collect { engineEnabled = it }
        }
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
        val n = eventCount.incrementAndGet()
        // T-003 仅计数留痕；T-004 接入管线
        if (n <= 3 || n % 100 == 0L) {
            Log.d(TAG, "event#$n type=${event.eventType} pkg=${event.packageName}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onDestroy() {
        isConnected = false
        serviceScope.cancel()
        Log.i(TAG, "onDestroy: totalEvents=${eventCount.get()}")
        super.onDestroy()
    }

    companion object {
        const val TAG = "WaveA11y"

        /** 连接状态镜像（进程内观测用；权威状态走 AccessibilityStateMonitor）。 */
        @Volatile
        var isConnected: Boolean = false
            private set

        /** 累计事件计数（C-005 冒烟的辅助证据）。 */
        val eventCount = AtomicLong()
    }
}
