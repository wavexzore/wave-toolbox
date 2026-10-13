package com.wavexzore.toolbox.core.rules.source

import android.content.res.AssetManager
import com.wavexzore.toolbox.core.rules.parser.RuleParser

/**
 * 内置规则加载（assets 内 rules/builtin 目录的 json5 文件）。
 * 坏文件隔离：单文件解析失败只登记错误，不影响整批。
 */
class BuiltinRuleLoader(
    private val assets: AssetManager,
    private val dir: String = DEFAULT_DIR,
) {

    fun loadAll(): RuleParser.Outcome {
        val files = try {
            assets.list(dir)?.sorted().orEmpty()
        } catch (t: Throwable) {
            return RuleParser.Outcome(emptyList(), listOf(RuleParser.RuleError("assets:$dir", null, null, "assets 列举失败: ${t.message}", fatal = true)))
        }
        val groups = ArrayList<com.wavexzore.toolbox.core.rules.model.RawRuleGroup>()
        val errors = ArrayList<RuleParser.RuleError>()
        files.filter { it.endsWith(".json5") }.forEach { name ->
            val source = "assets:$dir/$name"
            val text = try {
                assets.open("$dir/$name").bufferedReader().use { it.readText() }
            } catch (t: Throwable) {
                errors += RuleParser.RuleError(source, null, null, "读取失败: ${t.message}", fatal = true)
                return@forEach
            }
            val (group, groupErrors) = RuleParser.parseGroup(text, source)
            group?.let(groups::add)
            errors += groupErrors
        }
        return RuleParser.Outcome(groups, errors)
    }

    companion object {
        const val DEFAULT_DIR = "rules/builtin"
    }
}
