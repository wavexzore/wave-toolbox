package com.wavexzore.toolbox.core.accessibility.action

import com.wavexzore.toolbox.core.selector.snapshot.SnapshotCollector
import com.wavexzore.toolbox.core.selector.snapshot.SnapshotTree
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** 设备侧快照采集（T-010）：当前活动窗口树 → JSON（服务未连接/窗口缺失返回 null）。 */
@Singleton
class WindowSnapshotCollector @Inject constructor(
    private val treeProvider: WindowTreeProvider,
) : SnapshotCollector {

    override fun collectJson(): String? {
        val picked = treeProvider.currentTree() ?: return null
        return SnapshotTree.toJson(picked.tree.root)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SnapshotModule {

    @Binds
    abstract fun bindSnapshotCollector(collector: WindowSnapshotCollector): SnapshotCollector
}
