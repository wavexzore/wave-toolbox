package com.wavexzore.toolbox.feature.settings.update

import android.util.Log
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 更新状态（唯一事实源；设计 04 §6.1）。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data class Available(val info: ReleaseInfo, val ignored: Boolean) : UpdateState
    data class Downloading(val info: ReleaseInfo, val percent: Int, val bytes: Long, val total: Long) : UpdateState
    data class Downloaded(val file: File, val info: ReleaseInfo?, val apkVersionName: String) : UpdateState
    data class Error(
        val kind: ErrorKind,
        val info: ReleaseInfo? = null,
        val code: Int? = null,
        val resetAtEpochSec: Long? = null,
    ) : UpdateState
}

/** 失败分类（文案全表见设计 04 §10）。 */
enum class ErrorKind { NoNetwork, Timeout, Interrupted, RateLimited, Http, Parse, VerifyFailed, Storage, TooBig }

/**
 * 应用内更新单一事实源（T-306；设计 04 §5-§8）。
 *
 * 状态机约束：
 * - 同一时刻至多一个 [Operation]（检查或下载），由 [op] + [opJob] 唯一持有；
 * - 所有状态迁移都在 [mutex] 内完成，且必须通过 [isCurrent] 校验操作身份；
 * - 取消 = 清空 [op] + 取消 [opJob]，旧 job 的迟到回调因身份不符而全部失效；
 * - 检查/下载均从启动时登记的 [probeJob] 之后开始，避免恢复态与请求交错。
 *
 * 公开动作非 suspend（内部 launch 到自有 scope，离开页面不中断）。
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val http: UpdateHttp,
    private val prefs: UpdatePrefs,
    private val platform: UpdatePlatform,
    private val clock: Clock,
    dispatchers: AppDispatchers,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** 自动检查开关（入口行开关数据源）。 */
    val autoCheck: StateFlow<Boolean> = prefs.autoCheck.stateIn(scope, SharingStarted.Eagerly, true)

    /** 当前操作 id；0 = 空闲。下载进度回调据此丢弃旧 job 的进度。 */
    @Volatile
    private var activeOpId: Long = 0L

    /** 当前唯一操作；同一时刻最多一个。 */
    private var op: Operation? = null

    /** 当前操作的 job；取消后旧 job 的收尾不再改状态。 */
    private var opJob: Job? = null

    private var opSeq: Long = 0L

    private sealed interface Operation {
        val id: Long
        val kind: Kind

        data class Check(
            override val id: Long,
            val force: Boolean,
            val thenDownload: Boolean = false,
        ) : Operation {
            override val kind: Kind get() = Kind.CHECK
        }

        data class Download(
            override val id: Long,
            val info: ReleaseInfo,
            val asset: AssetRef,
        ) : Operation {
            override val kind: Kind get() = Kind.DOWNLOAD
        }
    }

    private enum class Kind { CHECK, DOWNLOAD }

    /** probe 首序：全部公开动作先 join 本任务。 */
    private val probeJob: Job = scope.launch { probe() }

    // ---------------------------------------------------------------- 检查

    /** 冷启动钩子：受 autoCheck 与 24h 节流约束。 */
    fun ensureFreshOnLaunch() = refresh(force = false)

    /** 手动检查（force=true 穿透节流与 autoCheck）。 */
    fun refresh(force: Boolean) = launchAfterProbe { startCheck(force = force) }

    private suspend fun startCheck(force: Boolean, thenDownload: Boolean = false): Boolean {
        mutex.withLock {
            if (op != null || _state.value is UpdateState.Downloading) return false
            val next = Operation.Check(++opSeq, force = force, thenDownload = thenDownload)
            op = next
            activeOpId = next.id
            opJob = scope.launch { runCheck(next) }
        }
        return true
    }

    private suspend fun runCheck(operation: Operation.Check) {
        var thenDownload = false
        try {
            val current = mutex.withLock {
                if (!isCurrent(operation.id)) return
                _state.value
            }
            when (current) {
                is UpdateState.Downloaded -> checkWhileDownloaded(operation, current)
                else -> checkNormal(operation)
            }
            thenDownload = operation.thenDownload && mutex.withLock {
                if (!isCurrent(operation.id)) {
                    false
                } else {
                    when (val state = _state.value) {
                        is UpdateState.Available -> state.info.asset != null
                        is UpdateState.Downloaded -> state.info != null && state.file.exists()
                        else -> false
                    }
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "check failed", t)
            publishCheck(operation.id, UpdateState.Error(mapIo(IoKind.Other)), expected = UpdateState.Checking)
        } finally {
            finish(operation.id)
        }
        if (thenDownload) startDownload()
    }

    private suspend fun checkNormal(operation: Operation.Check) {
        val now = clock.nowMs()
        if (!operation.force) {
            if (!prefs.autoCheck.first()) return
            val last = prefs.lastCheckAt.first()
            if (last <= now && now - last < UpdateConfig.THROTTLE_MS) return
        }
        if (!publishCheck(operation.id, UpdateState.Checking)) return
        Log.i(TAG, "check start force=${operation.force}")
        val result = http.getText(UpdateConfig.API_LATEST_URL)
        runCatching { prefs.setLastCheckAt(clock.nowMs()) }
            .onFailure { Log.w(TAG, "lastCheckAt write failed", it) }
        when (result) {
            is HttpText.Ok -> {
                val info = ReleaseParser.parse(result.text)
                if (info == null) {
                    publishCheck(operation.id, UpdateState.Error(ErrorKind.Parse), expected = UpdateState.Checking)
                    Log.i(TAG, "check done: ${stateSummary()}")
                    return
                }
                val skipped = prefs.skippedTag.first()
                val next = if (SemverCompare.isNewer(info.tag, platform.versionName())) {
                    UpdateState.Available(info, ignored = sameVersion(skipped, info.tag))
                } else {
                    UpdateState.UpToDate
                }
                publishCheck(operation.id, next, expected = UpdateState.Checking)
            }
            is HttpText.HttpError -> {
                publishCheck(
                    operation.id,
                    when (result.code) {
                        HTTP_NOT_FOUND -> UpdateState.NoRelease
                        HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
                            UpdateState.Error(ErrorKind.RateLimited, code = result.code, resetAtEpochSec = result.retryAfterEpochSec)
                        else -> UpdateState.Error(ErrorKind.Http, code = result.code)
                    },
                    expected = UpdateState.Checking,
                )
            }
            is HttpText.IoError -> publishCheck(
                operation.id,
                UpdateState.Error(mapIo(result.kind)),
                expected = UpdateState.Checking,
            )
        }
        Log.i(TAG, "check done: ${stateSummary()}")
    }

    /** Downloaded 时的检查：file 在 → 保持（同版本/非成功请求）/异版本更新 → Available；file 失 → 常规语义。 */
    private suspend fun checkWhileDownloaded(operation: Operation.Check, current: UpdateState.Downloaded) {
        if (!current.file.exists()) {
            checkNormal(operation)
            return
        }
        val result = http.getText(UpdateConfig.API_LATEST_URL)
        runCatching { prefs.setLastCheckAt(clock.nowMs()) }
            .onFailure { Log.w(TAG, "lastCheckAt write failed", it) }
        if (result !is HttpText.Ok) {
            Log.i(TAG, "check(downloaded) kept: ${stateSummary()}")
            return // 404/失败：保持 Downloaded（file 在）
        }
        val info = ReleaseParser.parse(result.text) ?: run {
            Log.i(TAG, "check(downloaded) parse-fail kept: ${stateSummary()}")
            return
        }
        val baseline = current.info?.tag ?: current.apkVersionName
        when {
            sameVersion(info.tag, baseline) -> publishCheck(
                operation.id,
                if (current.info == null) current.copy(info = info) else current,
                expected = current,
            )
            SemverCompare.isNewer(info.tag, baseline) -> {
                val skipped = prefs.skippedTag.first()
                publishCheck(
                    operation.id,
                    UpdateState.Available(info, ignored = sameVersion(skipped, info.tag)),
                    expected = current,
                )
            }
            else -> Unit // 不更新：保持 Downloaded
        }
    }

    /**
     * 检查结果只能在仍是当前操作、未进入 Downloading 且状态仍是 [expected] 时落地。
     * `expected=null` 表示检查起点（Idle/UpToDate/NoRelease/Available/Error 均可）。
     */
    private suspend fun publishCheck(opId: Long, next: UpdateState, expected: UpdateState? = null): Boolean =
        mutex.withLock {
            if (!isCurrent(opId)) return@withLock false
            val current = _state.value
            if (current is UpdateState.Downloading || (expected != null && current != expected)) {
                false
            } else {
                _state.value = next
                true
            }
        }

    // ---------------------------------------------------------------- 下载

    /** 下载动作（门卫：asset 可得且无其它操作）。 */
    fun download() = launchAfterProbe { startDownload() }

    private suspend fun startDownload(): Boolean {
        mutex.withLock {
            if (op != null) return false
            val candidate = when (val current = _state.value) {
                is UpdateState.Available -> current.info
                is UpdateState.Error -> current.info
                is UpdateState.Downloaded -> current.info
                else -> null
            } ?: return false
            val asset = candidate.asset ?: return false
            if (asset.size > UpdateConfig.MAX_APK_BYTES) {
                _state.value = UpdateState.Error(ErrorKind.TooBig, candidate)
                return false
            }
            val next = Operation.Download(++opSeq, info = candidate, asset = asset)
            op = next
            activeOpId = next.id
            // 在锁内进入 Downloading，避免 cancel 恰好落在「已登记 job、尚未置态」的窗口。
            _state.value = UpdateState.Downloading(candidate, 0, 0, asset.size)
            opJob = scope.launch { runDownload(next) }
        }
        return true
    }

    private suspend fun runDownload(operation: Operation.Download) {
        try {
            val dir = platform.updatesDir().apply { mkdirs() }
            dir.listFiles { f -> f.isFile }?.forEach { it.delete() }
            val target = File(dir, UpdateConfig.sanitizeForFileName(operation.asset.name))
            Log.i(
                TAG,
                "download start tag=${operation.info.tag} size=${operation.asset.size} digest=${operation.asset.digest != null}",
            )

            val result = http.downloadTo(operation.asset.url, target) { bytes, total ->
                _state.update { current ->
                    if (activeOpId == operation.id && current is UpdateState.Downloading) {
                        current.copy(percent = percent(bytes, total), bytes = bytes, total = total)
                    } else {
                        current
                    }
                }
            }
            when (result) {
                is DownloadResult.Ok -> finishDownload(operation, target)
                is DownloadResult.HttpError -> publishDownloadTerminal(operation.id) {
                    _state.value = when (result.code) {
                        HTTP_NOT_FOUND -> UpdateState.Error(ErrorKind.Http, operation.info, code = result.code)
                        HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
                            UpdateState.Error(ErrorKind.RateLimited, operation.info, code = result.code)
                        else -> UpdateState.Error(ErrorKind.Http, operation.info, code = result.code)
                    }
                }
                is DownloadResult.IoError -> publishDownloadTerminal(operation.id) {
                    _state.value = UpdateState.Error(mapIo(result.kind), operation.info)
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            publishDownloadTerminal(operation.id) {
                _state.value = UpdateState.Error(mapIo(IoKind.Other), operation.info)
            }
            Log.w(TAG, "download failed", t)
        } finally {
            finish(operation.id)
        }
    }

    /** 只有仍是当前操作且仍在 Downloading 时才允许写入终态。 */
    private suspend fun publishDownloadTerminal(opId: Long, publish: () -> Unit) {
        mutex.withLock {
            if (isCurrent(opId) && _state.value is UpdateState.Downloading) publish()
        }
    }

    private suspend fun finishDownload(operation: Operation.Download, file: File) {
        val info = operation.info
        val asset = operation.asset
        val sizeOk = asset.size <= 0 || file.length() == asset.size
        val digestOk = asset.digest == null ||
            Sha256.hex(file).equals(asset.digest.substringAfter(':'), ignoreCase = true)
        if (!sizeOk || !digestOk) {
            Log.w(TAG, "verify failed: sizeOk=$sizeOk digestOk=$digestOk tag=${info.tag}")
            publishDownloadTerminal(operation.id) {
                _state.value = UpdateState.Error(ErrorKind.VerifyFailed, info)
            }
            return
        }
        if (asset.digest == null) Log.w(TAG, "digest missing, size+signature only")
        val apkVersion = platform.readApkVersion(file) ?: info.tag
        mutex.withLock {
            if (isCurrent(operation.id) && _state.value is UpdateState.Downloading) {
                _state.value = UpdateState.Downloaded(file, info, apkVersion)
            }
        }
        Log.i(TAG, "download verified tag=${info.tag}")
    }

    /** 取消下载/校验：回 Available(info)，文件保留（下次 download 先清重下）。 */
    fun cancelDownload() = launchAfterProbe {
        mutex.withLock {
            val current = _state.value
            if (op?.kind != Kind.DOWNLOAD || current !is UpdateState.Downloading) return@withLock
            // 先清空当前操作，再取消 job：旧 job 的 finally/错误分支会因身份不符而失效。
            val job = opJob
            op = null
            opJob = null
            activeOpId = 0L
            job?.cancel()
            val skipped = prefs.skippedTag.first()
            _state.value = UpdateState.Available(current.info, ignored = sameVersion(skipped, current.info.tag))
            Log.i(TAG, "download cancelled")
        }
    }

    /** 自愈出口：Downloaded/下载侧 Error 重新下载；probe 恢复（info=null）先检查再续。 */
    fun redownload() = launchAfterProbe {
        val info = mutex.withLock {
            when (val current = _state.value) {
                is UpdateState.Available -> current.info
                is UpdateState.Downloaded -> current.info
                is UpdateState.Error -> current.info
                else -> null
            }
        }
        if (info?.asset != null) startDownload() else startCheck(force = true, thenDownload = true)
    }

    /** 失败重试：可重下的下载错误 → 重下；TooBig/安装失败/检查侧 → 强制重查。 */
    fun retry() = launchAfterProbe {
        val error = mutex.withLock { _state.value as? UpdateState.Error } ?: return@launchAfterProbe
        if (error.info?.asset != null && error.kind.isDownloadRetryable()) {
            startDownload()
        } else {
            // TooBig 重下同一 asset 只会再次 TooBig；安装失败重下同一 APK 也大概率再失败。
            // 回到检查侧，给新版本/更小包一次机会。
            startCheck(force = true)
        }
    }

    private fun ErrorKind.isDownloadRetryable(): Boolean = when (this) {
        ErrorKind.TooBig, ErrorKind.Storage -> false
        else -> true
    }

    // ---------------------------------------------------------------- 忽略 / 开关 / 关闭错误

    fun skipCurrent() = launchAfterProbe {
        val current = mutex.withLock { _state.value as? UpdateState.Available } ?: return@launchAfterProbe
        prefs.setSkippedTag(current.info.tag)
        mutex.withLock {
            val now = _state.value
            if (now is UpdateState.Available && now.info.tag == current.info.tag) {
                _state.value = now.copy(ignored = true)
            }
        }
    }

    fun clearSkip() = launchAfterProbe {
        prefs.setSkippedTag("")
        mutex.withLock {
            val current = _state.value
            if (current is UpdateState.Available && current.ignored) _state.value = current.copy(ignored = false)
        }
    }

    /** 自动检查开关；开启时补一次（受节流保护）。 */
    fun setAutoCheck(enabled: Boolean) = launchAfterProbe {
        prefs.setAutoCheck(enabled)
        if (enabled) startCheck(force = false)
    }

    fun dismissError() = launchAfterProbe {
        mutex.withLock { if (_state.value is UpdateState.Error) _state.value = UpdateState.Idle }
    }

    // ---------------------------------------------------------------- 安装

    fun canRequestPackageInstalls(): Boolean = platform.canRequestPackageInstalls()

    /** 当前应用 versionName（UI 展示）。 */
    fun currentVersionName(): String = platform.versionName()

    /** 「去 Release 页」：无具体链接时回落仓库 Releases 页。 */
    fun openReleasePage(url: String?) = platform.openUrl(url ?: UpdateConfig.RELEASES_PAGE)

    /** 安装：file 预检 → 未知来源门控（未授权跳设置，调用方 onResume 续）→ ACTION_VIEW。 */
    fun install() = launchAfterProbe {
        val current = mutex.withLock { _state.value as? UpdateState.Downloaded } ?: return@launchAfterProbe
        if (!current.file.exists()) {
            mutex.withLock {
                if (_state.value is UpdateState.Downloaded) {
                    _state.value = UpdateState.Error(ErrorKind.Storage, current.info)
                }
            }
            return@launchAfterProbe
        }
        if (!platform.canRequestPackageInstalls()) {
            runCatching { platform.openUnknownAppSources() }
                .onFailure { Log.w(TAG, "open unknown sources failed", it) }
            return@launchAfterProbe
        }
        runCatching { platform.startInstall(current.file) }
            .onFailure {
                mutex.withLock {
                    if (_state.value is UpdateState.Downloaded) {
                        _state.value = UpdateState.Error(ErrorKind.Storage, current.info)
                    }
                }
            }
    }

    // ---------------------------------------------------------------- 内部

    private fun launchAfterProbe(block: suspend () -> Unit) {
        scope.launch {
            probeJob.join()
            block()
        }
    }

    private fun isCurrent(opId: Long): Boolean = op?.id == opId

    private suspend fun finish(opId: Long) {
        mutex.withLock {
            if (op?.id == opId) {
                op = null
                opJob = null
                activeOpId = 0L
            }
        }
    }

    private suspend fun probe() {
        runCatching {
            val files = platform.updatesDir().listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
                .orEmpty()
            if (files.size != 1) return@runCatching
            val file = files.first()
            val apkVersion = platform.readApkVersion(file) ?: return@runCatching
            if (apkVersion == platform.versionName()) {
                file.delete()
                Log.i(TAG, "probe: stale same-version file cleaned")
            } else {
                mutex.withLock {
                    if (_state.value == UpdateState.Idle) {
                        _state.value = UpdateState.Downloaded(file, info = null, apkVersionName = apkVersion)
                    }
                }
                Log.i(TAG, "probe: restored downloaded apk $apkVersion")
            }
        }.onFailure { Log.w(TAG, "probe failed", it) }
    }

    /** 诊断快照（T-310）。 */
    fun stateSummary(): String = when (val current = state.value) {
        is UpdateState.Idle -> "Idle"
        is UpdateState.Checking -> "Checking"
        is UpdateState.UpToDate -> "UpToDate"
        is UpdateState.NoRelease -> "NoRelease"
        is UpdateState.Available -> "Available(${current.info.tag}${if (current.ignored) ",ignored" else ""})"
        is UpdateState.Downloading -> "Downloading(${current.percent}%)"
        is UpdateState.Downloaded -> "Downloaded(${current.info?.tag ?: current.apkVersionName})"
        is UpdateState.Error -> "Error(${current.kind}${current.code?.let { ",$it" }.orEmpty()})"
    }

    private fun percent(bytes: Long, total: Long): Int =
        if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0

    private fun sameVersion(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val pa = SemverCompare.parse(a) ?: return a.trim().equals(b.trim(), ignoreCase = true)
        val pb = SemverCompare.parse(b) ?: return false
        return pa.contentEquals(pb)
    }

    private fun mapIo(kind: IoKind): ErrorKind = when (kind) {
        IoKind.NoNetwork -> ErrorKind.NoNetwork
        IoKind.Timeout -> ErrorKind.Timeout
        IoKind.Interrupted -> ErrorKind.Interrupted
        IoKind.Storage -> ErrorKind.Storage
        IoKind.Other -> ErrorKind.Http
    }

    private companion object {
        const val TAG = "WaveUpdate"
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
