package com.wavexzore.toolbox.core.data.rules

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rule_groups")
data class RuleGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int,
    val enabled: Boolean,
    val schemaVersion: Int,
    val source: String,
    val json: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "rule_apps",
    primaryKeys = ["groupId", "pkg"],
    indices = [Index("pkg")],
)
data class RuleAppEntity(
    val groupId: String,
    val pkg: String,
)

@Entity(tableName = "rule_errors")
data class RuleErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val groupId: String?,
    val ruleId: String?,
    val message: String,
    val fatal: Boolean,
    val createdAt: Long,
)

@Dao
interface RuleDao {

    @Query("SELECT * FROM rule_groups WHERE enabled = 1 ORDER BY `order`, id")
    suspend fun enabledGroups(): List<RuleGroupEntity>

    @Query("SELECT * FROM rule_groups WHERE enabled = 1 ORDER BY `order`, id")
    fun observeEnabledGroups(): Flow<List<RuleGroupEntity>>

    /** 按包名索引直查（pkg + enabled 命中走 rule_apps.pkg 索引）。 */
    @Query(
        """
        SELECT DISTINCT g.* FROM rule_groups g
        JOIN rule_apps a ON a.groupId = g.id
        WHERE a.pkg = :pkg AND g.enabled = 1
        ORDER BY g.`order`, g.id
        """,
    )
    suspend fun groupsForPackage(pkg: String): List<RuleGroupEntity>

    @Query("SELECT DISTINCT pkg FROM rule_apps")
    fun observePackages(): Flow<List<String>>

    /** 某来源是否已导入（用于区分「全部规则被禁用」与「从未导入」）。 */
    @Query("SELECT COUNT(*) FROM rule_groups WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Upsert
    suspend fun upsertGroups(groups: List<RuleGroupEntity>)

    @Upsert
    suspend fun upsertApps(apps: List<RuleAppEntity>)

    @Query("DELETE FROM rule_groups WHERE source = :source")
    suspend fun deleteGroupsBySource(source: String)

    @Query("DELETE FROM rule_apps WHERE groupId NOT IN (SELECT id FROM rule_groups)")
    suspend fun deleteOrphanApps()

    @Insert
    suspend fun insertErrors(errors: List<RuleErrorEntity>)

    @Query("SELECT * FROM rule_errors ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentErrors(limit: Int = 200): List<RuleErrorEntity>

    @Query("DELETE FROM rule_errors")
    suspend fun clearErrors()

    @Transaction
    suspend fun replaceSource(source: String, groups: List<RuleGroupEntity>, apps: List<RuleAppEntity>) {
        deleteGroupsBySource(source)
        deleteOrphanApps()
        upsertGroups(groups)
        upsertApps(apps)
    }
}
