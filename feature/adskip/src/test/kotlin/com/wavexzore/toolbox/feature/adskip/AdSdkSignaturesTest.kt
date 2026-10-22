package com.wavexzore.toolbox.feature.adskip

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import com.wavexzore.toolbox.feature.adskip.infra.AdSdkSignatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-237 广告 SDK 指纹：只识别+日志，不参与拦截（2026-10-22）。 */
class AdSdkSignaturesTest {

    private fun tree(json: String) = SnapshotTree.fromJson(json)

    @Test
    fun detectsCsjByViewIdFragment() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1080,2400], "children":[
              { "vid":"com.example:id/tt_splash_skip_btn", "class":"TextView", "bounds":[880,160,1020,240] }
            ]}
            """.trimIndent(),
        )
        assertEquals(setOf("csj"), AdSdkSignatures.detect(root, null))
    }

    @Test
    fun detectsCsjByViewClass() {
        val root = tree(
            """
            { "class":"com.bytedance.sdk.openadsdk.core.nativeexpress.NativeExpressView", "bounds":[0,0,1080,2400] }
            """.trimIndent(),
        )
        assertEquals(setOf("csj"), AdSdkSignatures.detect(root, null))
    }

    @Test
    fun detectsGdtByActivity() {
        val root = tree("""{ "class":"FrameLayout", "bounds":[0,0,1080,2400] }""")
        assertEquals(setOf("gdt"), AdSdkSignatures.detect(root, "com.qq.e.ads.PortraitADActivity"))
    }

    @Test
    fun detectsMultipleProviders() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1080,2400], "children":[
              { "vid":"com.example:id/ksad_splash_close", "class":"ImageView", "bounds":[900,160,1000,240] },
              { "vid":"com.example:id/gdt_splash_skip", "class":"TextView", "bounds":[800,160,880,240] }
            ]}
            """.trimIndent(),
        )
        assertEquals(setOf("gdt", "ks"), AdSdkSignatures.detect(root, null))
    }

    @Test
    fun returnsEmptyForPlainAppTree() {
        val root = tree(
            """
            { "class":"FrameLayout", "bounds":[0,0,1080,2400], "children":[
              { "text":"跳过", "class":"TextView", "bounds":[900,160,1020,240] }
            ]}
            """.trimIndent(),
        )
        assertTrue(AdSdkSignatures.detect(root, "com.example.app.MainActivity").isEmpty())
    }
}
