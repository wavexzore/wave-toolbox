package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentThrottlerTest {

    private val throttler = ContentThrottler()

    @Test
    fun `content changed within window is throttled`() {
        val event = a11yEvent(type = A11yEventType.WINDOW_CONTENT_CHANGED, windowId = 7)

        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_000))
        assertFalse(throttler.shouldDispatch(event, throttleMs = 100, now = 1_050))
        assertFalse(throttler.shouldDispatch(event, throttleMs = 100, now = 1_099))
        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_100))
    }

    @Test
    fun `window state changed is never throttled`() {
        val event = a11yEvent(type = A11yEventType.WINDOW_STATE_CHANGED, windowId = 7)

        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_000))
        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_001))
        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_002))
    }

    @Test
    fun `windows changed is never throttled`() {
        val event = a11yEvent(type = A11yEventType.WINDOWS_CHANGED, windowId = 7)

        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_000))
        assertTrue(throttler.shouldDispatch(event, throttleMs = 100, now = 1_010))
    }

    @Test
    fun `different windows are independent`() {
        val w1 = a11yEvent(windowId = 1)
        val w2 = a11yEvent(windowId = 2)

        assertTrue(throttler.shouldDispatch(w1, throttleMs = 100, now = 1_000))
        assertTrue(throttler.shouldDispatch(w2, throttleMs = 100, now = 1_010))
        assertFalse(throttler.shouldDispatch(w1, throttleMs = 100, now = 1_020))
    }

    @Test
    fun `different packages are independent`() {
        val p1 = a11yEvent(packageName = "com.a", windowId = 1)
        val p2 = a11yEvent(packageName = "com.b", windowId = 1)

        assertTrue(throttler.shouldDispatch(p1, throttleMs = 100, now = 1_000))
        assertTrue(throttler.shouldDispatch(p2, throttleMs = 100, now = 1_010))
        assertFalse(throttler.shouldDispatch(p1, throttleMs = 100, now = 1_020))
    }
}
