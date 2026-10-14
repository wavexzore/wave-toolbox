package com.wavexzore.toolbox.feature.settings.update

import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * [UrlConnectionUpdateHttp] 行为锁定（T-305）。
 * 用裸 ServerSocket 手写最小 HTTP/1.1 服务（零依赖；Android 单测类路径无 com.sun.net.httpserver）。
 */
class UrlConnectionUpdateHttpTest {

    private lateinit var server: TinyServer
    private lateinit var baseUrl: String
    private lateinit var tempDir: File

    private val dispatchers = AppDispatchers(
        Dispatchers.IO, Dispatchers.IO, Dispatchers.IO, Dispatchers.IO, Dispatchers.IO, Dispatchers.IO,
    )

    private val http = UrlConnectionUpdateHttp(
        platform = FakeUpdatePlatform(File(System.getProperty("java.io.tmpdir")!!)),
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("waveupd-http").toFile()
        server = TinyServer()
        server.start()
        baseUrl = "http://127.0.0.1:${server.port}"
    }

    @AfterTest
    fun tearDown() {
        server.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `200 返回文本并透出 host 与请求头`() = runTest {
        server.route("/ok") { s -> server.respond(s, 200, """{"tag_name":"v1.0.0"}""".toByteArray()) }
        val ok = assertNotNull(http.getText("$baseUrl/ok") as? HttpText.Ok)
        assertEquals("""{"tag_name":"v1.0.0"}""", ok.text)
        assertEquals("127.0.0.1", ok.finalHost)
        assertTrue(server.captured["user-agent"]!!.startsWith("wave-toolbox/"))
        assertEquals("application/vnd.github+json", server.captured["accept"])
    }

    @Test
    fun `404 与 500 映射 HttpError`() = runTest {
        server.route("/404") { s -> server.respond(s, 404, "not found".toByteArray()) }
        server.route("/500") { s -> server.respond(s, 500, "boom".toByteArray()) }
        assertEquals(404, (http.getText("$baseUrl/404") as HttpText.HttpError).code)
        assertEquals(500, (http.getText("$baseUrl/500") as HttpText.HttpError).code)
    }

    @Test
    fun `403 透出限流重置时间`() = runTest {
        server.route("/403") { s ->
            server.respond(s, 403, "rate limited".toByteArray(), mapOf("x-ratelimit-reset" to "1900000000"))
        }
        val error = http.getText("$baseUrl/403") as HttpText.HttpError
        assertEquals(403, error.code)
        assertEquals(1_900_000_000L, error.retryAfterEpochSec)
    }

    @Test
    fun `读上限截断超长响应`() = runTest {
        server.route("/big") { s -> server.respond(s, 200, ByteArray(300_000) { 'a'.code.toByte() }) }
        val ok = http.getText("$baseUrl/big", maxBytes = 100_000) as HttpText.Ok
        assertEquals(100_000, ok.text.length)
    }

    @Test
    fun `302 重定向被跟随`() = runTest {
        server.route("/ok") { s -> server.respond(s, 200, "final".toByteArray()) }
        server.route("/redirect") { s ->
            server.respond(s, 302, ByteArray(0), mapOf("Location" to "$baseUrl/ok"))
        }
        val ok = http.getText("$baseUrl/redirect") as HttpText.Ok
        assertEquals("final", ok.text)
    }

    @Test
    fun `下载写入文件并上报进度`() = runTest {
        val payload = ByteArray(100_000) { (it % 251).toByte() }
        server.route("/apk") { s -> server.respond(s, 200, payload) }
        val target = File(tempDir, "wave-toolbox-v1.0.0.apk")
        val events = mutableListOf<Long>()
        val result = http.downloadTo("$baseUrl/apk", target) { bytes, _ -> events += bytes }
        assertEquals(DownloadResult.Ok, result)
        assertEquals(payload.size.toLong(), target.length())
        assertEquals(payload.size.toLong(), events.last())
    }

    @Test
    fun `下载 404 映射 HttpError`() = runTest {
        server.route("/apk404") { s -> server.respond(s, 404, "gone".toByteArray()) }
        val result = http.downloadTo("$baseUrl/apk404", File(tempDir, "missing.apk")) { _, _ -> }
        assertEquals(404, (result as DownloadResult.HttpError).code)
    }

    @Test
    fun `下载提前断流映射 Interrupted`() = runTest {
        server.route("/cut") { s ->
            // 声明 1000 字节只发 500 就断（触发 Premature EOF）
            server.respond(s, 200, ByteArray(500), declaredLength = 1000)
        }
        val result = http.downloadTo("$baseUrl/cut", File(tempDir, "cut.apk")) { _, _ -> }
        assertEquals(IoKind.Interrupted, (result as DownloadResult.IoError).kind)
    }

    @Test
    fun `连接失败映射 NoNetwork`() = runTest {
        // 先占用再释放端口，得到一个几乎必然拒绝连接的端口
        val freePort = java.net.ServerSocket(0).use { it.localPort }
        val result = http.getText("http://127.0.0.1:$freePort/closed")
        assertEquals(IoKind.NoNetwork, (result as HttpText.IoError).kind)
    }

    @Test
    fun `读超时映射 Timeout`() = runTest {
        server.route("/slow") { s ->
            Thread.sleep(16_000) // check readTimeout=15s
            server.respond(s, 200, "late".toByteArray())
        }
        val result = http.getText("$baseUrl/slow")
        assertEquals(IoKind.Timeout, (result as HttpText.IoError).kind)
    }

    // ---------------------------------------------------------------- 微型 HTTP 服务器

    private class TinyServer : Closeable {
        private val acceptor = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        private val closed = AtomicBoolean(false)
        private val routes = ConcurrentHashMap<String, (Socket) -> Unit>()
        val captured = ConcurrentHashMap<String, String>()
        val port: Int get() = acceptor.localPort

        fun start() {
            Thread {
                while (!closed.get()) {
                    val socket = runCatching { acceptor.accept() }.getOrNull() ?: break
                    Thread { handle(socket) }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true }.start()
        }

        fun route(path: String, handler: (Socket) -> Unit) {
            routes[path] = handler
        }

        fun respond(
            socket: Socket,
            code: Int,
            body: ByteArray,
            extraHeaders: Map<String, String> = emptyMap(),
            declaredLength: Long = body.size.toLong(),
        ) {
            val out = socket.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 $code ${reason(code)}\r\n")
                extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
                append("Content-Length: $declaredLength\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
        }

        private fun handle(socket: Socket) {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) captured[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                val handler = routes[path]
                if (handler == null) respond(s, 404, "not found".toByteArray()) else handler(s)
            }
        }

        private fun reason(code: Int): String = when (code) {
            200 -> "OK"
            302 -> "Found"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Status"
        }

        override fun close() {
            closed.set(true)
            runCatching { acceptor.close() }
        }
    }
}
