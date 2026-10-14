package com.wavexzore.toolbox.feature.settings.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 更新子页 VM（T-307）：纯转发 + 「未知来源授权后返回续装」一次性标志。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state
    val autoCheck: StateFlow<Boolean> = repository.autoCheck

    val currentVersion: String = repository.currentVersionName()

    /** 等待用户在系统页授予「安装未知应用」后返回续装。 */
    private val awaitingGrant = MutableStateFlow(false)

    fun check() = repository.refresh(force = true)

    fun download() = repository.download()

    fun redownload() = repository.redownload()

    fun retry() = repository.retry()

    fun skipCurrent() = repository.skipCurrent()

    fun clearSkip() = repository.clearSkip()

    fun cancelDownload() = repository.cancelDownload()

    fun dismissError() = repository.dismissError()

    fun openReleasePage(url: String?) = repository.openReleasePage(url)

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoCheck(enabled) }
    }

    /** 安装入口：已授权直接调起；未授权先跳设置并挂起续装标志。 */
    fun install() {
        if (repository.canRequestPackageInstalls()) {
            repository.install()
        } else {
            awaitingGrant.value = true
            repository.install()
        }
    }

    /** 页面回前台：若正在等待授权且已授权 → 自动续装一次。 */
    fun onResumed() {
        if (awaitingGrant.value && repository.canRequestPackageInstalls()) {
            awaitingGrant.value = false
            repository.install()
        }
    }
}
