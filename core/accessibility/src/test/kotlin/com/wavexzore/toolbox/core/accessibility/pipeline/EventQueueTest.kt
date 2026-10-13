package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class EventQueueTest {

    @Test
    fun `drops oldest when over capacity`() = runBlocking {
        val queue = EventQueue(capacity = 4)
        repeat(6) { i ->
            queue.offer(a11yEvent(text = "event$i"))
        }

        // 保留最新 4 个：event2..event5
        assertEquals("event2", queue.receive().text)
        assertEquals("event3", queue.receive().text)
        assertEquals("event4", queue.receive().text)
        assertEquals("event5", queue.receive().text)
    }

    @Test
    fun `offer then receive preserves order`() = runBlocking {
        val queue = EventQueue(capacity = 8)
        queue.offer(a11yEvent(text = "a"))
        queue.offer(a11yEvent(text = "b"))

        assertEquals("a", queue.receive().text)
        assertEquals("b", queue.receive().text)
    }
}
