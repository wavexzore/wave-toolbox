package com.wavexzore.toolbox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilitySelfHeal
import com.wavexzore.toolbox.core.accessibility.AccessibilityServiceBootstrap
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.action.RuleEngine
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val engineEnabled: Boolean = SettingsRepository.DEFAULT_ENGINE_ENABLED,
    val contentThrottleMs: Int = SettingsRepository.DEFAULT_CONTENT_THROTTLE_MS,
    val keepAliveEnabled: Boolean = SettingsRepository.DEFAULT_KEEPALIVE_ENABLED,
    val bootAutostart: Boolean = SettingsRepository.DEFAULT_BOOT_AUTOSTART,
    val eventRecordEnabled: Boolean = SettingsRepository.DEFAULT_EVENT_RECORD,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val ruleEngine: RuleEngine,
    private val pipeline: EventPipeline,
    private val stateMonitor: AccessibilityStateMonitor,
    private val selfHeal: AccessibilitySelfHeal,
) : ViewModel() {

    val accessibilityEnabled: StateFlow<Boolean> = stateMonitor.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** WRITE_SECURE_SETTINGS 授权态（自愈可用性）。 */
    private val _secureSettingsGranted = MutableStateFlow(selfHeal.isGranted())
    val secureSettingsGranted: StateFlow<Boolean> = _secureSettingsGranted

    /** 自愈动作最近结果文案（null=尚未执行）。 */
    private val _healStatus = MutableStateFlow<String?>(null)
    val healStatus: StateFlow<String?> = _healStatus

    /** 一次性 adb 授权命令（未授权时展示复制）。 */
    val grantCommand: String = selfHeal.grantCommand

    val versionLabel: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${info.versionName} (${info.versionCode})"
    }.getOrDefault("-")

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.engineEnabled,
        settingsRepository.contentThrottleMs,
        settingsRepository.keepAliveEnabled,
        settingsRepository.bootAutostartEnabled,
        settingsRepository.eventRecordEnabled,
    ) { engineEnabled, throttleMs, keepAlive, boot, eventRecord ->
        SettingsUiState(
            engineEnabled = engineEnabled,
            contentThrottleMs = throttleMs,
            keepAliveEnabled = keepAlive,
            bootAutostart = boot,
            eventRecordEnabled = eventRecord,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setEngineEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEngineEnabled(enabled) }

    fun setKeepAliveEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setKeepAliveEnabled(enabled) }

    fun setBootAutostart(enabled: Boolean) = viewModelScope.launch { settingsRepository.setBootAutostartEnabled(enabled) }

    fun setEventRecordEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEventRecordEnabled(enabled) }

    fun setContentThrottleMs(ms: Int) = viewModelScope.launch { settingsRepository.setContentThrottleMs(ms) }

    fun openAccessibilitySettings() = stateMonitor.openAccessibilitySettings()

    /** 重查授权态（进入页面 / 执行动作时调用，授权在电脑侧完成后可即时反映）。 */
    fun refreshGrant() {
        _secureSettingsGranted.value = selfHeal.isGranted()
    }

    /** 授权命令复制完成提示。 */
    fun onGrantCommandCopied() {
        refreshGrant()
        _healStatus.value = "命令已复制 · 在电脑执行后回到本页"
    }

    /** 执行无障碍自愈（受控重绑/补挂载）；结果映射为提示文案。 */
    fun healBinding() {
        refreshGrant()
        viewModelScope.launch {
            val result = selfHeal.heal()
            _secureSettingsGranted.value = selfHeal.isGranted()
            _healStatus.value = when (result) {
                AccessibilityServiceBootstrap.Result.ENABLED -> "已修复 · 已重新绑定"
                AccessibilityServiceBootstrap.Result.ALREADY_ENABLED -> "服务正常 · 无需修复"
                AccessibilityServiceBootstrap.Result.NOT_GRANTED -> "未授权 · 请先复制命令并 adb 执行"
                AccessibilityServiceBootstrap.Result.WRITE_FAILED -> "写入失败 · 稍后重试"
                AccessibilityServiceBootstrap.Result.INTERRUPTED -> "已中断 · 请重试"
            }
        }
    }

    /** 清空记录（统计库 + 事件环 + 动作环）。 */
    fun clearRecords() {
        statsRepository.clearAll()
        ruleEngine.clearActions()
        pipeline.clearLog()
    }

    /** 重置全部数据（设置回到默认 + 统计清空）。 */
    fun resetAllData() {
        viewModelScope.launch {
            statsRepository.clearAll()
            ruleEngine.clearActions()
            pipeline.clearLog()
            settingsRepository.clearAll()
        }
    }
}
