package com.wavexzore.toolbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页只需功能列表；引擎/无障碍/引导状态由设置页展示。 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    data class FeatureUi(
        val id: String,
        val name: String,
        val description: String,
        val enabled: Boolean,
    )

    /** 功能列表（来自 FeatureRegistry；SPI 注册即出现）。 */
    val features: StateFlow<List<FeatureUi>> = featureRegistry.enabledStates
        .map { states ->
            featureRegistry.all.map { feature ->
                FeatureUi(
                    id = feature.id,
                    name = feature.name,
                    description = feature.description,
                    enabled = states[feature.id] ?: feature.defaultEnabled,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            featureRegistry.all.map { FeatureUi(it.id, it.name, it.description, it.defaultEnabled) },
        )
}
