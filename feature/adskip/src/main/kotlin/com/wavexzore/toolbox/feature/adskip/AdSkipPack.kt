package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.rules.model.RawRuleGroup
import com.wavexzore.toolbox.core.rules.model.RawRulePack
import com.wavexzore.toolbox.core.rules.model.RulePosition
import com.wavexzore.toolbox.core.rules.parser.RuleParser

/**
 * adskip 兼容别名：adskip 的组模型就是统一规则模型 [RawRuleGroup]。
 *
 * 历史上 adskip 有自己的 `AdSkipGroup`/`AdSkipPosition`/`AdSkipPackFile`；
 * 统一后不再有第二份数据模型，仅保留类型别名以兼容现有策略/测试代码。
 */
typealias AdSkipGroup = RawRuleGroup
typealias AdSkipPosition = RulePosition
typealias AdSkipPackFile = RawRulePack

/** 规则包解析入口：委托统一解析器，保持原有「致命错误抛异常」契约。 */
object AdSkipPackCodec {
    fun parse(text: String): AdSkipPackFile = RuleParser.parsePackStrict(text, source = "adskip")
}
