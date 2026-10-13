package com.wavexzore.toolbox.feature.adskip.profile

/** 通用识别器（T-202）：无归属包，兜底任何未适配应用——现状默认行为的封装。 */
class GenericProfile : AppAdProfile {
    override val pkgs: Set<String> = emptySet()
}
