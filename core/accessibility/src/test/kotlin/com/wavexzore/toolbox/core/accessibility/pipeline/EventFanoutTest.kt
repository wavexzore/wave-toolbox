package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EventFanoutTest {

    @Test
    fun `slow subscriber only drops its own oldest events`() = runTest {
        val fanout = EventFanout<Int>(capacity = 2)
        val fast = mutableListOf<Int>()
        val slow = mutableListOf<Int>()
        val fastJob = launch(start = CoroutineStart.UNDISPATCHED) {
            fanout.events.collect { fast += it }
        }
        val slowJob = launch(start = CoroutineStart.UNDISPATCHED) {
            fanout.events.collect {
                delay(1_000)
                slow += it
            }
        }
        assertEquals(2, fanout.subscriberCount())

        fanout.publish(1)
        fanout.publish(2)
        runCurrent()
        fanout.publish(3)
        fanout.publish(4)
        runCurrent()

        // 快订阅者不受慢订阅者影响，完整收到 4 条。
        assertEquals(listOf(1, 2, 3, 4), fast)
        // 慢订阅者队列容量 2：3 被 4 挤掉，只计数自己这一条。
        assertEquals(1, fanout.droppedCount())

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(1, 3, 4), slow)
        fastJob.cancel()
        slowJob.cancel()
    }

    @Test
    fun `collector unregisters when flow completes`() = runTest {
        val fanout = EventFanout<Int>(capacity = 2)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { fanout.events.first() }
        assertEquals(1, fanout.subscriberCount())

        fanout.publish(7)
        runCurrent()
        job.join()
        assertEquals(0, fanout.subscriberCount())

        // 无订阅者时发布不计溢出。
        fanout.publish(8)
        assertEquals(0, fanout.droppedCount())
    }

    @Test
    fun `publishing without subscribers is cheap and uncounted`() {
        val fanout = EventFanout<Int>(capacity = 2)
        repeat(100) { fanout.publish(it) }
        assertEquals(0, fanout.subscriberCount())
        assertEquals(0, fanout.droppedCount())
    }
}
