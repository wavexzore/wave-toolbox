package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.design.components.WaveTone

/** 「检查更新」入口行 Pill（null=Idle 常态无 Pill；文案=设计 04 §10 全表）。 */
data class UpdateBadge(val text: String, val tone: WaveTone)

/** Repository 状态 → 入口行 Pill（8 状态 9 行唯一映射）。 */
fun UpdateState.toBadge(): UpdateBadge? = when (this) {
    is UpdateState.Idle -> null
    is UpdateState.Checking -> UpdateBadge("检查中", WaveTone.Neutral)
    is UpdateState.UpToDate -> UpdateBadge("已最新", WaveTone.Green)
    is UpdateState.NoRelease -> UpdateBadge("暂无发布", WaveTone.Neutral)
    is UpdateState.Available ->
        if (ignored) UpdateBadge("已忽略 ${info.tag}", WaveTone.Neutral) else UpdateBadge("有新版 ${info.tag}", WaveTone.Green)
    is UpdateState.Downloading -> UpdateBadge("下载中 $percent%", WaveTone.Blue)
    is UpdateState.Downloaded -> UpdateBadge("待安装 ${info?.tag ?: apkVersionName}", WaveTone.Green)
    is UpdateState.Error -> UpdateBadge(if (info != null) "下载失败" else "检查失败", WaveTone.Red)
}
