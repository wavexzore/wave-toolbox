package com.wavexzore.toolbox.feature.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilitySelfHeal
import com.wavexzore.toolbox.core.accessibility.AccessibilityServiceBootstrap
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.action.RuleEngine
import com.wavexzore.toolbox.core.accessibility.keepalive.KeepAliveHealth
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.feature.settings.update.UpdateBadge
import com.wavexzore.toolbox.feature.settings.update.UpdateRepository
import com.wavexzore.toolbox.feature.settings.update.UpdateState
import com.wavexzore.toolbox.feature.settings.update.toBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val engineEnabled: Boolean = SettingsRepository.DEFAULT_ENGINE_ENABLED,
    val contentThrottleMs: Int = SettingsRepository.DEFAULT_CONTENT_THROTTLE_MS,
    val keepAliveEnabled: Boolean = SettingsRepository.DEFAULT_KEEPALIVE_ENABLED,
    val bootAutostart: Boolean = SettingsRepository.DEFAULT_BOOT_AUTOSTART,
    val eventRecordEnabled: Boolean = SettingsRepository.DEFAULT_EVENT_RECORD,
)

/**
 * 自愈行状态（设置页展示模型）：
 * - OK：已授权且服务已连接 —— 只显示「已开启」，不展示动作；
 * - NEEDS_REPAIR：已授权但服务未开启/未绑定 —— 展示「修复教程」（一键修复 + 手动兜底 + 自动重试收敛）；
 * - UNAUTHORIZED：缺 WRITE_SECURE_SETTINGS —— 展示「修复教程」（系统设置手动修复为主，adb 命令可选）。
 */
enum class SelfHealState { OK, NEEDS_REPAIR, UNAUTHORIZED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val ruleEngine: RuleEngine,
    private val pipeline: EventPipeline,
    private val stateMonitor: AccessibilityStateMonitor,
    private val selfHeal: AccessibilitySelfHeal,
    private val keepAliveHealth: KeepAliveHealth,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    val accessibilityEnabled: StateFlow<Boolean> = stateMonitor.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 自愈动作最近结果文案（null=尚未执行）。 */
    private val _healStatus = MutableStateFlow<String?>(null)
    val healStatus: StateFlow<String?> = _healStatus

    /** 保活体检快照（FGS/无障碍/电池优化/OEM/开机自启）。 */
    private val _keepAliveHealth = MutableStateFlow<KeepAliveHealth.Snapshot?>(null)
    val keepAliveHealthState: StateFlow<KeepAliveHealth.Snapshot?> = _keepAliveHealth
    private var healthRefreshing = false

