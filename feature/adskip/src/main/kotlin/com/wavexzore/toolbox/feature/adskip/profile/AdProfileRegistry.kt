package com.wavexzore.toolbox.feature.adskip.profile

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 识别器注册表（T-202；D-217：Hilt 多绑定薄封装，不重复造 DI）。
 *
 * 生命周期：`@Singleton` 注入（`Set<AppAdProfile>` 由 Hilt `@IntoSet` 收集，`@Multibinds` 空集兜底）；
 * 冲突规则：同一 pkg 被多个 profile 声明 → **启动 fail-fast**（构造断言，保证解析唯一）。
 */
@Singleton
class AdProfileRegistry @Inject constructor(
    profiles: Set<@JvmSuppressWildcards AppAdProfile>,
) {

    val profiles: List<AppAdProfile> = profiles.toList()

    private val byPkg: Map<String, AppAdProfile> = buildMap {
        for (profile in profiles) {
            for (pkg in profile.pkgs) {
                val prev = put(pkg, profile)
                check(prev == null) {
                    "同一包名被多个 AppAdProfile 注册：$pkg（${prev?.javaClass?.simpleName} vs ${profile.javaClass.simpleName}）"
                }
            }
        }
    }

    fun resolve(pkg: String): AppAdProfile? = byPkg[pkg]
}
