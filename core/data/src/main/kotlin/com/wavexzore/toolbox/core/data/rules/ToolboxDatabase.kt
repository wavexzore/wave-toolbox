package com.wavexzore.toolbox.core.data.rules

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wavexzore.toolbox.core.data.stats.StatDao
import com.wavexzore.toolbox.core.data.stats.StatRecordEntity

@Database(
    entities = [RuleGroupEntity::class, RuleAppEntity::class, RuleErrorEntity::class, StatRecordEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class ToolboxDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    abstract fun statDao(): StatDao

    companion object {
        const val NAME = "toolbox.db"

        /** v1 → v2：新增动作统计表（T-011）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stat_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`feature_id` TEXT NOT NULL, " +
                        "`pkg` TEXT, " +
                        "`rule_id` TEXT, " +
                        "`kind` TEXT NOT NULL, " +
                        "`ok` INTEGER NOT NULL, " +
                        "`mechanism` TEXT, " +
                        "`duration_ms` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stat_records_at` ON `stat_records` (`at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stat_records_pkg` ON `stat_records` (`pkg`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stat_records_kind` ON `stat_records` (`kind`)")
            }
        }

        /** v2 → v3：统计明细新增 zone/provider 维度（T-207）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `stat_records` ADD COLUMN `zone` TEXT")
                db.execSQL("ALTER TABLE `stat_records` ADD COLUMN `provider` TEXT")
            }
        }

        /**
         * v3 → v4：规则组主键改为 `(id, source)`，规则包索引带 source。
         *
         * 用户规则包与内置包允许同 id 共存（用户包按 pkg 整包覆盖内置）；
         * 旧库组 id 唯一，迁移时用旧 rule_groups.source 回填 rule_apps.source。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rule_groups_new` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `order` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                        "`json` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`, `source`))",
                )
                db.execSQL(
                    "INSERT INTO `rule_groups_new` " +
                        "(`id`, `name`, `order`, `enabled`, `schemaVersion`, `source`, `json`, `updatedAt`) " +
                        "SELECT `id`, `name`, `order`, `enabled`, `schemaVersion`, `source`, `json`, `updatedAt` " +
                        "FROM `rule_groups`",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rule_apps_new` (" +
                        "`groupId` TEXT NOT NULL, `source` TEXT NOT NULL, `pkg` TEXT NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `source`, `pkg`))",
                )
                db.execSQL(
                    "INSERT INTO `rule_apps_new` (`groupId`, `source`, `pkg`) " +
                        "SELECT a.`groupId`, g.`source`, a.`pkg` FROM `rule_apps` a " +
                        "JOIN `rule_groups` g ON g.`id` = a.`groupId`",
                )
                db.execSQL("DROP TABLE `rule_apps`")
                db.execSQL("ALTER TABLE `rule_apps_new` RENAME TO `rule_apps`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rule_apps_pkg` ON `rule_apps` (`pkg`)")
                db.execSQL("DROP TABLE `rule_groups`")
                db.execSQL("ALTER TABLE `rule_groups_new` RENAME TO `rule_groups`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rule_groups_source` ON `rule_groups` (`source`)")
            }
        }
    }
}
