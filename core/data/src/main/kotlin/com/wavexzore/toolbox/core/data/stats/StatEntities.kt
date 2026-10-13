package com.wavexzore.toolbox.core.data.stats

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 动作统计明细（T-011）：按特性/包/规则/结果/机制记账；保留 14 天。 */
@Entity(
    tableName = "stat_records",
    indices = [Index("at"), Index("pkg"), Index("kind")],
)
data class StatRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    @ColumnInfo(name = "feature_id") val featureId: String,
    val pkg: String?,
    @ColumnInfo(name = "rule_id") val ruleId: String?,
    val kind: String,
    val ok: Boolean,
    val mechanism: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

data class StatsTotals(
    val total: Long,
    @ColumnInfo(name = "ok_count") val okCount: Long,
    @ColumnInfo(name = "fail_count") val failCount: Long,
)

data class PkgStat(
    val pkg: String,
    val count: Long,
    @ColumnInfo(name = "ok_count") val okCount: Long,
)

data class KindStat(
    val kind: String,
    val count: Long,
)

@Dao
interface StatDao {

    @Insert
    suspend fun insert(record: StatRecordEntity)

    @Query("SELECT COUNT(*) AS total, SUM(CASE WHEN ok THEN 1 ELSE 0 END) AS ok_count, SUM(CASE WHEN ok THEN 0 ELSE 1 END) AS fail_count FROM stat_records WHERE at >= :since")
    fun totalsSince(since: Long): Flow<StatsTotals>

    @Query("SELECT pkg, COUNT(*) AS count, SUM(CASE WHEN ok THEN 1 ELSE 0 END) AS ok_count FROM stat_records WHERE at >= :since AND pkg IS NOT NULL GROUP BY pkg ORDER BY count DESC LIMIT :limit")
    fun topPackages(since: Long, limit: Int): Flow<List<PkgStat>>

    @Query("SELECT kind, COUNT(*) AS count FROM stat_records WHERE at >= :since GROUP BY kind ORDER BY count DESC")
    fun byKind(since: Long): Flow<List<KindStat>>

    @Query("SELECT * FROM stat_records WHERE at >= :since ORDER BY at DESC LIMIT :limit")
    suspend fun allSince(since: Long, limit: Int): List<StatRecordEntity>

    @Query("DELETE FROM stat_records WHERE at < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM stat_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM stat_records")
    suspend fun count(): Long
}
