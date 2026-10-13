package com.wavexzore.toolbox.feature.wechat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 微信僵尸好友设置面板：检测控制 / 结果勾选与删除 / 导出。 */
@Composable
fun WechatSettingsPane(
    feature: WechatFeature,
    store: WechatStore,
    config: WechatConfig,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by feature.data.collectAsStateWithLifecycle()
    val detection by feature.detectionProgress.collectAsStateWithLifecycle()
    val deletion by feature.deleteProgress.collectAsStateWithLifecycle()
    val friendListState by feature.listProgress.collectAsStateWithLifecycle()
    val verifyState by feature.verifyProgress.collectAsStateWithLifecycle()
    val dryRun by config.dryRun.collectAsStateWithLifecycle(initialValue = false)
    val deleteRecord by config.deleteChatRecord.collectAsStateWithLifecycle(initialValue = false)
    val hudEnabled by config.hudEnabled.collectAsStateWithLifecycle(initialValue = true)
    val defaultSelection by config.defaultSelection.collectAsStateWithLifecycle(initialValue = WechatConfig.DEFAULT_SELECTION)

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var confirmDeleteRecord by remember { mutableStateOf(false) }

    val busy = detection.running || deletion.running || friendListState.running || verifyState.running

    // 默认勾选策略（deletedOnly=仅"已删除你"且未清理）
    LaunchedEffect(data.entries.size, initialized) {
        if (!initialized && data.entries.isNotEmpty()) {
            selected = data.entries
                .filter { !it.cleaned && (defaultSelection == "all" || it.category == ZombieCategory.DELETED) }
                .map { it.name }
                .toSet()
            initialized = true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(feature.resultsCsv()) }
            }
        }
    }

    val exportFriendsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(feature.friendsCsv()) }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ---- 转账验证（v1.1 主方案） ----
        Text("转账验证（实名信号 · 异常自动打标签）", style = MaterialTheme.typography.titleSmall)
        if (friendListState.running || verifyState.running) {
            Text(
                text = if (friendListState.running) {
                    "收集：${friendListState.phase} 已收${friendListState.collected}"
                } else {
                    "验证：${verifyState.phase} ${verifyState.index}/${verifyState.total} · 异常${verifyState.zomibes} 已标签${verifyState.tagged}${if (verifyState.paused) "（已暂停）" else ""} · ${verifyState.current}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verifyState.running) {
                    TextButton(onClick = { if (verifyState.paused) feature.resumeVerify() else feature.pauseVerify() }) {
                        Text(if (verifyState.paused) "继续" else "暂停")
                    }
                    TextButton(onClick = feature::stopVerify) { Text("停止验证") }
                }
                if (friendListState.running) {
                    TextButton(onClick = feature::stopFriendList) { Text("停止收集") }
                }
            }
        } else {
            val pending = data.friends.count { it.status == FriendStatus.PENDING }
            val okCount = data.friends.count { it.status == FriendStatus.OK }
            val zombies = data.friends.count { it.status == FriendStatus.ZOMBIE }
            val unknown = data.friends.count { it.status == FriendStatus.UNKNOWN }
            Text(
                text = "好友${data.friends.size} · 待验证${pending} 正常${okCount} 异常${zombies} 未判定${unknown}" +
                    (data.friendsCollectedAt?.let { " · 收集于 " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = feature::startFriendList, enabled = !busy) { Text("① 收集好友") }
                OutlinedButton(
                    onClick = { feature.startVerify(false) },
                    enabled = !busy && pending > 0,
                ) { Text("② 开始验证") }
                if (unknown > 0) {
                    TextButton(onClick = { feature.startVerify(true) }, enabled = !busy) { Text("重试未判定${unknown}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { feature.resetFriendsStatus() } }, enabled = !busy) { Text("重置状态") }
                TextButton(onClick = { exportFriendsLauncher.launch("wechat-friends.csv") }, enabled = data.friends.isNotEmpty()) { Text("导出好友CSV") }
            }
            val zombieNames = data.friends.filter { it.status == FriendStatus.ZOMBIE }
            if (zombieNames.isNotEmpty()) {
                Text(
                    text = "异常好友：" + zombieNames.take(8).joinToString("、") { it.name + if (it.tagged) "(已标签)" else "" } +
                        if (zombieNames.size > 8) " 等${zombieNames.size}人" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HorizontalDivider()

        // ---- 检测控制 ----
        Text("检测（拉群法 · 兼容保留）", style = MaterialTheme.typography.titleSmall)
        if (detection.running) {
            Text(
                text = "运行中：${detection.phase} ${detection.message} · 批${detection.batch} 处理${detection.processed} 命中${detection.found}${if (detection.paused) "（已暂停）" else ""}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { if (detection.paused) feature.resumeDetection() else feature.pauseDetection() }) {
                    Text(if (detection.paused) "继续" else "暂停")
                }
                TextButton(onClick = feature::stopDetection) { Text("停止") }
            }
        } else {
            Text(
                text = "上次状态：${detection.phase.ifEmpty { "idle" }} 批${detection.batch} 处理${detection.processed} 命中${detection.found}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = feature::startDetection, enabled = !busy) { Text("开始检测") }
                Text("dry-run（不退群）", style = MaterialTheme.typography.bodySmall)
                Switch(checked = dryRun, onCheckedChange = { scope.launch { config.setDryRun(it) } })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("进度悬浮球", style = MaterialTheme.typography.bodySmall)
            Switch(checked = hudEnabled, onCheckedChange = { scope.launch { config.setHudEnabled(it) } })
        }

        HorizontalDivider()

        // ---- 结果区 ----
        Text("检测结果（${data.entries.size}）", style = MaterialTheme.typography.titleSmall)
        if (data.entries.isEmpty()) {
            Text("暂无结果。先「开始检测」；检测后三类名单在此列出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ZombieCategory.entries.forEach { category ->
                val items = data.entries.filter { it.category == category }
                if (items.isEmpty()) return@forEach
                val names = items.map { it.name }.toSet()
                val allChecked = selected.containsAll(names)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + names else selected - names
                        },
                    )
                    Text("${category.label}（${items.size}）", style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = entry.name in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + entry.name else selected - entry.name
                                },
                            )
                            Text(
                                text = entry.name + if (entry.cleaned) "（已清理）" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.cleaned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(entry.at)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { confirmDeleteRecord = deleteRecord; showConfirm = true },
                    enabled = selected.isNotEmpty() && !busy,
                ) { Text("删除选中（${selected.size}）") }
                TextButton(onClick = { exportLauncher.launch("wechat-结果.csv") }) { Text("导出 CSV") }
                TextButton(onClick = feature::clearResults) { Text("清空结果") }
            }
        }

        if (deletion.running || deletion.finished) {
            Text(
                text = "删除：${deletion.done}/${deletion.total} 失败${deletion.failed} ${deletion.currentName} ${deletion.message}".trim(),
                style = MaterialTheme.typography.bodySmall,
            )
            if (deletion.running) {
                TextButton(onClick = feature::stopDelete) { Text("停止删除") }
            }
        }

        // ---- 运行历史 ----
        if (data.runs.isNotEmpty()) {
            HorizontalDivider()
            Text("最近轮次", style = MaterialTheme.typography.titleSmall)
            data.runs.takeLast(3).reversed().forEach { run ->
                Text(
                    text = "${SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(run.startedAt))} · ${run.status} · 批${run.batches} 处理${run.processed} 命中${run.found}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "提示：检测时请保持微信在前台；完成一批会自动退群（dry-run 例外）。删除默认保留聊天记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("删除好友") },
            text = {
                Column {
                    Text("将删除 ${selected.size} 人（可在资料页二次校验；失败项留痕跳过）。")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = confirmDeleteRecord, onCheckedChange = { confirmDeleteRecord = it })
                        Text("同时删除聊天记录", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch { config.setDeleteChatRecord(confirmDeleteRecord) }
                    feature.startDelete(selected.toList(), confirmDeleteRecord)
                }) { Text("开始删除") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("取消") } },
        )
    }
}
