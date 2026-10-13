# 开屏广告自动跳过

> **模板定位：** LLM 持续实施控制台  
> **功能 ID：** `splash-ad-skipper`  
> **对应 SDD Spec：** 无  
> **对应需求：** `R-001 ~ R-008`（本文件 §2.1）  
> **功能分支：** `main`  
> **优先级：** P0（首批功能之一）  
> **负责人：** wangxc  
> **前置依赖：** `docs/todo/工具箱基座与无障碍引擎/`（引用格式 `[基座]T-00X`）  
> **最新代码校准日期：** `2026-10-13`

---

## 0. LLM-STATE（每轮实施前必须读取）

```yaml
schema_version: "1.0"
feature_id: "splash-ad-skipper"
feature_name: "开屏广告自动跳过"
status: "todo"
current_task: "T-001"
last_completed_task: "none"
next_action: "等待/确认基座 [基座]T-006、T-007、T-010 完成；先执行 T-001（特性模块与规则包设计冻结）"
verification_status: "not_run"
research_status: "pass"
call_chain_status: "pass"
code_calibration_status: "pass"
last_verification_command: "none"
last_updated: "2026-10-13T20:30:00+08:00"
resume_from:
  - "README.md#0-LLM-STATE每轮实施前必须读取"
  - "01-实施计划.md#6-LLM任务队列"
  - "02-实施状态与验证.md#3-执行日志"
allowed_scope:
  - "feature/adskip/**"
  - "core/rules/src/main/assets/rules/adskip/**"
  - "docs/todo/开屏广告自动跳过/**"
forbidden_scope:
  - "third_party/**"
  - "core/accessibility/**、core/selector/**（基座冻结前不改；如需改动先回写基座计划）"
forbidden_notes: "引擎级改动一律回到 [基座] 任务，禁止在特性内私改引擎"
stop_condition: "所有 required=true 的 T-### 均为 done，02 记录 VERDICT: PASS"
blocker_policy: "记录 B-###、失败快照、已尝试方案、下一恢复动作"
```

---

## 1. 一句话结论

在基座（`[基座]T-006/T-007/T-010`）之上实现被动规则特性：**监听窗口事件 → 命中"跳过/关闭"规则 → 点击最优候选节点**，并把李跳跳还原源码中的关键词优先级、过滤条件、树路径与坐标兜底完整移植为 JSON5 规则体系；当前状态 `todo`，等基座关键任务就绪后从 T-001 开始。

---

## 2. 功能目标与非目标

### 2.1 目标

| R-ID | 目标 | 验收方式 | 状态 |
|---|---|---|---|
| R-001 | 关键词跳过：命中"跳过/关闭/×/我知道了"等文本或 desc 的节点优先点击（Y 最小优先） | 首批 10 个应用手动清单 ≥8 成功 | todo |
| R-002 | 规则化作用域：按包名/Activity/版本与界面切换触发，支持冷却与最大次数 | `:feature:adskip:test` 规则匹配用例 | todo |
| R-003 | 内置默认规则（精选 20+ 国内高频 App）+ 用户自定义覆盖 + JSON5 导入导出 | 规则管理页手动验证 | todo |
| R-004 | 开关体系：总开关、按 App 开关、黑/白名单、静默模式（仅开屏 vs 任意弹窗） | 设置页手动验证 | todo |
| R-005 | 统计：今日/累计跳过次数与按 App 明细 | 统计页手动验证 | todo |
| R-006 | 防误触：过滤条件（编辑框/勾选框/滚动容器/大宽无文本）、冷却、每次实例限次、点击后校验 | 单测 + 误点清单复核 | todo |
| R-007 | 树路径/坐标兜底（移植李跳跳 `clicks` 语义） | 至少 2 个无法关键词命中的 App 验证 | todo |
| R-008 | 全部匹配逻辑 JVM 单测（真实快照 fixtures） | `:feature:adskip:test` 全绿 | todo |

### 2.2 非目标

| 项 | 原因 | 后续处理 |
|---|---|---|
| "摇一摇"传感器劫持检测 | 需加速度计比对，基座无 | NEXT-201 |
| 图片型跳过按钮（无文本/desc） | 需 OCR | NEXT-202（依赖基座 NEXT-002） |
| 远程订阅规则更新 | 本地规则先行 | NEXT-203 |
| 完全屏蔽广告（不加载） | 需 hook/代理 | NEXT-204 |

