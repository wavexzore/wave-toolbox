package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.rules.model.RawApp
import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

/** 同 pkg 用户规则包整包覆盖内置（统一存储 source 语义）。 */
class AdSkipPackSelectionTest {

    private fun group(id: String, pkg: String, source: String) = RawRuleGroup(
        id = id,
        apps = listOf(RawApp(id = pkg)),
        kind = RawRuleGroup.KIND_ADSKIP,
        source = source,
    )

    @Test
    fun `builtin only keeps all groups`() {
        val groups = listOf(
            group("p#splash", "p", RawRuleGroup.SOURCE_BUILTIN),
            group("p#popup", "p", RawRuleGroup.SOURCE_BUILTIN),
        )
        assertEquals(groups, effectivePackGroups(groups))
    }

    @Test
    fun `user pack replaces builtin groups for same pkg`() {
        val builtin = group("p#splash", "p", RawRuleGroup.SOURCE_BUILTIN)
        val user = group("p#custom", "p", "user:mine")
        assertEquals(listOf(user), effectivePackGroups(listOf(builtin, user)))
    }

    @Test
    fun `disabled user pack still suppresses builtin groups`() {
        val builtin = group("p#splash", "p", RawRuleGroup.SOURCE_BUILTIN)
        val user = group("p#custom", "p", "user:mine").copy(enabled = false)
        assertEquals(listOf(user), effectivePackGroups(listOf(builtin, user)))
    }

    @Test
    fun `multiple user packs are all kept`() {
        val userA = group("p#a", "p", "user:a")
        val userB = group("p#b", "p", "user:b")
        assertEquals(listOf(userA, userB), effectivePackGroups(listOf(userA, userB)))
    }
}
