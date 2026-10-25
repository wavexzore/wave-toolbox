package com.wavexzore.toolbox.core.rules.source

import android.content.res.AssetManager
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.parser.RuleParser

/**
 * 内置规则加载（统一入口）：`rules/builtin`（通用规则）+ `rules/adskip`（广告跳过包）。
 * 两者都解析为 [RawRuleGroup]，坏文件隔离，不中断整批。
 */
class BuiltinRuleLoader(
    private val assets: AssetManager,
    private val dirs: List<String> = DEFAULT_DIRS,
) {

    fun loadAll(): RuleParser.Outcome {
        val groups = ArrayList<RawRuleGroup>()
        val errors = ArrayList<RuleParser.RuleError>()
        dirs.forEach { dir ->
            val files = try {
                assets.list(dir)?.sorted().orEmpty()
            } catch (t: Throwable) {
                errors += RuleParser.RuleError("assets:$dir", null, null, "assets 列举失败: ${t.message}", fatal = true)
                return@forEach
            }
            files.filter { it.endsWith(".json5") }.forEach { name ->
                val source = "assets:$dir/$name"
                val text = try {
                    assets.open("$dir/$name").bufferedReader().use { it.readText() }
                } catch (t: Throwable) {
                    errors += RuleParser.RuleError(source, null, null, "读取失败: ${t.message}", fatal = true)
                    return@forEach
                }
                val outcome = RuleParser.parseDocument(text, source)
                groups += outcome.groups
                errors += outcome.errors
            }
        }
        return RuleParser.Outcome(groups, errors)
    }

    companion object {
        const val DEFAULT_DIR = "rules/builtin"
        const val ADSKIP_DIR = "rules/adskip"
        val DEFAULT_DIRS = listOf(DEFAULT_DIR, ADSKIP_DIR)
    }
}
