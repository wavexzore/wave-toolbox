package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventFilterTest {

    private val filter = EventFilter()
    private val self = "com.wavexzore.toolbox"

    private fun evaluate(
        event: A11yEvent,
        engineEnabled: Boolean = true,
        allowPackages: Set<String> = emptySet(),
    ) = filter.shouldAccept(event, self, engineEnabled, allowPackages)

    @Test
    fun `accepts normal event`() {
        assertNull(evaluate(a11yEvent()))
    }

    @Test
    fun `drops when engine disabled`() {
        assertEquals(DropReason.ENGINE_DISABLED, evaluate(a11yEvent(), engineEnabled = false))
    }

    @Test
    fun `drops self package events`() {
        assertEquals(DropReason.SELF_PACKAGE, evaluate(a11yEvent(packageName = self)))
    }

    @Test
    fun `drops secondary display events`() {
        assertEquals(DropReason.SECONDARY_DISPLAY, evaluate(a11yEvent(displayId = 1)))
    }

    @Test
    fun `drops scrolled noise`() {
        assertEquals(
            DropReason.SCROLLED_NOISE,
            evaluate(a11yEvent(type = A11yEventType.VIEW_SCROLLED)),
        )
    }

    @Test
    fun `drops package not in allow list`() {
        assertEquals(
            DropReason.NOT_ALLOWED,
            evaluate(a11yEvent(packageName = "com.other"), allowPackages = setOf("com.allowed")),
        )
    }

    @Test
    fun `accepts package in allow list`() {
        assertNull(
            evaluate(a11yEvent(packageName = "com.allowed"), allowPackages = setOf("com.allowed")),
        )
    }

    @Test
    fun `null package bypasses allow list`() {
        assertNull(
            evaluate(
                a11yEvent(type = A11yEventType.WINDOWS_CHANGED, packageName = null),
                allowPackages = setOf("com.allowed"),
            ),
        )
    }
}
