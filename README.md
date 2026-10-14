<p align="center">
  <img src="artwork/icon-512.png" width="110" alt="wave工具箱">
</p>

<h1 align="center">wave工具箱</h1>

<p align="center">
  免 root 的安卓无障碍工具箱 —— 把手机上各种零散重复操作交给一个 App 自动完成。
  <br>
  <sub>Android 无障碍服务驱动 · 纯本地运行 · 零网络权限</sub>
</p>

<p align="center">
  <a href="https://github.com/wavexzore/wave-toolbox/actions/workflows/ci.yml"><img src="https://github.com/wavexzore/wave-toolbox/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/minSdk-26-brightgreen.svg" alt="minSdk 26">
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF.svg" alt="Kotlin">
</p>

---

## 功能

### 开屏广告自动跳过（v1.0.0 首个正式功能）

任意 App 冷启动时自动点击「跳过 / 关闭」，覆盖未适配应用，误点最少：

| 能力 | 说明 |
|---|---|
| 识别链 | 关键词/资源 id → 规则匹配 → 路径 → 位置，四级管线取最优候选 |
| 通用兜底 | 未适配应用也有兜底组；有内置规则包的 App 走专门适配（弹窗语义更保守） |
| 保守默认 | 默认「仅开屏」5 秒窗口；按应用三态可控（跟随全局 / 仅开屏 / 全屏蔽） |
| 防误点 | 弹窗判别、系统选择器豁免（分享/打开方式面板不再被点）、点击复核回退、去重上限 |
| 打穿静默期 | 心跳节拍兜底（成功即止），可配入口窗口时长 |
| OEM 适配 | MIUI / HyperOS 动画窗与保活路径专项处理，内置开机自启、保活通知、自愈重绑 |

<p align="center">
  <img src="artwork/screen-home.png" width="330" alt="功能页"> &nbsp;
  <img src="artwork/screen-guide.png" width="330" alt="引导页">
</p>

更多功能规划中（应用内弹窗拦截、桌面小组件……），以独立特性模块接入，零改动引擎。

## 架构

多模块工程（12 模块）：`app` 宿主 + `core` 引擎层（无障碍管线 / 选择器 / 规则 / 数据 / 设计系统等 9 模块）+ `feature` 特性层（彼此零依赖，经 `core:feature-api` 注册、可独立启停）。

Kotlin · Jetpack Compose · Hilt · Coroutines/Flow · Room/DataStore · kotlinx.serialization；minSdk 26 / targetSdk 36。

模块树、依赖方向与阅读导引见 **[ARCHITECTURE.md](ARCHITECTURE.md)**。

## 安装

**方式一：下载（推荐）** — 到 [Releases](https://github.com/wavexzore/wave-toolbox/releases) 下载最新 APK 安装。首次使用按应用内「引导页」三步配置：开启无障碍服务 → 关闭电池优化 → 自启动白名单。

**方式二：源码构建**

```bash
./gradlew :app:assembleDebug     # debug 包
./gradlew :app:assembleRelease   # release 包（可选签名：根目录 keystore.properties，不入库）
./gradlew test lint              # 质量门（同名聚合脚本：scripts/ci.sh）
```

## 隐私

- 仅申请两项能力：**无障碍服务**（读屏执行点击）与 **常驻通知**（保活）。
- **零网络权限**：没有 `INTERNET`，规则、统计、日志全部本地存储。
- 操作均为无障碍模拟点击：无 hook、无协议注入。

## 致谢

- 李跳跳 2.2 —— 关键词/规则语义的一手蓝本（本项目的识别链是其语义的整理与工程化移植）；
- [gkd](https://github.com/gkd-kit/gkd) 与 [worktool](https://github.com/gallonyin/worktool) —— 设计理念借鉴（规则包形态 / 无障碍执行分层）；
- [GitNexus](https://github.com/abhigyanpatwari/GitNexus) —— 代码图分析（影响面验证 / 变更审计）。

## 许可

[Apache-2.0](LICENSE)。

本项目为第三方工具，与被操作 App（微信等）无官方关联；「跳过广告」等基于系统无障碍模拟点击，使用产生的后果由使用者自行承担。
