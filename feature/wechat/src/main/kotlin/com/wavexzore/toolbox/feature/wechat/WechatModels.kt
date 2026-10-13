package com.wavexzore.toolbox.feature.wechat

import kotlinx.serialization.Serializable

/** 检测结果分类（RS-205：三类系统消息）。 */
@Serializable
enum class ZombieCategory(val label: String) {
    /** 你无法邀请未添加你为好友（对方把你删了）。 */
    DELETED("已删除你"),

    /** 「X」拒绝加入群聊。 */
    REJECTED("拒绝入群"),

    /** 「X」违反行为规范/无法加入。 */
    BANNED("无法入群"),
}

@Serializable
data class ResultEntry(
    val name: String,
    val category: ZombieCategory,
    val raw: String,
    val at: Long,
    val runId: String,
    val cleaned: Boolean = false,
    val cleanedAt: Long? = null,
    val alreadyFriendless: Boolean = false,
)

@Serializable
data class RunMeta(
    val runId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val wechatVersion: String? = null,
    val status: String = "running",
    val batches: Int = 0,
    val processed: Int = 0,
    val found: Int = 0,
)

/** 转账验证（v1.1 主方案）好友状态。 */
@Serializable
enum class FriendStatus(val label: String) {
    /** 待验证。 */
    PENDING("待验证"),

    /** 正常好友（转账页显示实名）。 */
    OK("正常"),

    /** 异常（删除你/无法收款）。 */
    ZOMBIE("异常"),

    /** 无法判定（超时/锚点缺失/人工跳过）。 */
    UNKNOWN("未判定"),
}

/** 好友条目（列表收集 + 转账验证结果）。 */
@Serializable
data class FriendEntry(
    val name: String,
    val seq: Int = 1,
    val wxid: String? = null,
    val region: String? = null,
    val phone: String? = null,
    val status: FriendStatus = FriendStatus.PENDING,
    val signal: String? = null,
    val verifiedAt: Long? = null,
    val tagged: Boolean = false,
)

@Serializable
data class WechatData(
    val runs: List<RunMeta> = emptyList(),
    val entries: List<ResultEntry> = emptyList(),
    val friends: List<FriendEntry> = emptyList(),
    val friendsCollectedAt: Long? = null,
    val verifyRunAt: Long? = null,
)
