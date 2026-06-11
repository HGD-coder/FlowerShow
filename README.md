<p align="center">
  <img src="./applogo.png" alt="FlowerShow Logo" width="180" />
</p>

<h1 align="center">FlowerShow</h1>

<p align="center">
  基于 Android、Jetpack Compose 和 Media3 的本地短视频/图文信息流应用。
</p>

## 项目简介

FlowerShow 是一个面向短视频浏览场景的 Android 应用原型，支持本地爬虫素材播放、视频清晰度切换、图文卡展示、搜索推荐、性能基线采集和播放器优化对比。项目当前主要用于验证短视频 Feed 的播放体验、缓存/预加载效果，以及图文与视频混排体验。

## 核心功能

- 短视频信息流：竖屏沉浸式播放，支持连续滑动浏览。
- 图文卡展示：支持本地爬虫图片素材，以图文卡形式混入 Feed。
- 清晰度切换：支持多清晰度视频源切换，并记录切换耗时。
- 倍速播放：横屏可切换 `0.5x`、`1x`、`1.5x`、`2x`，竖屏长按临时 `2x`。
- 搜索体验：支持本地搜索、搜索建议、图文/视频结果展示。
- 播放优化：集成 Media3 缓存、预加载窗口和短视频 LoadControl。
- 性能基线：提供脚本采集 baseline 与 optimized 的对比数据。

## 技术栈

- Android + Kotlin
- Jetpack Compose
- AndroidX Media3 / ExoPlayer
- MVI 风格 ViewModel 状态管理
- Coil 图片加载
- ONNX Runtime Android 本地向量检索
- Gradle + JaCoCo + Macrobenchmark

## 环境要求

- Android Studio 或 JDK 17 环境
- Android SDK 与 `adb`
- 一台 Android 真机或模拟器
- 本地素材服务器，默认端口为 `8081`

如果本机没有全局配置 Java，可以在 PowerShell 中临时设置：

```powershell
$env:JAVA_HOME='D:\android-studio-app\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 本地运行

1. 启动素材服务器：

```powershell
cd D:\MediaCrawler\MediaCrawler
.\start_video_server.bat
```

2. 检查真机素材服务器 IP：

真机运行时需要确保 `AssetJsonLoader.kt` 中的 `LAN_IP` 与电脑局域网 IP 一致；模拟器会自动使用 `10.0.2.2`。

3. 构建并安装 Debug APK：

```powershell
cd D:\android-studio\flowershow
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

也可以直接通过 Android Studio 运行 `app` 模块。

## 性能测试

项目提供 `tools/perf-run.ps1` 用于采集性能数据。推荐使用 5 轮 baseline + 5 轮 optimized：

```powershell
.\tools\perf-run.ps1 -Suite single -Profile baseline -Runs 5 -ClearMode EveryRun
.\tools\perf-run.ps1 -Suite single -Profile optimized -Runs 5 -ClearMode EveryRun -SkipBuild -SkipInstall
```

每轮建议保持一致操作：

- 首页停留约 10 秒。
- 匀速刷约 30 个视频。
- 在第 30 个视频附近切换画质 3 次。
- 搜索同一个关键词并打开同一个结果。
- 回到终端按 Enter 结束本轮。

输出目录：

- 单轮/单组结果：`performance-runs/manual-six-*/session-summary.csv`
- 汇总结果：`performance-runs/results.csv`

注意：`performance-runs/` 是本地测试产物，已加入 `.gitignore`，不会进入仓库。

## 当前核心性能指标

当前推荐关注以下指标：

| 指标 | 说明 |
|---|---|
| 视频就绪耗时 | 当前视频从请求播放到播放器就绪的耗时 |
| 视频缓冲耗时 | 播放过程中 buffering 状态持续时间 |
| 视频首帧耗时 | 播放或切换后首帧展示耗时 |
| 缓存命中率 | Media3 缓存命中占比 |
| 画质切换耗时 | 手动切换清晰度后恢复播放的耗时，建议作为辅助指标 |

`首页视频流就绪耗时` 和 `信息流卡顿率` 当前不建议作为核心指标：前者容易被页面重组/搜索返回污染，后者在当前优化项下变化不明显。

## 目录结构

```text
app/src/main/java/com/example/flower_show
├── ai/                 # 本地搜索、向量检索与分词
├── data/               # 素材加载、仓库与搜索匹配
├── model/              # 视频、图文卡、清晰度等数据模型
├── player/             # Media3 播放、缓存、预加载与 LoadControl
├── ui/                 # Compose 页面与组件
├── util/               # 性能诊断、实验配置与指标采集
└── viewmodel/          # MVI 状态与业务调度
```

## 常用命令

```powershell
# 构建 Debug APK
.\gradlew.bat :app:assembleDebug

# 运行单元测试
.\gradlew.bat :app:testDebugUnitTest

# 生成性能测试计划但不执行
.\tools\perf-run.ps1 -Suite single -Profile optimized -Runs 5 -ClearMode EveryRun -DryRun
```

## 说明

本项目依赖本地爬虫素材和局域网 HTTP 服务。若真机上出现视频、图文或音乐加载失败，优先检查：

- `D:\MediaCrawler\MediaCrawler` 素材服务器是否已启动。
- 手机与电脑是否在同一局域网。
- `AssetJsonLoader.kt` 中的 `LAN_IP` 是否为电脑当前 IP。
- Windows 防火墙是否允许 `8081` 端口访问。
