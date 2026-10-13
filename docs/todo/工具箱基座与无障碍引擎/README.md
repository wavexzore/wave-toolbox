# 工具箱基座与无障碍引擎

> **模板定位：** LLM 持续实施控制台  
> **功能 ID：** `toolbox-core`  
> **对应 SDD Spec：** 无  
> **对应需求：** `R-001 ~ R-010`（本文件 §2.1）  
> **功能分支：** `main`  
> **优先级：** P0（两个首批功能 `splash-ad-skipper`、`wechat-zombie-cleaner` 均依赖本功能）  
> **负责人：** wangxc  
> **最新代码校准日期：** `2026-10-13`

---

## 0. LLM-STATE（每轮实施前必须读取）

```yaml
schema_version: "1.0"
feature_id: "toolbox-core"
feature_name: "工具箱基座与无障碍引擎"
status: "in_progress"
current_task: "T-002"
last_completed_task: "T-001"
next_action: "执行 T-002：app 壳（Hilt、Compose 主题、导航、设置存储）→ 可安装启动空壳"
verification_status: "pass"
research_status: "pass"
call_chain_status: "pass"
code_calibration_status: "pass"
last_verification_command: "./gradlew test lint :app:assembleDebug（2026-10-13 PASS）"
last_updated: "2026-10-13T22:10:00+08:00"
resume_from:
  - "README.md#0-LLM-STATE每轮实施前必须读取"
  - "01-实施计划.md#6-LLM任务队列"
  - "02-实施状态与验证.md#3-执行日志"
allowed_scope:
  - "settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml / gradle.properties"
  - ".gitignore / local.properties"
  - "app/**"
  - "core/**"
  - "feature/**"
  - "scripts/**"
  - "README.md（仓库根）"
  - "LICENSE"
  - "docs/todo/工具箱基座与无障碍引擎/**"
forbidden_scope:
  - "third_party/**（只读参考，不纳入构建）"
  - "docs/todo/开屏广告自动跳过/**、docs/todo/微信僵尸好友检测与清理/**（各自独立维护）"
stop_condition: "所有 required=true 的 T-### 均为 done，02-实施状态与验证.md 记录 VERDICT: PASS"
blocker_policy: "遇到 blocked 时记录 B-###、失败证据、已尝试方案和下一恢复动作"
```

> **状态更新规则：** LLM 每完成一个任务，必须同步更新本块的 `current_task`、`last_completed_task`、`next_action`、`verification_status`、`code_calibration_status` 和 `last_updated`。

---

## 1. 一句话结论

新建仓库尚无任何代码；本方案确定 **Kotlin + Jetpack Compose + 自研无障碍引擎（事件管线 / 选择器匹配 / 动作执行 / 任务编排 / 保活）** 作为工具箱基座，先交付可独立验证的引擎最小切片（T-001 ~ T-012），为 `splash-ad-skipper` 与 `wechat-zombie-cleaner` 提供统一能力层。产品定名 **wave工具箱**（applicationId `com.wavexzore.toolbox`，Q-001 已定）；服务组件类名固定伪装 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`（D-011，微信空树门禁）。

---

## 2. 功能目标与非目标

### 2.1 目标

| R-ID | 目标 | 验收方式 | 状态 |
|---|---|---|---|
| R-001 | Kotlin 多模块工程骨架可构建、可安装运行 | `./gradlew :app:assembleDebug` 成功；`adb install` 启动无崩溃 | todo |
| R-002 | AccessibilityService 可在设备开启并稳定接收事件 | 手动：开启服务后 ≥30 分钟连续接收事件不中断 | todo |
| R-003 | 事件管线具备节流、单线程调度、ANR 规避 | `:core:accessibility:test` 通过；设备观测无 ANR | todo |
| R-004 | 选择器引擎（解析+匹配）纯 JVM 可测 | `:core:selector:test` 全部通过（含快照树 fixtures） | todo |
| R-005 | 动作执行层：节点点击/可点击祖先/坐标手势/返回/滚动 + 冷却/次数限制 | `:core:accessibility:test` + 设备手动验证清单 | todo |
| R-006 | 保活组合：TYPE_ACCESSIBILITY_OVERLAY 1×1 悬浮窗 + 常驻通知 + 开机自启 + OEM 引导页 | 手动：锁屏/重启后服务自动恢复 | todo |
| R-007 | 任务编排器：Step 状态机（等待/查找/点击/返回/滚动/读取），支持超时、重试、取消 | `:core:automation:test` 通过 | todo |
| R-008 | Feature SPI：新功能只需注册 `ToolFeature` 即接入事件分发与设置入口 | `:feature:adskip` 空壳接入演示 | todo |
| R-009 | 可观测：事件/动作结构化日志、快照导出、按功能统计 | 日志页与导出文件可读 | todo |
| R-010 | 质量门全绿：单测、Lint、Debug 构建 | `./gradlew test lint :app:assembleDebug` | todo |
| R-011 | 服务组件类名固定伪装 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`（Google SelectToSpeak 伪装名），微信节点树非空（空树门禁冒烟通过） | C-005：微信主界面 `uiautomator dump` ≥50KB 且根 cls 非 null（空树特征 ≈407B） | todo |

