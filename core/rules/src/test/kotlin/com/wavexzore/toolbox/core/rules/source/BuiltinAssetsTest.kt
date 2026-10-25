package com.wavexzore.toolbox.core.rules.source

import com.wavexzore.toolbox.core.rules.parser.RuleParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** assets 内置规则走查（单测工作目录=模块目录，直接读源文件）。 */
class BuiltinAssetsTest {

    private val genericDir = File("src/main/assets/rules/builtin")
    private val adSkipDir = File("src/main/assets/rules/adskip")

    @Test
    fun `builtin assets exist`() {
        assertTrue(genericDir.isDirectory, "assets 目录不存在: ${genericDir.absolutePath}")
        assertTrue(adSkipDir.isDirectory, "adskip assets 目录不存在: ${adSkipDir.absolutePath}")
        val generic = genericDir.listFiles { f -> f.name.endsWith(".json5") }.orEmpty()
        val adSkip = adSkipDir.listFiles { f -> f.name.endsWith(".json5") }.orEmpty()
        assertTrue(generic.size >= 2, "至少 2 个通用内置规则样例")
        assertEquals(24, adSkip.size, "adskip 内置规则包应完整迁移到 core:rules")
    }

    @Test
    fun `all builtin assets parse without errors`() {
        val errors = ArrayList<String>()
        val groups = ArrayList<String>()
        (listOf(genericDir, adSkipDir)).forEach { dir ->
            dir.listFiles { f -> f.name.endsWith(".json5") }.orEmpty().sorted().forEach { file ->
                val outcome = RuleParser.parseDocument(file.readText(), "assets:${file.name}")
                groups += outcome.groups.map { it.id }
                outcome.errors.forEach { errors += "[${file.name}] ${it.message}" }
            }
        }
        assertTrue(errors.isEmpty(), "内置规则存在错误: $errors")
        assertTrue(groups.contains("com.tencent.mm"))
        assertTrue(groups.contains("com.tencent.mm#splash-entry"))
        assertTrue(groups.size >= 2)
    }

    @Test
    fun `loader default dir constant`() {
        assertEquals("rules/builtin", BuiltinRuleLoader.DEFAULT_DIR)
        assertEquals("rules/adskip", BuiltinRuleLoader.ADSKIP_DIR)
        assertEquals(listOf("rules/builtin", "rules/adskip"), BuiltinRuleLoader.DEFAULT_DIRS)
    }
}
