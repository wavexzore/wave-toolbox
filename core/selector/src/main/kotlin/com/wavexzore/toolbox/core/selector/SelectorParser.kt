package com.wavexzore.toolbox.core.selector

/**
 * 选择器解析（纯逻辑、永不抛异常：语法错误返回 [ParseResult.Error]）。
 *
 * 语法：
 * ```
 * selector := ws? step ((ws? ('>' | '@')? ws+) | (ws* '>' ws*)) step)*
 * step     := '[' ws* attr ws* op ws* value ws* ']'
 * attr     := vid | text | desc | class
 * op       := '=' | '^=' | '*=' | '!='
 * value    := 裸值（到 ']' 为止，支持 \] \" \\ 转义） | '"' 引号值 '"'
 * ```
 */
object SelectorParser {

    sealed interface ParseResult {
        data class Ok(val selector: Selector) : ParseResult
        data class Error(val message: String, val position: Int) : ParseResult
    }

    fun parse(raw: String): ParseResult {
        val input = raw
        var pos = 0
        val steps = ArrayList<Selector.Step>()

        fun skipWs() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        fun parseValue(): String {
            if (pos < input.length && input[pos] == '"') {
                pos++
                val sb = StringBuilder()
                while (pos < input.length) {
                    val c = input[pos]
                    when {
                        c == '\\' && pos + 1 < input.length -> {
                            sb.append(input[pos + 1])
                            pos += 2
                        }
                        c == '"' -> {
                            pos++
                            return sb.toString()
                        }
                        else -> {
                            sb.append(c)
                            pos++
                        }
                    }
                }
                throw ParseFailure("unterminated quoted value", pos)
            }
            val sb = StringBuilder()
            while (pos < input.length && input[pos] != ']') {
                val c = input[pos]
                if (c == '\\' && pos + 1 < input.length) {
                    sb.append(input[pos + 1])
                    pos += 2
                } else {
                    sb.append(c)
                    pos++
                }
            }
            return sb.toString().trim()
        }

        fun parseCondition(): SelCondition {
            // 已消费 '['
            skipWs()
            val attrStart = pos
            while (pos < input.length && input[pos].isLetter()) pos++
            if (pos == attrStart) throw ParseFailure("expected attribute name", pos)
            val attrName = input.substring(attrStart, pos)
            val attr = SelAttr.fromName(attrName)
                ?: throw ParseFailure("unknown attribute '$attrName'", attrStart)

            skipWs()
            val op = when {
                input.startsWith("^=", pos) -> SelOp.PREFIX
                input.startsWith("*=", pos) -> SelOp.CONTAINS
                input.startsWith("!=", pos) -> SelOp.NOT_EQ
                input.startsWith("=", pos) -> SelOp.EQ
                else -> throw ParseFailure("expected one of = ^= *= != after '$attrName'", pos)
            }
            pos += op.symbol.length

            skipWs()
            val value = parseValue()
            skipWs()
            if (pos >= input.length || input[pos] != ']') {
                throw ParseFailure("expected ']'", pos)
            }
            pos++
            return SelCondition(attr, op, value)
        }

        return try {
            var first = true
            var pendingRelation: SelRelation? = null
            var carriedLeading: SelRelation? = null

            while (true) {
                skipWs()
                if (pos >= input.length) {
                    if (pendingRelation != null) throw ParseFailure("selector ends with a combinator", pos)
                    break
                }
                // 组合子（可重复出现，最后一个生效）
                while (pos < input.length && (input[pos] == '>' || input[pos] == '@')) {
                    pendingRelation = if (input[pos] == '>') SelRelation.CHILD else SelRelation.ANCHOR
                    pos++
                    skipWs()
                }
                if (pos >= input.length) throw ParseFailure("selector ends with a combinator", pos)
                if (input[pos] != '[') throw ParseFailure("expected '[' to start a step", pos)
                pos++
                val condition = parseCondition()

                val relation = if (first) {
                    // 前导组合子（`@[a] [b]`）语义上等同于 `[a] @ [b]`：挂到下一步
                    carriedLeading = pendingRelation
                    pendingRelation = null
                    SelRelation.FIRST
                } else {
                    val r = pendingRelation ?: carriedLeading ?: SelRelation.DESCENDANT
                    pendingRelation = null
                    carriedLeading = null
                    r
                }
                steps.add(Selector.Step(listOf(condition), relation))
                first = false

                skipWs()
                if (pos < input.length && input[pos] != '[' && input[pos] != '>' && input[pos] != '@') {
                    throw ParseFailure("unexpected character '${input[pos]}'", pos)
                }
            }

            if (steps.isEmpty()) throw ParseFailure("empty selector", pos)
            ParseResult.Ok(Selector(raw, steps.toList()))
        } catch (f: ParseFailure) {
            ParseResult.Error(f.message, f.position)
        }
    }

    /** 多条件能力预留：一个 step 内多个 `[...]` 并列在首期视为独立 step（AND，后代关系放宽）。 */
    private class ParseFailure(override val message: String, val position: Int) : RuntimeException(message)
}

/**
 * 解析缓存（LRU 512，04 §5.2）：同一字符串返回同一 [SelectorParser.ParseResult] 实例。
 */
object SelectorCache {

    private const val CAPACITY = 512

    private val map = object : LinkedHashMap<String, SelectorParser.ParseResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SelectorParser.ParseResult>?): Boolean =
            size > CAPACITY
    }

    @Synchronized
    fun parse(raw: String): SelectorParser.ParseResult =
        map.getOrPut(raw) { SelectorParser.parse(raw) }

    @Synchronized
    fun size(): Int = map.size
}
