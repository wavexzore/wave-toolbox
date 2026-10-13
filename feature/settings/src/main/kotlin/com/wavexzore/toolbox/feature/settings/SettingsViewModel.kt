package com.wavexzore.toolbox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.action.RuleEngine
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
) : ViewModel() {

    val accessibilityEnabled: StateFlow<Boolean> = stateMonitor.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
