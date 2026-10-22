package com.wavexzore.toolbox.feature.adskip.infra

import com.wavexzore.toolbox.core.selector.NodeView

/**
 * 广告 SDK 指纹（D-237；2026-10-22）——**仅识别与日志，不参与放行/拦截决策**。
 *
 * 用途：在 `WaveAdSkip` 中输出 `ad-surface … providers=csj,gdt`，为后续「按提供商
 * 精确适配/统计」提供证据；拦截仍由窗口形态（D-235）与浮层容器（D-236）负责，
 * 避免「没有指纹就不点」导致未适配应用开屏全部漏点。
 *
 * 说明：
 * - 开屏广告多数在宿主 Activity 内嵌 View 展示，Activity 类名通常**不是**广告 SDK；
 *   这里的 activity 前缀只覆盖插屏/激励/全屏视频等独立桩 Activity。
 * - 视图指纹取 `viewId` 片段与类名前缀（资源名由宿主资源表决定，片段比包名稳定）。
 */
internal object AdSdkSignatures {

    data class Provider(
        val id: String,
        val activityPrefixes: List<String> = emptyList(),
        val classPrefixes: List<String> = emptyList(),
        val vidFragments: List<String> = emptyList(),
    )

    val PROVIDERS: List<Provider> = listOf(
        Provider(
            id = "csj",
            activityPrefixes = listOf("com.bytedance.sdk.openadsdk."),
            classPrefixes = listOf("com.bytedance.sdk.openadsdk."),
            vidFragments = listOf("tt_splash", "tt_ad_", "pangle_"),
        ),
        Provider(
            id = "gdt",
            activityPrefixes = listOf("com.qq.e.ads."),
            classPrefixes = listOf("com.qq.e."),
            vidFragments = listOf("gdt_"),
        ),
        Provider(
            id = "ks",
            activityPrefixes = listOf("com.kwad.sdk.api.proxy.app."),
            classPrefixes = listOf("com.kwad."),
            vidFragments = listOf("ksad_"),
        ),
        Provider(
            id = "mb",
            activityPrefixes = listOf("com.mbridge.msdk."),
            classPrefixes = listOf("com.mbridge."),
            vidFragments = listOf("mb_"),
        ),
        Provider(
            id = "sigmob",
            activityPrefixes = listOf("com.sigmob."),
            classPrefixes = listOf("com.sigmob."),
            vidFragments = listOf("sigmob_"),
        ),
        Provider(
            id = "admob",
            activityPrefixes = listOf("com.google.android.gms.ads."),
            classPrefixes = listOf("com.google.android.gms.ads."),
        ),
        Provider(
            id = "huawei",
            activityPrefixes = listOf("com.huawei.hms.ads."),
            classPrefixes = listOf("com.huawei.hms.ads."),
        ),
    )

    /** 当前树命中的提供商 id 集合（稳定顺序；空集=未识别到 SDK 指纹）。 */
    fun detect(root: NodeView, activity: String?): Set<String> {
        val found = LinkedHashSet<String>()
        val act = activity.orEmpty()
        if (act.isNotEmpty()) {
            PROVIDERS.forEach { provider ->
                if (provider.activityPrefixes.any { act.startsWith(it) }) found += provider.id
            }
        }
        walk(root) { node ->
            val cls = node.className.orEmpty()
            val vid = node.viewId.orEmpty()
            if (cls.isNotEmpty() || vid.isNotEmpty()) {
                PROVIDERS.forEach { provider ->
                    if (provider.classPrefixes.any { cls.startsWith(it) } ||
                        provider.vidFragments.any { vid.contains(it, ignoreCase = true) }
                    ) {
                        found += provider.id
                    }
                }
            }
        }
        return found
    }

    private fun walk(node: NodeView, visit: (NodeView) -> Unit) {
        visit(node)
        node.children.forEach { walk(it, visit) }
    }
}
