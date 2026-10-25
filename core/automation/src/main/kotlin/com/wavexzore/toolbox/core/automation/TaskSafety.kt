package com.wavexzore.toolbox.core.automation

/**
 * 任务安全上限（内部验证版）：解析器拒绝越界定义，执行器再做一次硬收敛。
 * 目的是防止资产/调试入口误配置导致长时间驻留、无限等待或连点。
 */
object TaskSafety {
    const val MAX_STEPS = 30
    const val MIN_TASK_DURATION_MS = 1_000L
    const val MAX_TASK_DURATION_MS = 120_000L
    const val MIN_STEP_TIMEOUT_MS = 100L
    const val MAX_STEP_TIMEOUT_MS = 15_000L
    const val MAX_DELAY_MS = 10_000L
    const val MAX_CLICK_RETRY = 3
    const val MAX_BACK_TIMES = 5
    const val MAX_SCROLL_TIMES = 30
}
