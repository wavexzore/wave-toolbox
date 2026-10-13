package com.wavexzore.toolbox.core.accessibility.pipeline

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ANR 守卫（worktool 方案移植）：系统"应用无响应"对话框出现时自动点「等待」。
 * 检测口径：包名 `android` 的对话框窗口 + 根节点文本含"无响应/未响应/isn't responding" + 存在"等待/Wait"按钮。
 */
@Singleton
class AnrGuard @Inject constructor() {

    private var lastHandleAt = 0L

    fun onEvent(event: A11yEvent) {
        if (event.packageName != SYSTEM_PACKAGE) return
        val now = System.currentTimeMillis()
        if (now - lastHandleAt < COOLDOWN_MS) return

        val service = SelectToSpeakService.instance ?: return
        val root = service.rootInActiveWindow ?: return

        val texts = ArrayList<String>(96)
        collectTexts(root, texts, limit = 200)
        val looksLikeAnr = texts.any { text ->
            ANR_MARKERS.any { text.contains(it) }
        }
        if (!looksLikeAnr) return

        val waitButton = findFirstByTexts(root, WAIT_TEXTS, limit = 200) ?: return
        val clicked = waitButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        lastHandleAt = now
        Log.i(TAG, "ANR dialog detected, clicked '${waitButton.text}', ok=$clicked")
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, limit: Int) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(node)
        while (stack.isNotEmpty() && out.size < limit) {
            val current = stack.removeLast()
            current.text?.let { out.add(it.toString()) }
            current.contentDescription?.let { out.add(it.toString()) }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
    }

    private fun findFirstByTexts(
        node: AccessibilityNodeInfo,
        candidates: List<String>,
        limit: Int,
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(node)
        var visited = 0
        while (stack.isNotEmpty() && visited < limit) {
            val current = stack.removeLast()
            visited++
            val text = current.text?.toString() ?: current.contentDescription?.toString()
            if (text != null && text in candidates && current.isClickable) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    companion object {
        const val TAG = "WaveA11y.AnrGuard"
        private const val SYSTEM_PACKAGE = "android"
        private const val COOLDOWN_MS = 1_500L
        private val ANR_MARKERS = listOf("无响应", "未响应", "isn't responding", "is not responding")
        private val WAIT_TEXTS = listOf("等待", "Wait")
    }
}
