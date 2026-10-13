package com.wavexzore.toolbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val accessibilityStateMonitor: AccessibilityStateMonitor,
    private val featureRegistry: FeatureRegistry,
) : ViewModel() {

    data class FeatureUi(
        val id: String,
        val name: String,
        val description: String,
        val enabled: Boolean,
    )

    val engineEnabled: StateFlow<Boolean> = settingsRepository.engineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val accessibilityEnabled: StateFlow<Boolean> = accessibilityStateMonitor.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val setupCompleted: StateFlow<Boolean> = settingsRepository.setupCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    fun setFeatureEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { featureRegistry.setEnabled(id, enabled) }
    }

    fun openAccessibilitySettings() {
        accessibilityStateMonitor.openAccessibilitySettings()
    }
}
