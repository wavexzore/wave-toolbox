package com.wavexzore.toolbox.core.data.rules

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ToolboxDatabase 迁移测试（T-207；C-206）：
 * - v2→v3：新增 zone/provider 列，旧行保留、新列可写；
 * - v1→v3 全链可用（历史安装升级路径）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolboxDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ToolboxDatabase::class.java,
    )

    @Test
    fun migrate2To3AddsColumnsAndKeepsRows() {
        helper.createDatabase(ToolboxDatabase.NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO stat_records (at, feature_id, pkg, rule_id, kind, ok, mechanism, duration_ms) " +
                    "VALUES (1, 'adskip', 'com.a', 'keyword:跳过', 'SKIP', 1, 'tap', 12)",
            )
        }
        helper.runMigrationsAndValidate(
            ToolboxDatabase.NAME,
            3,
            true,
            ToolboxDatabase.MIGRATION_2_3,
        ).use { db ->
            db.query("SELECT zone, provider FROM stat_records WHERE pkg = 'com.a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
                assertNull(cursor.getString(1))
            }
            db.execSQL(
                "INSERT INTO stat_records (at, feature_id, pkg, rule_id, kind, ok, mechanism, duration_ms, zone, provider) " +
                    "VALUES (2, 'adskip', 'com.b', 'match:x', 'SKIP', 1, 'tap', 3, 'entry', 'match')",
            )
        }
    }

    @Test
    fun migrate1To3FullChain() {
        helper.createDatabase(ToolboxDatabase.NAME, 1).close()
        helper.runMigrationsAndValidate(
            ToolboxDatabase.NAME,
            3,
            true,
            ToolboxDatabase.MIGRATION_1_2,
            ToolboxDatabase.MIGRATION_2_3,
        ).close()
    }
}
