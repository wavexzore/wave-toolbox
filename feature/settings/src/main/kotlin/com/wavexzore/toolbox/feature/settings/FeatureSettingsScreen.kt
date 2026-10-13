package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.data.stats.StatRecordEntity
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveFilterChip
import com.wavexzore.toolbox.core.design.components.WaveIconBox
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveStatsRow
import com.wavexzore.toolbox.core.design.components.WaveSwitchRow
import com.wavexzore.toolbox.core.design.components.WaveTabs
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 特性详情页（原型 02/03）：顶部 Tab「设置 / 日志」。 */
@Composable
fun FeatureSettingsScreen(
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: FeatureSettingsViewModel = hiltViewModel(),
) {
    val feature = viewModel.feature
    var tab by rememberSaveable { mutableIntStateOf(0) }

    WaveScaffold(topBar = { WaveTopBar(feature?.name ?: "未知特性", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WaveTabs(titles = listOf("设置", "日志"), selected = tab, onSelect = { tab = it })

            if (feature == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该特性未注册（可能已被移除）。", color = Wave.Ink2, fontSize = 13.5.sp)
                }
            } else if (tab == 0) {
                SettingsTab(
                    enabledFlow = viewModel.enabled.collectAsStateWithLifecycle().value,
                    todaySkips = viewModel.todayStats.collectAsStateWithLifecycle().value.skips,
                    weekSkips = viewModel.weekSkips.collectAsStateWithLifecycle().value,
                    totalSkips = viewModel.totalSkips.collectAsStateWithLifecycle().value,
                    onToggle = viewModel::setEnabled,
                    pane = { scope, modifier -> feature.SettingsPane(scope, modifier, onOpenApps) },
                )
            } else {
                LogsTab(
                    viewModel = viewModel,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    enabledFlow: Boolean,
    todaySkips: Long,
    weekSkips: Long,
    totalSkips: Long,
    onToggle: (Boolean) -> Unit,
    pane: @Composable (androidx.compose.foundation.layout.ColumnScope, Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        WaveCard {
            WaveSwitchRow(
                title = "自动跳过",
                subtitle = "开启后自动识别并点击开屏广告",
                checked = enabledFlow,
                onCheckedChange = onToggle,
            )
            WaveDivider(modifier = Modifier.padding(vertical = 10.dp))
            WaveStatsRow(
                stats = listOf(
                    Triple(todaySkips.toString(), "次", "今日跳过"),
                    Triple(weekSkips.toString(), "次", "本周"),
                    Triple(totalSkips.toString(), "次", "累计"),
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        pane(this, Modifier)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LogsTab(
    viewModel: FeatureSettingsViewModel,
    onOpenDiagnostics: () -> Unit,
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val stats by viewModel.todayStats.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val filtered = when (filter) {
        1 -> records.filter { it.kind == "SKIP" || it.kind == "ACTION" }
        2 -> records.filter { it.kind == "MISS" }
        3 -> records.filter { it.kind == "DENY" }
        else -> records
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))
        WaveCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("今日概览", color = Wave.Ink2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                WaveTextAction("导出", onOpenDiagnostics)
            }
            WaveStatsRow(
                modifier = Modifier.padding(top = 6.dp),
                stats = listOf(
                    Triple(stats.skips.toString(), "次", "今日跳过"),
                    Triple(stats.miss.toString(), "", "未命中"),
                    Triple(stats.deny.toString(), "", "约束拦截"),
                    Triple(stats.okRatio.toString(), "%", "生效占比"),
                ),
            )
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            listOf("全部", "成功", "未命中", "拦截").forEachIndexed { index, label ->
                WaveFilterChip(label, active = filter == index, onClick = { filter = index })
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text("还没有记录，去打开几个应用试试", color = Wave.Ink3, fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                items(filtered, key = { it.id }) { item ->
                    LogRow(item)
                    WaveDivider()
                }
            }
        }

        WaveTextAction(
            text = "清空全部记录",
            onClick = { showClearDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Wave.Card,
            title = { Text("清空记录？", fontSize = 16.sp) },
            text = { Text("统计与运行明细将被清空，不影响规则与设置。", fontSize = 13.5.sp, color = Wave.Ink2) },
            confirmButton = {
                TextButton({ viewModel.clearRecords(); showClearDialog = false }) { Text("清空", color = Wave.RedTx) }
            },
            dismissButton = {
                TextButton({ showClearDialog = false }) { Text("取消", color = Wave.Ink2) }
            },
        )
    }
}

private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun LogRow(item: StatRecordEntity) {
    val (tone, icon) = when (item.kind) {
        "MISS" -> WaveTone.Yellow to Icons.Filled.WarningAmber
        "DENY" -> WaveTone.Red to Icons.Filled.Close
        "VERIFY_RETRY" -> WaveTone.Blue to Icons.Filled.Refresh
        else -> WaveTone.Green to Icons.Filled.Check
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaveIconBox(icon, tone, size = 34.dp, iconSize = 16.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.pkg ?: "-", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Wave.Ink)
            Text(
                buildString {
                    append(item.kind.label())
                    item.mechanism?.let { append(" · ").append(it) }
                    item.ruleId?.let { append(" · ").append(it) }
                },
                fontSize = 12.5.sp,
                color = Wave.Ink2,
                maxLines = 1,
            )
        }
        Text(
            logTimeFormat.format(Date(item.at)),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = Wave.Ink3,
        )
    }
}

private fun String.label(): String = when (this) {
    "SKIP" -> "跳过"
    "ACTION" -> "点击"
    "MISS" -> "未命中"
    "DENY" -> "拦截"
    "VERIFY_RETRY" -> "复核"
    else -> this
}
