package com.wavexzore.toolbox.core.accessibility.pipeline

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 多订阅者事件扇出（替代共享缓冲的 `SharedFlow`）。
 *
 * 每个订阅者持有独立有界队列：
 * - 慢订阅者队列满时只淘汰自己的最旧事件，并计入 [droppedCount]，不拖累其它订阅者
 *   （共享 `SharedFlow` 的下游缓冲溢出无法逐条归属，也无法统计）；
 * - 发布方可从事件管线单线程调用 [publish]，全程 `trySend`，不阻塞系统回调；
 * - [events] 是冷流，每次收集注册一个订阅者，收集结束自动注销。
 */
class EventFanout<T>(capacity: Int = DEFAULT_CAPACITY) {

    private class Subscriber<T>(capacity: Int) {
        val channel = Channel<T>(capacity)
        val dropped = AtomicLong()
    }

    private val bufferCapacity = capacity.coerceAtLeast(1)
    private val subscribers = CopyOnWriteArrayList<Subscriber<T>>()
    private val dropped = AtomicLong()

    /** 每个收集者独立订阅；下游变慢只会丢自己的事件。 */
    val events: Flow<T> = flow {
        val subscriber = Subscriber<T>(bufferCapacity)
        subscribers += subscriber
        try {
            for (event in subscriber.channel) emit(event)
        } finally {
            subscribers -= subscriber
            subscriber.channel.close()
        }
    }

    /**
     * 非阻塞发布：队列满时淘汰该订阅者最旧一条（保持「保留最新状态」语义）并计数。
     * 必须在单线程发布（[EventPipeline] 的消费线程）下调用。
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun publish(event: T) {
        for (subscriber in subscribers) {
            if (subscriber.channel.isClosedForSend) continue
            if (subscriber.channel.trySend(event).isSuccess) continue
            // 消费者可能刚好腾出空位：再试一次，避免多丢一条。
            if (subscriber.channel.trySend(event).isSuccess) continue
            if (subscriber.channel.tryReceive().isSuccess) {
                subscriber.dropped.incrementAndGet()
                dropped.incrementAndGet()
            }
            subscriber.channel.trySend(event)
        }
    }

    /** 所有订阅者累计被淘汰的事件数。 */
    fun droppedCount(): Long = dropped.get()

    /** 当前活跃订阅者数（诊断用）。 */
    fun subscriberCount(): Int = subscribers.size

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
