package com.wavexzore.toolbox.feature.adskip.strategy

import com.wavexzore.toolbox.feature.adskip.KeywordStrategy
import com.wavexzore.toolbox.feature.adskip.PathResolver

/** 关键词/vid 提供者（默认链首；弹窗或 suppressGeneric 上下文跳过）。 */
class KeywordProvider : CandidateProvider {

    override val id: String = "keyword"

    override suspend fun provide(ctx: ProviderContext): Candidate? {
        if (ctx.skipKeyword) return null
        val pick = KeywordStrategy.pick(
            root = ctx.root,
            extraKeywords = ctx.extraKeywords,
            screenWidth = ctx.screenWidth,
            screenHeight = ctx.screenHeight,
        ) ?: return null
        if (NodeIdentity.of(pick.node) in ctx.suppressedIdentities) return null
        return Candidate(node = pick.node, position = null, source = "keyword:${pick.matched}")
    }
}

/** 规则包 `matches` 提供者（组准入 + 全局 Y 最优，保持旧语义）。 */
class MatchProvider : CandidateProvider {

    override val id: String = "match"

    private data class Grouped(val pick: KeywordStrategy.Pick, val groupId: String)

    override suspend fun provide(ctx: ProviderContext): Candidate? {
        val grouped = ArrayList<Grouped>(4)
        for (group in ctx.groups) {
            if (!ctx.scheduler.groupEligible(ctx.pkg, group.id, group.actionCd)) continue
            for (selector in group.matches) {
                ctx.selectorEngine.findAll(ctx.root, selector).forEach { match ->
                    grouped += Grouped(KeywordStrategy.Pick(match.node, selector, "match"), group.id)
                }
            }
        }
        val best = KeywordStrategy.bestOf(grouped.map { it.pick }, ctx.screenHeight) ?: return null
        val origin = grouped.first { it.pick === best }
        return Candidate(node = best.node, position = null, source = "match:${best.matched}", groupId = origin.groupId)
    }
}

/** `path` 兜底（组序优先，首个命中）。 */
class PathProvider : CandidateProvider {

    override val id: String = "path"

    override suspend fun provide(ctx: ProviderContext): Candidate? {
        for (group in ctx.groups) {
            val path = group.path ?: continue
            if (!ctx.scheduler.groupEligible(ctx.pkg, group.id, group.actionCd)) continue
            val node = PathResolver.resolve(ctx.root, path) ?: continue
            return Candidate(node = node, position = null, source = "path:${group.id}", groupId = group.id)
        }
        return null
    }
}

/** `position` 兜底（组序优先，首个命中）。 */
class PositionProvider : CandidateProvider {

    override val id: String = "position"

    override suspend fun provide(ctx: ProviderContext): Candidate? {
        for (group in ctx.groups) {
            val position = group.position ?: continue
            if (!ctx.scheduler.groupEligible(ctx.pkg, group.id, group.actionCd)) continue
            return Candidate(node = null, position = position, source = "position:${group.id}", groupId = group.id)
        }
        return null
    }
}
