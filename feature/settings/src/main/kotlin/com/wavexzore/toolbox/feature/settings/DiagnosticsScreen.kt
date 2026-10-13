package com.wavexzore.toolbox.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WavePrimaryButton
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import java.io.File

/** 诊断导出：zip 含设备/设置/规则/统计/日志，可分享。 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    WaveScaffold(topBar = { WaveTopBar("导出诊断文件", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            WaveNote("打包设备信息、引擎设置、规则、7 天统计、事件/动作环与最近快照（约数百 KB）。")
            Spacer(Modifier.height(6.dp))

            WavePrimaryButton(
                text = if (uiState.busy) "打包中…" else "生成诊断 zip",
                onClick = viewModel::export,
                enabled = !uiState.busy,
            )

            uiState.error?.let {
                Text(it, fontSize = 13.sp, color = Wave.RedTx, modifier = Modifier.padding(top = 10.dp))
            }

            uiState.file?.let { file ->
                Spacer(Modifier.height(12.dp))
                WaveCard {
                    Text("已生成（${file.length() / 1024} KB）", fontSize = 13.5.sp, color = Wave.Ink)
                    Text(
                        file.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = Wave.Ink3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "内容：${uiState.entries.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = Wave.Ink2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        WaveTextAction("分享 zip", onClick = { shareZip(context, file) })
                    }
                }
            }
        }
    }
}

private fun shareZip(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享诊断 zip"))
    }
}
