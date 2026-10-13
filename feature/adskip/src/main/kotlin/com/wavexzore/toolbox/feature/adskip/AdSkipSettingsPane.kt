package com.wavexzore.toolbox.feature.adskip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 开屏跳过设置面板：模式 / 追加关键词 / 单 App 开关 / 规则包状态 / 统计 / 导入导出。 */
@Composable
fun AdSkipSettingsPane(
    feature: AdSkipFeature,
    config: AdSkipConfig,
    loaderState: StateFlow<AdSkipPackLoader.State>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by config.mode.collectAsStateWithLifecycle(initialValue = AdSkipConfig.DEFAULT_MODE)
    val extraKeywords by config.extraKeywords.collectAsStateWithLifecycle(initialValue = emptySet())
    val disabled by config.disabledPackages.collectAsStateWithLifecycle(initialValue = emptySet())
    val packState by loaderState.collectAsStateWithLifecycle()
    val skips by feature.skipCount.collectAsStateWithLifecycle()
    val events by feature.eventCount.collectAsStateWithLifecycle()

    var keywordDraft by remember { mutableStateOf(extraKeywords.joinToString(" ")) }
    var apps by remember { mutableStateOf(feature.launcherApps()) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
        }.onSuccess { feature.importUserPack(it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(feature.exportPacksJson()) }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch { config.setMode(mode) }
        apps = feature.launcherApps()
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("模式", style = MaterialTheme.typography.titleSmall)
        ModeRow("仅开屏（保守）", AdSkipGroup.MODE_SPLASH, mode) { scope.launch { config.setMode(it) } }
        ModeRow("任意界面弹窗（激进）", AdSkipGroup.MODE_ANY, mode) { scope.launch { config.setMode(it) } }

        HorizontalDivider()

        Text("追加关键词（空格分隔）", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keywordDraft,
                onValueChange = { keywordDraft = it },
                modifier = Modifier.weight(1f).padding(top = 4.dp),
                singleLine = true,
                placeholder = { Text("如：跳过 3s / 忽略") },
            )
            TextButton(onClick = {
                val set = keywordDraft.split(' ', ',', '，').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                scope.launch { config.setExtraKeywords(set) }
            }) { Text("保存") }
        }

        HorizontalDivider()

        Text(
            text = "运行状态：累计事件 $events · 成功跳过 $skips",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "规则包：${packState.packs.size} 个应用（含内置）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (packState.errors.isNotEmpty()) {
            Card {
                Column(Modifier.padding(8.dp).heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                    Text("坏规则（已隔离）：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    packState.errors.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("导入规则") }
            TextButton(onClick = { exportLauncher.launch("adskip-packs.json") }) { Text("导出规则") }
        }

        HorizontalDivider()

        Text("单 App 开关（默认跟随特性总开关）", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            apps.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (entry.pkg in disabled) "已关闭" else "开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RadioButton(
                        selected = entry.pkg !in disabled,
                        onClick = { scope.launch { config.setPackageDisabled(entry.pkg, entry.pkg !in disabled) } },
                    )
                    TextButton(onClick = {
                        scope.launch { config.setPackageDisabled(entry.pkg, entry.pkg !in disabled) }
                    }) { Text(if (entry.pkg in disabled) "启用" else "关闭") }
                }
            }
        }

        Text(
            text = "提示：误点某应用时先单 App 关闭；规则改动需重进设置页刷新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeRow(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
