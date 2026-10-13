package com.wavexzore.toolbox.feature.wechat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 系统消息解析（T-004/T-009）：多文案样例 + 三类语义 + 兜底。 */
class SystemMessageParserTest {

    @Test
    fun parsesJoinedList() {
        val outcome = SystemMessageParser.parse(listOf("你邀请「张三」、「李四」加入了群聊"))
        assertEquals(setOf("张三", "李四"), outcome.joined)
        assertTrue(outcome.hits.isEmpty())
    }

    @Test
    fun parsesRejected() {
        val outcome = SystemMessageParser.parse(listOf("「王五」拒绝加入群聊"))
        assertEquals(listOf("王五"), outcome.rejected.single().names)
    }

    @Test
    fun parsesRejectedPlural() {
        val outcome = SystemMessageParser.parse(listOf("「王五」、「钱七」拒绝加入群聊"))
        assertEquals(setOf("王五", "钱七"), outcome.rejected.flatMap { it.names }.toSet())
    }

    @Test
    fun parsesBanned() {
        val outcome = SystemMessageParser.parse(listOf("「赵六」违反微信用户行为规范，无法加入群聊"))
        assertEquals(listOf("赵六"), outcome.banned.single().names)
    }

    @Test
    fun deletedFromFallback() {
        val messages = listOf(
            "你邀请「A」加入了群聊",
            "你无法邀请未添加你为好友的用户进来群聊，请先发送朋友验证申请给对方，对方验证通过后即可加入。",
        )
        val outcome = SystemMessageParser.parse(messages, invited = setOf("A", "B", "C"))
        assertEquals(setOf("B", "C"), outcome.deleted.flatMap { it.names }.toSet())
    }

    @Test
    fun deletedWithQuotedNames() {
        val outcome = SystemMessageParser.parse(listOf("你无法邀请未添加你为好友的用户「B」进来群聊"))
        assertEquals(listOf("B"), outcome.deleted.single().names)
    }

    @Test
    fun deletedWithNeighborNameList() {
        val messages = listOf(
            "你无法邀请未添加你为好友的用户进来群聊",
            "B、C",
        )
        val outcome = SystemMessageParser.parse(messages, invited = setOf("A", "B", "C"))
        assertEquals(setOf("B", "C"), outcome.deleted.flatMap { it.names }.toSet())
    }

    @Test
    fun deletedFallbackExcludesRejectedAndBanned() {
        val messages = listOf(
            "你邀请「A」加入了群聊",
            "你无法邀请未添加你为好友的用户进来群聊",
            "「B」拒绝加入群聊",
            "「C」违反微信用户行为规范，无法加入群聊",
        )
        val outcome = SystemMessageParser.parse(messages, invited = setOf("A", "B", "C", "D"))
        assertEquals(setOf("D"), outcome.deleted.flatMap { it.names }.toSet())
    }

    @Test
    fun anchorWordsFilteredOutOfNames() {
        // 邻居行混入锚点词（不同版本断行）不应把"用户进来群聊"当名字
        val names = SystemMessageParser.extractNames("你无法邀请未添加你为好友的用户进来群聊，请先发送朋友验证申请给对方")
        assertTrue(names.isEmpty(), "锚点行无名单时不应产生名字：$names")
    }

    @Test
    fun nicknameWithPunctuationKept() {
        val outcome = SystemMessageParser.parse(listOf("「阿🌸」拒绝加入群聊"))
        assertEquals(listOf("阿🌸"), outcome.rejected.single().names)
    }
}
