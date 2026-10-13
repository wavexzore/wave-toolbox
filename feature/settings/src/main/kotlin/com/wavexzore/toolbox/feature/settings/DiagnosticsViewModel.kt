package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 诊断导出页（T-011）。 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val exporter: DiagnosticsExporter,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val file: File? = null,
        val entries: List<String> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun export() {
        if (_uiState.value.busy) return
        _uiState.value = UiState(busy = true)
        viewModelScope.launch {
            runCatching { exporter.export() }
                .onSuccess { result -> _uiState.value = UiState(file = result.file, entries = result.entries) }
                .onFailure { t -> _uiState.value = UiState(error = "导出失败：${t.message}") }
        }
    }
}
