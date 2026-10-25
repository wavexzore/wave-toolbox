package com.wavexzore.toolbox.debug

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.wavexzore.toolbox.core.automation.TaskDef
import com.wavexzore.toolbox.core.automation.TaskEnvironment
import com.wavexzore.toolbox.core.automation.TaskExecutor
import com.wavexzore.toolbox.core.automation.TaskProgress
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Debug-only 任务验证入口（内部验证版，不进入 release）。
 *
 * - 任务定义来自 `assets/tasks` 下的 JSON5，解析与安全上限见 TaskParser/TaskSafety；
 * - 通过 `adb shell am start -n com.wavexzore.toolbox/.debug.TaskLabActivity --es task_id demo.settings.nav`
 *   可自动执行指定任务，进度写 logcat（TAG=WaveTaskLab）。
 */
@AndroidEntryPoint
class TaskLabActivity : ComponentActivity() {

    @Inject
    lateinit var environment: TaskEnvironment

    private val executor by lazy { TaskExecutor(environment) }
    private val logs = mutableStateListOf<String>()
    private val running = mutableStateOf(false)
    private var runJob: Job? = null
    private lateinit var catalog: DebugTaskCatalog.Result

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = DebugTaskCatalog(this).load()
        setContent {
            MaterialTheme {
                TaskLabScreen(
                    catalog = catalog,
                    logs = logs,
                    running = running.value,
                    onRun = ::runTask,
                )
            }
        }
        val autoTaskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (!autoTaskId.isNullOrBlank()) {
            window.decorView.post {
                catalog.tasks.firstOrNull { it.id == autoTaskId }?.let(::runTask)
                    ?: Log.w(TAG, "auto task not found: $autoTaskId")
            }
        }
    }

    override fun onDestroy() {
        executor.cancel()
        runJob?.cancel()
        super.onDestroy()
    }

    private fun runTask(task: TaskDef) {
        if (running.value) return
        running.value = true
        logs.clear()
        logs += "run ${task.id} pkg=${task.pkg} steps=${task.steps.size}"
        runJob = lifecycleScope.launch {
            try {
                executor.run(task).collect { progress ->
                    val line = render(progress)
                    logs += line
                    Log.i(TAG, line)
                }
            } finally {
                running.value = false
                logs += "run finished"
            }
        }
    }

    private fun render(progress: TaskProgress): String = when (progress) {
        is TaskProgress.Started -> "started task=${progress.taskId} steps=${progress.totalSteps}"
        is TaskProgress.StepStarted -> "step#${progress.index + 1} ${progress.step::class.simpleName}"
        is TaskProgress.StepFinished -> buildString {
            append("step#${progress.index + 1} ok=${progress.ok}")
            if (progress.skipped) append(" skipped")
            progress.reason?.let { append(" reason=$it") }
        }
        is TaskProgress.Finished -> "finished ${progress.status}${progress.reason?.let { " reason=$it" } ?: ""}"
    }

    companion object {
        const val TAG = "WaveTaskLab"
        const val EXTRA_TASK_ID = "task_id"
    }
}

@androidx.compose.runtime.Composable
private fun TaskLabScreen(
    catalog: DebugTaskCatalog.Result,
    logs: List<String>,
    running: Boolean,
    onRun: (TaskDef) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text("任务验证（Debug）", style = MaterialTheme.typography.titleLarge)
                Text(
                    "任务定义：assets/tasks/*.json5；安全上限：30 步 / 120 秒 / 单步 15 秒。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (catalog.errors.isNotEmpty()) {
                    Text(
                        "加载错误：\n${catalog.errors.joinToString("\n")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        items(catalog.tasks, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.name.ifBlank { task.id }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${task.id} · ${task.pkg} · ${task.steps.size} 步 / ${task.maxDurationMs / 1000}s",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Button(onClick = { onRun(task) }, enabled = !running) {
                        Text(if (running) "运行中" else "运行")
                    }
                }
            }
        }
        item {
            Text("运行日志", style = MaterialTheme.typography.titleSmall)
        }
        if (logs.isEmpty()) {
            item { Text("（尚无日志）", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(logs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
