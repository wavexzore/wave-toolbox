package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.feature.adskip.infra.AttemptScheduler
import com.wavexzore.toolbox.feature.adskip.infra.OemQuirks
import com.wavexzore.toolbox.feature.adskip.infra.PopupClassifier
import com.wavexzore.toolbox.feature.adskip.infra.SkipGate
import com.wavexzore.toolbox.feature.adskip.infra.WindowBounds
import com.wavexzore.toolbox.feature.adskip.infra.WindowTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-201 基础设施单测：OemQuirks / PopupClassifier / WindowTracker / AttemptScheduler / SkipGate。 */
class AdSkipInfraTest {

    private val screen = 1080 to 2400

    private fun classifier(
        quirks: OemQuirks = OemQuirks.DEFAULT,
        screen: Pair<Int, Int> = 1080 to 2400,
        bounds: (String) -> WindowBounds? = { null },
    ) = PopupClassifier(quirks, bounds, { screen })

    private var fakeNow = 0L
    private fun tracker(
        quirks: OemQuirks = OemQuirks.DEFAULT,
        bounds: (String) -> WindowBounds? = { Bounds.FULL },
    ) = WindowTracker(classifier(quirks = quirks, bounds = bounds), quirks) { fakeNow }

    private object Bounds {
        val FULL = WindowBounds(0, 0, 1080, 2400)
        /** F1 实测：全屏 dim 弹窗（宽 100%、高 92%）——几何判为弹窗（原先漏判的案例）。 */
        val DIM_DIALOG = WindowBounds(0, 144, 1080, 2260)
        /** F2 实测：MIUI 开屏动画等比缩放（315x685 / 1208x2624 ≈ 0.26）。 */
        val ANIM_SCALED = WindowBounds(280, 553, 595, 1238)
    }

    // ---- OemQuirks ----

    @Test
    fun miuiQuirksExposeNoiseAndHint() {
        val miui = OemQuirks.MIUI
        assertTrue(miui.isMiui)
        assertTrue(miui.matchesNoiseClass("androidx.viewpager.widget.ViewPager"))
        assertNotNull(miui.keepAliveHint)
        val def = OemQuirks.DEFAULT
        assertFalse(def.isMiui)
        assertTrue(def.noiseClassPatterns.isEmpty())
        assertNull(def.keepAliveHint)
        assertEquals(0.8f, def.animationScaleFloor)
    }

    // ---- PopupClassifier ----

    @Test
    fun fullWindowIsNotPopup() {
        assertFalse(classifier { Bounds.FULL }.isPopupWindow("p"))
    }

    @Test
    fun dimDialogWindowIsPopup() {
        assertTrue(classifier { Bounds.DIM_DIALOG }.isPopupWindow("p"))
    }

    @Test
    fun animationScaledWindowIsNotPopup() {
        assertTrue(classifier { Bounds.ANIM_SCALED }.isPopupWindow("p").not())
    }

    @Test
    fun missingBoundsIsNotPopup() {
        assertFalse(classifier { null }.isPopupWindow("p"))
    }

    @Test
    fun hyperOsLaunchAnimationWindowIsNotPopup() {
        // M154FF 实测启动动画帧：15,21,1193,2579 / 1208x2624（等比 0.975，内缩 ≤2%）
        val c = classifier(screen = 1208 to 2624) { WindowBounds(15, 21, 1193, 2579) }
        assertFalse(c.isPopupWindow("p"))
    }

    @Test
    fun nearFullInsetsWithinToleranceAreNotPopup() {
        val c = classifier { WindowBounds(10, 20, 1075, 2385) }
        assertFalse(c.isPopupWindow("p"))
    }

    @Test
    fun largeScaleUniformWindowIsNotPopup() {
        // 5% 内缩、等比 0.9/0.9（启动动画中段）
        val c = classifier { WindowBounds(54, 120, 1026, 2280) }
        assertFalse(c.isPopupWindow("p"))
    }

    @Test
    fun popupClassWhitelistOnlyDialogAndPopupWindow() {
        val c = classifier()
        assertTrue(c.isPopupWindowClass("android.app.Dialog"))
        assertTrue(c.isPopupWindowClass("android.widget.PopupWindow\$PopupDecorView"))
        assertFalse(c.isPopupWindowClass("androidx.viewpager.widget.ViewPager"))
        assertFalse(c.isPopupWindowClass("com.foo.MainActivity"))
        assertFalse(c.isPopupWindowClass(null))
    }

    // ---- WindowTracker ----

    @Test
    fun foregroundTransitionCreatesNewInstance() {
        val t = tracker()
        fakeNow = 100
        val m1 = t.onForeground("a", "com.a.MainActivity")
        fakeNow = 200
        t.onForeground("b", "com.b.MainActivity")
        fakeNow = 300
        val m2 = t.onForeground("a", "com.a.MainActivity")
        assertNotEquals(m1.instance, m2.instance)
    }

