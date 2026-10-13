package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 特性详情页（T-010）：开关 + 描述 + 特性自渲染设置面板。 */
@HiltViewModel
class FeatureSettingsViewModel @Inject constructor(
    private val registry: FeatureRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val feature: ToolFeature? = registry.byId(savedStateHandle["featureId"] ?: "")

    val enabled: StateFlow<Boolean> =
        (feature?.let { target -> registry.enabledFlow(target.id) } ?: flowOf(false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), feature?.defaultEnabled ?: false)

    val dispatchCount: StateFlow<Long> = registry.dispatchTotals
        .map { totals -> totals[feature?.id] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setEnabled(value: Boolean) {
        val target = feature ?: return
        viewModelScope.launch { registry.setEnabled(target.id, value) }
    }
}
