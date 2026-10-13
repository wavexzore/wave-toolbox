package com.wavexzore.toolbox.feature.adskip

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveChevron
import com.wavexzore.toolbox.core.design.components.WaveChip
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveIconBox
import com.wavexzore.toolbox.core.design.components.WaveRow
import com.wavexzore.toolbox.core.design.components.WaveSectionLabel
import com.wavexzore.toolbox.core.design.components.WaveSeg
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.theme.Wave
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 开屏跳过设置面板（原型 02 · 设置 Tab）：模式 / 关键词 / 规则包 / 按应用控制入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdSkipSettingsPane(
    feature: AdSkipFeature,
    config: AdSkipConfig,
    loaderState: StateFlow<AdSkipPackLoader.State>,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode by config.mode.collectAsStateWithLifecycle(initialValue = AdSkipConfig.DEFAULT_MODE)
    val extraKeywords by config.extraKeywords.collectAsStateWithLifecycle(initialValue = emptySet())
    val packState by loaderState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var keywordDraft by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }

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

    Column(modifier = modifier.fillMaxWidth()) {
        WaveSectionLabel("运行模式")
        WaveCard {
            WaveSeg(
                items = listOf("仅开屏 · 稳妥", "任意弹窗 · 激进"),
                selected = if (mode == AdSkipGroup.MODE_ANY) 1 else 0,
                onSelect = { index ->
                    scope.launch { config.setMode(if (index == 1) AdSkipGroup.MODE_ANY else AdSkipGroup.MODE_SPLASH) }
                },
            )
            Text(
                text = if (mode == AdSkipGroup.MODE_ANY) {
                    "任何界面出现匹配的弹窗、提示条也会点击；覆盖更全，节奏更激进。"
                } else {
                    "只在 App 启动后 5 秒内、全屏窗口上点击；日常弹窗与抽屉一律不碰，误点最少。"
                },
                fontSize = 12.5.sp,
                color = Wave.Ink2,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 11.dp),
            )
        }

        WaveSectionLabel("追加关键词")
        WaveCard {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("跳过", "关闭", "×", "我知道了").forEach { word ->
                    WaveChip(word)
                }
                extraKeywords.sorted().forEach { word ->
                    WaveChip(word, onRemove = {
                        scope.launch { config.setExtraKeywords(extraKeywords - word) }
                    })
                }
                WaveChip("+ 添加", onClick = { showAddDialog = true })
            }
            Text(
                "排前的 4 个为默认词；「关闭 / ×」另需满足顶部区域或可点击约束。",
                fontSize = 12.sp,
                color = Wave.Ink3,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        WaveSectionLabel("规则包")
        WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            WaveRow(
                title = "内置应用规则包",
                subtitle = "${packState.packs.size} 个 · 已全部启用",
                leading = { WaveIconBox(Icons.Filled.Category) },
                trailing = { WaveChevron() },
                onClick = { showHelpDialog = true },
            )
            WaveDivider()
            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                WaveTextAction("导入规则", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                WaveTextAction("导出规则", onClick = { exportLauncher.launch("adskip-packs.json") })
                WaveTextAction("规则说明", onClick = { showHelpDialog = true })
            }
            if (packState.errors.isNotEmpty()) {
                Text(
                    "坏规则（已隔离）：${packState.errors.size} 条",
                    fontSize = 12.sp,
                    color = Wave.RedTx,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        WaveSectionLabel("应用")
        WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            WaveRow(
                title = "按应用控制",
                subtitle = "默认跟随总开关，可逐个关闭",
                leading = { WaveIconBox(Icons.Filled.Apps) },
                trailing = { WaveChevron() },
                onClick = onOpenApps,
            )
        }

        Text(
            "点击还受 500ms 间隔与单屏 3 次预算保护，避免连点失控。",
            fontSize = 12.sp,
            color = Wave.Ink3,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Wave.Card,
            title = { Text("添加关键词", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = keywordDraft,
                    onValueChange = { keywordDraft = it },
                    singleLine = true,
                    placeholder = { Text("如：跳过 3s / 恭喜获得", fontSize = 13.sp) },
                )
            },
            confirmButton = {
                TextButton({
                    val words = keywordDraft.split(' ', ',', '，').map { it.trim() }.filter { it.isNotEmpty() }
                    if (words.isNotEmpty()) {
                        scope.launch { config.setExtraKeywords(extraKeywords + words) }
                    }
                    keywordDraft = ""
                    showAddDialog = false
                }) { Text("添加", color = Wave.Ink) }
            },
            dismissButton = {
                TextButton({ showAddDialog = false }) { Text("取消", color = Wave.Ink2) }
            },
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = Wave.Card,
            title = { Text("规则包说明", fontSize = 16.sp) },
            text = {
                Text(
                    "内置规则按应用编写：指定开屏活动的关键词、节点路径或屏幕坐标。\n" +
                        "支持导入 JSON5 自制包；规则改动后重进本页生效。",
                    fontSize = 13.sp,
                    color = Wave.Ink2,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton({ showHelpDialog = false }) { Text("知道了", color = Wave.Ink) }
            },
        )
    }
}
