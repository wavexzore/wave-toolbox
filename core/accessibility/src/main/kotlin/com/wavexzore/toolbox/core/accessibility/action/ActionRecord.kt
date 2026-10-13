package com.wavexzore.toolbox.core.accessibility.action

/** 单条动作台账（UI 展示；结构稳定，导出诊断复用）。 */
data class ActionRecord(
    val seq: Long,
    val timeMillis: Long,
    val kind: Kind,
    val ruleId: String,
    val packageName: String?,
    val mechanism: String?,
    val ok: Boolean,
    val detail: String?,
) {
    enum class Kind { ACTION, VERIFY_RETRY, DENY, MISS }
}