### 2.2 非目标

| 项 | 原因 | 后续处理 |
|---|---|---|
| 内置 JS 脚本引擎（AutoX/AutoJs 方式） | 规则引擎覆盖首批场景；Rhino 体积与复杂度高 | NEXT-001：按需引入 QuickJS/Rhino |
| OCR/截图识别 | 文字节点覆盖首批场景；截图需额外权限与算力 | NEXT-002 |
| root 功能增强 | 保持"免 root"承诺 | NEXT-003 |
| 多语言 UI | 首批中文用户 | NEXT-004 |
| 远程订阅规则 | 本地规则先行 | NEXT-005（gkd 订阅格式导入） |
| 复制任何 GPL 代码 | 锁定许可证自由度 | 见 D-010 |

---

## 3. 当前运行时链路 / 目标调用链

### 3.1 调研与调用链证据摘要

| 项 | 状态 | 证据位置 | 结论摘要 |
|---|---|---|---|
| 官方文档调研 | pass | `01-实施计划.md §2.4.1` | a11y service 配置/手势 API、FGS specialUse、Android 13 受限设置、Compose 推荐 |
| GitHub 开源实现调研 | pass | `01-实施计划.md §2.4.2` | 借鉴 gkd 引擎架构、worktool 编排/ANR 自愈、AutoJs6 选择器 API、zfdang 隐私策略 |
| subagent/deepwiki 调用链分析 | pass | `01-实施计划.md §3` | 上游参考调用链已还原（本仓库无代码，替代 GitNexus） |
| 当前代码实时校准 | pass | `02-实施状态与验证.md §8` | 2026-10-13 校准：仓库仅 `.gitignore`，无历史包袱 |

### 3.2 目标调用链

```text
系统事件（TYPE_WINDOW_STATE_CHANGED / CONTENT_CHANGED / ...）
  -> AccessibilityService.onAccessibilityEvent()            [core:accessibility]
  -> EventPipeline：过滤/归一化/入队 -> eventDispatcher 单线程消费 + 100ms 节流
  -> SnapshotCache：safeActiveWindow 缓存（避免重复跨进程 IPC）
  -> FeatureRegistry 按包名分发（Pkg 白名单过滤）
      -> [规则特性] RuleEngine：按 pkg/activity/版本取规则 -> SelectorEngine 匹配 -> ActionExecutor
      -> [任务特性] TaskOrchestrator：Step 状态机 -> 等待条件命中 -> ActionExecutor
  -> ActionExecutor：performAction(ACTION_CLICK) / 可点击祖先回退 / dispatchGesture 坐标兜底
  -> 结果：结构化日志 / 统计 / 通知栏状态
```

关键约束：

1. 默认行为：任何特性关闭时引擎零副作用（仅记录日志）。
2. 所有匹配与执行走单线程 Dispatcher，禁止阻塞主线程；单步骤超时兜底。
3. 引擎不得直接依赖具体特性（`:feature:*` 单向依赖 `:core:*`）。

---

## 4. 当前实施状态总览