---

## 3. 当前运行时链路 / 目标调用链

### 3.1 调研与调用链证据摘要

| 项 | 状态 | 证据位置 | 结论摘要 |
|---|---|---|---|
| 官方文档调研 | pass | `01 §2.4.1` | 事件/节点/手势 API 契约；节流与 ANR 约束 |
| GitHub 开源实现调研 | pass | `01 §2.4.2` | 李跳跳（语义蓝本）、gkd（规则字段）、zfdang（三档策略）、paper（可行性） |
| 上游调用链分析 | pass | `01 §3` | 李跳跳 `LttService.onAccessibilityEvent → e/f/b/d 任务 → performAction` 全链路已还原 |
| 当前代码实时校准 | pass | `02 §8` | 2026-10-13：仓库零代码；基座计划 v1.0 已产出 |

### 3.2 目标调用链

```text
系统 WINDOW_STATE_CHANGED(SplashActivity) / CONTENT_CHANGED
  -> [基座]EventPipeline 过滤节流
  -> AdSkipFeature.onEvent(ctx)                       // pkg 在作用域内
  -> 模式判定: 静默(仅 activity 切换触发) / 全局
  -> [基座]RuleEngine: Room 查 adskip 组规则(pkg+activity+版本)
  -> 逐关键词优先级匹配 → 命中候选过滤(c()) → 点击最优(可点击祖先→坐标兜底)
  -> 冷却/账本记账 → 统计 + 日志
```

关键约束：

1. 特性关闭时零动作、仅入库统计为 0。
2. 单次事件最多触发一次点击（防连点由基座账本兜底）。
3. 规则文件不得写死屏幕坐标（除明显固定位置的坐标规则，且用百分比）。

---

## 4. 当前实施状态总览

| T-ID | 任务 | Status | DependsOn | Evidence | Next |
|---|---|---|---|---|---|
| T-001 | 特性模块 + 规则包结构 + schema 冻结 | todo | [基座]T-006 | none | 建模块 |
| T-002 | 关键词跳过策略（优先级/过滤/排序） | todo | T-001,[基座]T-005 | none | 单测 |
| T-003 | 规则仓库接入（Room/assets/导入导出） | todo | T-001 | none | 单测 |
| T-004 | 开关体系与设置页（总/按App/名单/模式） | todo | T-003,[基座]T-010 | none | 手动 |
| T-005 | 树路径/坐标兜底动作 | todo | T-002,[基座]T-007 | none | 手动 |
| T-006 | 内置默认规则集（20+ App） | todo | T-003 | none | 手动清单 |
| T-007 | 统计页与明细 | todo | T-004,[基座]T-011 | none | 手动 |
| T-008 | 快照回归 fixtures 与最终验证 | todo | T-006 | none | `VERDICT` |

状态值只允许：`todo`、`in_progress`、`blocked`、`done`、`skipped`。

---

## 5. 交付物清单

| 类型 | 路径/对象 | 预期变更 | 状态 | 证据 |
|---|---|---|---|---|
| Code | `feature/adskip/**` | 新增 | todo | none |
| Rules | `core/rules/src/main/assets/rules/adskip/*.json5` | 新增 | todo | none |
| Test | `feature/adskip/src/test/**` | 新增 | todo | none |
| Doc | 本目录 02/03/04 | 更新 | todo | none |

---

## 6. 数据模型 / 配置 / 接口

### 6.1 数据模型（内置规则，李跳跳语义移植示例）

```json5
// assets/rules/adskip/com.example.app.json5
{
  schemaVersion: 1,
  pkg: "com.example.app",
  groups: [
    {
      id: "com.example.app#splash",
      name: "开屏跳过",
      order: 0,
      activityIds: ["com.example.app.SplashActivity"],
      matches: ['[text="跳过"]'],       // 语义: 含"跳过"且 Y 最小优先（由引擎排序）
      action: "click",
      actionCd: 800,
      actionMaximum: 1,                 // 每次 activity 实例 1 次
      mode: "splash"                    // splash=仅开屏类 activity 触发
    },
    {
      id: "com.example.app#popup",
      name: "弹窗关闭",
      order: 10,
      matches: ['[text*="我知道了"]', '@[vid="close"]'],
      action: "click",
      actionCd: 1000,
      actionMaximum: 3,
      mode: "any"                       // 任意界面
    }
  ]
}
```

