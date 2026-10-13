package com.wavexzore.toolbox.feature.wechat

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 选择行扫描（T-003）：行列识别/昵称提取/同名去重/稳定哈希。 */
class ContactSelectTest {

    private fun tree(json: String) = SnapshotTree.fromJson(json)

    @Test
    fun scansClickableRowsWithNickname() {
        val root = tree(
            """
            { "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
              {"class":"LinearLayout","clickable":true,"bounds":[0,300,1080,460],"children":[
                {"class":"CheckBox","bounds":[40,350,120,430]},
                {"text":"张三","class":"TextView","bounds":[150,360,500,420]}
              ]},
              {"class":"LinearLayout","clickable":true,"bounds":[0,460,1080,620],"children":[
                {"text":"李四","class":"TextView","bounds":[150,520,500,580]}
              ]}
            ]}
            """.trimIndent(),
        )
        val rows = ContactSelect.scanRows(root, 1080)
        assertEquals(listOf("张三", "李四"), rows.map { it.name })
    }

    @Test
    fun sameNameKeepsBottomListOccurrence() {
        val root = tree(
            """
            { "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
              {"class":"LinearLayout","clickable":true,"bounds":[0,100,1080,240],"children":[{"text":"王五","class":"TextView","bounds":[150,140,500,200]}]},
              {"class":"LinearLayout","clickable":true,"bounds":[0,1200,1080,1340],"children":[{"text":"王五","class":"TextView","bounds":[150,1240,500,1300]}]}
            ]}
            """.trimIndent(),
        )
        val rows = ContactSelect.scanRows(root, 1080)
        assertEquals(1, rows.size)
        assertEquals(1270, rows[0].centerY)
    }

    @Test
    fun excludesLetterHeadersAndSelectedHeader() {
        val root = tree(
            """
            { "class":"FrameLayout","bounds":[0,0,1080,2400],"children":[
              {"text":"A","class":"TextView","bounds":[30,300,80,340]},
              {"text":"已选(2)","class":"TextView","bounds":[30,600,300,660]},
              {"class":"LinearLayout","clickable":true,"bounds":[0,700,1080,860],"children":[{"text":"Alice","class":"TextView","bounds":[150,760,500,820]}]}
            ]}
            """.trimIndent(),
        )
        val rows = ContactSelect.scanRows(root, 1080)
        assertEquals(listOf("Alice"), rows.map { it.name })
    }

    @Test
    fun stableHashIgnoresOrder() {
        val a = listOf(
            ContactSelect.Row("张三", tree("""{"class":"View","bounds":[0,0,1,1]}"""), 10, false),
            ContactSelect.Row("李四", tree("""{"class":"View","bounds":[0,0,1,1]}"""), 20, false),
        )
        val b = a.reversed()
        assertEquals(ContactSelect.stableHash(a), ContactSelect.stableHash(b))
        assertTrue(ContactSelect.stableHash(a) != ContactSelect.stableHash(a.drop(1)))
    }
}
