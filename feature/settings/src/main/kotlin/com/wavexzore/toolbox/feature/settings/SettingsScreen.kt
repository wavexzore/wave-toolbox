package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEventLog: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        ) {
            SectionTitle("引擎")

            SwitchRow(
                title = "引擎总开关",
                subtitle = "关闭后所有规则与任务停止执行（零副作用）",
                checked = uiState.engineEnabled,
                onCheckedChange = viewModel::setEngineEnabled,
            )

            HorizontalDivider()

            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text("事件节流", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${uiState.contentThrottleMs} ms（范围 ${SettingsRepository.MIN_CONTENT_THROTTLE_MS}~${SettingsRepository.MAX_CONTENT_THROTTLE_MS}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = uiState.contentThrottleMs.toFloat(),
                    onValueChange = { raw ->
                        val snapped = (raw / THROTTLE_STEP).roundToInt() * THROTTLE_STEP
                        viewModel.setContentThrottleMs(snapped)
                    },
                    valueRange = SettingsRepository.MIN_CONTENT_THROTTLE_MS.toFloat()..SettingsRepository.MAX_CONTENT_THROTTLE_MS.toFloat(),
                    steps = THROTTLE_STEPS,
                )
            }

            HorizontalDivider()

            SectionTitle("诊断")

            PlainRow("事件日志", "查看（实时）", onClick = onOpenEventLog)
            HorizontalDivider()
            PlainRow("应用白名单", if (uiState.allowPackages.isEmpty()) "全部应用" else "${uiState.allowPackages.size} 个应用")
        }
    }
}

private const val THROTTLE_STEP = 50
private const val THROTTLE_STEPS =
    (SettingsRepository.MAX_CONTENT_THROTTLE_MS - SettingsRepository.MIN_CONTENT_THROTTLE_MS) / THROTTLE_STEP - 1

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlainRow(title: String, trailing: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
