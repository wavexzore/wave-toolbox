package com.wavexzore.toolbox.debug

import android.content.Context
import com.wavexzore.toolbox.core.automation.TaskDef
import com.wavexzore.toolbox.core.automation.TaskParser
import java.io.FileNotFoundException

/** Debug-only 任务目录：读取 `assets/tasks` 下的 JSON5，坏任务隔离并保留错误。 */
class DebugTaskCatalog(private val context: Context) {

    data class Result(
        val tasks: List<TaskDef>,
        val errors: List<String>,
    )

    fun load(): Result {
        val tasks = ArrayList<TaskDef>()
        val errors = ArrayList<String>()
        val names = runCatching { context.assets.list(DIR).orEmpty().sorted() }
            .getOrElse {
                errors += "assets/$DIR 列举失败: ${it.message}"
                emptyList()
            }
        val seen = HashSet<String>()
        names.filter { it.endsWith(".json5") }.forEach { name ->
            val source = "assets:$DIR/$name"
            val text = try {
                context.assets.open("$DIR/$name").bufferedReader().use { it.readText() }
            } catch (e: FileNotFoundException) {
                errors += "[$source] 文件不存在"
                return@forEach
            } catch (t: Throwable) {
                errors += "[$source] 读取失败: ${t.message}"
                return@forEach
            }
            val outcome = TaskParser.parse(text, source)
            outcome.errors.forEach { error -> errors += "[${error.source}] ${error.message}" }
            outcome.tasks.forEach { task ->
                if (seen.add(task.id)) tasks += task else errors += "[$source] 重复任务 id: ${task.id}"
            }
        }
        return Result(tasks.sortedBy { it.id }, errors)
    }

    companion object {
        const val DIR = "tasks"
    }
}
