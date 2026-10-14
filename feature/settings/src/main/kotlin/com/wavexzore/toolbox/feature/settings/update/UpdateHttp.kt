package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** 文本请求结果（语义化，T-305）。 */
sealed interface HttpText {
    data class Ok(val text: String, val finalHost: String) : HttpText
    data class HttpError(val code: Int, val retryAfterEpochSec: Long?) : HttpText
    data class IoError(val kind: IoKind) : HttpText
}

/** 下载结果。 */
sealed interface DownloadResult {
    data object Ok : DownloadResult
    data class HttpError(val code: Int) : DownloadResult
    data class IoError(val kind: IoKind) : DownloadResult
}

/** IO 错误分类（映射到 UI 文案的关键维度）。 */
enum class IoKind { NoNetwork, Timeout, Interrupted, Storage, Other }

/** 网络窄接口（可注入 fake；T-305）。实现负责全部 HTTP 细节。 */
interface UpdateHttp {
    suspend fun getText(url: String, maxBytes: Long = UpdateConfig.API_READ_CAP_BYTES): HttpText

    suspend fun downloadTo(url: String, file: File, onProgress: (bytes: Long, total: Long) -> Unit): DownloadResult
}

/**
 * JDK HttpURLConnection 实现（T-305）：无三方依赖；恒无条件 GET；
 * 超时/UA/Accept/重定向跟随（final host 透出）/读上限/进度节流/错误分类。
 */
@Singleton
class UrlConnectionUpdateHttp @Inject constructor(
    private val platform: UpdatePlatform,
    private val dispatchers: AppDispatchers,
) : UpdateHttp {

    override suspend fun getText(url: String, maxBytes: Long): HttpText = withContext(dispatchers.io) {
        var conn: HttpURLConnection? = null
        try {
            conn = open(url, UpdateConfig.CHECK_CONNECT_TIMEOUT_MS, UpdateConfig.CHECK_READ_TIMEOUT_MS)
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext HttpText.HttpError(code, retryAfterEpochSec(conn))
            }
            val text = conn.inputStream.use { it.readLimited(maxBytes) }
            HttpText.Ok(text, conn.url?.host.orEmpty())
        } catch (e: IOException) {
            HttpText.IoError(classifyIo(e))
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun downloadTo(
        url: String,
        file: File,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): DownloadResult = withContext(dispatchers.io) {
        var conn: HttpURLConnection? = null
        var storagePhase = false
        try {
            conn = open(url, UpdateConfig.DOWNLOAD_CONNECT_TIMEOUT_MS, UpdateConfig.DOWNLOAD_READ_TIMEOUT_MS)
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadResult.HttpError(code)
            }
            val total = conn.contentLengthLong
            file.parentFile?.mkdirs()
            var bytes = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                storagePhase = true
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        storagePhase = false
                        output.write(buffer, 0, read)
                        storagePhase = true
                        bytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= UpdateConfig.PROGRESS_MIN_INTERVAL_MS) {
                            lastEmit = now
                            onProgress(bytes, total)
                        }
                    }
                }
            }
            onProgress(bytes, total)
            // JDK 对「提前断流」可能静默 EOF：显式比对 content-length 兜底归类
            if (total > 0 && bytes < total) {
                return@withContext DownloadResult.IoError(IoKind.Interrupted)
            }
            DownloadResult.Ok
        } catch (e: IOException) {
            val kind = classifyIo(e)
            DownloadResult.IoError(if (kind == IoKind.Other && storagePhase) IoKind.Storage else kind)
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(url: String, connectMs: Int, readMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UpdateConfig.userAgent(platform.versionName(), platform.sdkInt()))
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        return conn
    }

    /** 403/429 时提取重试时间：Retry-After（秒）→ 绝对化；否则 x-ratelimit-reset（秒）。 */
    private fun retryAfterEpochSec(conn: HttpURLConnection): Long? {
        conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
            ?.let { return System.currentTimeMillis() / 1000 + it }
        return conn.getHeaderField("x-ratelimit-reset")?.trim()?.toLongOrNull()
    }

    private fun classifyIo(e: IOException): IoKind = when {
        e is UnknownHostException || e is ConnectException || e is NoRouteToHostException -> IoKind.NoNetwork
        e is SocketTimeoutException -> IoKind.Timeout
        e is EOFException || e is SocketException -> IoKind.Interrupted
        e.message?.contains("Premature EOF", ignoreCase = true) == true -> IoKind.Interrupted
        e is FileNotFoundException -> IoKind.Storage
        else -> IoKind.Other
    }

    private fun InputStream.readLimited(maxBytes: Long): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < maxBytes) {
            val want = minOf(buffer.size.toLong(), maxBytes - out.size()).toInt()
            val read = read(buffer, 0, want)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
