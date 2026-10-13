package com.wavexzore.toolbox.feature.wechat

/**
 * 系统消息解析（T-004；D-204 锚点短语 + 分隔符名字切分）。
 *
 * 三类锚点（RS-205）：
 * - [DELETED_ANCHOR]：有人把你删了（邀请失败）；
 * - [REJECTED_ANCHOR]：「X」拒绝加入群聊；
 * - [BANNED_ANCHORS]：「X」违反行为规范等。
 *
 * 名字来源优先级：引号直取 → 相邻名单行（顿号/逗号列表）→ 失败兜底（invited − joined − rejected − banned）。
 * 只认锚点最短稳定片段，抗文案漂移（D-204）。
 */
object SystemMessageParser {

    const val DELETED_ANCHOR = "无法邀请未添加你为好友"
    const val COMPANION_ANCHOR = "请先发送朋友验证申请"
    const val REJECTED_ANCHOR = "拒绝加入群聊"
    const val JOINED_ANCHOR = "加入了群聊"
    val BANNED_ANCHORS = listOf("违反微信用户行为规范", "无法加入群聊", "限制登录")

    data class Hit(
        val category: ZombieCategory,
        val names: List<String>,
        val raw: String,
    )

    data class Outcome(
        /** 命中的分类条目（deleted 可能带兜底名单）。 */
        val hits: List<Hit>,
        /** 本批成功入群名单（诊断用）。 */
        val joined: Set<String>,
    ) {
        val deleted: List<Hit> get() = hits.filter { it.category == ZombieCategory.DELETED }
        val rejected: List<Hit> get() = hits.filter { it.category == ZombieCategory.REJECTED }
        val banned: List<Hit> get() = hits.filter { it.category == ZombieCategory.BANNED }
    }

    fun parse(messages: List<String>, invited: Set<String> = emptySet()): Outcome {
        val joined = messages
            .filter { it.contains(JOINED_ANCHOR) }
            .flatMap { extractNames(it, stripJoinWords = true) }
            .toSet()
        val rejectedHits = messages.filter { it.contains(REJECTED_ANCHOR) }.map { raw ->
            Hit(ZombieCategory.REJECTED, extractNames(raw), raw)
        }
        val bannedHits = messages.filter { raw -> BANNED_ANCHORS.any { raw.contains(it) } }.map { raw ->
            Hit(ZombieCategory.BANNED, extractNames(raw), raw)
        }
        val rejectedNames = rejectedHits.flatMap { it.names }.toSet()
        val bannedNames = bannedHits.flatMap { it.names }.toSet()

        val deletedHits = ArrayList<Hit>()
        messages.forEachIndexed { index, raw ->
            if (!raw.contains(DELETED_ANCHOR)) return@forEachIndexed
            var names = extractNames(raw)
            if (names.isEmpty()) {
                // 相邻行名单（部分版本把名单独立成行）
                val neighbors = listOfNotNull(messages.getOrNull(index - 1), messages.getOrNull(index + 1))
                    .filter { it != raw && it.contains(COMPANION_ANCHOR).not() }
                names = neighbors.flatMap { if (looksLikeNameList(it)) extractNames(it) else emptyList() }
            }
            if (names.isEmpty() && invited.isNotEmpty()) {
                names = (invited - joined - rejectedNames - bannedNames).toList()
            }
            deletedHits += Hit(ZombieCategory.DELETED, names.distinct(), raw)
        }

        return Outcome(deletedHits + rejectedHits + bannedHits, joined)
    }

    private val QUOTE_PAIRS = listOf('「' to '」', '“' to '”', '‘' to '’', '«' to '»')

    /** 引号直取；无引号时按分隔符切分（仅当看起来是名单）。 */
    internal fun extractNames(text: String, stripJoinWords: Boolean = false): List<String> {
        val quoted = ArrayList<String>()
        for ((open, close) in QUOTE_PAIRS) {
            var i = text.indexOf(open)
            while (i >= 0) {
                val j = text.indexOf(close, i + 1)
                if (j > i) {
                    quoted += text.substring(i + 1, j).trim()
                    i = text.indexOf(open, j + 1)
                } else {
                    break
                }
            }
        }
        val candidate = if (quoted.isNotEmpty()) quoted else if (looksLikeNameList(text)) {
            text.split('、', '，', ',', '；', ';', ' ').map { it.trim() }
        } else {
            emptyList()
        }
        return candidate.filter { isPlausibleName(it, stripJoinWords) }.distinct()
    }

    private val ANCHOR_WORDS = listOf(
        "无法邀请", "未添加", "好友", "邀请", "加入了群聊", "加入群聊", "拒绝", "验证申请", "请先发送", "用户进来群聊",
        "违反", "微信用户行为规范", "限制登录", "无法加入", "通过后即可加入", "对方验证", "群聊",
    )

    private fun isPlausibleName(name: String, stripJoinWords: Boolean): Boolean {
        if (name.length !in 1..24) return false
        if (name.all { it.isDigit() }) return false
        val cleaned = if (stripJoinWords) name.removeSuffix("等") else name
        if (cleaned.isBlank()) return false
        return ANCHOR_WORDS.none { cleaned.contains(it) }
    }

    internal fun looksLikeNameList(text: String): Boolean {
        val compact = text.trim()
        if (compact.isEmpty() || compact.length > 200) return false
        if (BANNED_ANCHORS.any { compact.contains(it) }) return false
        if (compact.contains(DELETED_ANCHOR)) return false
        if (compact.contains(REJECTED_ANCHOR)) return false
        if (compact.contains(JOINED_ANCHOR)) return false
        return compact.contains('、') || compact.contains('，') || compact.contains(',')
    }
}
