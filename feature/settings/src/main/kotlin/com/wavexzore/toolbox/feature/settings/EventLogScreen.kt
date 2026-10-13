package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.accessibility.action.ActionRecord
import com.wavexzore.toolbox.core.accessibility.pipeline.A11yEventType
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import com.wavexzore.toolbox.core.design.components.WaveDivider
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveTabs
import com.wavexzore.toolbox.core.design.components.WaveTextAction
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 原始事件流（诊断）：结构化事件 + 规则动作双页签。 */
@Composable
fun EventLogScreen(
    onBack: () -> Unit,
    viewModel: EventLogViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    WaveScaffold(
        topBar = {
            WaveTopBar(
                title = "原始事件流",
                onBack = onBack,
                actions = { WaveTextAction("清空", onClick = viewModel::clear) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WaveTabs(titles = listOf("事件", "动作"), selected = tab, onSelect = { tab = it })

            if (tab == 0) {
                WaveNote(
                    "收 ${stats.received} · 出 ${stats.dispatched} · 滤 ${stats.dropped} · 节 ${stats.throttled}（最近 ${recent.size} 条）",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recent, key = { it.seq }) { item ->
                        EventRow(item)
                        WaveDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            } else {
                WaveNote(
                    "规则动作流（最近 ${actions.size} 条）· 命中 / 拒绝 / 复核重试",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(actions, key = { it.seq }) { item ->
                        ActionRow(item)
                        WaveDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Wave.Ink3,
            )
            Text(type.shortLabel(), fontSize = 12.sp, color = Wave.BlueTx)
        }
        Text(
            text = "${packageName ?: "-"} ${className ?: ""}".trim(),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Wave.Ink,
        )
        text?.let {
            Text(it, fontSize = 12.sp, color = Wave.Ink2, maxLines = 1)
        }
    }
}

@Composable
private fun ActionRow(item: ActionRecord) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(item.timeMillis)),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Wave.Ink3,
            )
            Text(
                text = item.kind.shortLabel(),
                fontSize = 12.sp,
                color = if (item.ok) Wave.GreenTx else Wave.RedTx,
            )
            Text(item.ruleId, fontSize = 11.sp, color = Wave.Ink3)
        }
        Text(
            text = buildString {
                append(item.packageName ?: "-")
                item.mechanism?.let { append(" · mech=").append(it) }
                append(" · ok=").append(item.ok)
                item.detail?.let { append(" · ").append(it) }
            },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Wave.Ink,
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
