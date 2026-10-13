package com.wavexzore.toolbox.feature.wechat

import com.wavexzore.toolbox.core.selector.NodeView
import java.security.MessageDigest

/**
 * 选择页行扫描（T-003；D-203 可见昵称集合稳定哈希判尾）。
 *
 * 行识别启发式（真机校准于 WeChat 8.0.79）：可点击的整行容器（无 text/desc、宽 >60% 屏、行高 60~420px），
 * 昵称取行内首个短文本后代；同名取更靠后者（底部列表行优先于"已选"头像区）。
 */
object ContactSelect {

    data class Row(val name: String, val node: NodeView, val centerY: Int, val checked: Boolean)

    private val EXCLUDE_NAMES = listOf("已选", "清空", "搜索")

    /** 选择页顶部快捷入口（点击会离开联系人列表，需排除）。 */
    val PAGE_OPTION_TITLES = setOf(
        "选择一个已有的群",
        "创建新群聊",
        "面对面建群",
        "选择群聊中的朋友",
        "选择企业微信联系人",
    )

    /** 通讯录页非好友行（功能入口，收集/验证时排除）。 */
    val SPECIAL_ROWS = setOf(
        "新的朋友",
        "仅聊天的朋友",
        "群聊",
        "标签",
        "公众号",
        "服务号",
        "我的企业及企业联系人",
        "企业微信联系人",
        "企业微信通知",
        "学校通知",
        "搜索联系人",
    )

    fun scanRows(root: NodeView, screenWidth: Int, excludeNames: Set<String> = emptySet()): List<Row> {
        val rows = ArrayList<Row>(32)
        walk(root, screenWidth, rows)
        // 同名保留最后一个（列表行在"已选"区之后）
        val byName = LinkedHashMap<String, Row>()
        rows.forEach { row ->
            if (row.name !in excludeNames) byName[row.name] = row
        }
        return byName.values.toList()
    }

    private fun walk(node: NodeView, screenWidth: Int, out: MutableList<Row>) {
        if (isRowContainer(node, screenWidth)) {
            nicknameOf(node)?.let { name ->
                out += Row(name, node, (node.bounds.top + node.bounds.bottom) / 2, rowChecked(node))
            }
        }
        node.children.forEach { walk(it, screenWidth, out) }
    }

    /** 行内 CheckBox 勾选态（真值来源；无 CheckBox 视为未选）。 */
    private fun rowChecked(row: NodeView): Boolean {
        var found = false
        fun walk(node: NodeView, depth: Int) {
            if (found || depth > 6) return
            if (node.className?.contains("CheckBox") == true && node.isChecked) {
                found = true
                return
            }
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(row, 0)
        return found
    }

    private fun isRowContainer(node: NodeView, screenWidth: Int): Boolean {
        if (!node.isClickable) return false
        if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) return false
        if (!node.isVisible) return false
        val h = node.bounds.height
        val w = node.bounds.width
        if (h !in 60..420) return false
        if (screenWidth > 0 && w < screenWidth * 0.6) return false
        return true
    }

    private fun nicknameOf(row: NodeView): String? {
        var found: String? = null
        fun walk(node: NodeView, depth: Int) {
            if (found != null || depth > 8) return
            val text = node.text?.trim()
            if (!text.isNullOrEmpty() && text.length in 1..24 &&
                EXCLUDE_NAMES.none { text.contains(it) } &&
                !(text.length == 1 && text[0] in 'A'..'Z')
            ) {
                found = text
                return
            }
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(row, 0)
        return found
    }

    /** 诊断：行节点昵称提取（暴露私有逻辑）。 */
    fun nicknameDebug(row: NodeView): String? = nicknameOf(row)

    /** 可见昵称集合的稳定哈希（D-203 判尾）。 */
    fun stableHash(rows: List<Row>): String {
        val joined = rows.map { it.name }.distinct().sorted().joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-1").digest(joined.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
