package com.wavexzore.toolbox.core.accessibility.pipeline

import android.content.Context
import android.util.Log
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * 事件管线（T-004）：offer → 过滤 → 节流 → 单线程消费 → 广播 + 日志环。
 *
 * - 消费固定在 `AppDispatchers.event` 单线程（D-006），下游（规则/任务）串行拿到事件；
 * - 任一环节异常不回抛到系统（服务侧 try/catch + 消费侧兜底），防 ANR；
 * - 设置（引擎开关/节流毫秒/白名单）热更新，立即生效；
 * - `recent`/`stats` 对 UI 以 [UI_FLUSH_INTERVAL_MS] 合并发布：日志页自刷新不会引发
 *   "重组→窗口 CONTENT_CHANGED→重组"的帧级反馈环（实测可将自洪泛从 ~370/s 降至低位）。
 */
@Singleton
class EventPipeline @Inject constructor(
    @ApplicationContext context: Context,
    dispatchers: AppDispatchers,
    settingsRepository: SettingsRepository,
) {

    data class RecentEvent(
        val seq: Long,
        val timeMillis: Long,
        val type: A11yEventType,
        val packageName: String?,
        val className: String?,
        val text: String?,
    )

    data class Stats(
        val received: Long = 0,
        val dispatched: Long = 0,
        val dropped: Long = 0,
        val throttled: Long = 0,
    )

    private val selfPackage = context.packageName
    private val consumeScope = CoroutineScope(SupervisorJob() + dispatchers.event)
    private val settingsScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val queue = EventQueue()

    private val _events = MutableSharedFlow<A11yEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 已过滤 + 已节流后的可用事件流（T-006 规则、AnrGuard 等消费）。 */
    val events: SharedFlow<A11yEvent> = _events.asSharedFlow()

    private val _recent = MutableStateFlow<List<RecentEvent>>(emptyList())

    /** 最近事件环（UI 事件日志页；150ms 合并发布）。 */
    val recent: StateFlow<List<RecentEvent>> = _recent

    private val _stats = MutableStateFlow(Stats())

    /** 管线计数（150ms 合并发布）。 */
    val stats: StateFlow<Stats> = _stats

    private val filter = EventFilter()
    private val throttler = ContentThrottler()
    private val activeWindowTracker = ActiveWindowTracker()

    // 真实计数（原子，逐事件）；UI 侧走合并发布
    private val receivedCount = AtomicLong()
    private val dispatchedCount = AtomicLong()
    private val droppedCount = AtomicLong()
    private val throttledCount = AtomicLong()

    private val recentBuffer = ArrayDeque<RecentEvent>(LOG_LIMIT + 1)
    private var recentSeq = 0L
    private var lastFlushAt = 0L

    @Volatile
    private var engineEnabled = SettingsRepository.DEFAULT_ENGINE_ENABLED

    @Volatile
    private var throttleMs = SettingsRepository.DEFAULT_CONTENT_THROTTLE_MS.toLong()

    @Volatile
    private var allowPackages: Set<String> = emptySet()

    @Volatile
    private var logEnabled: Boolean = SettingsRepository.DEFAULT_EVENT_RECORD

    init {
        settingsScope.launch {
            settingsRepository.engineEnabled.collect { engineEnabled = it }
        }
        settingsScope.launch {
            settingsRepository.contentThrottleMs.collect { throttleMs = it.toLong() }
        }
        settingsScope.launch {
            settingsRepository.allowPackages.collect { allowPackages = it }
        }
        settingsScope.launch {
            settingsRepository.eventRecordEnabled.collect { logEnabled = it }
        }
        consumeScope.launch { consumeLoop() }
    }

    /** 服务侧入口（必须廉价、不阻塞系统回调线程）。 */
    fun offer(event: A11yEvent) {
        receivedCount.incrementAndGet()
        queue.offer(event)
    }

    /** 最近的活动窗口（含时间戳容差），供上游低成本取"当前窗口"。 */
    suspend fun currentWindow(now: Long = System.currentTimeMillis()): ActiveWindowTracker.ActiveWindow? =
        activeWindowTracker.current(ActiveWindowTracker.DEFAULT_TOLERANCE_MS, now)

    /** 不经过 UI 合并的实时计数（诊断/压测）。 */
    fun liveStats(): Stats = Stats(
        received = receivedCount.get(),
        dispatched = dispatchedCount.get(),
        dropped = droppedCount.get(),
        throttled = throttledCount.get(),
    )

    fun clearLog() {
        recentBuffer.clear()
        _recent.value = emptyList()
    }

    private suspend fun consumeLoop() {
        while (true) {
            val event = queue.receive()
            try {
                handle(event)
            } catch (t: Throwable) {
                // ANR 守卫兜底：单个事件处理失败不影响后续
                Log.e(TAG, "handle failed: type=${event.type} pkg=${event.packageName}", t)
            }
        }
    }

    private fun handle(event: A11yEvent) {
        val now = System.currentTimeMillis()

        val reason = filter.shouldAccept(event, selfPackage, engineEnabled, allowPackages)
        if (reason != null) {
            droppedCount.incrementAndGet()
            if (reason != DropReason.SCROLLED_NOISE) {
                appendRecent(event.toRecent(now, reason), now)
            }
            maybeFlush(now)
            return
        }

        if (!throttler.shouldDispatch(event, throttleMs, now)) {
            throttledCount.incrementAndGet()
            maybeFlush(now)
            return
        }

        activeWindowTracker.onEvent(event, now)
        _events.tryEmit(event)
        appendRecent(event.toRecent(now, null), now)

        val dispatched = dispatchedCount.incrementAndGet()
        if (dispatched <= TRACE_DISPATCH_LIMIT) {
            Log.d(TAG, "dispatch#$dispatched pkg=${event.packageName} type=${event.type} cls=${event.className}")
        }
        if (dispatched % 500 == 0L) {
            Log.d(TAG, "pipeline stats=${liveStats()}")
        }
        maybeFlush(now)
    }

    private fun appendRecent(recent: RecentEvent, now: Long) {
        if (!logEnabled) return
        recentBuffer.addFirst(recent)
        while (recentBuffer.size > LOG_LIMIT) recentBuffer.removeLast()
        maybeFlush(now)
    }

    private fun maybeFlush(now: Long) {
        if (now - lastFlushAt < UI_FLUSH_INTERVAL_MS) return
        lastFlushAt = now
        _recent.value = recentBuffer.toList()
        _stats.value = liveStats()
    }

    private fun A11yEvent.toRecent(now: Long, reason: DropReason?): RecentEvent = RecentEvent(
        seq = ++recentSeq,
        timeMillis = if (eventTime > 0) eventTime else now,
        type = type,
        packageName = packageName,
        className = className?.substringAfterLast('.'),
        text = text?.take(64) ?: reason?.let { "[$it]" },
    )

    companion object {
        const val TAG = "WaveA11y.Pipeline"
        const val LOG_LIMIT = 100

        /** UI 合并发布间隔：过密会让"重组→窗口事件→重组"形成帧级反馈环。 */
        const val UI_FLUSH_INTERVAL_MS = 400L
        private const val TRACE_DISPATCH_LIMIT = 40L
    }
}
