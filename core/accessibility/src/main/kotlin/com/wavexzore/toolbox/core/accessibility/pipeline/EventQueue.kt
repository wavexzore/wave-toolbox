package com.wavexzore.toolbox.core.accessibility.pipeline

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * 事件入队缓冲：容量上限 + DROP_OLDEST（积压时丢最旧，保证消费的是"当前"状态）。
 */
class EventQueue(capacity: Int = DEFAULT_CAPACITY) {

    private val channel = Channel<A11yEvent>(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun offer(event: A11yEvent): Boolean = channel.trySend(event).isSuccess

    suspend fun receive(): A11yEvent = channel.receive()

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
