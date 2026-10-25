package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.accessibility.diagnostics.DisplayDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 诊断导出页（T-011）+ 多屏诊断。 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val exporter: DiagnosticsExporter,
    private val displayDiagnostics: DisplayDiagnostics,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val file: File? = null,
        val entries: List<String> = emptyList(),
        val error: String? = null,
        val displays: List<DisplayDiagnostics.DisplayProbe> = emptyList(),
        val displayNote: String = "正在检测显示器…",
        val displaysLoading: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        refreshDisplays()
    }

    fun export() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, file = null, entries = emptyList(), error = null) }
        viewModelScope.launch {
            runCatching { exporter.export() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(busy = false, file = result.file, entries = result.entries)
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(busy = false, error = "导出失败：${t.message}") }
                }
        }
    }

    /** 重新探测显示器（副屏/投屏插拔后手动刷新）。 */
    fun refreshDisplays() {
        if (_uiState.value.displaysLoading) return
        _uiState.update { it.copy(displaysLoading = true) }
        viewModelScope.launch {
            val report = runCatching {
                withContext(Dispatchers.Default) { displayDiagnostics.snapshot() }
            }
            _uiState.update {
                it.copy(
                    displaysLoading = false,
                    displays = report.getOrNull()?.displays.orEmpty(),
                    displayNote = report.getOrNull()?.note
                        ?: "显示器诊断失败：${report.exceptionOrNull()?.message}",
                )
            }
        }
    }
}
