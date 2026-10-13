package com.wavexzore.toolbox.core.rules.matcher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityMatcherTest {

    @Test
    fun `empty list allows any activity`() {
        assertTrue(ActivityMatcher.allows(emptyList(), activity = "com.x.Any"))
    }

    @Test
    fun `exact match`() {
        assertTrue(ActivityMatcher.allows(listOf("com.x.Splash"), activity = "com.x.Splash"))
    }

    @Test
    fun `suffix match with dot boundary`() {
        assertTrue(ActivityMatcher.allows(listOf("Splash"), activity = "com.x.y.Splash"))
        assertTrue(ActivityMatcher.allows(listOf(".Splash"), activity = "com.x.y.Splash"))
        assertFalse(ActivityMatcher.allows(listOf("Splash"), activity = "com.x.y.SplashActivity"))
    }

    @Test
    fun `exclude wins`() {
        assertFalse(
            ActivityMatcher.allows(
                activityIds = listOf("Main"),
                excludeActivityIds = listOf("com.x.Main"),
                activity = "com.x.Main",
            ),
        )
    }

    @Test
    fun `null activity requires empty ids`() {
        assertTrue(ActivityMatcher.allows(emptyList(), activity = null))
        assertFalse(ActivityMatcher.allows(listOf("X"), activity = null))
    }

    @Test
    fun `no hit`() {
        assertFalse(ActivityMatcher.allows(listOf("A", "B"), activity = "com.x.C"))
    }
}
