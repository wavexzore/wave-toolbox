package com.wavexzore.toolbox.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveChevron
import com.wavexzore.toolbox.core.design.components.WaveIconBox
import com.wavexzore.toolbox.core.design.components.WaveIconButton
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WavePill
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveSectionLabel
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.core.design.theme.Wave
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** 首页（原型 01）：功能列表 + 更多能力占位；引擎状态在设置页。 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onOpenFeature: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val features by viewModel.features.collectAsStateWithLifecycle()

    WaveScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "工具箱",
                        fontSize = 22.sp,
                        fontWeight = FontWeight(650),
                        letterSpacing = (-0.6).sp,
                        color = Wave.Ink,
                    )
                    Text("轻量 · 纯本地 · 仅更新联网", fontSize = 11.5.sp, color = Wave.Ink2, modifier = Modifier.padding(top = 2.dp))
                }
                WaveIconButton(Icons.Outlined.Info, "说明", onOpenSetupGuide, tint = Wave.Ink2)
            }

            WaveSectionLabel("功能")
            features.forEach { feature ->
                WaveCard(onClick = { onOpenFeature(feature.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WaveIconBox(Icons.Filled.Bolt)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                feature.name,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Wave.Ink,
                            )
                            Text(
                                feature.description,
                                fontSize = 12.5.sp,
                                color = Wave.Ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        WaveChevron()
                    }
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (feature.enabled) {
                            WavePill("已启用", WaveTone.Green)
                        } else {
                            WavePill("已停用")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            WaveCard(modifier = Modifier.alpha(0.72f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaveIconBox(Icons.Outlined.Widgets)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("更多能力", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink)
                        Text("应用内弹窗拦截 · 桌面小组件", fontSize = 12.5.sp, color = Wave.Ink2)
                    }
                    WavePill("规划中")
                }
            }

            WaveNote("全部数据仅存本机，仅检查更新时联网。")
            Spacer(Modifier.height(24.dp))
        }
    }
}
