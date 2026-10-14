package com.wavexzore.toolbox.feature.settings.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavexzore.toolbox.core.design.components.WaveCard
import com.wavexzore.toolbox.core.design.components.WaveGhostButton
import com.wavexzore.toolbox.core.design.components.WaveNote
import com.wavexzore.toolbox.core.design.components.WavePill
import com.wavexzore.toolbox.core.design.components.WavePrimaryButton
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveSwitchRow
import com.wavexzore.toolbox.core.design.components.WaveTone
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 更新子页（T-307）：全状态分支 + 门控续装；文案表=设计 04 §10。 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheck.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    WaveScaffold(topBar = { WaveTopBar("检查更新", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            WaveNote("当前版本 v${viewModel.currentVersion} · 更新来源 GitHub Releases（仅检查更新时联网）")
            Spacer(Modifier.height(6.dp))

            when (val current = state) {
                is UpdateState.Idle -> {
                    WavePrimaryButton("检查更新", onClick = viewModel::check)
                }

                is UpdateState.Checking -> {
                    Text("正在检查…", fontSize = 13.5.sp, color = Wave.Ink2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Wave.InkStrong,
                        trackColor = Wave.Line,
                    )
                }

                is UpdateState.UpToDate -> {
                    WaveCard {
                        Text("已是最新版本", fontSize = 15.sp, color = Wave.Ink)
                        Text(
                            "v${viewModel.currentVersion}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Wave.Ink3,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    WaveGhostButton("重新检查", onClick = viewModel::check)
                }

                is UpdateState.NoRelease -> {
                    WaveCard {
                        Text("仓库暂无发布版本", fontSize = 15.sp, color = Wave.Ink)
                        Text("可稍后再来检查", fontSize = 12.sp, color = Wave.Ink3, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    WaveGhostButton("重新检查", onClick = viewModel::check)
                }

                is UpdateState.Available -> {
                    WaveCard {
                        Column {
                            androidx.compose.foundation.layout.Row {
                                Text("发现新版本", fontSize = 15.sp, color = Wave.Ink, modifier = Modifier.padding(end = 8.dp))
                                WavePill(current.info.tag, WaveTone.Green)
                                if (current.ignored) {
                                    Spacer(Modifier.padding(start = 4.dp))
                                    WavePill("已忽略", WaveTone.Neutral)
                                }
                            }
                            if (current.info.asset == null) {
                                Text(
                                    "该发布未附带 APK 资产，可前往 Release 页",
                                    fontSize = 12.sp,
                                    color = Wave.YellowTx,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            if (current.info.notes.isNotBlank()) {
                                Text(
                                    current.info.notes,
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                    color = Wave.Ink2,
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (current.info.asset != null) {
                        WavePrimaryButton("立即更新", onClick = viewModel::download)
                    }
                    if (current.ignored) {
                        WaveGhostButton("取消忽略", onClick = viewModel::clearSkip)
                    } else {
                        WaveGhostButton("忽略此版本", onClick = viewModel::skipCurrent)
                    }
                    WaveGhostButton("去 Release 页", onClick = { viewModel.openReleasePage(current.info.htmlUrl) })
                }

                is UpdateState.Downloading -> {
                    WaveCard {
                        Text("正在下载 ${current.info.tag}", fontSize = 15.sp, color = Wave.Ink)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { current.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Wave.InkStrong,
                            trackColor = Wave.Line,
                        )
                        Text(
                            "${mb(current.bytes)} / ${mb(current.total)}（${current.percent}%）",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = Wave.Ink3,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    WaveGhostButton("取消下载", onClick = viewModel::cancelDownload)
                }

                is UpdateState.Downloaded -> {
                    WaveCard {
                        Row2("下载完成 · 校验通过", current.info?.tag)
                        Text(
                            current.file.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Wave.Ink3,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (current.info?.asset?.digest == null) {
                            Text(
                                "发布未提供摘要，已按体积校验 + 系统签名兜底",
                                fontSize = 11.5.sp,
                                color = Wave.YellowTx,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    WavePrimaryButton("安装", onClick = viewModel::install)
                    WaveGhostButton("重新下载", onClick = viewModel::redownload)
                }

                is UpdateState.Error -> {
                    val downloadSide = current.info != null
                    WaveCard {
                        Text(
                            if (downloadSide) "下载失败" else "检查失败",
                            fontSize = 15.sp,
                            color = Wave.RedTx,
                        )
                        Text(
                            errorText(current),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = Wave.Ink2,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val info = current.info
                    if (info != null && info.asset != null) {
                        WavePrimaryButton("重新下载", onClick = viewModel::retry)
                        WaveGhostButton("仅重试检查", onClick = viewModel::check)
                    } else {
                        WavePrimaryButton("重试", onClick = viewModel::retry)
                    }
                    WaveGhostButton("去 Release 页", onClick = { viewModel.openReleasePage(info?.htmlUrl) })
                    WaveGhostButton("关闭", onClick = viewModel::dismissError)
                }
            }

            Spacer(Modifier.height(20.dp))
            WaveCard {
                WaveSwitchRow(
                    title = "启动时自动检查",
                    subtitle = "24 小时内最多自动检查一次；失败同样计入窗口",
                    checked = autoCheck,
                    onCheckedChange = viewModel::setAutoCheck,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Row2(title: String, tag: String?) {
    androidx.compose.foundation.layout.Row {
        Text(title, fontSize = 15.sp, color = Wave.Ink)
        if (tag != null) {
            Spacer(Modifier.padding(start = 8.dp))
            WavePill(tag, WaveTone.Green)
        }
    }
}

private fun errorText(error: UpdateState.Error): String = when (error.kind) {
    ErrorKind.NoNetwork -> "网络不可用，请检查网络后重试。"
    ErrorKind.Timeout -> "连接超时，请重试。"
    ErrorKind.Interrupted -> "下载中断，文件已保留，可直接重试。"
    ErrorKind.RateLimited -> error.resetAtEpochSec?.let {
        "GitHub 检查次数已达上限，约 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000))} 后可重试。"
    } ?: "GitHub 检查次数已达上限，请稍后重试。"
    ErrorKind.Http -> "服务异常${error.code?.let { "（HTTP $it）" }.orEmpty()}，请重试。"
    ErrorKind.Parse -> "返回数据异常，请重试。"
    ErrorKind.VerifyFailed -> "校验未通过，文件已保留，可重新下载。"
    ErrorKind.Storage -> "存储异常，可重新下载。"
    ErrorKind.TooBig -> "安装包异常偏大（>200MB）。"
}

private fun mb(bytes: Long): String = "%.1f MB".format(Locale.US, bytes / 1024.0 / 1024.0)
