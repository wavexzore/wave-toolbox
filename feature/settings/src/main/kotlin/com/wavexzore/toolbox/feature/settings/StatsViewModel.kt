package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.data.stats.KindStat
import com.wavexzore.toolbox.core.data.stats.PkgStat
import com.wavexzore.toolbox.core.data.stats.StatsRepository
import com.wavexzore.toolbox.core.data.stats.StatsTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 动作统计页（T-011）：今日/7 日汇总、按类型、按包 Top。 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val now = System.currentTimeMillis()

    private val todayStart: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayTotals: StateFlow<StatsTotals> = statsRepository.totalsSince(todayStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsTotals(0, 0, 0))

    val weekTotals: StateFlow<StatsTotals> = statsRepository.totalsSince(now - 7 * StatsRepository.DAY_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsTotals(0, 0, 0))

    val topPackages: StateFlow<List<PkgStat>> = statsRepository.topPackages(now - 7 * StatsRepository.DAY_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val kinds: StateFlow<List<KindStat>> = statsRepository.byKind(now - 7 * StatsRepository.DAY_MS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() = statsRepository.clearAll()
}
