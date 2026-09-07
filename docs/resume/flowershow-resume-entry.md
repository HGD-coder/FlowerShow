# 简历项目经历：FlowerShow

> 由 `resume-project` skill 基于仓库真实事实生成。仓库可证实的数字已直接写入；
> 需要实测/产品侧数据的位置以「待补充」标注，填写前不要写进简历。

## 中文版（可直接粘贴）

**FlowerShow —— 短视频/图文信息流 Android 应用**（个人独立开发，2026.05–2026.06）
技术栈：Kotlin · Jetpack Compose (Material3) · AndroidX Media3/ExoPlayer · ONNX Runtime · Coroutines + MVI · Macrobenchmark/JaCoCo

- **端侧语义搜索**：基于 ONNX Runtime 在端侧部署量化 BGE-small-zh-v1.5 中文句向量模型（512 维、约 23MB），纯 Kotlin 实现 BERT WordPiece 分词器与余弦相似度向量索引，采用「离线预计算视频向量 + 端上实时查询编码」架构；与词法、热度信号按 0.55/0.40/0.05 加权融合为混合检索，语义链路异常自动降级词法搜索；配套 Python 离线管线以 Chinese-CLIP 对视频抽帧生成多模态向量。
- **播放内核优化**：封装 Media3/ExoPlayer 播放内核，构建 500MB LRU 磁盘缓存（播放与预加载共享 CacheDataSource）、基于 DefaultPreloadManager 的前后各 1 条滑动窗口预加载（2.5s/0.75s 档位）、自研三档短视频 LoadControl 缓冲策略（默认 Balanced 档起播门槛 250ms，替代 Media3 面向长视频的默认 2500ms），并以 TransferListener 实现字节级缓存命中率统计。
- **多清晰度切换**：实现手动 + 带宽自适应双策略画质切换，画质决策逻辑（分辨率/码率解析、带宽充足性判断）沉淀为纯函数并完整单测覆盖；切换保持播放进度，埋点记录切换耗时。
- **性能度量体系**：搭建 Macrobenchmark 基准（冷启动、视频首帧、滑动帧率、清晰度切换 4 项）+ App 内 14 类指标采集（Logcat 实时诊断 + 退出自动生成诊断报告）+ PowerShell 脚本自动化执行 baseline/optimized 各 5 轮对比，形成可复现的性能验证闭环。
- **架构与工程化**：单 Activity + Navigation Compose（14 路由、受保护路由拦截）；MVI 架构 18 组 State/Intent/ViewModel；仓库模式接口化 + 工厂装配，本地/远端数据源可切换；OkHttp OAuth token 自动刷新链（Interceptor/Authenticator/Keystore 加密存储）。全仓约 3.2 万行 Kotlin，306 个单元/UI/宏基准测试用例，JaCoCo 覆盖率报告。

**精简版（简历空间不足时保留前 3 条 + 技术栈行即可）**

## 待补充的真实数据（仓库无法证实，面试前补齐后再写入简历）

- [ ] 端侧向量检索单次查询耗时（跑一次搜索看 `vector_search_query` 指标即可得到）
- [ ] 优化前后实测对比：视频首帧耗时、缓存命中率、滑动缓冲次数（`performance-runs/` 实测数据未入仓库；`docs/PROJECT_DOCUMENTATION.md` 中的 800ms→300ms、65% 命中率为**设计目标**，不是实测值，勿直接引用）
- [ ] 若有分发/使用数据：用户量、日活等

## 面试深挖问题（每条简历要点反推）

1. 为什么选 BGE-small-zh-v1.5？量化后精度损失如何评估？端侧单次查询耗时和内存占用多少？
2. 混合检索权重 0.55/0.40/0.05 怎么确定的？做过相关性评测吗？
3. 为什么用 Media3 DefaultPreloadManager 而不是自写预加载？2.5s/0.75s 档位如何在流量和体验间权衡？
4. 起播门槛降到 250ms 是否更容易二次卡顿？三档策略分别对应什么网络场景？
5. 有了 Macrobenchmark 为什么还要自建指标采集？（OEM user build 无法抓 Perfetto trace）
6. 为什么用手工工厂而不用 Hilt？项目规模再扩大时如何演进？

## 事实来源对照

| 简历表述 | 来源 |
|---|---|
| BGE-small-zh-v1.5 / 512 维 / 23MB | `ai/OnDeviceEmbeddingService.kt`、`docs/LOCAL_VECTOR_SEARCH.md`、assets 实测大小 |
| 混合权重 0.55/0.40/0.05 | `data/repository/FakeVideoRepository.kt` |
| 500MB LRU 缓存、2.5s/0.75s 预加载、三档 LoadControl | `player/CacheManager.kt`、`FeedPreloadController.kt`、`ShortVideoLoadControl.kt` |
| 4 项 Macrobenchmark、14 类指标 | `macrobenchmark/`、`util/MetricsCollector.kt`、`docs/PERFORMANCE_BASELINE.md` |
| 14 路由、18 组 ViewModel、306 用例、3.2 万行 | `MainActivity.kt`、`viewmodel/`、测试目录与 `wc -l` 实测统计 |
