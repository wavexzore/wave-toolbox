package com.wavexzore.toolbox.feature.settings.update

/**
 * 应用内更新常量（T-303；设计 04 §11.2）。
 * 单一来源：URL/超时/节流/截断/上限/UA/目录/文件名清理。
 */
object UpdateConfig {

    /** GitHub 仓库（唯一分发渠道）。 */
    const val REPO_OWNER = "wavexzore"
    const val REPO_NAME = "wave-toolbox"

    /** latest 端点（官方语义：最新非 draft 非 pre-release 发布）。 */
    const val API_LATEST_URL = "https://api.github.com/repos/wavexzore/wave-toolbox/releases/latest"

    /** Release 页面兜底（无版本信息时的「去 Release 页」）。 */
    const val RELEASES_PAGE = "https://github.com/wavexzore/wave-toolbox/releases"

    /** 资产名精确匹配前缀（tag 自带 v，前缀不含 v）。 */
    const val ASSET_NAME_PREFIX = "wave-toolbox-"

    /** 检查请求超时（connect / read，ms）。 */
    const val CHECK_CONNECT_TIMEOUT_MS = 10_000
    const val CHECK_READ_TIMEOUT_MS = 15_000

    /** 下载请求超时（connect / read，ms）。 */
    const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
    const val DOWNLOAD_READ_TIMEOUT_MS = 30_000

    /** 自动检查节流窗（ms；失败完成同样计入）。 */
    const val THROTTLE_MS = 24L * 60 * 60 * 1000

    /** release notes 截断（字符，码点安全）。 */
    const val NOTES_MAX_CHARS = 2_000

    /** API 响应最大读取字节数（超限归解析失败）。 */
    const val API_READ_CAP_BYTES = 1L * 1024 * 1024

    /** 安装包体积上限（字节；超限拒绝下载）。 */
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    /** 下载进度回调最小间隔（ms）。 */
    const val PROGRESS_MIN_INTERVAL_MS = 1_000L

    /** 下载目录名（cacheDir 下；file_paths.xml 已白名单 updates/）。 */
    const val UPDATES_DIR = "updates"

    /** User-Agent（GitHub 匿名 API 要求提供 UA）。 */
    fun userAgent(versionName: String, sdkInt: Int): String = "wave-toolbox/$versionName (Android $sdkInt)"

    /** 文件名清理：仅保留字母/数字/`.`/`-`/`_`，其余转 `-`；空则回落 update.apk。 */
    fun sanitizeForFileName(name: String): String {
        val cleaned = buildString(name.length) {
            name.forEach { ch ->
                append(if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '-')
            }
        }
        return cleaned.ifEmpty { "update.apk" }
    }
}
