package com.wavexzore.toolbox.core.selector

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NodeFilters 单测（R-209）：两条链过滤语义 + 宽容器边界。 */
class NodeFiltersTest {

    private fun node(json: String) = SnapshotTree.fromJson(json)

    @Test
    fun invisibleEditableScrollableAreRejected() {
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"visible":false}"""), 1080))
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"editable":true}"""), 1080))
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"scrollable":true}"""), 1080))
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"bounds":[0,0,0,0]}"""), 1080))
        assertTrue(NodeFilters.passesKeywordCandidate(node("""{"text":"跳过","bounds":[0,0,100,50]}"""), 1080))
        // B-205：零高但可点击且非全退化（实测 Compose 按钮 height=0）→ 放行
        assertTrue(
            NodeFilters.passesKeywordCandidate(
                node("""{"text":"关闭","clickable":true,"bounds":[1077,2564,1179,2564]}"""), 1080,
            ),
        )
        // 同类零高但不可点击 → 仍剔除
        assertFalse(
            NodeFilters.passesKeywordCandidate(
                node("""{"text":"关闭","clickable":false,"bounds":[1077,2564,1179,2564]}"""), 1080,
            ),
        )
    }

    @Test
    fun wideContainerWithoutTextIsRejected() {
        // 宽度 == 648（1080*0.6 边界，统一为 >=）
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"bounds":[0,0,648,100]}"""), 1080))
        assertFalse(NodeFilters.passesKeywordCandidate(node("""{"bounds":[0,0,700,100]}"""), 1080))
        assertTrue(NodeFilters.passesKeywordCandidate(node("""{"text":"跳过","bounds":[0,0,700,100]}"""), 1080))
        // 屏幕宽未知（<=0）时不判定宽容器
        assertTrue(NodeFilters.passesKeywordCandidate(node("""{"bounds":[0,0,700,100]}"""), 0))
    }

    @Test
    fun performTargetFilters() {
        assertTrue(NodeFilters.isFilteredPerformTarget(node("""{"editable":true}"""), 1080))
        assertTrue(NodeFilters.isFilteredPerformTarget(node("""{"class":"android.widget.Switch"}"""), 1080))
        assertTrue(
            NodeFilters.isFilteredPerformTarget(node("""{"bounds":[0,0,700,100]}"""), 1080),
        )
        // 有 desc 的宽容器不算过滤
        assertFalse(
            NodeFilters.isFilteredPerformTarget(node("""{"desc":"关闭","bounds":[0,0,700,100]}"""), 1080),
        )
        assertFalse(NodeFilters.isFilteredPerformTarget(node("""{"text":"跳过","bounds":[0,0,100,50]}"""), 1080))
    }
}
