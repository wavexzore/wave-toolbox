package com.wavexzore.toolbox.core.accessibility.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ClickVerifierTest {

    @Test
    fun `retries while attempts remain and node still present`() {
        assertTrue(ClickVerifier.shouldRetry(attemptsUsed = 1, maxAttempts = 2, nodeStillPresent = true))
    }

    @Test
    fun `no retry when node gone or attempts exhausted`() {
        assertFalse(ClickVerifier.shouldRetry(attemptsUsed = 1, maxAttempts = 2, nodeStillPresent = false))
        assertFalse(ClickVerifier.shouldRetry(attemptsUsed = 2, maxAttempts = 2, nodeStillPresent = true))
    }

    @Test
    fun `signature stable for identical node`() {
        val a = A11yTestTrees.node(text = "跳过", cls = "android.widget.TextView")
        val b = A11yTestTrees.node(text = "跳过", cls = "android.widget.TextView")
        assertEquals(ClickVerifier.signature(a), ClickVerifier.signature(b))
    }

    @Test
    fun `signature changes on bounds but ignores volatile text`() {
        val a = A11yTestTrees.node(text = "跳过 5")
        val moved = A11yTestTrees.node(text = "跳过 5", left = 5)
        val countdown = A11yTestTrees.node(text = "跳过 4")
        assertNotEquals(ClickVerifier.signature(a), ClickVerifier.signature(moved))
        assertEquals(ClickVerifier.signature(a), ClickVerifier.signature(countdown))
    }

    @Test
    fun `signature changes on class change`() {
        val a = A11yTestTrees.node(cls = "android.widget.TextView")
        val changed = A11yTestTrees.node(cls = "android.widget.Button")
        assertNotEquals(ClickVerifier.signature(a), ClickVerifier.signature(changed))
    }
}
