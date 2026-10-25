package com.wavexzore.toolbox.core.data.rules

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [RuleRepository] 来源判定：全部规则禁用 ≠ 从未导入（防止索引重建→重复导入循环）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuleRepositoryTest {

    private lateinit var db: ToolboxDatabase

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ToolboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `disabled builtin groups still count as imported`() = runBlocking {
        db.ruleDao().upsertGroups(
            listOf(
                RuleGroupEntity(
                    id = "disabled-builtin",
                    name = "disabled",
                    order = 0,
                    enabled = false,
                    schemaVersion = 1,
                    source = RuleRepository.SOURCE_BUILTIN,
                    json = "{}",
                    updatedAt = 0L,
                ),
            ),
        )
        val repository = RuleRepository(ApplicationProvider.getApplicationContext(), db)

        // enabled=1 的查询为空，但来源已存在，不能再触发 importBuiltin。
        assertTrue(repository.currentGroups().isEmpty())
        assertTrue(repository.hasBuiltinSource())
    }

    @Test
    fun `empty database has no builtin source`() = runBlocking {
        val repository = RuleRepository(ApplicationProvider.getApplicationContext(), db)
        assertTrue(!repository.hasBuiltinSource())
    }
}
