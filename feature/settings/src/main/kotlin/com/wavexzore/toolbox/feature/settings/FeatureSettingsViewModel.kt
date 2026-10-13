package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.data.stats.StatRecordEntity
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 特性详情页：开关 + 当日统计 + 运行日志（Tab）。 */
@HiltViewModel
class FeatureSettingsViewModel @Inject constructor(
    private val registry: FeatureRegistry,
    private val statsRepository: StatsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val feature: ToolFeature? = registry.byId(savedStateHandle["featureId"] ?: "")

    val enabled: StateFlow<Boolean> =
        (feature?.let { target -> registry.enabledFlow(target.id) } ?: flowOf(false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), feature?.defaultEnabled ?: false)

    private val featureId: String = feature?.id.orEmpty()

    private val todayStart: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 明细（最近 200 条，倒序）。 */
    val records: StateFlow<List<StatRecordEntity>> =
        (if (feature == null) flowOf(emptyList()) else statsRepository.recordsSinceFor(featureId))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class TodayStats(val skips: Long = 0, val miss: Long = 0, val deny: Long = 0, val okRatio: Int = 0)

    val todayStats: StateFlow<TodayStats> = records
        .map { list ->
            val today = list.filter { it.at >= todayStart }
            val skips = today.count { it.kind == "SKIP" || it.kind == "ACTION" }.toLong()
            val miss = today.count { it.kind == "MISS" }.toLong()
            val deny = today.count { it.kind == "DENY" }.toLong()
            val denominator = skips + miss + deny
            TodayStats(
                skips = skips,
                miss = miss,
                deny = deny,
                okRatio = if (denominator == 0L) 0 else ((skips * 100) / denominator).toInt(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayStats())

    val totalSkips: StateFlow<Long> = (if (feature == null) flowOf(0L) else statsRepository.totalsSinceFor(featureId, 0L).map { it.okCount })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val weekSkips: StateFlow<Long> =
        (if (feature == null) flowOf(0L) else statsRepository.totalsSinceFor(featureId, todayStart - 6 * StatsRepository.DAY_MS).map { it.okCount })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setEnabled(value: Boolean) {
        val target = feature ?: return
        viewModelScope.launch { registry.setEnabled(target.id, value) }
    }

    fun clearRecords() {
        statsRepository.clearAll()
    }
}