### 6.2 配置项

| 配置项 | 默认值 | 行为 | 兼容性 |
|---|---|---|---|
| `adskip.enabled` | `true` | 特性总开关（默认开，符合用户安装预期） | 新增 |
| `adskip.mode` | `splash` | `splash`=仅开屏类 Activity 首屏；`any`=任意弹窗 | 新增 |
| `adskip.perApp[pkg]` | 未设置=跟随总开关 | 单 App 开关 | 新增 |
| `adskip.keywords.extra` | `[]` | 用户追加关键词 | 新增 |

---

## 7. 验收标准

| AC-ID | 验收项 | 覆盖任务 | 验证命令/证据 | Status |
|---|---|---|---|---|
| AC-001 | 关键词匹配单测（原子性/排序/过滤） | T-002 | `./gradlew :feature:adskip:test` | todo |
| AC-002 | 规则导入导出与坏规则隔离 | T-003 | 同上 + 手动 | todo |
| AC-003 | 开关矩阵生效（总关/单关/名单） | T-004 | 手动清单 | todo |
| AC-004 | 10 应用实测 ≥8 成功跳过 | T-006 | 手动清单（记录版本/耗时） | todo |
| AC-005 | 冷却与限次：同一界面不连点 | T-002,T-006 | 手动 + 统计 | todo |
| AC-006 | 树路径/坐标兜底可用 | T-005 | 手动 2 例 | todo |
| AC-007 | 统计数字与实际一致 | T-007 | 手动比对 | todo |
| AC-008 | 快照回归 fixtures 全绿 | T-008 | `./gradlew :feature:adskip:test` | todo |

---

## 8. 验证入口

```bash
./gradlew :feature:adskip:test          # T-002 起每任务后
./gradlew :app:assembleDebug            # 每任务后
# 手动：10 应用清单（02 §4 记录）
```

| C-ID | Command | 何时运行 | 预期 |
|---|---|---|---|
| C-101 | `./gradlew :feature:adskip:test` | T-002 后 | PASS |
| C-102 | `./gradlew :app:assembleDebug` | 每任务后 | PASS |
| C-103 | 10 应用手动清单 | T-006 后 | ≥8 成功 |
| C-199 | `./gradlew test lint :app:assembleDebug` | 全部完成后 | PASS |

---

## 9. 风险与回滚摘要

| RK-ID | 风险 | 影响 | 缓解措施 | 回滚方式 |
|---|---|---|---|---|
| RK-101 | 误点（点到登录/引导按钮） | 中 | 过滤条件 + 冷却 + 限次 + 名单 | 单 App 关闭 |
| RK-102 | 应用改版导致规则失效 | 中 | fixtures 回归 + 版本字段 + 关键词兜底 | 规则库更新 |
| RK-103 | 点击过早（跳转刚发生就点） | 中 | 年轻弹窗延迟（可配 100~300ms） | 调参 |
| RK-104 | 与微信批检任务抢点击 | 低 | 基座任务互斥 | 关任一特性 |

---

## 10. 文档索引

| 文档 | 用途 |
|---|---|
| [`00-使用说明.md`](00-使用说明.md) | LLM 执行协议、状态枚举、证据规范 |
| [`01-实施计划.md`](01-实施计划.md) | 调研证据、任务队列、步骤、文件范围 |
| [`02-实施状态与验证.md`](02-实施状态与验证.md) | 执行日志、命令结果、验收覆盖、最终 VERDICT |
| [`03-复盘与交接.md`](03-复盘与交接.md) | 决策、踩坑、回滚、维护与交接 |
| [`04-设计方案.md`](04-设计方案.md) | 策略/规则/防误触设计、验证方案 |

---

## 11. 变更日志

| 时间 | 变更 | 作者/Agent | 证据 |
|---|---|---|---|
| 2026-10-13T18:00:00+08:00 | 创建实施控制台（含调研证据） | opencode agent | `01 §2.4` |
| 2026-10-13T20:30:00+08:00 | Q-101/102 决议：内置规则 Top20 起步；默认 splash 模式 | opencode agent | 用户决议 |
