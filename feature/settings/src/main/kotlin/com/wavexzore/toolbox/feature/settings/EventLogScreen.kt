package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.accessibility.action.ActionRecord
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEventType
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 事件日志（T-010 双页签）：结构化事件流 + 规则动作流（命中/拒绝/重试）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    onBack: () -> Unit,
    viewModel: EventLogViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clear) { Text("清空") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("事件") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("动作") })
            }

            if (tab == 0) {
                Text(
                    text = "收 ${stats.received} · 出 ${stats.dispatched} · 滤 ${stats.dropped} · 节 ${stats.throttled}（最近 ${recent.size} 条）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(recent, key = { _, item -> item.seq }) { index, item ->
                        EventRow(item)
                        if (index < recent.lastIndex) HorizontalDivider()
                    }
                }
            } else {
                Text(
                    text = "规则动作流（最近 ${actions.size} 条）· 命中 / 拒绝 / 复核重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(actions, key = { _, item -> item.seq }) { index, item ->
                        ActionRow(item)
                        if (index < actions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
private fun EventRow(item: EventPipeline.RecentEvent) = with(item) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(timeMillis)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = type.shortLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "${packageName ?: "-"} ${className ?: ""}".trim(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        text?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActionRow(item: ActionRecord) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(item.timeMillis)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.kind.shortLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = item.ruleId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildString {
                append(item.packageName ?: "-")
                item.mechanism?.let { append(" · mech=$it") }
                append(" · ok=${item.ok}")
                item.detail?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
    }
}

private fun A11yEventType.shortLabel(): String = when (this) {
    A11yEventType.WINDOW_STATE_CHANGED -> "WIN"
    A11yEventType.WINDOW_CONTENT_CHANGED -> "CONTENT"
    A11yEventType.WINDOWS_CHANGED -> "WINDOWS"
    A11yEventType.VIEW_SCROLLED -> "SCROLL"
    A11yEventType.OTHER -> "OTHER"
}

private fun ActionRecord.Kind.shortLabel(): String = when (this) {
    ActionRecord.Kind.ACTION -> "ACTION"
    ActionRecord.Kind.VERIFY_RETRY -> "RETRY"
    ActionRecord.Kind.DENY -> "DENY"
    ActionRecord.Kind.MISS -> "MISS"
}
