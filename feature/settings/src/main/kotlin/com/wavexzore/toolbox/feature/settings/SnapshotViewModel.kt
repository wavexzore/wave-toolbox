package com.wavexzore.toolbox.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.selector.NodeView
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotCollector
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 快照检查器（T-010）：把当前活动窗口树导出为 JSON（落盘 + 预览 + 分享），
 * 供规则开发对照节点属性。
 */
@HiltViewModel
class SnapshotViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val collector: SnapshotCollector,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    data class DumpResult(
        val path: String,
        val bytes: Int,
        val nodes: Int,
        val preview: String,
        val json: String,
    )

    data class UiState(
        val busy: Boolean = false,
        val result: DumpResult? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun dump() {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        viewModelScope.launch(dispatchers.io) {
            val json = runCatching { collector.collectJson() }.getOrNull()
            if (json == null) {
                _uiState.value = UiState(error = "未取到窗口树（无障碍服务未连接或当前无活动窗口）。")
                return@launch
            }
            val nodes = runCatching { countNodes(SnapshotTree.fromJson(json)) }.getOrDefault(-1)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
            val file = File(dir, "snapshot-$stamp.json")
            file.writeText(json)
            _uiState.value = UiState(
                result = DumpResult(
                    path = file.absolutePath,
                    bytes = json.toByteArray().size,
                    nodes = nodes,
                    preview = json.take(PREVIEW_CHARS),
                    json = json,
                ),
            )
        }
    }

    private fun countNodes(node: NodeView): Int = 1 + node.children.sumOf { countNodes(it) }

    companion object {
        const val PREVIEW_CHARS = 8_000
    }
}
