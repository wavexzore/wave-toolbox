package com.wavexzore.toolbox.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveGhostButton
import com.wavexzore.toolbox.core.design.components.WavePill
import com.wavexzore.toolbox.core.design.components.WavePrimaryButton
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave

/** 首次引导（原型 06）：三步检查 + 实时状态；完成写 setup.completed。 */
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit,
    viewModel: SetupGuideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    WaveScaffold(topBar = { WaveTopBar("", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            WaveBrandLockup(chipSize = 16.dp, titleSize = 12.5.sp, titleColor = Wave.Ink3)
            Text(
                "三步完成设置",
                fontFamily = FontFamily.Serif,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                color = Wave.Ink,
                letterSpacing = (-0.2).sp,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "开启无障碍服务后，打开任意 App 的开屏广告都会被自动跳过。",
                fontSize = 14.sp,
                color = Wave.Ink2,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(Modifier.height(22.dp))
            WaveDivider()

            GuideStep(
                index = "01",
                title = "开启无障碍服务",
                desc = if (uiState.serviceEnabled) "已开启，引擎工作中。" else "授予后引擎才能读取屏幕并执行点击，数据不出本机。",
                state = if (uiState.serviceEnabled) StateTag.On("已开启") else StateTag.Off("未开启"),
                actionLabel = if (uiState.serviceEnabled) "查看设置" else "去开启",
                onAction = viewModel::openAccessibilitySettings,
                extra = if (!uiState.serviceEnabled) {
                    {
                        Column {
                            Text(
                                "系统设置里找不到本应用时：应用信息 → 右上角 ⋮ → 允许受限设置，再回无障碍列表开启。",
                                fontSize = 12.sp,
                                color = Wave.Ink3,
                                lineHeight = 18.sp,
                            )
                            WaveTextAction("打开应用信息页", viewModel::openAppDetails)
                        }
                    }
                } else {
                    null
                },
            )

            GuideStep(
                index = "02",
                title = "关闭电池优化",
                desc = if (uiState.batteryIgnoring) "已忽略电池优化。" else "未忽略时，后台可能被系统冻结导致跳过失效。",
                state = if (uiState.batteryIgnoring) StateTag.On("已设置") else StateTag.Warn("未设置"),
                actionLabel = "去设置",
                onAction = viewModel::requestIgnoreBatteryOptimization,
            )

            GuideStep(
                index = "03",
                title = "自启动白名单（${uiState.guide.brandLabel}）",
                desc = "按厂商路径手动确认（系统无法自动检测）：",
                state = null,
                actionLabel = "打开自启管理",
                onAction = viewModel::openOemAutostart,
                extra = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.guide.steps.forEach { step ->
                            Text("· $step", fontSize = 12.sp, color = Wave.Ink2, lineHeight = 18.sp)
                        }
                    }
                },
            )

            GuideStep(
                index = "04",
                title = "允许常驻通知",
                desc = "常驻状态与一键开关（Android 13+）。",
                state = if (uiState.notificationGranted) StateTag.On("已授权") else StateTag.Neutral("未授权"),
                actionLabel = "去授权",
                onAction = {
                    if (uiState.notificationSupported && !uiState.notificationGranted) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.refresh()
                    }
                },
                showDivider = true,
                visible = uiState.notificationSupported || uiState.notificationGranted,
            )

            Spacer(Modifier.height(26.dp))
            WavePrimaryButton(
                text = if (uiState.serviceEnabled) "完成引导" else "去开启无障碍服务",
                onClick = {
                    if (uiState.serviceEnabled) {
                        viewModel.completeSetup()
                        onBack()
                    } else {
                        viewModel.openAccessibilitySettings()
                        viewModel.refresh()
                    }
                },
            )
            WaveGhostButton("稍后再说 · 先逛逛", onBack)

            // 深底字标段（字标 V1 深底态：纸白文字、图形不变，lime 点题——设计规范见 docs/design/app-icon）
            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Wave.Deep)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                WaveBrandLockup(chipSize = 18.dp, titleSize = 14.sp, titleColor = Wave.Bg)
                Spacer(Modifier.height(5.dp))
                Text("轻量 · 纯本地 · 无联网", fontSize = 11.5.sp, color = Wave.Lime)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private sealed interface StateTag {
    data class On(val label: String) : StateTag
    data class Off(val label: String) : StateTag
    data class Warn(val label: String) : StateTag
    data class Neutral(val label: String) : StateTag
}

@Composable
private fun GuideStep(
    index: String,
    title: String,
    desc: String,
    state: StateTag?,
    actionLabel: String,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    visible: Boolean = true,
) {
    if (!visible) return
    Column(modifier = Modifier.padding(vertical = 18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                index,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Wave.Ink3,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Wave.Ink, modifier = Modifier.weight(1f, fill = false))
                    when (state) {
                        is StateTag.On -> WavePill(state.label, WaveTone.Green)
                        is StateTag.Off -> WavePill(state.label, WaveTone.Red)
                        is StateTag.Warn -> WavePill(state.label, WaveTone.Yellow)
                        is StateTag.Neutral -> WavePill(state.label)
                        null -> Unit
                    }
                }
                Text(desc, fontSize = 12.5.sp, color = Wave.Ink2, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
                extra?.let {
                    Box(modifier = Modifier.padding(top = 6.dp)) { it() }
                }
                WaveTextAction(actionLabel, onAction, modifier = Modifier.padding(start = 0.dp))
            }
        }
    }
    if (showDivider) WaveDivider()
}
