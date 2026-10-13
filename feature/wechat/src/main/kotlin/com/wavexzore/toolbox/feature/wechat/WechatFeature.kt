package com.wavexzore.toolbox.feature.wechat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wavexzore.toolbox.core.feature.api.FeatureEvent
import com.wavexzore.toolbox.core.feature.api.ToolFeature
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 微信僵尸好友检测与清理（T-001）：SPI 注册 + 会话控制 + 结果面板。
 * 检测/删除状态机在 [WechatDetectionSession]/[WechatDeleteSession]；结果持久化 [WechatStore]。
 */
@Singleton
class WechatFeature @Inject constructor(
    private val detection: WechatDetectionSession,
    private val deleter: WechatDeleteSession,
    private val friendList: WechatFriendListSession,
    private val verifier: WechatTransferVerifySession,
    private val store: WechatStore,
    private val config: WechatConfig,
) : ToolFeature {

    override val id: String = "wechat"
    override val name: String = "微信僵尸好友清理"
    override val description: String = "转账验证单向删除的好友（实名信号+弹窗复核），异常自动打标签"
    override val order: Int = 20

    private val _eventCount = MutableStateFlow(0L)
    val eventCount: StateFlow<Long> = _eventCount

    override fun onEvent(event: FeatureEvent) {
        _eventCount.value = _eventCount.value + 1
    }

    val detectionProgress = detection.progress
    val deleteProgress = deleter.progress
    val listProgress = friendList.progress
    val verifyProgress = verifier.progress
    val data = store.data

    fun startDetection() = detection.start()
    fun stopDetection() = detection.stop()
    fun pauseDetection() = detection.pause()
    fun resumeDetection() = detection.resume()

    fun startDelete(names: List<String>, deleteChatRecord: Boolean) = deleter.start(names, deleteChatRecord)
    fun stopDelete() = deleter.stop()

    fun startFriendList() = friendList.start()
    fun stopFriendList() = friendList.stop()
    fun startVerify(retryUnknown: Boolean = false) = verifier.start(retryUnknown)
    fun stopVerify() = verifier.stop()
    fun pauseVerify() = verifier.pause()
    fun resumeVerify() = verifier.resume()
    fun resetFriendsStatus() = store.resetFriendsStatus()

    fun clearResults() = store.clearAll()

    suspend fun resultsCsv(): String = store.csv()
    suspend fun friendsCsv(): String = store.friendsCsv()

    @Composable
    override fun SettingsPane(scope: ColumnScope, modifier: Modifier) {
        WechatSettingsPane(
            feature = this,
            store = store,
            config = config,
            modifier = modifier,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WechatCoreModule {

    @Binds
    @IntoSet
    abstract fun bindWechatFeature(feature: WechatFeature): ToolFeature
}
