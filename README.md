# wave工具箱

> 免 root 的安卓无障碍工具箱：把手机上各种零散重复操作交给一个 App 自动完成。

**当前状态：方案阶段（实施未开始）** — 实施方案见 [`docs/todo/`](docs/todo/)，按 LLM 持续实施模板编写，可从第一个任务开始执行。

## 规划中的首批功能

| 功能 | 说明 | 方案 |
|---|---|---|
| 开屏广告自动跳过 | 任意 App 冷启动时自动点击"跳过/关闭"，关键词优先、规则兜底 | [`docs/todo/开屏广告自动跳过/`](docs/todo/开屏广告自动跳过/) |
| 微信僵尸好友检测与清理 | 拉群法批量检测（<40 人零打扰）→ 列表勾选 → 弹窗确认 → 自动删除选中好友 | [`docs/todo/微信僵尸好友检测与清理/`](docs/todo/微信僵尸好友检测与清理/) |

二者共享同一套无障碍引擎底座（事件管线 / 选择器匹配 / 动作执行 / 任务编排 / 保活 / 特性 SPI），方案见 [`docs/todo/工具箱基座与无障碍引擎/`](docs/todo/工具箱基座与无障碍引擎/)。未来新功能以独立 `feature/` 模块 + 规则文件接入，零改动引擎。

## 技术栈

Kotlin · Jetpack Compose · Hilt · Coroutines/Flow · Room/DataStore · kotlinx.serialization；minSdk 26 / targetSdk 36；纯本地运行、零网络权限。

## 构建

> 工程骨架随基座任务 T-001 落地后可用。

```bash
./gradlew :app:assembleDebug        # 出 debug 包
./gradlew test lint                 # 质量门
```

## 免责声明

- 本项目为第三方工具，与腾讯、微信及任何被操作 App 均无官方关联。
- 全部操作基于 Android 无障碍服务模拟人工点击，无 hook、无协议注入；"检测僵尸好友""删除好友""建群退群"等均为微信官方既有功能，账号风险与人工操作等同。
- 使用本工具产生的任何后果（包括但不限于误删好友、误点操作）由使用者自行承担。

## 许可

[Apache-2.0](LICENSE)。设计与思想借鉴了开源项目 [gkd](https://github.com/gkd-kit/gkd)（GPL-3.0，借鉴设计不复制代码）与 [worktool](https://github.com/gallonyin/worktool)（Apache-2.0）；李跳跳 2.2 的还原源码（`third_party/`，只读参考）为规则语义提供了第一手蓝本。