    @Test
    fun splashPageSwitchKeepsWindowFresh() {
        val t = tracker()
        fakeNow = 0
        val m = t.onForeground("a", "com.a.SplashActivity")
        fakeNow = 1000
        val updated = t.onWindowStateChanged("a", "com.a.MainActivity", AdSkipGroup.MODE_SPLASH, foreground = true)
        assertEquals(m.instance, updated.instance)
        assertEquals("com.a.MainActivity", updated.activity)
        fakeNow = 4000
        assertTrue(t.isFresh("a", AdSkipGroup.MODE_SPLASH, 5000))
        fakeNow = 6000
        assertFalse(t.isFresh("a", AdSkipGroup.MODE_SPLASH, 5000))
    }

    @Test
    fun anyModeResetsOnRealActivityOutsidePopup() {
        val t = tracker(bounds = { Bounds.FULL })
        fakeNow = 0
        val m = t.onForeground("a", "com.a.MainActivity")
        fakeNow = 5000
        val reset = t.onWindowStateChanged("a", "com.a.DetailActivity", AdSkipGroup.MODE_ANY, foreground = true)
        assertNotEquals(m.instance, reset.instance)
        assertTrue(t.isFresh("a", AdSkipGroup.MODE_ANY, 1))
    }

    @Test
    fun anyModeKeepsInstanceWhenPopupWindow() {
        val t = tracker(bounds = { Bounds.DIM_DIALOG })
        fakeNow = 0
        val m = t.onForeground("a", "com.a.MainActivity")
        fakeNow = 1000
        val same = t.onWindowStateChanged("a", "com.a.DetailActivity", AdSkipGroup.MODE_ANY, foreground = true)
        assertEquals(m.instance, same.instance)
    }

    @Test
    fun noiseClassDoesNotSwitchActivity() {
        val t = tracker(bounds = { Bounds.FULL }, quirks = OemQuirks.MIUI)
        fakeNow = 0
        val m = t.onForeground("a", "com.a.MainActivity")
        fakeNow = 1000
        val same = t.onWindowStateChanged("a", "androidx.viewpager.widget.ViewPager", AdSkipGroup.MODE_ANY, foreground = true)
        assertEquals(m.instance, same.instance)
    }

    // ---- AttemptScheduler ----

    @Test
    fun cooldownGapAndInFlightGate() {
        val s = AttemptScheduler { fakeNow }
        fakeNow = 10_000
        assertTrue(s.tryEnter("p", "p#1", 1200, 500))
        assertFalse(s.tryEnter("p", "p#1", 1200, 500)) // inFlight
        s.leave("p#1")
        assertFalse(s.tryEnter("p", "p#1", 1200, 500)) // gap 未到
        fakeNow += 600
        assertTrue(s.tryEnter("p", "p#1", 1200, 500))
        s.leave("p#1")
        s.recordClick("p")
        fakeNow += 600
        assertFalse(s.tryEnter("p", "p#1", 1200, 500)) // 点击冷却未到
        fakeNow += 700
        assertTrue(s.tryEnter("p", "p#1", 1200, 500))
    }

    @Test
    fun budgetStopsAfterSuccessAndOverhead() {
        val s = AttemptScheduler { fakeNow }
        assertTrue(s.hasBudget("k", 1, 2))
        s.onOutcome("k", ok = true)
        assertFalse(s.hasBudget("k", 1, 2)) // 成功上限=1
        val s2 = AttemptScheduler { fakeNow }
        s2.onOutcome("k", ok = false)
        s2.onOutcome("k", ok = false)
        s2.onOutcome("k", ok = false)
        assertFalse(s2.hasBudget("k", 1, 2)) // 失败兜底=1+2
        assertTrue(s2.onceMiss("k"))
        assertFalse(s2.onceMiss("k"))
    }

    @Test
    fun retainOnlyCleansOldInstances() {
        val s = AttemptScheduler { fakeNow }
        s.onOutcome("p#1", ok = true)
        s.onOutcome("p#2", ok = true)
        s.retainOnly("p", "p#2")
        assertEquals(0, s.successCount("p#1"))
        assertEquals(1, s.successCount("p#2"))
    }

    @Test
    fun groupCooldownGatesAndResetsOnNewInstance() {
        val s = AttemptScheduler { fakeNow }
        fakeNow = 0
        assertTrue(s.groupEligible("p", "g1", 2000))
        s.recordGroupAction("p", "g1")
        fakeNow = 1000
        assertFalse(s.groupEligible("p", "g1", 2000))
        s.retainOnly("p", "p#2") // 新实例 → 组级 cooldown 随之清理
        fakeNow = 2500
        assertTrue(s.groupEligible("p", "g1", 2000))
    }

    // ---- SkipGate ----

    @Test
    fun gateBlocksPopupOnlyInSplashMode() {
        val g = SkipGate(classifier { Bounds.DIM_DIALOG })
        assertEquals("window", g.blockReason("p", "com.p.MainActivity", AdSkipGroup.MODE_SPLASH))
        assertNull(g.blockReason("p", "com.p.MainActivity", AdSkipGroup.MODE_ANY))
        val g2 = SkipGate(classifier { Bounds.FULL })
        assertEquals("class", g2.blockReason("p", "android.app.Dialog", AdSkipGroup.MODE_SPLASH))
        assertNull(g2.blockReason("p", "com.p.MainActivity", AdSkipGroup.MODE_SPLASH))
    }
}
