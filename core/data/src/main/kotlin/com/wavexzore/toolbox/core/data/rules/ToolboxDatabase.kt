package com.wavexzore.toolbox.core.data.rules

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wavexzore.toolbox.core.data.stats.StatDao
import com.wavexzore.toolbox.core.data.stats.StatRecordEntity

@Database(
    entities = [RuleGroupEntity::class, RuleAppEntity::class, RuleErrorEntity::class, StatRecordEntity::class],
    version = 2,
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
    }
}
