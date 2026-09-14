<p align="center">
  <img src="./applogo.png" alt="FlowerShow Logo" width="180" />
</p>

<h1 align="center">FlowerShow</h1>

<p align="center">
  基于 Kotlin、Jetpack Compose 与 AndroidX Media3 构建的短视频、图文与社交信息流客户端。
</p>

<p align="center">
  <strong>简体中文</strong> · <a href="./README_EN.md">English</a>
</p>

## 项目简介

FlowerShow 是一个面向短视频浏览与社交互动场景的 Android 项目。应用以单 Activity 和 Compose Navigation 组织页面，通过统一 Gateway 接入推荐流、搜索、认证、用户关系、评论、通知和聊天接口；播放侧围绕 Media3 构建共享播放器、磁盘缓存、邻近内容预加载、清晰度切换和可观测性能指标。

项目同时保留了端侧 ONNX 向量检索与本地素材仓库，用于离线实验和测试。当前生产默认链路使用经过认证的网络仓库，端侧向量检索不等同于线上搜索的默认实现。

## 核心能力

| 领域 | 能力 |
|---|---|
| 信息流 | 推荐流与关注流、游标分页、视频/单图/图集混排、竖屏连续浏览 |
| 视频播放 | Media3 ExoPlayer、单播放器复用、播放进度与拖动、倍速、横屏沉浸播放 |
| 播放优化 | 500 MB LRU 磁盘缓存、邻近窗口预加载、短视频 LoadControl、画质切换保持进度 |
| 搜索 | 服务端搜索与推荐词、本地搜索历史、结果分页与去重、从结果定位到指定内容 |
| 端侧 AI | ONNX Runtime、WordPiece 分词、中文向量索引与词法回退；用于离线/实验路径 |
| 认证 | 注册、登录、会话恢复、单设备/全设备登出、401 自动刷新、Keystore 加密 Refresh Token |
| 社交 | 个人主页、关注/粉丝、点赞、收藏、评论、作品可见性和通知已读状态 |
| 消息 | 私聊、群聊、历史消息分页、已读回执、视频分享与群成员管理 |
| 实时通信 | WebSocket 实时消息，断线时回退 REST 轮询并自动重连 |
| 推送 | 可选 Firebase Cloud Messaging，按构建配置启用设备注册与通知服务 |
| 性能工程 | 首帧、缓冲、缓存命中、预加载、画质切换等埋点，以及 Macrobenchmark 基准测试 |

## 运行时架构

GitHub 首页会根据当前主题自动选择浅色或深色架构预览图。

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./docs/archify/flowershow-runtime.architecture.visual-check.1440x900.dark.png" />
  <source media="(prefers-color-scheme: light)" srcset="./docs/archify/flowershow-runtime.architecture.visual-check.1440x900.light.png" />
  <img src="./docs/archify/flowershow-runtime.architecture.visual-check.1440x900.light.png" alt="FlowerShow 运行时架构预览" />
</picture>

[获取完整交互式架构图](./docs/archify/flowershow-runtime.architecture.html)（GitHub README 只展示静态预览；请从文件页下载 HTML 后用浏览器打开）。架构图是生成时快照，运行配置与实现细节以当前源码为准。

### 主要数据流

```text
Compose UI
   │ Intent / 用户操作
   ▼
MVI ViewModel
   │ Repository 接口
   ├── Feed / Search / Social / Chat ──► Authenticated OkHttp ──► Gateway API
   ├── Chat realtime ──────────────────► WebSocket（断线回退 REST 轮询）
   └── Playback ───────────────────────► Media3 Player + Cache + Preload

Gateway Base URL
   ├── REST API:  {gateway}/api/v1
   ├── Media:     {gateway}/media
   └── WebSocket: ws(s)://{host}/ws/chat
```

## 技术栈

| 分类 | 技术 |
|---|---|
| 语言与构建 | Kotlin、Gradle Wrapper、JDK 17 |
| UI | Jetpack Compose、Material 3、Compose Navigation |
| 状态管理 | MVI 风格 Intent/State、Android ViewModel、StateFlow、Coroutines |
| 媒体 | AndroidX Media3 / ExoPlayer、SimpleCache、DefaultPreloadManager |
| 网络与实时通信 | OkHttp、REST、WebSocket、Gson |
| 图片 | Coil |
| 端侧检索 | ONNX Runtime Android、WordPiece、向量索引 |
| 安全与推送 | Android Keystore、Firebase Cloud Messaging（可选） |
| 质量保障 | JUnit、MockWebServer、Compose UI Test、Espresso、JaCoCo、Macrobenchmark |

Android 配置：`minSdk 28`、`compileSdk 35`、`targetSdk 35`，应用 ID 为 `com.example.flower_show`。

## 项目结构

