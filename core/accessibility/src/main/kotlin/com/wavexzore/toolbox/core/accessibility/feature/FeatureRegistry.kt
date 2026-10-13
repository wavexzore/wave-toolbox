package com.wavexzore.toolbox.core.accessibility.feature

import android.util.Log
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEvent
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.feature.api.FeatureEvent
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 特性注册表（T-010，D-009）：Hilt multibinding 汇聚 [ToolFeature]，统一提供
 * 「展示顺序 / 开关状态 / 事件分发」。引擎与 UI 均只依赖本表，新增特性零改引擎。
 *
 * - 开关存于 `feature.<id>.enabled`（DataStore），默认取特性 [ToolFeature.defaultEnabled]；
 * - [start]/[stop] 由无障碍服务跟随规则引擎生命周期调用；事件在事件单线程串行派发；
 * - 单个特性异常不影响其它特性与管线。
 */
@Singleton
class FeatureRegistry @Inject constructor(
    features: Set<@JvmSuppressWildcards ToolFeature>,
    private val pipeline: EventPipeline,
    private val settingsRepository: SettingsRepository,
    dispatchers: AppDispatchers,
) {

    /** 全部特性（order 升序；同序按 id）。 */
    val all: List<ToolFeature> = features.sortedWith(compareBy({ it.order }, { it.id }))

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.event)
    private val enabledNow = ConcurrentHashMap<String, Boolean>()
    private val dispatchCounts = ConcurrentHashMap<String, AtomicLong>()

    @Volatile
    private var running = false

    private var collectorStarted = false

    /** 实时开关映射（UI 首页列表 / 分发过滤）。 */
    val enabledStates: StateFlow<Map<String, Boolean>> = (
        if (all.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(all.map { feature -> settingsRepository.featureEnabled(feature.id, feature.defaultEnabled) }) { values ->
                all.mapIndexed { index, feature -> feature.id to values[index] }.toMap()
            }
        }
        )
        .stateIn(scope, SharingStarted.Eagerly, all.associate { it.id to it.defaultEnabled })

    /** 各特性派发计数（诊断/占位统计）。 */
    private val _dispatchTotals = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dispatchTotals: StateFlow<Map<String, Long>> = _dispatchTotals

    init {
        require(all.size == all.map { it.id }.distinct().size) {
            "duplicate feature ids: ${all.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }
        // 分发过滤用的实时开关镜像（与 UI 同源）
        all.forEach { feature ->
            scope.launch {
                settingsRepository.featureEnabled(feature.id, feature.defaultEnabled).collect { enabled ->
                    enabledNow[feature.id] = enabled
                }
            }
        }
    }

    fun byId(id: String): ToolFeature? = all.firstOrNull { it.id == id }

    fun isEnabled(id: String): Boolean = enabledNow[id] ?: byId(id)?.defaultEnabled ?: false

    suspend fun setEnabled(id: String, enabled: Boolean) {
        settingsRepository.setFeatureEnabled(id, enabled)
    }

    /** 服务连接时启动分发（幂等）。 */
    fun start() {
        running = true
        if (collectorStarted) return
        collectorStarted = true
        scope.launch {
            pipeline.events.collect { event ->
                if (!running) return@collect
                val featureEvent = event.toFeatureEvent()
                for (feature in all) {
                    if (!isEnabled(feature.id)) continue
                    runCatching { feature.onEvent(featureEvent) }
                        .onFailure { Log.w(TAG, "feature ${feature.id} onEvent failed", it) }
                    val count = dispatchCounts.getOrPut(feature.id) { AtomicLong() }.incrementAndGet()
                    if (count == 1L || count % 20 == 0L) {
                        _dispatchTotals.value = dispatchCounts.mapValues { it.value.get() }
                    }
                }
            }
        }
        Log.i(TAG, "started: features=${all.map { it.id }}")
    }

    fun stop() {
        running = false
        Log.i(TAG, "stopped")
    }

    fun dispatchCountOf(id: String): Long = dispatchCounts[id]?.get() ?: 0L

    /** 单特性开关流（设置页用）。 */
    fun enabledFlow(id: String): Flow<Boolean> {
        val feature = byId(id) ?: return flowOf(false)
        return settingsRepository.featureEnabled(id, feature.defaultEnabled)
    }

    private fun A11yEvent.toFeatureEvent(): FeatureEvent = FeatureEvent(
        type = type.name,
        packageName = packageName,
        className = className,
        text = text,
        timeMillis = if (eventTime > 0) eventTime else System.currentTimeMillis(),
    )

    companion object {
        const val TAG = "WaveA11y.Feature"
    }
}
