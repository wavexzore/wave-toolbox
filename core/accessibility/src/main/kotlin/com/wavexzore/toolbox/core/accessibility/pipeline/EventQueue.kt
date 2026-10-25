package com.wavexzore.toolbox.core.accessibility.pipeline

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel

/**
 * 事件入队缓冲：容量上限 + 丢最旧（积压时保留"当前"状态）。
 *
 * 不用 `Channel(DROP_OLDEST)`：那种模式下 `trySend` 永远成功，无法观测溢出。
 * 这里用短临界区的双端队列显式淘汰最旧事件，并累计 [droppedCount] 供诊断。
 */
class EventQueue(capacity: Int = DEFAULT_CAPACITY) {

    private val capacity = capacity.coerceAtLeast(1)
    private val lock = Any()
    private val buffer = ArrayDeque<A11yEvent>(this.capacity)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val dropped = AtomicLong()

    fun offer(event: A11yEvent): Boolean {
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
                dropped.incrementAndGet()
            }
            buffer.addLast(event)
        }
        signal.trySend(Unit)
        return true
    }

    suspend fun receive(): A11yEvent {
        while (true) {
            synchronized(lock) { buffer.removeFirstOrNull() }?.let { return it }
            signal.receive()
        }
    }

    /** 因容量上限被淘汰的事件数（不包含 filter/throttle 丢弃）。 */
    fun droppedCount(): Long = dropped.get()

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
