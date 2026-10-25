package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.NodeView

/**
 * 点击结果校验（纯逻辑）：动作后 [verifyMs] 复核，原节点特征仍在且未达重试上限 → 重试一次。
 */
object ClickVerifier {

    /** 校验窗口后是否重试。`attemptsUsed` 为已执行次数，`maxAttempts` 含首次执行。 */
    fun shouldRetry(attemptsUsed: Int, maxAttempts: Int, nodeStillPresent: Boolean): Boolean =
        nodeStillPresent && attemptsUsed < maxAttempts

    /**
     * 节点特征签名（同一性近似判定）：bounds + desc + class。
     *
     * 不包含 text：倒计时/秒数等文本会随时间变化，若纳入签名会把「仍在原地的节点」
     * 误判为已消失，从而放弃 350ms 补点。
     */
    fun signature(node: NodeView): String {
        val b = node.bounds
        return "${b.left},${b.top},${b.right},${b.bottom}|${node.contentDescription.orEmpty()}|${node.className.orEmpty()}"
    }
}
