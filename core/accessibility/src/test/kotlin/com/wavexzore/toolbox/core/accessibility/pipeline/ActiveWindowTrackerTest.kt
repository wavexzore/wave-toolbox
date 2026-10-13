package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveWindowTrackerTest {

    private val tracker = ActiveWindowTracker()

    @Test
    fun `state changed updates active window`() {
        tracker.onEvent(
            a11yEvent(
                type = A11yEventType.WINDOW_STATE_CHANGED,
                packageName = "com.example.app",
                className = "com.example.app.Main",
                windowId = 42,
            ),
            now = 1_000,
        )

        val window = tracker.current(toleranceMs = 5_000, now = 3_000)
        assertEquals("com.example.app", window?.packageName)
        assertEquals(42, window?.windowId)
    }

    @Test
    fun `expired window returns null`() {
        tracker.onEvent(a11yEvent(type = A11yEventType.WINDOW_STATE_CHANGED), now = 1_000)

        assertNull(tracker.current(toleranceMs = 5_000, now = 6_001))
    }

    @Test
    fun `content changed does not update active window`() {
        tracker.onEvent(a11yEvent(type = A11yEventType.WINDOW_CONTENT_CHANGED), now = 1_000)

        assertNull(tracker.current(toleranceMs = 5_000, now = 1_100))
    }

    @Test
    fun `latest state changed wins`() {
        tracker.onEvent(a11yEvent(type = A11yEventType.WINDOW_STATE_CHANGED, windowId = 1), now = 1_000)
        tracker.onEvent(a11yEvent(type = A11yEventType.WINDOW_STATE_CHANGED, windowId = 2), now = 2_000)

        assertEquals(2, tracker.current(toleranceMs = 5_000, now = 2_100)?.windowId)
    }
}
