package com.wavexzore.toolbox.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveChevron
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveIconBox
import com.wavexzore.toolbox.core.design.components.WavePill
import com.wavexzore.toolbox.core.design.components.WaveRow
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveSectionLabel
import com.wavexzore.toolbox.core.design.components.WaveSwitchRow
import com.wavexzore.toolbox.core.design.components.WaveSlider
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.core.design.theme.Wave
import kotlin.math.roundToInt

/** 设置页（原型 05）：引擎卡前置 + 数据/诊断 + 通用 + 关于。 */
@Composable
fun SettingsScreen(
    onOpenEventLog: () -> Unit,
    onOpenSetupGuide: () -> Unit,
    onOpenSnapshot: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val secureSettingsGranted by viewModel.secureSettingsGranted.collectAsStateWithLifecycle()
    val healStatus by viewModel.healStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.refreshGrant() }

    WaveScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                "设置",
                fontSize = 22.sp,
                fontWeight = FontWeight(650),
                letterSpacing = (-0.6).sp,
                color = Wave.Ink,
            )

            WaveSectionLabel("引擎")
            WaveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaveIconBox(
                        Icons.Filled.Security,
                        if (accessibilityEnabled) WaveTone.Green else WaveTone.Red,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("无障碍引擎", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink)
                        Text(
                            text = if (accessibilityEnabled) "运行中 · 服务正常" else "未开启 · 引擎无法工作",
                            fontSize = 12.5.sp,
                            color = Wave.Ink2,
                        )
                    }
                    if (accessibilityEnabled) {
                        WavePill("已开启", WaveTone.Green)
                    } else {
                        WavePill("未开启", WaveTone.Red)
                    }
                }
                WaveDivider(modifier = Modifier.padding(top = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "未开启时无法读取屏幕",
                        fontSize = 12.sp,
                        color = Wave.Ink3,
                        modifier = Modifier.weight(1f),
                    )
                    WaveTextAction("系统设置 ›", viewModel::openAccessibilitySettings)
                }
                WaveDivider(modifier = Modifier.padding(top = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("绑定自愈", fontSize = 12.5.sp, color = Wave.Ink2)
                        Text(
                            text = healStatus ?: if (secureSettingsGranted) {
                                "服务被系统回收后可一键重绑"
                            } else {
                                "未授权：先复制命令在电脑执行一次 adb"
                            },
                            fontSize = 11.5.sp,
                            color = Wave.Ink3,
                        )
                        if (!secureSettingsGranted) {
                            Text(
                                text = viewModel.grantCommand,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Wave.Ink3,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    if (secureSettingsGranted) {
                        WaveTextAction("立即修复", viewModel::healBinding)
                    } else {
                        WaveTextAction(
                            text = "复制命令",
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("adb", viewModel.grantCommand))
                                viewModel.onGrantCommandCopied()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                WaveSwitchRow(
                    title = "引擎总开关",
                    subtitle = "关闭后所有规则与任务停止执行",
                    checked = uiState.engineEnabled,
                    onCheckedChange = viewModel::setEngineEnabled,
                )
                WaveDivider()
                WaveSwitchRow(
                    title = "前台保活",
                    subtitle = "常驻通知，降低被系统回收的概率",
                    checked = uiState.keepAliveEnabled,
                    onCheckedChange = viewModel::setKeepAliveEnabled,
                )
                WaveDivider()
                WaveSwitchRow(
                    title = "开机自启",
                    subtitle = "开机后自动拉起常驻服务",
                    checked = uiState.bootAutostart,
                    onCheckedChange = viewModel::setBootAutostart,
                )
                WaveDivider()
                WaveSwitchRow(
                    title = "事件记录",
                    subtitle = "关闭后不记运行明细（统计仍保留）",
                    checked = uiState.eventRecordEnabled,
                    onCheckedChange = viewModel::setEventRecordEnabled,
                )
                WaveDivider()
                ThrottleRow(
                    value = uiState.contentThrottleMs,
                    onValueChange = viewModel::setContentThrottleMs,
                )
            }

            WaveSectionLabel("数据与诊断")
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                WaveRow(
                    title = "导出诊断文件",
                    subtitle = "设备/设置/统计/日志打包，JSON 分享",
                    leading = { WaveIconBox(Icons.Filled.Description, WaveTone.Blue) },
                    onClick = onOpenDiagnostics,
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "动作统计",
                    subtitle = "今日 / 7 日命中与失败",
                    leading = { WaveIconBox(Icons.Filled.BarChart) },
                    onClick = onOpenStats,
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "原始事件流",
                    subtitle = "引擎收到的每条无障碍事件",
                    leading = { WaveIconBox(Icons.Filled.List) },
                    onClick = onOpenEventLog,
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "窗口快照检查器",
                    subtitle = "导出当前窗口树 JSON",
                    leading = { WaveIconBox(Icons.Filled.CameraAlt) },
                    onClick = onOpenSnapshot,
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "清空记录",
                    subtitle = "不影响规则与设置",
                    leading = { WaveIconBox(Icons.Filled.Delete, WaveTone.Red) },
                    onClick = { dialog = "clear" },
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "重置全部数据",
                    subtitle = "设置回默认 + 统计清空",
                    leading = { WaveIconBox(Icons.Filled.RestartAlt, WaveTone.Red) },
                    onClick = { dialog = "reset" },
                    trailing = { WaveChevron() },
                )
            }

            WaveSectionLabel("通用")
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                WaveRow(
                    title = "隐私说明",
                    subtitle = "纯本地运行 · 零网络请求",
                    leading = { WaveIconBox(Icons.Filled.Lock, WaveTone.Yellow) },
                    onClick = { dialog = "privacy" },
                    trailing = { WaveChevron() },
                )
                WaveDivider()
                WaveRow(
                    title = "重新查看引导",
                    subtitle = "无障碍 / 电池优化 / 自启动白名单",
                    leading = { WaveIconBox(Icons.Filled.HelpOutline) },
                    onClick = onOpenSetupGuide,
                    trailing = { WaveChevron() },
                )
            }

            WaveSectionLabel("关于")
            WaveCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                WaveRow(
                    title = "版本",
                    minHeight = 52.dp,
                    trailing = {
                        Text(
                            viewModel.versionLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            color = Wave.Ink3,
                        )
                    },
                )
                WaveDivider()
                WaveRow(
                    title = "更新日志",
                    minHeight = 52.dp,
                    onClick = { dialog = "changelog" },
                    trailing = { WaveChevron() },
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("wave工具箱 · 纯本地运行", fontSize = 12.sp, color = Wave.Ink3)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "clear" -> ConfirmDialog(
            title = "清空记录？",
            message = "统计与运行明细将被清空，不影响规则与设置。",
            confirmLabel = "清空",
            onConfirm = { viewModel.clearRecords(); dialog = "" },
            onDismiss = { dialog = "" },
        )
        "reset" -> ConfirmDialog(
            title = "重置全部数据？",
            message = "所有设置回到默认值，统计清空。此操作不可撤销。",
            confirmLabel = "重置",
            onConfirm = { viewModel.resetAllData(); dialog = "" },
            onDismiss = { dialog = "" },
        )
        "privacy" -> InfoDialog(
            title = "隐私说明",
            message = "全部数据仅存本机：不申请网络权限、无账号、无遥测。\n无障碍服务用于读取屏幕节点并执行点击；事件明细保留 14 天。",
            onDismiss = { dialog = "" },
        )
        "changelog" -> InfoDialog(
            title = "更新日志",
            message = "v0.1 · 首个版本\n· 开屏广告自动跳过（稳妥/激进双模式）\n· 按应用开关 · 运行日志\n· 误操作修复：前台守卫 / 窗口收紧 / 连点防护",
            onDismiss = { dialog = "" },
        )
    }
}

@Composable
private fun ThrottleRow(value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("事件节流", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink, modifier = Modifier.weight(1f))
            Text(
                "$value ms",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                color = Wave.Ink2,
            )
        }
        Text(
            "CONTENT_CHANGED 最小间隔（${SettingsRepository.MIN_CONTENT_THROTTLE_MS}~${SettingsRepository.MAX_CONTENT_THROTTLE_MS} ms）",
            fontSize = 12.sp,
            color = Wave.Ink2,
        )
        WaveSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = SettingsRepository.MIN_CONTENT_THROTTLE_MS..SettingsRepository.MAX_CONTENT_THROTTLE_MS,
            step = 50,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wave.Card,
        title = { Text(title, fontSize = 16.sp) },
        text = { Text(message, fontSize = 13.5.sp, color = Wave.Ink2) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = Wave.RedTx) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = Wave.Ink2) }
        },
    )
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Wave.Card,
        title = { Text(title, fontSize = 16.sp) },
        text = { Text(message, fontSize = 13.5.sp, color = Wave.Ink2, lineHeight = 21.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了", color = Wave.Ink) }
        },
    )
}