    /**
     * 自愈行状态（授权 × 服务连接 × 引擎开关；2s 轮询，adb 授权后可自动反映）。
     * 经 [graceNeeds] 去抖：瞬断（系统抑制/uiautomator 探针等 1s 级自恢复）不闪「需要修复」也不触发重绑。
     */
    val selfHealState: StateFlow<SelfHealState> = combine(
        selfHeal.status,
        accessibilityEnabled,
    ) { status, enabled ->
        when {
            !status.granted -> SelfHealState.UNAUTHORIZED
            !enabled || !status.connected -> SelfHealState.NEEDS_REPAIR
            else -> SelfHealState.OK
        }
    }.graceNeeds(NEEDS_GRACE_MS).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        if (selfHeal.isGranted()) SelfHealState.NEEDS_REPAIR else SelfHealState.UNAUTHORIZED,
    )

    private var autoHealing = false
    private var autoRetryQueued = false

    init {
        // 进入「需要修复」且已授权 → 自动重试（重装/被回收/授权后自愈；失败每 5s 收敛重试）
        viewModelScope.launch {
            selfHealState.collect { state ->
                if (state == SelfHealState.NEEDS_REPAIR && selfHeal.isGranted()) maybeAutoHeal()
            }
        }
        refreshKeepAliveHealth()
        viewModelScope.launch {
            while (true) {
                delay(HEALTH_REFRESH_MS)
                refreshKeepAliveHealth()
            }
        }
    }

    /** 刷新保活体检（FGS 可能被系统延迟启动/回收，周期刷新）。 */
    fun refreshKeepAliveHealth() {
        if (healthRefreshing) return
        healthRefreshing = true
        viewModelScope.launch {
            try {
                _keepAliveHealth.value = keepAliveHealth.snapshot()
            } finally {
                healthRefreshing = false
            }
        }
    }

    fun openBatteryOptimizationSettings() = keepAliveHealth.openBatteryOptimizationSettings()

    private fun maybeAutoHeal() {
        // 执行中再次触发 → 排队，结束后补一次（避免守卫期状态变化丢失重试）
        if (autoHealing) {
            autoRetryQueued = true
            return
        }
        autoHealing = true
        autoRetryQueued = false
        viewModelScope.launch {
            val retryNeeded = try {
                val result = selfHeal.heal()
                Log.i(TAG, "auto retry result=${result.name}")
                if (result == AccessibilityServiceBootstrap.Result.ENABLED) {
                    _healStatus.value = "已自动修复 · 已重新绑定"
                }
                delay(AUTO_RETRY_DELAY_MS)
                selfHeal.isGranted() && selfHealState.value != SelfHealState.OK
            } finally {
                autoHealing = false
            }
            if (retryNeeded || autoRetryQueued) maybeAutoHeal()
        }
    }

    /** 一次性 adb 授权命令（未授权时展示复制）。 */
    val grantCommand: String = selfHeal.grantCommand

    val versionLabel: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${info.versionName}"
    }.getOrDefault("-")

    /** 关于行 Pill 映射（唯一来源=Repository state；9 行全表）。 */
    val updateBadge: StateFlow<UpdateBadge?> = updateRepository.state
        .map { state -> state.toBadge() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun setEngineEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEngineEnabled(enabled)
        refreshKeepAliveHealth()
    }

    fun setKeepAliveEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setKeepAliveEnabled(enabled)
        refreshKeepAliveHealth()
    }

    fun setBootAutostart(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBootAutostartEnabled(enabled)
        refreshKeepAliveHealth()
    }

    fun setEventRecordEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEventRecordEnabled(enabled) }

    fun setContentThrottleMs(ms: Int) = viewModelScope.launch { settingsRepository.setContentThrottleMs(ms) }

    fun openAccessibilitySettings() = stateMonitor.openAccessibilitySettings()

    /** 授权命令复制完成提示（授权态由轮询自动反映）。 */
    fun onGrantCommandCopied() {
        _healStatus.value = "命令已复制 · 在电脑执行后回到本页"
    }

    /** 执行无障碍自愈（受控重绑/补挂载）；结果映射为提示文案。 */
    fun healBinding() {
        viewModelScope.launch {
            val result = selfHeal.heal()
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

    private companion object {
        const val TAG = "WaveA11y.SelfHeal"

        /** 自动重试失败后的收敛间隔。 */
        const val AUTO_RETRY_DELAY_MS = 5_000L

        /** NEEDS 去抖窗口：大于 1 个状态轮询周期，吸收 uiautomator 探针/系统抑制导致的 1s 级瞬断。 */
        const val NEEDS_GRACE_MS = 3_000L

        /** 保活体检刷新间隔。 */
        const val HEALTH_REFRESH_MS = 15_000L
    }
}

/**
 * NEEDS 去抖：瞬断在 [graceMs] 内自恢复则不外显「需要修复」（也不触发重绑）；
 * 恢复到非 NEEDS 即时透传并取消挂起。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<SelfHealState>.graceNeeds(graceMs: Long): Flow<SelfHealState> =
    transformLatest { state ->
        if (state == SelfHealState.NEEDS_REPAIR) delay(graceMs)
        emit(state)
    }
