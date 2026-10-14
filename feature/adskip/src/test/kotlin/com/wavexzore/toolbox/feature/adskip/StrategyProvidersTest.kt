package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.SelectorEngine
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import com.wavexzore.toolbox.feature.adskip.infra.AttemptScheduler
import com.wavexzore.toolbox.feature.adskip.infra.Heartbeat
import com.wavexzore.toolbox.feature.adskip.profile.GenericProfile
import com.wavexzore.toolbox.feature.adskip.strategy.KeywordProvider
import com.wavexzore.toolbox.feature.adskip.strategy.MatchProvider
import com.wavexzore.toolbox.feature.adskip.strategy.NodeIdentity
import com.wavexzore.toolbox.feature.adskip.strategy.PathProvider
import com.wavexzore.toolbox.feature.adskip.strategy.PositionProvider
import com.wavexzore.toolbox.feature.adskip.strategy.ProviderContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** T-203：Provider 链（关键词抑制/全局最优/组冷却/兜底）+ 心跳计划 + 去重键记账。 */
class StrategyProvidersTest {

    private val engine = SelectorEngine()
    private var fakeNow = 0L

    private fun ctx(
        json: String,
        groups: List<AdSkipGroup> = emptyList(),
        extraKeywords: List<String> = emptyList(),
        suppressed: Set<String> = emptySet(),
        skipKeyword: Boolean = false,
        scheduler: AttemptScheduler = AttemptScheduler { fakeNow },
    ): ProviderContext = ProviderContext(
        pkg = "com.p",
        root = SnapshotTree.fromJson(json),
        screenWidth = 1080,
        screenHeight = 2400,
        groups = groups,
        profile = GenericProfile(),
        activity = "com.p.MainActivity",
        extraKeywords = extraKeywords,
        suppressedIdentities = suppressed,
        skipKeyword = skipKeyword,
        selectorEngine = engine,
        scheduler = scheduler,
    )

    private val skipTree = """
        { "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
          {"text":"跳过","class":"TextView","clickable":true,"bounds":[880,170,1030,230]}]}
    """.trimIndent()

    @Test
    fun keywordProviderReturnsCandidateAndSkipsByFlag() = runBlocking {
        val c = KeywordProvider().provide(ctx(skipTree))
        assertNotNull(c)
        assertTrue(c.source.startsWith("keyword:"))
        assertNull(KeywordProvider().provide(ctx(skipTree, skipKeyword = true)))
    }

    @Test
    fun keywordProviderRespectsSuppression() = runBlocking {
        val first = KeywordProvider().provide(ctx(skipTree))!!
        val identity = NodeIdentity.of(first.node!!)
        assertNull(KeywordProvider().provide(ctx(skipTree, suppressed = setOf(identity))))
    }

    @Test
    fun keywordProviderWorksWithGenericFallbackGroup() = runBlocking {
        // 未适配应用（组为空→兜底组）仍走 keyword 来源（D-223 回归锁定）
        val c = KeywordProvider().provide(ctx(skipTree, groups = listOf(GroupFallback.generic)))
        assertNotNull(c)
        assertTrue(c.source.startsWith("keyword:"))
    }

    @Test
    fun matchProviderPicksGlobalBestAcrossGroups() = runBlocking {
        val tree = """
            { "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
              {"text":"关闭","class":"TextView","bounds":[100,200,300,260]},
              {"text":"跳过","class":"TextView","bounds":[100,100,300,160]}]}
        """.trimIndent()
        val groups = listOf(
            AdSkipGroup(id = "gA", matches = listOf("""[text="关闭"]""")),
            AdSkipGroup(id = "gB", matches = listOf("""[text="跳过"]""")),
        )
        val c = MatchProvider().provide(ctx(tree, groups))
        assertNotNull(c)
        assertEquals("gB", c.groupId) // Y 最优跨组生效（旧语义保持）
    }

    @Test
    fun matchProviderSkipsGroupInCooldown(): Unit = runBlocking {
        val tree = """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"text":"关闭","class":"TextView","bounds":[100,200,300,260]}]}"""
        val scheduler = AttemptScheduler { fakeNow }
        val groups = listOf(AdSkipGroup(id = "gA", matches = listOf("""[text="关闭"]"""), actionCd = 2000))
        // 冷却生效 → 无候选
        scheduler.recordGroupAction("com.p", "gA")
        assertNull(MatchProvider().provide(ctx(tree, groups, scheduler = scheduler)))
        fakeNow = 3000
        assertNotNull(MatchProvider().provide(ctx(tree, groups, scheduler = scheduler)))
    }

    @Test
    fun pathAndPositionProvidersFallThrough(): Unit = runBlocking {
        val tree = """{ "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
            {"text":"x","class":"TextView","bounds":[100,100,300,160]}]}"""
        val pathGroup = AdSkipGroup(id = "gp", path = "0")
        val pathCandidate = PathProvider().provide(ctx(tree, listOf(pathGroup)))
        assertNotNull(pathCandidate)
        assertEquals("gp", pathCandidate.groupId)

        val posGroup = AdSkipGroup(id = "gpos", position = AdSkipPosition(xPercent = 0.9f, yPercent = 0.05f))
        val posCandidate = PositionProvider().provide(ctx(tree, listOf(posGroup)))
        assertNotNull(posCandidate)
        assertNotNull(posCandidate.position)
    }

    @Test
    fun nodeIdentityFindRoundTrip() {
        val root = SnapshotTree.fromJson(skipTree)
        val node = root.children.first()
        val identity = NodeIdentity.of(node)
        val found = NodeIdentity.find(root, identity)
        assertNotNull(found)
        assertEquals(node.bounds, found.bounds)
    }

    @Test
    fun heartbeatScheduleAndWindow() {
        val hb = Heartbeat(firstMs = 500, intervalMs = 750)
        assertEquals(1500, hb.dueAt(1000, 1))
        assertEquals(2250, hb.dueAt(1000, 2))
        assertEquals(3000, hb.dueAt(1000, 3))
        assertTrue(hb.withinWindow(1000, 6000, 5000)) // 边界含
        assertFalse(hb.withinWindow(1000, 6001, 5000))
    }

    @Test
    fun identityAccountingAndVerifyFailed() {
        val s = AttemptScheduler { fakeNow }
        s.recordIdentity("p#1", "id")
        s.recordIdentity("p#1", "id")
        assertEquals(2, s.identityCount("p#1", "id"))
        s.onOutcome("p#1", ok = true)
        assertEquals(1, s.successCount("p#1"))
        s.onVerifyFailed("p#1", "id")
        assertEquals(0, s.successCount("p#1")) // 成功记账回退
        assertEquals(0, s.identityCount("p#1", "id")) // 去重放开
        s.recordIdentity("p#2", "id2")
        s.retainOnly("p", "p#2")
        assertEquals(1, s.identityCount("p#2", "id2")) // 当前实例键保留
        assertEquals(0, s.identityCount("p#1", "id")) // 旧实例键清理
    }
}
