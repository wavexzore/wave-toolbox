package com.wavexzore.toolbox.core.rules.source

import com.wavexzore.toolbox.core.rules.parser.RuleParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** assets 内置规则走查（单测工作目录=模块目录，直接读源文件）。 */
class BuiltinAssetsTest {

    private val assetsDir = File("src/main/assets/rules/builtin")

    @Test
    fun `builtin assets exist`() {
        assertTrue(assetsDir.isDirectory, "assets 目录不存在: ${assetsDir.absolutePath}")
        val files = assetsDir.listFiles { f -> f.name.endsWith(".json5") }.orEmpty()
        assertTrue(files.size >= 2, "至少 2 个内置规则样例")
    }

    @Test
    fun `all builtin assets parse without errors`() {
        val errors = ArrayList<String>()
        val groups = ArrayList<String>()
        assetsDir.listFiles { f -> f.name.endsWith(".json5") }.orEmpty().sorted().forEach { file ->
            val (group, groupErrors) = RuleParser.parseGroup(file.readText(), "assets:${file.name}")
            group?.let { groups += it.id }
            groupErrors.forEach { errors += "[${file.name}] ${it.message}" }
        }
        assertTrue(errors.isEmpty(), "内置规则存在错误: $errors")
        assertTrue(groups.contains("com.tencent.mm"))
        assertTrue(groups.size >= 2)
    }

    @Test
    fun `loader default dir constant`() {
        assertEquals("rules/builtin", BuiltinRuleLoader.DEFAULT_DIR)
    }
}