```text
flowershow/
├── app/
│   └── src/main/java/com/example/flower_show/
│       ├── ai/          # ONNX 推理、分词、向量索引与搜索建议
│       ├── config/      # Gateway、REST、媒体和 WebSocket 地址
│       ├── data/        # 本地数据、API、认证、社交、聊天与 Repository
│       ├── model/       # Feed、视频、社交、聊天和推荐数据模型
│       ├── player/      # Media3 播放器、缓存、预加载与 LoadControl
│       ├── push/        # FCM 消息服务与通知处理
│       ├── ui/          # Compose 页面、组件、预览和主题
│       ├── util/        # 性能埋点、诊断与实验配置
│       └── viewmodel/   # Feed、搜索、认证、社交与聊天 MVI 状态
├── macrobenchmark/      # 冷启动、首帧、Feed 与画质切换基准测试
├── docs/                # 架构展示与核心项目文档
└── tools/               # 性能采集和视频预处理工具
```

## 环境要求

- Android Studio，或可用的 JDK 17 + Android SDK 环境。
- Android SDK 35；运行设备最低 Android 9（API 28）。
- Android 真机或模拟器；设备需能访问所配置的 Gateway。
- 使用设备测试、UI 测试或 Macrobenchmark 时需要可用的 `adb`。

## 快速开始

### 1. 获取项目

```powershell
git clone https://github.com/HGD-coder/FlowerShow.git
cd FlowerShow
```

### 2. 配置后端 Gateway

构建属性 `FLOWER_SHOW_PUBLIC_GATEWAY_BASE_URL` 是唯一的公开网关入口，可由 `gradle.properties` 或命令行 `-P` 参数提供。未设置该属性时，构建脚本回退到：

```text
http://10.0.2.2:8088
```

`10.0.2.2` 适用于 Android 模拟器访问宿主机。克隆后请先检查 `gradle.properties` 中的环境配置；真机运行时，请使用手机可访问的局域网地址或 HTTPS 域名。可以仅对当前构建覆盖：

```powershell
.\gradlew.bat :app:assembleDebug `
  -PFLOWER_SHOW_PUBLIC_GATEWAY_BASE_URL=https://your-domain.example.com
```

应用会自动从 Gateway 派生 REST、媒体和 WebSocket 地址，不需要分别配置。Debug 构建允许明文 HTTP 以便本地调试；Release 构建要求安全网络配置，生产环境建议始终使用 HTTPS/WSS。

### 3. 可选：启用 Firebase Cloud Messaging

1. 在 Firebase Console 创建 Android 应用，`applicationId` 必须是 `com.example.flower_show`。
2. 下载 `google-services.json` 并放到 `app/google-services.json`。
3. 重新构建应用；Gradle 仅在该文件存在时应用 Google Services 插件。

缺少 Firebase 配置时，FCM、设备 Token 注册和通知服务会被关闭，但普通构建、Feed、搜索和聊天功能仍可编译。不要向仓库提交服务账号密钥、私钥或后端凭据。

### 4. 构建与安装

```powershell
# 构建 Debug APK
.\gradlew.bat :app:assembleDebug

# 安装到当前连接设备
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

也可以直接在 Android Studio 中选择 `app` 配置运行。

## 测试与质量

```powershell
# JVM 单元测试；任务结束后同时生成 JaCoCo 报告
.\gradlew.bat :app:testDebugUnitTest

# Android 仪器化与 Compose UI 测试，需要设备或模拟器
.\gradlew.bat :app:connectedDebugAndroidTest

# 独立生成/刷新 JaCoCo 报告
.\gradlew.bat :app:jacocoTestReport

# Macrobenchmark，需要满足基准测试条件的设备
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
```

JaCoCo HTML 报告默认位于 `app/build/reports/jacoco/jacocoTestReport/html/index.html`。基准测试覆盖冷启动、视频首帧、Feed 滑动/缓冲和清晰度切换；具体结果必须在目标设备上采集，README 不把设计目标当作实测成绩。

## 性能采集

项目提供 `tools/perf-run.ps1` 采集 baseline 与 optimized 会话：

```powershell
.\tools\perf-run.ps1 -Suite single -Profile baseline -Runs 5 -ClearMode EveryRun
.\tools\perf-run.ps1 -Suite single -Profile optimized -Runs 5 -ClearMode EveryRun -SkipBuild -SkipInstall
```

重点指标包括视频 READY/首帧/缓冲耗时、缓存命中、预加载窗口、LoadControl 配置和画质切换恢复耗时。每组对比应在同一台目标设备、相同网络与操作路径下采集。

## 文档导航

- [代码阅读指南](./docs/CODE_READING_GUIDE.md)
- [项目技术文档](./docs/FlowerShow%E6%8A%80%E6%9C%AF%E6%96%87%E6%A1%A3.md)

## 使用边界与安全说明

- 推荐流、生产搜索、社交和聊天默认依赖可访问的后端 Gateway；`FakeVideoRepository` 与端侧向量搜索主要用于离线实验和测试。
- 本地素材文件保存的是元数据，媒体地址由 Gateway 的 `/media` 路径提供；旧版 `8081` 素材服务器流程不再是当前默认方案。
- FCM 是可选能力。未提供 `google-services.json` 时不会启用 Firebase 组件。
- Refresh Token 由 Android Keystore 支持的 AES/GCM 存储；仍应避免在日志、客户端代码或版本库中写入真实凭据。
- 架构图和设计文档可能是生成时快照；出现差异时，以当前源码、构建脚本和测试为准。
