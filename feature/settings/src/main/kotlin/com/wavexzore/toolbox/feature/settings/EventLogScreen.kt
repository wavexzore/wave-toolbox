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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEventType
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
    onBack: () -> Unit,
    viewModel: EventLogViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

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

private fun A11yEventType.shortLabel(): String = when (this) {
    A11yEventType.WINDOW_STATE_CHANGED -> "WIN"
    A11yEventType.WINDOW_CONTENT_CHANGED -> "CONTENT"
    A11yEventType.WINDOWS_CHANGED -> "WINDOWS"
    A11yEventType.VIEW_SCROLLED -> "SCROLL"
    A11yEventType.OTHER -> "OTHER"
}
