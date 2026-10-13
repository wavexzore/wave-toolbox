package com.wavexzore.toolbox.core.automation

/** 任务/步骤进度（StateFlow 友好；[TaskExecutor.run] 的流元素）。 */
sealed interface TaskProgress {

    data class Started(val taskId: String, val totalSteps: Int) : TaskProgress

    data class StepStarted(val index: Int, val step: Step) : TaskProgress

    data class StepFinished(
        val index: Int,
        val ok: Boolean,
        val skipped: Boolean = false,
        val reason: String? = null,
    ) : TaskProgress

    data class Finished(val status: Status, val reason: String? = null) : TaskProgress

    enum class Status { SUCCESS, FAILED, CANCELLED }
}
