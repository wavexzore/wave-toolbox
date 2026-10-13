package com.wavexzore.toolbox.core.accessibility.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionLedgerTest {

    private fun ledgerWithWindow(
        pkg: String = "com.a",
        cls: String = "com.a.Main",
        now: Long = 0L,
    ): ActionLedger = ActionLedger().also { it.onWindow(pkg, cls, activityLike = true, now = now) }

    @Test
    fun `activity switch after window passes resets counts`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertEquals(1, ledger.countOf("r1"))
        // 距上次切换 > CARRYOVER_MS：真实导航，重置
        assertTrue(ledger.onWindow("com.a", "com.a.Detail", activityLike = true, now = 5_000))
        assertEquals(2L, ledger.instance)
        assertEquals(0, ledger.countOf("r1"))
        assertEquals("com.a.Detail", ledger.activity)
    }

    @Test
    fun `same activity key is not a new instance`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertFalse(ledger.onWindow("com.a", "com.a.Main", activityLike = true, now = 1_100))
        assertEquals(1L, ledger.instance)
        assertEquals(1, ledger.countOf("r1"))
    }

    @Test
    fun `rapid switch carries counts`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 100)
        // 距离上次切换 ≤ CARRYOVER_MS：视为同一页面链（内容事件先于窗口事件），保留
        assertTrue(ledger.onWindow("com.a", "com.a.Main2", activityLike = true, now = 500))
        assertEquals(1, ledger.countOf("r1"))
        // 再慢速切换（> CARRYOVER_MS）则重置
        assertTrue(ledger.onWindow("com.a", "com.a.Main3", activityLike = true, now = 3_000))
        assertEquals(0, ledger.countOf("r1"))
    }

    @Test
    fun `returning to a page after window passes is a new instance`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.onWindow("com.a", "com.a.Detail", activityLike = true, now = 5_000)
        ledger.markExecuted("r1", 6_000)
        assertTrue(ledger.onWindow("com.a", "com.a.Main", activityLike = true, now = 10_000))
        assertEquals(0, ledger.countOf("r1"))
    }

    @Test
    fun `non activity event for same package does not reset`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertFalse(ledger.onWindow("com.a", "android.widget.FrameLayout", activityLike = false, now = 1_100))
        assertEquals(1L, ledger.instance)
        assertEquals(1, ledger.countOf("r1"))
        assertEquals("com.a.Main", ledger.activity)
    }

    @Test
    fun `placeholder upgrade to activity does not reset`() {
        val ledger = ActionLedger()
        assertTrue(ledger.onWindow("com.a", null, activityLike = false, now = 0))
        ledger.markExecuted("r1", 100)
        assertFalse(ledger.onWindow("com.a", "com.a.Main", activityLike = true, now = 300))
        assertEquals(1L, ledger.instance)
        assertEquals(1, ledger.countOf("r1"))
        assertEquals("com.a.Main", ledger.activity)
    }

    @Test
    fun `non activity event for new package after window passes resets`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertTrue(ledger.onWindow("com.b", null, activityLike = false, now = 5_000))
        assertEquals(0, ledger.countOf("r1"))
        assertEquals(2L, ledger.instance)
    }

    @Test
    fun `cooldown denies until window passes`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        val denied = ledger.decision("r1", cooldownMs = 500, actionMaximum = null, now = 1_200)
        assertFalse(denied.allowed)
        assertEquals(ActionLedger.DenyReason.COOLDOWN, denied.reason)
        assertTrue(ledger.decision("r1", 500, null, 1_500).allowed)
    }

    @Test
    fun `zero cooldown always allows`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertTrue(ledger.decision("r1", 0, null, 1_000).allowed)
    }

    @Test
    fun `action maximum denies after limit`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        val denied = ledger.decision("r1", 0, actionMaximum = 1, now = 2_000)
        assertFalse(denied.allowed)
        assertEquals(ActionLedger.DenyReason.MAX_EXCEEDED, denied.reason)
        assertEquals(1, denied.count)
    }

    @Test
    fun `null maximum never denies`() {
        val ledger = ledgerWithWindow(now = 0)
        repeat(10) { ledger.markExecuted("r1", 1_000L + it) }
        assertTrue(ledger.decision("r1", 0, null, 3_000).allowed)
    }

    @Test
    fun `counters are per rule`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        assertEquals(1, ledger.countOf("r1"))
        assertEquals(0, ledger.countOf("r2"))
        val decision = ledger.decision("r2", 0, actionMaximum = 1, now = 2_000)
        assertTrue(decision.allowed)
        assertEquals(0, decision.count)
    }

    @Test
    fun `reset clears all state`() {
        val ledger = ledgerWithWindow(now = 0)
        ledger.markExecuted("r1", 1_000)
        ledger.resetAll()
        assertEquals(0, ledger.countOf("r1"))
        assertTrue(ledger.decision("r1", 500, null, 1_010).allowed)
    }
}
