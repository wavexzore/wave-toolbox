package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** [UpdateViewModel] 冒烟：门控标志 / 续装 / 转发（T-311）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private lateinit var dir: File
    private lateinit var http: FakeUpdateHttp
    private lateinit var prefs: FakeUpdatePrefs
    private lateinit var platform: FakeUpdatePlatform
    private lateinit var clock: FixedClock

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("waveupd-vm").toFile()
        http = FakeUpdateHttp()
        prefs = FakeUpdatePrefs()
        platform = FakeUpdatePlatform(dir)
        clock = FixedClock(1_000_000_000_000)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun TestScope.newVm(): UpdateViewModel {
        val d = UnconfinedTestDispatcher(testScheduler)
        val repo = UpdateRepository(http, prefs, platform, clock, AppDispatchers(d, d, d, d, d, d))
        return UpdateViewModel(repo)
    }

    /** 预置 probe 恢复态（Downloaded 0.9.0）。 */
    private fun seedProbedDownloaded(): File {
        platform.apkVersion = "0.9.0"
        val file = File(dir, "wave-toolbox-v0.9.0.apk")
        file.writeBytes(ByteArray(16))
        return file
    }

    @Test
    fun `未授权安装跳设置并在回前台续装`() = runTest {
        seedProbedDownloaded()
        val vm = newVm()
        advanceUntilIdle()

        platform.canInstall = false
        vm.install()
        advanceUntilIdle()
        assertEquals(1, platform.unknownSourcesOpened)
        assertEquals(0, platform.installed.size)

        platform.canInstall = true
        vm.onResumed()
        advanceUntilIdle()
        assertEquals(1, platform.installed.size)
        assertEquals(1, platform.unknownSourcesOpened)
    }

    @Test
    fun `已授权直接安装且回前台不重复`() = runTest {
        seedProbedDownloaded()
        val vm = newVm()
        advanceUntilIdle()

        platform.canInstall = true
        vm.install()
        advanceUntilIdle()
        assertEquals(1, platform.installed.size)

        vm.onResumed()
        advanceUntilIdle()
        assertEquals(1, platform.installed.size)
    }

    @Test
    fun `未等待授权时回前台不动作`() = runTest {
        seedProbedDownloaded()
        val vm = newVm()
        advanceUntilIdle()
        platform.canInstall = true
        vm.onResumed()
        advanceUntilIdle()
        assertEquals(0, platform.installed.size)
    }

    @Test
    fun `check 与 setAutoCheck 转发`() = runTest {
        http.textResult = HttpText.Ok(
            """{"tag_name":"v1.0.0","body":"","html_url":"https://h","assets":[]}""",
            "api.github.com",
        )
        val vm = newVm()
        vm.check()
        advanceUntilIdle()
        assertEquals(1, http.requestedUrls.size)

        vm.setAutoCheck(false)
        advanceUntilIdle()
        assertEquals(false, prefs.autoCheckFlow.value)
    }
}
