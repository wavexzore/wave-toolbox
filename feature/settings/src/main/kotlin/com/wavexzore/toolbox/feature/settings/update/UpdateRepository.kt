package com.wavexzore.toolbox.feature.settings.update

import android.util.Log
import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
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
 * - 全部动作非 suspend（内部 launch 到自有 scope，离开页面不中断）；probe 首序；
 * - 合并规则：Downloading 中 refresh 短路；Downloaded 且 file 在 → 保持/回填 info；file 失 → 常规语义；
 * - 门卫：download 需 asset 可得（Available/Error.info）且无 activeJob；失败保文件；retry 按 info 分流。
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

    private var activeJob: Job? = null
    private var refreshRunning = false
    private var pendingRedownload = false

    /** 下载代次：取消/新下载后旧 job 的收尾不得改动新状态。 */
    @Volatile
    private var downloadGeneration = 0L

    /** probe 首序：全部公开动作先 join 本任务。 */
    private val probeJob: Job = scope.launch { probe() }

    // ---------------------------------------------------------------- 检查

    /** 冷启动钩子：受 autoCheck 与 24h 节流约束。 */
    fun ensureFreshOnLaunch() = refresh(force = false)

    /** 手动检查（force=true 穿透节流与 autoCheck）；检查发现异版本时按合并规则处理。 */
    fun refresh(force: Boolean) {
        scope.launch {
            probeJob.join()
            val proceed = mutex.withLock {
                val current = _state.value
                if (current is UpdateState.Downloading || refreshRunning) {
                    false
                } else {
                    refreshRunning = true
                    true
                }
            }
            if (!proceed) return@launch
            try {
                when (val current = mutex.withLock { _state.value }) {
                    is UpdateState.Downloaded -> refreshWhileDownloaded(current)
                    else -> refreshNormal(force)
                }
            } finally {
                mutex.withLock { refreshRunning = false }
                maybeConsumePendingRedownload()
            }
        }
    }

    /**
     * 检查结果只能在未进入 Downloading 且状态仍是检查起点时落地；
     * [expected] 用于拦住「检查期间已有新下载完成」的迟到结果。
     */
    private suspend fun publishCheck(next: UpdateState, expected: UpdateState? = null): Boolean = mutex.withLock {
        val current = _state.value
        if (current is UpdateState.Downloading || (expected != null && current != expected)) {
            false
        } else {
            _state.value = next
            true
        }
    }

    private suspend fun refreshNormal(force: Boolean) {
        val now = clock.nowMs()
        if (!force) {
            if (!prefs.autoCheck.first()) return
            val last = prefs.lastCheckAt.first()
            if (last <= now && now - last < UpdateConfig.THROTTLE_MS) return
        }
        if (!publishCheck(UpdateState.Checking)) return
        Log.i(TAG, "check start force=$force")
        val result = http.getText(UpdateConfig.API_LATEST_URL)
        runCatching { prefs.setLastCheckAt(clock.nowMs()) }
            .onFailure { Log.w(TAG, "lastCheckAt write failed", it) }
        when (result) {
            is HttpText.Ok -> {
                val info = ReleaseParser.parse(result.text)
                if (info == null) {
                    publishCheck(UpdateState.Error(ErrorKind.Parse), expected = UpdateState.Checking)
                    Log.i(TAG, "check done: ${stateSummary()}")
                    return
                }
                val skipped = prefs.skippedTag.first()
                val next = if (SemverCompare.isNewer(info.tag, platform.versionName())) {
                    UpdateState.Available(info, ignored = sameVersion(skipped, info.tag))
                } else {
                    UpdateState.UpToDate
                }
                publishCheck(next, expected = UpdateState.Checking)
            }
            is HttpText.HttpError -> {
                publishCheck(
                    when (result.code) {
                        HTTP_NOT_FOUND -> UpdateState.NoRelease
                        HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
                            UpdateState.Error(ErrorKind.RateLimited, code = result.code, resetAtEpochSec = result.retryAfterEpochSec)
                        else -> UpdateState.Error(ErrorKind.Http, code = result.code)
                    },
                    expected = UpdateState.Checking,
                )
            }
            is HttpText.IoError -> publishCheck(UpdateState.Error(mapIo(result.kind)), expected = UpdateState.Checking)
        }
        Log.i(TAG, "check done: ${stateSummary()}")
    }

    /** Downloaded 时的检查：file 在 → 保持（同版本/非成功请求）/异版本更新 → Available；file 失 → 常规语义。 */
    private suspend fun refreshWhileDownloaded(current: UpdateState.Downloaded) {
        if (!current.file.exists()) {
            refreshNormal(force = true)
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
                if (current.info == null) current.copy(info = info) else current,
                expected = current,
            )
            SemverCompare.isNewer(info.tag, baseline) -> {
                val skipped = prefs.skippedTag.first()
                publishCheck(UpdateState.Available(info, ignored = sameVersion(skipped, info.tag)), expected = current)
            }
            else -> Unit // 不更新：保持 Downloaded
        }
    }

    // ---------------------------------------------------------------- 下载

    private fun startDownload() {
        scope.launch {
            probeJob.join()
            val job = coroutineContext[Job]
            val plan = mutex.withLock {
                if (activeJob?.isActive == true) return@launch
                val candidate = when (val current = _state.value) {
                    is UpdateState.Available -> current.info
                    is UpdateState.Error -> current.info
                    is UpdateState.Downloaded -> current.info
                    else -> null
                } ?: return@launch
                val asset = candidate.asset ?: return@launch
                if (asset.size > UpdateConfig.MAX_APK_BYTES) {
                    _state.value = UpdateState.Error(ErrorKind.TooBig, candidate)
                    return@launch
                }
                val generation = ++downloadGeneration
                activeJob = job
                // 在锁内进入 Downloading，避免 cancel 恰好落在「已登记 job、尚未置态」的窗口。
                _state.value = UpdateState.Downloading(candidate, 0, 0, asset.size)
                DownloadPlan(candidate, asset, generation)
            }
            try {
                val dir = platform.updatesDir().apply { mkdirs() }
                dir.listFiles { f -> f.isFile }?.forEach { it.delete() }
                val target = File(dir, UpdateConfig.sanitizeForFileName(plan.asset.name))
                Log.i(TAG, "download start tag=${plan.info.tag} size=${plan.asset.size} digest=${plan.asset.digest != null}")

                val result = http.downloadTo(plan.asset.url, target) { bytes, total ->
                    _state.update { current ->
                        if (plan.generation != downloadGeneration || current !is UpdateState.Downloading) {
                            current
                        } else {
                            current.copy(percent = percent(bytes, total), bytes = bytes, total = total)
                        }
                    }
                }
                when (result) {
                    is DownloadResult.Ok -> finishDownload(plan, target)
                    is DownloadResult.HttpError -> publishDownloadError(plan.generation) {
                        _state.value = when (result.code) {
                            HTTP_NOT_FOUND -> UpdateState.Error(ErrorKind.Http, plan.info, code = result.code)
                            HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
                                UpdateState.Error(ErrorKind.RateLimited, plan.info, code = result.code)
                            else -> UpdateState.Error(ErrorKind.Http, plan.info, code = result.code)
                        }
                    }
                    is DownloadResult.IoError -> publishDownloadError(plan.generation) {
                        _state.value = UpdateState.Error(mapIo(result.kind), plan.info)
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                publishDownloadError(plan.generation) {
                    _state.value = UpdateState.Error(mapIo(IoKind.Other), plan.info)
                }
                Log.w(TAG, "download failed", t)
            } finally {
                mutex.withLock { if (job != null && activeJob === job) activeJob = null }
            }
        }
    }

    /** 只有仍属于当前代次且仍在 Downloading 时才允许写入终态。 */
    private suspend fun publishDownloadError(generation: Long, publish: () -> Unit) {
        mutex.withLock {
            if (generation == downloadGeneration && _state.value is UpdateState.Downloading) publish()
        }
    }

    /** 下载动作（门卫：asset 可得且无 activeJob）。 */
    fun download() = startDownload()

    /** 自愈出口：Downloaded/下载侧 Error 重新下载；probe 恢复（info=null）先 refresh 再续。 */
    fun redownload() {
        scope.launch {
            probeJob.join()
            val info = mutex.withLock {
                when (val current = _state.value) {
                    is UpdateState.Available -> current.info
                    is UpdateState.Downloaded -> current.info
                    is UpdateState.Error -> current.info
                    else -> null
                }
            }
            if (info?.asset != null) {
                startDownload()
            } else {
                mutex.withLock { pendingRedownload = true }
                refresh(force = true)
            }
        }
    }

    /** 取消下载/校验：回 Available(info)，文件保留（下次 download 先清重下）。 */
    fun cancelDownload() {
        scope.launch {
            probeJob.join()
            mutex.withLock {
                val current = _state.value
                if (current is UpdateState.Downloading) {
                    // 先推进代次，再取消：旧 job 的 finally/错误分支不会再改状态。
                    downloadGeneration++
                    activeJob?.cancel()
                    val skipped = prefs.skippedTag.first()
                    _state.value = UpdateState.Available(current.info, ignored = sameVersion(skipped, current.info.tag))
                }
            }
        }
    }

    /** 失败重试：可重下的下载错误 → 重下；TooBig/安装失败/检查侧 → 强制重查。 */
    fun retry() {
        scope.launch {
            probeJob.join()
            val error = mutex.withLock { _state.value as? UpdateState.Error } ?: return@launch
            if (error.info?.asset != null && error.kind.isDownloadRetryable()) {
                startDownload()
            } else {
                // TooBig 重下同一 asset 只会再次 TooBig；安装失败重下同一 APK 也大概率再失败。
                // 回到检查侧，给新版本/更小包一次机会。
                refresh(force = true)
            }
        }
    }

    private fun ErrorKind.isDownloadRetryable(): Boolean = when (this) {
        ErrorKind.TooBig, ErrorKind.Storage -> false
        else -> true
    }

    // ---------------------------------------------------------------- 忽略 / 开关 / 关闭错误

    fun skipCurrent() {
        scope.launch {
            probeJob.join()
            val current = mutex.withLock { _state.value } as? UpdateState.Available ?: return@launch
            prefs.setSkippedTag(current.info.tag)
            mutex.withLock { _state.value = current.copy(ignored = true) }
        }
    }

    fun clearSkip() {
        scope.launch {
            probeJob.join()
            prefs.setSkippedTag("")
            mutex.withLock {
                val current = _state.value
                if (current is UpdateState.Available && current.ignored) _state.value = current.copy(ignored = false)
            }
        }
    }

    /** 自动检查开关；开启时补一次（受节流保护）。 */
    fun setAutoCheck(enabled: Boolean) {
        scope.launch {
            probeJob.join()
            prefs.setAutoCheck(enabled)
            if (enabled) refresh(force = false)
        }
    }

    fun dismissError() {
        scope.launch {
            probeJob.join()
            mutex.withLock { if (_state.value is UpdateState.Error) _state.value = UpdateState.Idle }
        }
    }

    // ---------------------------------------------------------------- 安装

    fun canRequestPackageInstalls(): Boolean = platform.canRequestPackageInstalls()

    /** 当前应用 versionName（UI 展示）。 */
    fun currentVersionName(): String = platform.versionName()

    /** 「去 Release 页」：无具体链接时回落仓库 Releases 页。 */
    fun openReleasePage(url: String?) = platform.openUrl(url ?: UpdateConfig.RELEASES_PAGE)

    /** 安装：file 预检 → 未知来源门控（未授权跳设置，调用方 onResume 续）→ ACTION_VIEW。 */
    fun install() {
        scope.launch {
            probeJob.join()
            val current = mutex.withLock { _state.value } as? UpdateState.Downloaded ?: return@launch
            if (!current.file.exists()) {
                mutex.withLock { _state.value = UpdateState.Error(ErrorKind.Storage, current.info) }
                return@launch
            }
            if (!platform.canRequestPackageInstalls()) {
                runCatching { platform.openUnknownAppSources() }
                    .onFailure { Log.w(TAG, "open unknown sources failed", it) }
                return@launch
            }
            runCatching { platform.startInstall(current.file) }
                .onFailure { mutex.withLock { _state.value = UpdateState.Error(ErrorKind.Storage, current.info) } }
        }
    }

    // ---------------------------------------------------------------- 内部

    private data class DownloadPlan(val info: ReleaseInfo, val asset: AssetRef, val generation: Long)

    private suspend fun finishDownload(plan: DownloadPlan, file: File) {
        val info = plan.info
        val asset = plan.asset
        val sizeOk = asset.size <= 0 || file.length() == asset.size
        val digestOk = asset.digest == null ||
            Sha256.hex(file).equals(asset.digest.substringAfter(':'), ignoreCase = true)
        if (!sizeOk || !digestOk) {
            Log.w(TAG, "verify failed: sizeOk=$sizeOk digestOk=$digestOk tag=${info.tag}")
            publishDownloadError(plan.generation) {
                _state.value = UpdateState.Error(ErrorKind.VerifyFailed, info)
            }
            return
        }
        if (asset.digest == null) Log.w(TAG, "digest missing, size+signature only")
        val apkVersion = platform.readApkVersion(file) ?: info.tag
        mutex.withLock {
            if (plan.generation == downloadGeneration && _state.value is UpdateState.Downloading) {
                _state.value = UpdateState.Downloaded(file, info, apkVersion)
            }
        }
        Log.i(TAG, "download verified tag=${info.tag}")
    }

    private fun maybeConsumePendingRedownload() {
        scope.launch {
            val fire = mutex.withLock {
                if (!pendingRedownload) return@launch
                when (val current = _state.value) {
                    is UpdateState.Checking, is UpdateState.Downloading -> false
                    is UpdateState.Available -> {
                        pendingRedownload = false
                        current.info.asset != null
                    }
                    is UpdateState.Downloaded -> {
                        pendingRedownload = false
                        current.info != null && current.file.exists()
                    }
                    else -> {
                        pendingRedownload = false
                        false
                    }
                }
            }
            if (fire) startDownload()
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
