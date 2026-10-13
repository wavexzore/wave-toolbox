package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.feature.adskip.profile.AdProfileFactory
import com.wavexzore.toolbox.feature.adskip.profile.AdProfileRegistry
import com.wavexzore.toolbox.feature.adskip.profile.AppAdProfile
import com.wavexzore.toolbox.feature.adskip.profile.GenericProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** T-202 注册表/工厂单测：回落、缓存、合并规则（同 id 替换/稳定排序）、冲突 fail-fast。 */
class AdProfileFactoryTest {

    private class FakeProfile(
        override val pkgs: Set<String>,
        private val groups: List<AdSkipGroup> = emptyList(),
        private val keywords: List<String> = emptyList(),
    ) : AppAdProfile {
        override fun groups() = groups
        override fun keywordExtra() = keywords
    }

    private fun factory(
        profiles: List<AppAdProfile>,
        packs: Map<String, List<AdSkipGroup>> = emptyMap(),
    ) = AdProfileFactory(AdProfileRegistry(profiles.toSet()), PackGroupsSource { packs[it] ?: emptyList() })

    private fun group(id: String, order: Int = 0, name: String = "", enabled: Boolean = true) =
        AdSkipGroup(id = id, order = order, name = name, enabled = enabled)

    @Test
    fun unknownPackageFallsBackToGeneric() {
        val f = factory(emptyList())
        assertIs<GenericProfile>(f.profile("com.unknown.app"))
        assertSame(f.profile("com.unknown.app"), f.profile("com.unknown.app"))
    }

    @Test
    fun registeredPackageResolvesAndCaches() {
        val profile = FakeProfile(setOf("com.app.one"))
        val f = factory(listOf(profile))
        assertSame(profile, f.profile("com.app.one"))
    }

    @Test
    fun sameIdGroupIsReplacedByProfile() {
        val pack = listOf(group("g1", name = "pack"), group("g2", order = 1))
        val profile = FakeProfile(setOf("com.app.one"), groups = listOf(group("g1", name = "profile")))
        val merged = factory(listOf(profile), mapOf("com.app.one" to pack)).mergedGroups("com.app.one")
        assertEquals(2, merged.size)
        assertEquals("profile", merged.first { it.id == "g1" }.name)
    }

    @Test
    fun mergeOrderIsStablePackBeforeProfileBeforeId() {
        val pack = listOf(group("a", order = 1), group("c", order = 2))
        val profile = FakeProfile(setOf("com.app.one"), groups = listOf(group("b", order = 1)))
        val merged = factory(listOf(profile), mapOf("com.app.one" to pack)).mergedGroups("com.app.one")
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun disabledProfileGroupsAreSkippedAndKeywordsExposed() {
        val profile = FakeProfile(
            setOf("com.app.one"),
            groups = listOf(group("on"), group("off", enabled = false)),
            keywords = listOf("进入微信"),
        )
        val f = factory(listOf(profile))
        assertEquals(listOf("on"), f.mergedGroups("com.app.one").map { it.id })
        assertEquals(listOf("进入微信"), f.profile("com.app.one").keywordExtra())
    }

    @Test
    fun duplicatePackageRegistrationFailsFast() {
        val a = FakeProfile(setOf("com.dup"))
        val b = FakeProfile(setOf("com.dup"))
        val e = assertFailsWith<IllegalStateException> { AdProfileRegistry(setOf(a, b)) }
        assertTrue(e.message!!.contains("com.dup"))
    }
}
