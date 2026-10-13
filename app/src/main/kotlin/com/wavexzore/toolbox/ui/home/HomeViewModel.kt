package com.wavexzore.toolbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.AccessibilityStateMonitor
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val accessibilityStateMonitor: AccessibilityStateMonitor,
) : ViewModel() {

    val engineEnabled: StateFlow<Boolean> = settingsRepository.engineEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val accessibilityEnabled: StateFlow<Boolean> = accessibilityStateMonitor.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val setupCompleted: StateFlow<Boolean> = settingsRepository.setupCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun openAccessibilitySettings() {
        accessibilityStateMonitor.openAccessibilitySettings()
    }
}
