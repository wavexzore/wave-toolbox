package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.design.components.WaveTone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [UpdateBadge.toBadge] 9 行全表锁定（T-311；文案=设计 04 §10）。 */
class UpdateBadgeTest {

    private val info = ReleaseInfo(
        tag = "v1.1.0",
        notes = "notes",
        publishedAt = null,
        htmlUrl = "https://h",
        asset = AssetRef("wave-toolbox-v1.1.0.apk", "https://d", 10, null),
    )

    @Test
    fun `Idle 无 Pill`() {
        assertNull((UpdateState.Idle as UpdateState).toBadge())
    }

    @Test
    fun `检查中与已最新与暂无发布`() {
        assertEquals(UpdateBadge("检查中", WaveTone.Neutral), (UpdateState.Checking as UpdateState).toBadge())
        assertEquals(UpdateBadge("已最新", WaveTone.Green), (UpdateState.UpToDate as UpdateState).toBadge())
        assertEquals(UpdateBadge("暂无发布", WaveTone.Neutral), (UpdateState.NoRelease as UpdateState).toBadge())
    }

    @Test
    fun `有新版与已忽略`() {
        assertEquals(UpdateBadge("有新版 v1.1.0", WaveTone.Green), UpdateState.Available(info, ignored = false).toBadge())
        assertEquals(UpdateBadge("已忽略 v1.1.0", WaveTone.Neutral), UpdateState.Available(info, ignored = true).toBadge())
    }

    @Test
    fun `下载中与待安装`() {
        assertEquals(
            UpdateBadge("下载中 42%", WaveTone.Blue),
            UpdateState.Downloading(info, percent = 42, bytes = 10, total = 24).toBadge(),
        )
        val file = File("/tmp/x.apk")
        assertEquals(UpdateBadge("待安装 v1.1.0", WaveTone.Green), UpdateState.Downloaded(file, info, "1.1.0").toBadge())
        assertEquals(UpdateBadge("待安装 0.9.0", WaveTone.Green), UpdateState.Downloaded(file, null, "0.9.0").toBadge())
    }

    @Test
    fun `检查失败与下载失败区分`() {
        assertEquals(UpdateBadge("检查失败", WaveTone.Red), UpdateState.Error(ErrorKind.NoNetwork).toBadge())
        assertEquals(UpdateBadge("下载失败", WaveTone.Red), UpdateState.Error(ErrorKind.Interrupted, info).toBadge())
    }
}
