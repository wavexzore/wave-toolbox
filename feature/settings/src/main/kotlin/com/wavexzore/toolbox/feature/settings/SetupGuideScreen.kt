package com.wavexzore.toolbox.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/** 首启引导（T-009）：三步检查 + 受限设置分支 + 逐机型自启文案。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit,
    viewModel: SetupGuideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(1_500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首次引导") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "三步开启后台稳定运行；状态实时检测。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            GuideStepCard(
                index = 1,
                title = "开启无障碍服务",
                desc = if (uiState.serviceEnabled) "已开启" else "未开启（引擎运行的必需项）",
                done = uiState.serviceEnabled,
                actionLabel = if (uiState.serviceEnabled) "查看设置" else "去开启",
                onAction = viewModel::openAccessibilitySettings,
            ) {
                Text(
                    text = "受限设置（Android 13+）：系统设置里找不到本应用时，去「应用信息 → 右上角 ⋮ → 允许受限设置」，再回无障碍列表开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::openAppDetails) { Text("打开应用信息页") }
            }

            if (uiState.notificationSupported) {
                GuideStepCard(
                    index = 2,
                    title = "允许常驻通知",
                    desc = if (uiState.notificationGranted) "已授权（常驻状态与一键开关）" else "未授权（通知不显示）",
                    done = uiState.notificationGranted,
                    actionLabel = "授权",
                    onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            val batteryIndex = if (uiState.notificationSupported) 3 else 2
            GuideStepCard(
                index = batteryIndex,
                title = "关闭电池优化",
                desc = if (uiState.batteryIgnoring) "已忽略电池优化" else "未忽略（后台可能被冻结）",
                done = uiState.batteryIgnoring,
                actionLabel = "去设置",
                onAction = viewModel::requestIgnoreBatteryOptimization,
            )

            GuideStepCard(
                index = batteryIndex + 1,
                title = "厂商自启白名单（${uiState.guide.brandLabel}）",
                desc = "按下述路径手动确认（无法自动检测）",
                done = null,
                actionLabel = "打开自启管理",
                onAction = viewModel::openOemAutostart,
            ) {
                uiState.guide.steps.forEach { step ->
                    Text("· $step", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    viewModel.completeSetup()
                    onBack()
                },
                enabled = uiState.stepsSatisfied,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.setupCompleted) "已完成引导（可重开）" else "完成引导")
            }

            if (!uiState.stepsSatisfied) {
                Text(
                    text = "前 ${batteryIndex + 1} 步全部为绿色后即可完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideStepCard(
    index: Int,
    title: String,
    desc: String,
    done: Boolean?,
    actionLabel: String,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (done == true) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (done == true) "✓" else "$index.",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(0.dp))
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            extra?.let {
                Column(modifier = Modifier.padding(top = 4.dp)) { it() }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
