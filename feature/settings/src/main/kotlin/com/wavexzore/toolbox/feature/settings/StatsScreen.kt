package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveSectionLabel
import com.wavexzore.toolbox.core.design.components.WaveStatsRow
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave

/** 动作统计（诊断）：今日 / 7 日汇总 + 按类型 + 按包 Top。 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val today by viewModel.todayTotals.collectAsStateWithLifecycle()
    val week by viewModel.weekTotals.collectAsStateWithLifecycle()
    val topPackages by viewModel.topPackages.collectAsStateWithLifecycle()
    val kinds by viewModel.kinds.collectAsStateWithLifecycle()

    WaveScaffold(
        topBar = {
            WaveTopBar(
                title = "动作统计",
                onBack = onBack,
                actions = { WaveTextAction("清空", onClick = viewModel::clear) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            WaveCard {
                Text("今日", fontSize = 13.sp, color = Wave.Ink2)
                WaveStatsRow(
                    modifier = Modifier.padding(top = 8.dp),
                    stats = listOf(
                        Triple(today.total.toString(), "次", "动作"),
                        Triple(today.okCount.toString(), "次", "成功"),
                        Triple(today.failCount.toString(), "次", "失败"),
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            WaveCard {
                Text("最近 7 天", fontSize = 13.sp, color = Wave.Ink2)
                WaveStatsRow(
                    modifier = Modifier.padding(top = 8.dp),
                    stats = listOf(
                        Triple(week.total.toString(), "次", "动作"),
                        Triple(week.okCount.toString(), "次", "成功"),
                        Triple(week.failCount.toString(), "次", "失败"),
                    ),
                )
            }

            WaveSectionLabel("按类型（7 天）")
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                if (kinds.isEmpty()) {
                    WaveNote("暂无数据")
                } else {
                    kinds.forEachIndexed { index, stat ->
                        if (index > 0) WaveDivider()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                            Text(stat.kind, fontSize = 13.5.sp, color = Wave.Ink, modifier = Modifier.weight(1f))
                            Text(
                                stat.count.toString(),
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Wave.Ink,
                            )
                        }
                    }
                }
            }

            WaveSectionLabel("按应用 Top（7 天）")
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                if (topPackages.isEmpty()) {
                    WaveNote("暂无数据")
                } else {
                    topPackages.forEachIndexed { index, stat ->
                        if (index > 0) WaveDivider()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(
                                stat.pkg,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Wave.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${stat.count} 次 · ok ${stat.okCount}",
                                fontSize = 12.sp,
                                color = Wave.Ink2,
                            )
                        }
                    }
                }
            }
            WaveNote("时长明细与原始记录见「导出诊断文件」中的 stats.csv。")
            Spacer(Modifier.height(24.dp))
        }
    }
}
