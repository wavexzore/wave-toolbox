package com.wavexzore.toolbox.feature.settings

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WavePrimaryButton
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave

/** 快照检查器（诊断）：导出当前活动窗口树 JSON（预览/分享）。 */
@Composable
fun SnapshotScreen(
    onBack: () -> Unit,
    viewModel: SnapshotViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    WaveScaffold(topBar = { WaveTopBar("窗口快照检查器", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            WaveNote("抓取当前活动窗口的节点树（text/desc/vid/class/bounds/clickable…），用于核对规则选择器。")
            Spacer(Modifier.height(6.dp))

            WavePrimaryButton(
                text = if (uiState.busy) "抓取中…" else "导出当前窗口树",
                onClick = viewModel::dump,
                enabled = !uiState.busy,
            )

            uiState.error?.let {
                Text(it, fontSize = 13.sp, color = Wave.RedTx, modifier = Modifier.padding(top = 10.dp))
            }

            uiState.result?.let { result ->
                Spacer(Modifier.height(12.dp))
                WaveCard {
                    Text("已导出：${result.nodes} 节点 · ${result.bytes / 1024} KB", fontSize = 13.5.sp, color = Wave.Ink)
                    Text(
                        result.path,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = Wave.Ink3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                        WaveTextAction("分享 JSON", onClick = { shareJson(context, result.json) })
                    }
                }

                Text(
                    "预览（前 ${SnapshotViewModel.PREVIEW_CHARS} 字符）",
                    fontSize = 13.sp,
                    color = Wave.Ink2,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Text(
                        text = result.preview,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Wave.Ink,
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun shareJson(context: android.content.Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "wave工具箱 快照")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "分享快照 JSON"))
}
