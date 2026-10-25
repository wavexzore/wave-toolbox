package com.wavexzore.toolbox.core.rules.matcher

import com.wavexzore.toolbox.core.rules.model.VersionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionMatcherTest {

    @Test
    fun `null range or empty range allows`() {
        assertTrue(VersionMatcher.allows(null, 5))
        assertTrue(VersionMatcher.allows(VersionRange(), 5))
    }

    @Test
    fun `null version fails closed by default`() {
        assertFalse(VersionMatcher.allows(VersionRange(min = 10), null))
        assertFalse(VersionMatcher.allowsVersionName(VersionRange(min = "2.0"), null))
        assertFalse(VersionMatcher.allowsVersionName(VersionRange(min = "2.0"), ""))
        // 没有版本约束时，未知版本仍放行（约束本身为空）。
        assertTrue(VersionMatcher.allows(VersionRange(), null))
        assertTrue(VersionMatcher.allowsVersionName(VersionRange(), null))
    }

    @Test
    fun `null version can opt into fail open`() {
        val policy = VersionMatcher.MissingVersionPolicy.ALLOW
        assertTrue(VersionMatcher.allows(VersionRange(min = 10), null, policy))
        assertTrue(VersionMatcher.allowsVersionName(VersionRange(min = "2.0"), null, policy))
    }

    @Test
    fun `min and max bounds`() {
        val range = VersionRange(min = 10, max = 20)
        assertFalse(VersionMatcher.allows(range, 9))
        assertTrue(VersionMatcher.allows(range, 10))
        assertTrue(VersionMatcher.allows(range, 20))
        assertFalse(VersionMatcher.allows(range, 21))
    }

    @Test
    fun `include overrides bounds`() {
        val range = VersionRange(min = 100, include = listOf(5, 7))
        assertTrue(VersionMatcher.allows(range, 5))
        assertTrue(VersionMatcher.allows(range, 7))
        assertFalse(VersionMatcher.allows(range, 101))
    }

    @Test
    fun `exclude wins over include and bounds`() {
        val range = VersionRange(min = 1, max = 100, include = listOf(50), exclude = listOf(50))
        assertFalse(VersionMatcher.allows(range, 50))
    }

    @Test
    fun `version name semantic compare`() {
        assertTrue(VersionMatcher.compareVersionNames("1.10", "1.9") > 0)
        assertEquals(0, VersionMatcher.compareVersionNames("8.0.78", "8.0.78"))
        assertTrue(VersionMatcher.compareVersionNames("8.0", "8.0.1") < 0)
    }

    @Test
    fun `version name bounds`() {
        val range = VersionRange(min = "8.0", max = "9.0")
        assertTrue(VersionMatcher.allowsVersionName(range, "8.0"))
        assertTrue(VersionMatcher.allowsVersionName(range, "9.0"))
        assertFalse(VersionMatcher.allowsVersionName(range, "7.9"))
        assertFalse(VersionMatcher.allowsVersionName(range, "9.1"))
    }

    @Test
    fun `version name include overrides bounds and exclude wins`() {
        // 语义：include 非空时以 include 为准（覆盖 min/max）；exclude 优先于一切
        val range = VersionRange(min = "100.0", include = listOf("7.5"), exclude = listOf("8.5"))
        assertTrue(VersionMatcher.allowsVersionName(range, "7.5"))
        assertFalse(VersionMatcher.allowsVersionName(range, "8.5"))
        assertFalse(VersionMatcher.allowsVersionName(range, "8.0"))
        assertFalse(VersionMatcher.allowsVersionName(range, "101.0"))
    }

    @Test
    fun `non numeric suffix compare lexicographic`() {
        assertTrue(VersionMatcher.compareVersionNames("8.0.0-beta", "8.0.0-alpha") > 0)
    }

    @Test
    fun `prerelease is lower than release`() {
        assertTrue(VersionMatcher.compareVersionNames("1.0.0-rc.1", "1.0.0") < 0)
        assertTrue(VersionMatcher.compareVersionNames("1.0.0", "1.0.0-rc.1") > 0)
        assertEquals(0, VersionMatcher.compareVersionNames("1.0.0", "1.0"))
    }

    @Test
    fun `prerelease identifiers follow semver ordering`() {
        assertTrue(VersionMatcher.compareVersionNames("1.0.0-alpha", "1.0.0-beta") < 0)
        assertTrue(VersionMatcher.compareVersionNames("1.0.0-beta.2", "1.0.0-beta.11") < 0)
        assertTrue(VersionMatcher.compareVersionNames("1.0.0-alpha", "1.0.0-alpha.1") < 0)
        // 数字标识优先级低于字母标识。
        assertTrue(VersionMatcher.compareVersionNames("1.0.0-1", "1.0.0-alpha") < 0)
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(0, VersionMatcher.compareVersionNames("1.0.0+build.5", "1.0.0+build.1"))
        assertEquals(0, VersionMatcher.compareVersionNames("1.0.0-rc.1+sha.abc", "1.0.0-rc.1"))
    }

    @Test
    fun `v prefix and underscore separators are tolerated`() {
        assertEquals(0, VersionMatcher.compareVersionNames("v1.2.3", "1.2.3"))
        assertEquals(0, VersionMatcher.compareVersionNames("1_2_3", "1.2.3"))
    }
}
