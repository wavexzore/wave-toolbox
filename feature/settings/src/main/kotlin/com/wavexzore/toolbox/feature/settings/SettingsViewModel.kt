package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val engineEnabled: Boolean = SettingsRepository.DEFAULT_ENGINE_ENABLED,
    val contentThrottleMs: Int = SettingsRepository.DEFAULT_CONTENT_THROTTLE_MS,
    val allowPackages: Set<String> = emptySet(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.engineEnabled,
        settingsRepository.contentThrottleMs,
        settingsRepository.allowPackages,
    ) { enabled, throttleMs, packages ->
        SettingsUiState(enabled, throttleMs, packages)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(),
    )

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEngineEnabled(enabled) }
    }

    fun setContentThrottleMs(ms: Int) {
        viewModelScope.launch { settingsRepository.setContentThrottleMs(ms) }
    }
}
