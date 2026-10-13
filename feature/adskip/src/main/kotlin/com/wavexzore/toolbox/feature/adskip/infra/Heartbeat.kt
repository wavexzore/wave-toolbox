package com.wavexzore.toolbox.feature.adskip.infra

/**
 * 心跳节拍（T-203；Q-211）：入口窗口内固定间隔的单次尝试计划（纯计算，便于单测）。
 *
 * 语义：首拍 [firstMs] 后触发，此后每 [intervalMs] 一拍；到达 [windowMs] 边界即停。
 */
class Heartbeat(
    private val firstMs: Long = 500L,
    private val intervalMs: Long = 750L,
) {

    /** 第 [tick] 拍（1-based）的计划时刻。 */
    fun dueAt(startAt: Long, tick: Int): Long = startAt + firstMs + (tick - 1).toLong() * intervalMs

    /** 是否仍在心跳窗口内（含边界）。 */
    fun withinWindow(startAt: Long, now: Long, windowMs: Long): Boolean = now - startAt <= windowMs
}
