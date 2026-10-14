package com.wavexzore.toolbox.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * [graceNeeds] 瞬断吸收语义：
 * - NEEDS 需持续窗口时长才外显；
 * - 窗口内恢复则完全不外显（不闪「需要修复」）；
 * - OK/UNAUTHORIZED 即时透传，挂起中的 NEEDS 可被取消。
 */
class GraceNeedsTest {

    @Test
    fun `窗口内恢复的瞬断 NEEDS 不外显`() = runTest {
        val src = flow {
            emit(SelfHealState.OK)
            delay(100)
            emit(SelfHealState.NEEDS_REPAIR)
            delay(1_000)
            emit(SelfHealState.OK)
        }
        val collected = src.graceNeeds(3_000).toList()
        assertEquals(listOf(SelfHealState.OK, SelfHealState.OK), collected)
    }

    @Test
    fun `持续 NEEDS 在窗口结束后外显一次`() = runTest {
        val src = flow {
            emit(SelfHealState.OK)
            delay(100)
            emit(SelfHealState.NEEDS_REPAIR)
        }
        val collected = src.graceNeeds(3_000).toList()
        assertEquals(listOf(SelfHealState.OK, SelfHealState.NEEDS_REPAIR), collected)
        assertEquals(3_100L, testScheduler.currentTime)
    }

    @Test
    fun `恢复侧 OK 与 UNAUTHORIZED 即时透传`() = runTest {
        val src = flowOf(SelfHealState.OK, SelfHealState.UNAUTHORIZED, SelfHealState.OK)
        val collected = src.graceNeeds(3_000).toList()
        assertEquals(
            listOf(SelfHealState.OK, SelfHealState.UNAUTHORIZED, SelfHealState.OK),
            collected,
        )
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `挂起中的 NEEDS 被 UNAUTHORIZED 取消`() = runTest {
        val src = flow {
            emit(SelfHealState.NEEDS_REPAIR)
            delay(500)
            emit(SelfHealState.UNAUTHORIZED)
        }
        val collected = src.graceNeeds(3_000).toList()
        assertEquals(listOf(SelfHealState.UNAUTHORIZED), collected)
    }
}