| T-ID | 任务 | Status | DependsOn | Evidence | Next |
|---|---|---|---|---|---|
| T-001 | Gradle 多模块骨架 + 版本目录 | done | none | C-001 PASS（APK 902KB） | 初始化并构建 |
| T-002 | app 壳：Hilt、Compose 主题、导航、设置存储 | todo | T-001 | none | 首页空壳可运行 |
| T-003 | SelectToSpeakService（伪装类名） + 配置 XML + manifest + 开启状态观测 | todo | T-002 | none | 服务可开启 |
| T-004 | 事件管线（过滤/节流/单线程/ANR 守卫） | todo | T-003 | none | 管线单测 |
| T-005 | 选择器引擎（NodeView 抽象/解析/匹配/快照 fixtures） | todo | T-001 | none | 单测通过 |
| T-006 | 规则模型 + 仓库（JSON5/assets/Room）+ 版本与 activity 匹配 | todo | T-005 | none | 单测通过 |
| T-007 | 动作执行器（点击链/手势/冷却/次数/回退） | todo | T-004,T-005 | none | 单测+手动 |
| T-008 | 任务编排器（Step DSL/超时/重试/取消/进度） | todo | T-007 | none | 单测通过 |
| T-009 | 保活：overlay+通知+开机自启+OEM 引导 | todo | T-003 | none | 重启恢复 |
| T-010 | Feature SPI + 首页/日志/快照检查器 UI | todo | T-008,T-009 | none | 空壳特性接入 |
| T-011 | 统计与诊断导出 | todo | T-010 | none | 导出可读 |
| T-012 | 质量门与最终验证 | todo | T-011 | none | `VERDICT` |

状态值只允许：`todo`、`in_progress`、`blocked`、`done`、`skipped`。

---

## 5. 交付物清单

| 类型 | 路径/对象 | 预期变更 | 状态 | 证据 |
|---|---|---|---|---|
| Code | `settings.gradle.kts`、`gradle/libs.versions.toml` | 新增 | todo | none |
| Code | `app/`、`core/{common,model,selector,rules,accessibility,automation,feature-api,data,design}/`、`feature/{settings,adskip,wechat}/` | 新增 | todo | none |
| Test | `core/selector/src/test/**`、`core/automation/src/test/**` 等 | 新增 | todo | none |
| Config | `core/accessibility/src/main/res/xml/accessibility_service_config.xml` | 新增 | todo | none |
| Doc | 本目录 `02/03/04` 文档 | 更新 | todo | none |

---

## 6. 数据模型 / 配置 / 接口

### 6.1 数据模型（规则 JSON5，节选）

```json5
{
  id: "com.example.app@SplashActivity",   // 稳定 ID：pkg@activity
  name: "开屏跳过",
  order: 0,                                // 同界面多规则优先级
  activityIds: ["com.example.app.SplashActivity"],
  versionName: { min: "8.0" },             // 可选：版本约束
  matches: ['@[vid="skip"]', '[text^="跳过"]'],  // 必须全部命中（骨架：text/vid/desc/class 前缀语法）
  anyMatches: [],                           // 任一命中即可
  excludeMatches: ['[text="跳过登录"]'],
  action: "click",
  position: null,                           // 或 {x: 900, y: 120}（px） / {x: "95%", y: "5%"}
  actionCd: 500,                            // 冷却毫秒
  actionMaximum: 1,                         // 每个 activity 实例最大次数
  enabled: true
}
```

### 6.2 配置项（DataStore）

| 配置项 | 默认值 | 行为 | 兼容性 |
|---|---|---|---|
| `engine.enabled` | `true` | 引擎总开关；关闭时仅记录事件不动作 | 新增 |
| `engine.eventTypes` | `WINDOW_STATE_CHANGED|WINDOW_CONTENT_CHANGED` 运行时集合 | 控制 setServiceInfo | 新增 |
| `engine.contentThrottleMs` | `100` | CONTENT_CHANGED 节流窗口 | 新增 |
| `debug.snapshotExport` | `false` | 导出窗口节点树 JSON | 新增 |

### 6.3 SPI 接口

```kotlin
interface ToolFeature {
    val id: String                       // e.g. "adskip"
    val name: String                     // e.g. "开屏广告自动跳过"
    val packages: Set<String>            // 关心的包名；空集=全部（受开关约束）
    suspend fun onEvent(ctx: EventContext): Boolean   // 返回是否处理
    @Composable fun SettingsScreen(nav: NavController) // 设置入口
}
```

---

## 7. 验收标准

| AC-ID | 验收项 | 覆盖任务 | 验证命令/证据 | Status |
|---|---|---|---|---|
| AC-001 | Debug 包可构建、安装、启动 | T-001,T-002 | `./gradlew :app:assembleDebug` + `adb install` | todo |
| AC-002 | 服务开启后日志页持续出现事件记录 | T-003,T-004 | 手动：操作任意 App 后查看日志 | todo |
| AC-003 | 选择器解析匹配单测覆盖正/反/边界 | T-005 | `./gradlew :core:selector:test` | todo |
| AC-004 | 规则按 pkg+activity 命中并执行点击 | T-006,T-007 | 手动：用测试 Apk 或系统界面验证 | todo |
| AC-005 | 编排器任务可等待条件、超时、取消 | T-008 | `./gradlew :core:automation:test` | todo |
| AC-006 | 重启后服务自动恢复、通知常驻 | T-009 | 手动：重启设备验证 | todo |
| AC-007 | 空壳特性可注册、可开关、有设置页 | T-010 | 手动：首页出现条目并可进入 | todo |
| AC-008 | 回归：所有质量门命令 | T-012 | `./gradlew test lint :app:assembleDebug` | todo |
| AC-009 | 微信空树门禁冒烟：微信主界面节点树非空（无 407B 空树特征） | T-003,T-012 | `C-005` | todo |

