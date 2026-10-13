package com.wavexzore.toolbox.core.data.rules

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RuleGroupEntity::class, RuleAppEntity::class, RuleErrorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ToolboxDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    companion object {
        const val NAME = "toolbox.db"
    }
}
