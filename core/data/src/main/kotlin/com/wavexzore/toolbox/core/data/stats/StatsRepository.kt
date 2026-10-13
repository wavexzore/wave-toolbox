package com.wavexzore.toolbox.core.data.stats

import com.wavexzore.toolbox.core.common.dispatchers.AppDispatchers
import com.wavexzore.toolbox.core.data.rules.ToolboxDatabase
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 动作统计仓库（T-011）：写路径全异步（事件线程零阻塞），读路径 Flow 供统计页。
 * 保留 [RETENTION_DAYS] 天，每 [PURGE_EVERY_INSERTS] 次写入顺带清理。
 */
@Singleton
class StatsRepository @Inject constructor(
    database: ToolboxDatabase,
    dispatchers: AppDispatchers,
) {

    private val dao = database.statDao()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val inserts = AtomicLong()

    /** 记一条（非挂起，事件线程可直调）。 */
    fun record(
        featureId: String = ENGINE_FEATURE_ID,
        pkg: String?,
        ruleId: String?,
        kind: String,
        ok: Boolean,
        mechanism: String? = null,
        durationMs: Long = 0,
        zone: String? = null,
        provider: String? = null,
    ) {
        val now = System.currentTimeMillis()
        scope.launch {
            runCatching {
                dao.insert(
                    StatRecordEntity(
                        at = now,
                        featureId = featureId,
                        pkg = pkg,
                        ruleId = ruleId,
                        kind = kind,
                        ok = ok,
                        mechanism = mechanism,
                        durationMs = durationMs,
                        zone = zone,
                        provider = provider,
                    ),
                )
                if (inserts.incrementAndGet() % PURGE_EVERY_INSERTS == 0L) {
                    dao.deleteBefore(now - RETENTION_DAYS * DAY_MS)
                }
            }
        }
    }

    fun totalsSince(since: Long): Flow<StatsTotals> = dao.totalsSince(since)

    fun totalsSinceFor(featureId: String, since: Long): Flow<StatsTotals> = dao.totalsSinceFor(featureId, since)

    fun topPackages(since: Long, limit: Int = 10): Flow<List<PkgStat>> = dao.topPackages(since, limit)

    fun topPackagesFor(featureId: String, since: Long, limit: Int = 20): Flow<List<PkgStat>> = dao.topPackagesFor(featureId, since, limit)

    fun byKind(since: Long): Flow<List<KindStat>> = dao.byKind(since)

    suspend fun allSince(since: Long, limit: Int = 5_000): List<StatRecordEntity> = dao.allSince(since, limit)

    /** 特性维度明细流（日志页）。 */
    fun recordsSinceFor(featureId: String, since: Long = 0L, limit: Int = 200): Flow<List<StatRecordEntity>> =
        dao.recordsSinceFor(featureId, since, limit)

    fun clearAll() {
        scope.launch { dao.clearAll() }
    }

    companion object {
        const val ENGINE_FEATURE_ID = "engine"
        const val DAY_MS = 86_400_000L
        const val RETENTION_DAYS = 14L
        private const val PURGE_EVERY_INSERTS = 200L
    }
}