---

## 8. 验证入口

```bash
./gradlew :core:selector:test          # T-005 后
./gradlew :core:automation:test        # T-008 后
./gradlew :app:assembleDebug           # 每任务后
./gradlew test lint :app:assembleDebug # 最终全量（C-999）
```

| C-ID | Command | 何时运行 | 预期 |
|---|---|---|---|
| C-001 | `./gradlew :app:assembleDebug` | T-001 起每任务后 | PASS |
| C-002 | `./gradlew :core:selector:test` | T-005 后 | PASS |
| C-003 | `./gradlew :core:automation:test` | T-008 后 | PASS |
| C-004 | `adb install -r app/build/outputs/apk/debug/app-debug.apk` | T-002 后 | PASS |
| C-005 | 微信空树门禁冒烟：`adb shell uiautomator dump`（微信主界面，根包校验）→ 文件 ≥50KB 且根 cls 非 null | T-003 后、T-012 | PASS |
| C-999 | `./gradlew test lint :app:assembleDebug` | 全部完成后 | PASS |

---

## 9. 风险与回滚摘要

| RK-ID | 风险 | 影响 | 缓解措施 | 回滚方式 |
|---|---|---|---|---|
| RK-001 | 国内 OEM 杀后台导致服务静默失效 | 高 | 保活组合 + OEM 引导页 + 心跳自检（T-009） | 关闭保活组件回到系统默认 |
| RK-002 | Android 13+ 侧载受限设置导致无障碍灰色 | 高 | 引导页专章 + 检测状态分支（T-009） | 无代码回滚需求 |
| RK-003 | 事件风暴引发耗电/ANR | 中 | 节流+单线程+白名单包过滤（T-004） | 调大 throttle 或限制 eventTypes |
| RK-004 | 多特性相互干扰（点击冲突） | 中 | Feature 优先级 + 冷却 + 处理权返回语义（T-010） | 关闭对应特性 |
| RK-005 | 上游参考实现变更导致规则失效 | 低 | 快照 fixtures 回归（T-005） | 更新 fixtures |
| RK-006 | 微信空树门禁回归（类名被改/白名单变更） | 高 | 类名冻结（D-011）+ 空树冒烟进 CI 清单（C-005）；空树 407B/根 null 特征可快速定位 | 核对服务组件名；必要时重新伪装验证 |

---

## 10. 文档索引

| 文档 | 用途 |
|---|---|
| [`00-使用说明.md`](00-使用说明.md) | LLM 执行协议、状态枚举、证据规范 |
| [`01-实施计划.md`](01-实施计划.md) | 调研证据、任务队列、步骤、文件范围 |
| [`02-实施状态与验证.md`](02-实施状态与验证.md) | 执行日志、命令结果、验收覆盖、最终 VERDICT |
| [`03-复盘与交接.md`](03-复盘与交接.md) | 决策、踩坑、回滚、维护与交接 |
| [`04-设计方案.md`](04-设计方案.md) | 架构设计、组件职责、数据流、验证方案 |

---

## 11. 变更日志

| 时间 | 变更 | 作者/Agent | 证据 |
|---|---|---|---|
| 2026-10-13T18:00:00+08:00 | 创建实施控制台（含调研证据） | opencode agent | `01-实施计划.md §2.4` |
| 2026-10-13T20:30:00+08:00 | Q-001~003 决议：名称 wave工具箱 / com.wavexzore.toolbox；许可证 Apache-2.0；分发首发侧载+GitHub Release | opencode agent | 用户决议 |
| 2026-10-13T21:30:00+08:00 | 新增 R-011/RK-006/AC-009/C-005：服务组件类名固定伪装 SelectToSpeak（微信空树门禁，参考 wavexzore-ledger D-014/PIT-004） | opencode agent | ledger `03 §3 D-014`、`02 §3 E-032` |
