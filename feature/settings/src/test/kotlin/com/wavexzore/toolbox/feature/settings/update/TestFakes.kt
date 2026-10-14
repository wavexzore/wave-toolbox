package com.wavexzore.toolbox.feature.settings.update

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 测试共用假件（四窄接口；T-305/T-306）。 */

class FakeUpdateHttp : UpdateHttp {
    var textResult: HttpText = HttpText.IoError(IoKind.Other)
    val requestedUrls = mutableListOf<String>()
    var downloadResult: DownloadResult = DownloadResult.Ok
    var downloadTotal: Long = 0L
    var downloadBody: ((File) -> Unit)? = null
    val downloadedTo = mutableListOf<File>()
    var getGate: CompletableDeferred<Unit>? = null
    var downloadGate: CompletableDeferred<Unit>? = null

    override suspend fun getText(url: String, maxBytes: Long): HttpText {
        getGate?.await()
        requestedUrls += url
        return textResult
    }

    override suspend fun downloadTo(
        url: String,
        file: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): DownloadResult {
        downloadGate?.await()
        downloadedTo += file
        downloadBody?.invoke(file) ?: file.writeBytes(ByteArray(downloadTotal.toInt().coerceAtLeast(0)))
        onProgress(downloadTotal, downloadTotal)
        return downloadResult
    }
}

class FakeUpdatePrefs : UpdatePrefs {
    val autoCheckFlow = MutableStateFlow(true)
    val lastCheckAtFlow = MutableStateFlow(0L)
    val skippedTagFlow = MutableStateFlow("")

    override val autoCheck: Flow<Boolean> = autoCheckFlow
    override val lastCheckAt: Flow<Long> = lastCheckAtFlow
    override val skippedTag: Flow<String> = skippedTagFlow

    override suspend fun setAutoCheck(enabled: Boolean) {
        autoCheckFlow.value = enabled
    }

    override suspend fun setLastCheckAt(epochMs: Long) {
        lastCheckAtFlow.value = epochMs
    }

    override suspend fun setSkippedTag(tag: String) {
        skippedTagFlow.value = tag
    }
}

class FakeUpdatePlatform(private val dir: File) : UpdatePlatform {
    var version: String = "1.0.0"
    var sdk: Int = 33
    var canInstall: Boolean = true
    var apkVersion: String? = null
    val installed = mutableListOf<File>()
    var unknownSourcesOpened = 0
    val openedUrls = mutableListOf<String>()

    override fun versionName(): String = version
    override fun sdkInt(): Int = sdk
    override fun canRequestPackageInstalls(): Boolean = canInstall
    override fun startInstall(file: File) {
        installed += file
    }

    override fun openUnknownAppSources() {
        unknownSourcesOpened++
    }

    override fun openUrl(url: String) {
        openedUrls += url
    }

    override fun updatesDir(): File = dir

    override fun readApkVersion(file: File): String? = apkVersion
}

class FixedClock(var now: Long = 0L) : Clock {
    override fun nowMs(): Long = now
}
