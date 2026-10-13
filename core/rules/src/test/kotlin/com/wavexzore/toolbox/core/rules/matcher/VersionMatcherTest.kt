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
    fun `null version fail open`() {
        assertTrue(VersionMatcher.allows(VersionRange(min = 10), null))
        assertTrue(VersionMatcher.allowsVersionName(VersionRange(min = "2.0"), null))
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
}
