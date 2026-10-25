package com.wavexzore.toolbox.core.data.rules

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ToolboxDatabase 迁移测试（T-207；C-206）：
 * - v2→v3：新增 zone/provider 列，旧行保留、新列可写；
 * - v1→v3 全链可用（历史安装升级路径）；
 * - v3→v4：规则组主键改为 `(id, source)`，旧数据保留且允许同 id 跨来源共存。
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

    @Test
    fun migrate3To4KeepsRowsAndAllowsSameIdAcrossSources() {
        helper.createDatabase(ToolboxDatabase.NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO rule_groups (`id`, `name`, `order`, `enabled`, `schemaVersion`, `source`, `json`, `updatedAt`) " +
                    "VALUES ('g1', 'legacy', 0, 1, 1, 'builtin', '{\"id\":\"g1\"}', 0)",
            )
            db.execSQL("INSERT INTO rule_apps (`groupId`, `pkg`) VALUES ('g1', 'com.a')")
        }
        helper.runMigrationsAndValidate(
            ToolboxDatabase.NAME,
            4,
            true,
            ToolboxDatabase.MIGRATION_3_4,
        ).use { db ->
            db.query("SELECT source FROM rule_groups WHERE id = 'g1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(RuleRepository.SOURCE_BUILTIN, cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM rule_apps WHERE pkg = 'com.a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            // 同 id 的用户包必须能与内置组共存（整包覆盖在读取层裁决）。
            db.execSQL(
                "INSERT INTO rule_groups (`id`, `name`, `order`, `enabled`, `schemaVersion`, `source`, `json`, `updatedAt`) " +
                    "VALUES ('g1', 'user', 0, 1, 1, 'user:custom', '{\"id\":\"g1\"}', 0)",
            )
            db.execSQL(
                "INSERT INTO rule_apps (`groupId`, `source`, `pkg`) VALUES ('g1', 'user:custom', 'com.a')",
            )
            db.query(
                "SELECT COUNT(*) FROM rule_apps WHERE pkg = 'com.a' AND groupId = 'g1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }
}
