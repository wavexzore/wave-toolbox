package com.wavexzore.toolbox.feature.adskip.profile

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 超级 App「小程序容器 / 授权页」的「关闭」抑制（B-207→B-209）。
 *
 * 准入理由（pack 不可表达——需按页面选择性别抑制关键词）：这些页面右上角都有页面固有的
 * 「关闭」胶囊/按钮（退出小程序、取消授权），通用关键词/兜底选择器一旦点到即秒退：
 * - 微信小程序（AppBrandUI*）：实测 21:05 打开小程序 165ms 后 keyword:关闭 → 返回聊天；
 *   启动中转页（AppBrandLaunchProxyUI）同理（实测点掉即中止拉起）；
 * - 支付宝小程序（XRiverActivity$App*）：实测 21:40 被 launcher 回退尝试点中 desc=关闭 → 退回首页；
 * - QQ 小程序（mini.appbrand.ui.AppBrandUI*，实测 AppBrandUI3）与独立运行容器
 *   （luggage.wxa…WxaContainerMultiTaskActivity*，实测随授权后拉起）、加载弹窗
 *   （QUILoadingDialog，其关闭=取消启动，实测点掉即回退）；
 * - 微信 SDK 授权页（SDKOAuthUI）：关闭=取消授权，实测 26ms 内被微信残留实例点掉 → 授权流回退。
 *
 * 处置：仅在本清单页面上抑制该按钮；页面自身开屏广告的「跳过」不受影响（跳过≠关闭）。
 * [suppressOn] 入参由引擎按「树的归属方最近真实 Activity」传入（B-209 跨包回退/标签竞态修正）。
 * 扩展=在 [CONTAINER_MARKERS] 加一行。
 */
@Singleton
class MiniProgramCapsuleProfile @Inject constructor() : AppAdProfile {

    override val pkgs: Set<String> = CONTAINER_MARKERS.keys

    /** 命中任一容器/授权页前缀 → 抑制「关闭」（text/desc 双形态、包含匹配）。 */
    override fun suppressOn(activity: String?): List<String> {
        val act = activity ?: return emptyList()
        return if (CONTAINER_MARKERS.values.any { markers -> markers.any { act.contains(it) } }) {
            CAPSULE_SELECTORS
        } else {
            emptyList()
        }
    }

    /**
     * 授权/隐私弹层动作词：关闭被抑制后由关键词链续取（「允许」=授权、「同意」=隐私协议）。
     *
     * 仅在授权页生效；不能对整个包的所有页面放开，否则聊天/正文里的「同意」文本
     * 也会成为候选并造成误点。新增授权页时把 Activity 特征加进 [AUTH_PAGE_MARKERS]。
     */
    override fun keywordExtra(): List<String> = emptyList()

    override fun keywordExtra(activity: String?): List<String> {
        val act = activity ?: return emptyList()
        return if (AUTH_PAGE_MARKERS.any { act.contains(it, ignoreCase = true) }) {
            AUTH_KEYWORDS
        } else {
            emptyList()
        }
    }

    companion object {
        /**
         * 胶囊/关闭按钮选择器（包含匹配；B-210 由 EQ 放宽——WebView 授权页节点属性可能带
         * 空白/后缀变体，EQ 曾漏杀并导致点击仍在；配合 Provider 续取次优后误伤可控）。
         */
        val CAPSULE_SELECTORS = listOf("""[text*="关闭"]""", """[desc*="关闭"]""")

        /** 只在这些授权页上追加「允许/同意」；避免正文/聊天中的同名文本被当按钮。 */
        val AUTH_KEYWORDS = listOf("允许", "同意")
        val AUTH_PAGE_MARKERS = listOf("SDKOAuthUI", "OAuth", "AuthPage", "Authorization")

        /** 包名 → 小程序容器/授权页 Activity 前缀（contains 判定；覆盖 AppBrandUI00/01/3、$App01 等后缀变体）。 */
        val CONTAINER_MARKERS: Map<String, List<String>> = mapOf(
            "com.tencent.mm" to listOf(
                "com.tencent.mm.plugin.appbrand.ui.AppBrandUI",
                "com.tencent.mm.plugin.appbrand.launching.AppBrandLaunchProxyUI",
                "com.tencent.mm.plugin.webview.ui.tools.SDKOAuthUI",
            ),
            "com.eg.android.AlipayGphone" to listOf(
                "com.alipay.mobile.nebulax.xriver.activity.XRiverActivity",
                "com.alipay.mobile.nebulax.xriver.activity.XRiverLoadingActivity",
            ),
            "com.tencent.mobileqq" to listOf(
                "com.tencent.mobileqq.mini.appbrand.ui.AppBrandUI",
                "com.tencent.luggage.wxa.standalone_open_runtime.container.WxaContainerMultiTaskActivity",
                "com.tencent.widget.loading.QUILoadingDialog",
            ),
        )
    }
}
