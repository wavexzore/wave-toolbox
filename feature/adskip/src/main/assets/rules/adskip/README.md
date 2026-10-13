# adskip 规则包 schema v2（v1 兼容；v1 冻结于 2026-10-13，v2 于 2026-10-14 批准 Q-208）

> 目录：`feature/adskip/src/main/assets/rules/adskip/*.json5`（内置）；用户覆盖放 `<files>/adskip/*.json5`（同 pkg 整包覆盖）。
> 解析：`li.songe:json5`（JSON5 语法）；未知字段忽略；`schemaVersion` 高于支持版本报错隔离。
> v1 文件零修改可用：所有 v2 字段缺省值 = v1 旧语义（见「v2 新增」列）。

## 文件结构

```json5
{
  schemaVersion: 2,          // 必填；<= 2（v1 仍可解析）
  pkg: "com.example.app",    // 必填；目标应用包名
  packVersion: 1,            // 规则包版本（导出/回归比对用）
  groups: [ /* AdSkipGroup[]，见下 */ ]
}
```

## 组（AdSkipGroup，全部字段）

| 字段 | 类型 | 默认 | 语义 |
|---|---|---|---|
| `id` | string | 必填 | 稳定 ID（`pkg#用途`） |
| `name` | string | "" | 展示名 |
| `order` | int | 0 | 执行顺序（小者先） |
| `enabled` | bool | true | 组开关 |
| `activityIds` | string[] | [] | Activity 约束（空=任意；全等/前缀/后缀）。**v2 起接线**（v1 仅解析） |
| `excludeActivityIds` | string[] | [] | Activity 排除（命中即不进该组） |
| `mode` | `splash`\|`any` | `splash` | 全局 `adskip.mode=splash` 时仅 `splash` 组参与 |
| `zone` | `entry`\|`activity`\|`popup`\|`any` | 由 mode 推导 | **v2 新增**：生效区域；`splash→entry`、`any→any` |
| `matches` | string[] | [] | 基座选择器语法（OR）；命中后同样走过滤/排序（Y 最小+顶部优先） |
| `keywords` | string[] | [] | 该应用追加关键词（并入默认关键词，包含匹配） |
| `path` | string | null | 树路径兜底：`0/2/1`（逐层按 drawingOrder 排序后的子索引） |
| `position` | object | null | 坐标兜底（px 或 %，二选一）：`{x,y}` / `{xPercent,yPercent}` |
| `actionCd` | long | 800 | 组级最小间隔毫秒。**v2 起生效**（内置声明值 ≤1200 受全局冷却支配→节奏不变；声明 >1200 才会放宽） |
| `actionMaximum` | int | 1 | 同实例最大执行次数 |
| `delayMs` | long | 150 | 组级年轻窗口延迟毫秒。**v2 起生效**（默认同旧值；取命中组最小值） |
| `settleMs` | long | 350 | **v2 新增**：`activity` 区域稳定期（0~2000） |
| `popupAware` | bool? | null | **v2 新增**：弹窗窗口放行。null=按运行模式：`splash` 拦、`any` 放行（Q-206 决议）；显式 true/false 恒定 |
| `suppressGeneric` | bool | false | **v2 新增**：本组命中活动页时压制通用 keyword/vid 回退 |

## 执行优先级（T-002/T-005；T-203 起为 Provider 链）

`关键词（含 vid 片段）` → `matches` → `path` → `position`（前档为避免误点，兜底档脆弱性最高）。

## 示例

```json5
{
  schemaVersion: 2,
  pkg: "com.example.app",
  packVersion: 1,
  groups: [
    {
      id: "com.example.app#splash",
      name: "开屏跳过",
      order: 0,
      activityIds: ["com.example.app.SplashActivity"],
      matches: ['[text*="跳过"]', '[vid="skip"]'],
      keywords: ["跳过 3s"],
      actionCd: 800,
      actionMaximum: 1,
      mode: "splash",
    },
    {
      id: "com.example.app#popup",
      name: "弹窗关闭（仅声明后生效于弹窗窗口）",
      order: 10,
      zone: "popup",
      popupAware: true,
      matches: ['[text*="我知道了"]', '@[vid="close"]'],
      mode: "any",
      actionMaximum: 3,
    },
  ],
}
```

## 约束

1. 坐标不得写死像素（优先百分比；仅明显固定位置用 px 并注明机型）。
2. 坏文件隔离：单文件解析失败只进错误列表，不影响其它包。
3. 每包文件独立（便于回归与覆盖）；同名 pkg 用户包整包覆盖内置。
4. v2 字段治理：`zone=activity` 应同时给出 `activityIds`；`popupAware=true` 需在真机核对（弹窗默认保守）。
