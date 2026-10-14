package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** [UpdateRepository] 状态机/合并/恢复/取消/门控全量锁定（T-306）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {

    private lateinit var dir: File
    private lateinit var http: FakeUpdateHttp
    private lateinit var prefs: FakeUpdatePrefs
    private lateinit var platform: FakeUpdatePlatform
    private lateinit var clock: FixedClock

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("waveupd-repo").toFile()
        http = FakeUpdateHttp()
        prefs = FakeUpdatePrefs()
        platform = FakeUpdatePlatform(dir)
        clock = FixedClock(1_000_000_000_000)
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun TestScope.newRepo(): UpdateRepository {
        val d = UnconfinedTestDispatcher(testScheduler)
        return UpdateRepository(http, prefs, platform, clock, AppDispatchers(d, d, d, d, d, d))
    }

    // ---------------------------------------------------------------- 工具

    private fun releaseJson(
        tag: String,
        digest: String? = null,
        size: Long = 64,
        body: String = "notes",
        htmlUrl: String = "https://github.com/wavexzore/wave-toolbox/releases/tag/$tag",
    ): String {
        val asset = buildString {
            append("""{"name":"wave-toolbox-$tag.apk","browser_download_url":"https://d/$tag","size":$size""")
            if (digest != null) append(""","digest":"$digest"""")
            append("}")
        }
        return """{"tag_name":"$tag","body":"$body","html_url":"$htmlUrl","assets":[$asset]}"""
    }

    private fun zerosDigest(n: Int): String = "sha256:" + Sha256.hex(ByteArray(n))

    /** 使状态到达 Available(v1.1.0，digest=零字节 64)。 */
    private fun seedAvailable(repo: UpdateRepository, tag: String = "v1.1.0", size: Long = 64, digest: String? = zerosDigest(64)) {
        http.textResult = HttpText.Ok(releaseJson(tag, digest = digest, size = size), "api.github.com")
        repo.refresh(force = true)
        assertTrue(repo.state.value is UpdateState.Available)
    }

    private fun okDownloads(bytes: Int = 64) {
        http.downloadTotal = bytes.toLong()
        http.downloadResult = DownloadResult.Ok
    }

    // ---------------------------------------------------------------- 检查：节流 / 回拨 / force / autoCheck

    @Test
    fun `24h 节流内不请求`() = runTest {
        prefs.lastCheckAtFlow.value = clock.now - UpdateConfig.THROTTLE_MS + 1_000
        val repo = newRepo()
        repo.refresh(force = false)
        advanceUntilIdle()
        assertEquals(0, http.requestedUrls.size)
        assertTrue(repo.state.value is UpdateState.Idle)
    }

    @Test
    fun `节流过期后请求`() = runTest {
        prefs.lastCheckAtFlow.value = clock.now - UpdateConfig.THROTTLE_MS - 1
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        val repo = newRepo()
        repo.refresh(force = false)
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)
        assertTrue(repo.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `时钟回拨视为需检查`() = runTest {
        prefs.lastCheckAtFlow.value = clock.now + 60_000
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        val repo = newRepo()
        repo.refresh(force = false)
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)
    }

    @Test
    fun `force 穿透节流与 autoCheck 关闭`() = runTest {
        prefs.lastCheckAtFlow.value = clock.now
        prefs.autoCheckFlow.value = false
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        val repo = newRepo()
        repo.refresh(force = false)
        advanceUntilIdle()
        assertEquals(0, http.requestedUrls.size)
        repo.refresh(force = true)
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)
    }

    @Test
    fun `并发双 refresh 仅一次请求`() = runTest {
        http.getGate = CompletableDeferred()
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        val repo = newRepo()
        repo.refresh(force = true)
        repo.refresh(force = true)
        http.getGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)
    }

    // ---------------------------------------------------------------- 检查：结果分类

    @Test
    fun `有新版进入 Available 且 ignored 命中标注`() = runTest {
        prefs.skippedTagFlow.value = "v1.1.0"
        val repo = newRepo()
        seedAvailable(repo)
        val available = repo.state.value as UpdateState.Available
        assertTrue(available.ignored)
        assertEquals("v1.1.0", available.info.tag)
    }

    @Test
    fun `404 归 NoRelease`() = runTest {
        http.textResult = HttpText.HttpError(404, null)
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.NoRelease)
    }

    @Test
    fun `403 归 RateLimited 并携带重置时间`() = runTest {
        http.textResult = HttpText.HttpError(403, 1_800_000_000L)
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        val error = repo.state.value as UpdateState.Error
        assertEquals(ErrorKind.RateLimited, error.kind)
        assertEquals(1_800_000_000L, error.resetAtEpochSec)
    }

    @Test
    fun `断网归 NoNetwork 且写入 lastCheckAt`() = runTest {
        http.textResult = HttpText.IoError(IoKind.NoNetwork)
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        assertEquals(ErrorKind.NoNetwork, (repo.state.value as UpdateState.Error).kind)
        assertEquals(clock.now, prefs.lastCheckAtFlow.value)
    }

    @Test
    fun `解析失败归 Parse`() = runTest {
        http.textResult = HttpText.Ok("garbage", "api.github.com")
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        assertEquals(ErrorKind.Parse, (repo.state.value as UpdateState.Error).kind)
    }

    // ---------------------------------------------------------------- 下载：成功/校验/覆盖

    @Test
    fun `下载成功进入 Downloaded 且文件落盘`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        okDownloads()
        platform.apkVersion = "1.1.0"
        repo.download()
        advanceUntilIdle()
        val downloaded = repo.state.value as UpdateState.Downloaded
        assertTrue(downloaded.file.exists())
        assertEquals("v1.1.0", downloaded.info?.tag)
        assertEquals(1, http.downloadedTo.size)
    }

    @Test
    fun `校验失败保留文件并归 VerifyFailed`() = runTest {
        val repo = newRepo()
        seedAvailable(repo, digest = "sha256:" + Sha256.hex(ByteArray(64) { 1 }))
        okDownloads()
        repo.download()
        advanceUntilIdle()
        assertEquals(ErrorKind.VerifyFailed, (repo.state.value as UpdateState.Error).kind)
        val file = dir.listFiles()!!.single()
        assertTrue(file.exists(), "校验失败文件应保留")
    }

    @Test
    fun `大小不符归 VerifyFailed`() = runTest {
        val repo = newRepo()
        seedAvailable(repo, size = 64, digest = zerosDigest(64))
        okDownloads(bytes = 32)
        repo.download()
        advanceUntilIdle()
        assertEquals(ErrorKind.VerifyFailed, (repo.state.value as UpdateState.Error).kind)
    }

    @Test
    fun `digest 缺失放行并保持 null 标记`() = runTest {
        val repo = newRepo()
        seedAvailable(repo, digest = null)
        okDownloads()
        platform.apkVersion = "1.1.0"
        repo.download()
        advanceUntilIdle()
        val downloaded = repo.state.value as UpdateState.Downloaded
        assertEquals(null, downloaded.info?.asset?.digest)
    }

    @Test
    fun `超过 200MB 归 TooBig 不发起下载`() = runTest {
        val repo = newRepo()
        seedAvailable(repo, size = UpdateConfig.MAX_APK_BYTES + 1)
        repo.download()
        advanceUntilIdle()
        assertEquals(ErrorKind.TooBig, (repo.state.value as UpdateState.Error).kind)
        assertEquals(0, http.downloadedTo.size)
    }

    @Test
    fun `下载 404 与 403 分流`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        http.downloadResult = DownloadResult.HttpError(404)
        repo.download()
        advanceUntilIdle()
        val notFound = repo.state.value as UpdateState.Error
        assertEquals(ErrorKind.Http, notFound.kind)
        assertEquals(404, notFound.code)
        assertNotNull(notFound.info)

        http.downloadResult = DownloadResult.HttpError(403)
        repo.download()
        advanceUntilIdle()
        assertEquals(ErrorKind.RateLimited, (repo.state.value as UpdateState.Error).kind)
    }

    @Test
    fun `取消下载回到 Available 且 info 保留`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        http.downloadGate = CompletableDeferred()
        repo.download()
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Downloading)
        repo.cancelDownload()
        advanceUntilIdle()
        val available = repo.state.value as UpdateState.Available
        assertEquals("v1.1.0", available.info.tag)
        http.downloadGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Available)
    }

    // ---------------------------------------------------------------- 合并：Downloaded 分支

    private fun seedProbedDownloaded(): File {
        platform.apkVersion = "0.9.0"
        val file = File(dir, "wave-toolbox-v0.9.0.apk")
        file.writeBytes(ByteArray(64))
        return file
    }

    @Test
    fun `probe 异版本恢复为 Downloaded`() = runTest {
        val file = seedProbedDownloaded()
        val repo = newRepo()
        advanceUntilIdle()
        val downloaded = repo.state.value as UpdateState.Downloaded
        assertEquals(file.absolutePath, downloaded.file.absolutePath)
        assertEquals("0.9.0", downloaded.apkVersionName)
    }

    @Test
    fun `Downloaded 检查同版本保持并回填 info`() = runTest {
        seedProbedDownloaded()
        val repo = newRepo()
        http.textResult = HttpText.Ok(releaseJson("v0.9.0"), "api.github.com")
        repo.refresh(force = true)
        advanceUntilIdle()
        val downloaded = repo.state.value as UpdateState.Downloaded
        assertEquals("v0.9.0", downloaded.info?.tag)
    }

    @Test
    fun `Downloaded 检查异版本转 Available`() = runTest {
        seedProbedDownloaded()
        val repo = newRepo()
        http.textResult = HttpText.Ok(releaseJson("v1.1.0", digest = zerosDigest(64)), "api.github.com")
        repo.refresh(force = true)
        advanceUntilIdle()
        assertEquals("v1.1.0", (repo.state.value as UpdateState.Available).info.tag)
    }

    @Test
    fun `Downloaded 检查 404 保持`() = runTest {
        seedProbedDownloaded()
        val repo = newRepo()
        http.textResult = HttpText.HttpError(404, null)
        repo.refresh(force = true)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Downloaded)
    }

    @Test
    fun `Downloaded 文件缺失回退常规语义`() = runTest {
        val file = seedProbedDownloaded()
        val repo = newRepo()
        file.delete()
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        repo.refresh(force = true)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `probe 同版本残留启动清理`() = runTest {
        platform.apkVersion = "1.0.0"
        val file = File(dir, "wave-toolbox-v1.0.0.apk")
        file.writeBytes(ByteArray(8))
        newRepo()
        advanceUntilIdle()
        assertFalse(file.exists())
    }

    @Test
    fun `probe 不可解析留盘`() = runTest {
        platform.apkVersion = null
        val file = File(dir, "wave-toolbox-v0.9.0.apk")
        file.writeBytes(ByteArray(8))
        val repo = newRepo()
        advanceUntilIdle()
        assertTrue(file.exists())
        assertTrue(repo.state.value is UpdateState.Idle)
    }

    // ---------------------------------------------------------------- 自愈：redownload / retry

    @Test
    fun `redownload 从 probe 恢复态经 pending 续接完成`() = runTest {
        seedProbedDownloaded()
        val repo = newRepo()
        http.textResult = HttpText.Ok(releaseJson("v1.1.0", digest = zerosDigest(64)), "api.github.com")
        okDownloads()
        platform.apkVersion = "1.1.0"
        repo.redownload()
        advanceUntilIdle()
        val downloaded = repo.state.value as UpdateState.Downloaded
        assertEquals("v1.1.0", downloaded.info?.tag)
        assertEquals(1, http.downloadedTo.size)
    }

    @Test
    fun `retry 按 info 分流：检查侧重查、下载侧重下`() = runTest {
        val repo = newRepo()
        http.textResult = HttpText.IoError(IoKind.NoNetwork)
        repo.refresh(force = true)
        advanceUntilIdle()
        val checkError = repo.state.value as UpdateState.Error
        assertEquals(null, checkError.info)
        repo.retry()
        advanceUntilIdle()
        assertEquals(2, http.requestedUrls.size)

        seedAvailable(repo)
        http.downloadResult = DownloadResult.IoError(IoKind.Interrupted)
        repo.download()
        advanceUntilIdle()
        assertNotNull((repo.state.value as UpdateState.Error).info)
        http.downloadResult = DownloadResult.Ok
        http.downloadTotal = 64
        platform.apkVersion = "1.1.0"
        repo.retry()
        advanceUntilIdle()
        assertEquals(2, http.downloadedTo.size)
        assertTrue(repo.state.value is UpdateState.Downloaded)
    }

    // ---------------------------------------------------------------- 忽略 / 开关 / 错误关闭 / 安装

    @Test
    fun `忽略与取消忽略`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        repo.skipCurrent()
        advanceUntilIdle()
        assertEquals("v1.1.0", prefs.skippedTagFlow.value)
        assertTrue((repo.state.value as UpdateState.Available).ignored)
        repo.clearSkip()
        advanceUntilIdle()
        assertEquals("", prefs.skippedTagFlow.value)
        assertFalse((repo.state.value as UpdateState.Available).ignored)
    }

    @Test
    fun `setAutoCheck 开启时补一次检查`() = runTest {
        prefs.autoCheckFlow.value = false
        prefs.lastCheckAtFlow.value = 0
        http.textResult = HttpText.Ok(releaseJson("v1.0.0"), "api.github.com")
        val repo = newRepo()
        repo.setAutoCheck(true)
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)
        assertTrue(prefs.autoCheckFlow.value)
    }

    @Test
    fun `dismissError 回到 Idle`() = runTest {
        http.textResult = HttpText.IoError(IoKind.Timeout)
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Error)
        repo.dismissError()
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Idle)
    }

    @Test
    fun `install 门控与续装`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        okDownloads()
        platform.apkVersion = "1.1.0"
        repo.download()
        advanceUntilIdle()

        platform.canInstall = false
        repo.install()
        advanceUntilIdle()
        assertEquals(1, platform.unknownSourcesOpened)
        assertEquals(0, platform.installed.size)

        platform.canInstall = true
        repo.install()
        advanceUntilIdle()
        assertEquals(1, platform.installed.size)
    }

    @Test
    fun `install 文件丢失归 Storage`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        okDownloads()
        platform.apkVersion = "1.1.0"
        repo.download()
        advanceUntilIdle()
        (repo.state.value as UpdateState.Downloaded).file.delete()
        repo.install()
        advanceUntilIdle()
        val error = repo.state.value as UpdateState.Error
        assertEquals(ErrorKind.Storage, error.kind)
        assertNotNull(error.info)
    }

    @Test
    fun `下载进行中再次 download 幂等`() = runTest {
        val repo = newRepo()
        seedAvailable(repo)
        http.downloadGate = CompletableDeferred()
        repo.download()
        repo.download()
        http.downloadGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, http.downloadedTo.size)
    }

    @Test
    fun `429 无 reset 头时重置时间为空`() = runTest {
        http.textResult = HttpText.HttpError(429, null)
        val repo = newRepo()
        repo.refresh(force = true)
        advanceUntilIdle()
        val error = repo.state.value as UpdateState.Error
        assertEquals(ErrorKind.RateLimited, error.kind)
        assertNull(error.resetAtEpochSec)
    }
}
