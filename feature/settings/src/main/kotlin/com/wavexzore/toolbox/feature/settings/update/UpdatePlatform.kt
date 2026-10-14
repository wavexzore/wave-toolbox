package com.wavexzore.toolbox.feature.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 系统能力窄接口（版本/门控/安装/目录/读 APK 版本/开 URL；测试可 fake；T-306）。 */
interface UpdatePlatform {
    /** 当前应用 versionName（读不到回落 "0"）。 */
    fun versionName(): String

    /** SDK_INT。 */
    fun sdkInt(): Int

    /** 是否已被授予「安装未知应用」（API26+）。 */
    fun canRequestPackageInstalls(): Boolean

    /** 经 FileProvider + ACTION_VIEW 调起系统安装器。 */
    fun startInstall(file: File)

    /** 跳「安装未知应用」授权页（带本应用包名）。 */
    fun openUnknownAppSources()

    /** 打开外链（Release 页兜底）。 */
    fun openUrl(url: String)

    /** 下载目录（cacheDir/updates）。 */
    fun updatesDir(): File

    /** 读取 APK 内 versionName（包名不匹配/不可解析 → null）。 */
    fun readApkVersion(file: File): String?
}

@Singleton
class AndroidUpdatePlatform @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdatePlatform {

    override fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    override fun sdkInt(): Int = Build.VERSION.SDK_INT

    override fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun startInstall(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun openUnknownAppSources() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun updatesDir(): File = File(context.cacheDir, UpdateConfig.UPDATES_DIR)

    @Suppress("DEPRECATION")
    override fun readApkVersion(file: File): String? = runCatching {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
    }.getOrNull()
        ?.takeIf { it.packageName == context.packageName }
        ?.versionName
}
