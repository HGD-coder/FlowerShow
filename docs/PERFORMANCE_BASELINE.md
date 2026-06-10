# Performance Baseline / 性能基线

This project now has a dedicated `:macrobenchmark` module for repeatable Android performance baselines.

项目现在新增了独立的 `:macrobenchmark` 模块，用于在 Android 设备上重复采集性能基线。

## What Is Measured / 测量内容

| Requirement area / 需求项 | Benchmark / 测试方法 | Metric / 指标 |
| --- | --- | --- |
| Startup latency / 启动耗时 | `coldStartup` | `StartupTimingMetric` |
| Video first frame / 视频首帧时间 | `videoFirstFrame` | `TraceSectionMetric("FlowerVideoFirstFrame")` |
| Feed scroll smoothness / Feed 滑动流畅度 | `feedScrollFramesAndBuffering` | `FrameTimingMetric` |
| Buffering count and duration / 缓冲次数和总耗时 | `feedScrollFramesAndBuffering` | `TraceSectionMetric("FlowerVideoBuffering", Count/Sum)` |
| Player buffering profile / 播放器缓冲策略 | app exit metrics report / App 退出指标报告 | `load_control_profile`, `load_control_buffer_ms`, `load_control_start_playback_ms` |
| Local cache hit ratio / 本地缓存命中率 | app exit metrics report / App 退出指标报告 | `cache_bytes` byte hit ratio / 字节级命中率 |
| Quality switch latency / 清晰度切换耗时 | `qualitySwitchLatency` | `TraceSectionMetric("FlowerQualitySwitch")` |

## Run / 运行方式

Use a real device or performance-capable emulator, then run:

请使用真机或性能稳定的模拟器，然后运行：

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Run a single benchmark class:

只运行单个 benchmark 类：

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.flower_show.macrobenchmark.FlowerShowPerformanceBenchmark
```

## Test Data / 测试数据

The repository reads local asset JSON data from `assets/video_data.json` or `assets/video_data.jsonl`. If no asset data is available, the feed/search data set is empty.

Repository 会读取 `assets/video_data.json` 或 `assets/video_data.jsonl` 中的本地 JSON 数据；如果没有可用 asset 数据，Feed/搜索数据集为空。

## Buffering A/B / 缓冲 A/B

Media3's default `DefaultLoadControl` is optimized for general playback and uses long buffer windows. FlowerShow now uses `ShortVideoLoadControl.Profile.Balanced` by default for Feed playback:

Media3 默认 `DefaultLoadControl` 面向通用播放，缓冲窗口较长。FlowerShow 现在默认使用 `ShortVideoLoadControl.Profile.Balanced` 作为 Feed 播放策略：

| Profile / 档位 | min buffer / 最小缓冲 | max buffer / 最大缓冲 | playback start / 起播 | after rebuffer / 卡顿后恢复 | back buffer / 后向缓存 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `FastStart` | 1.5s | 6s | 150ms | 500ms | 1s |
| `Balanced` | 3s | 10s | 250ms | 750ms | 1.5s |
| `RebufferGuard` | 5s | 15s | 500ms | 1.2s | 2.5s |

Use `FastStart` when first-frame latency is above target and rebuffer count is acceptable. Use `RebufferGuard` when `video_buffering` count or p95 duration is too high on target devices.

当首帧时间高于目标且缓冲次数可接受时，可尝试 `FastStart`；当目标设备上的 `video_buffering` 次数或 p95 时长偏高时，可尝试 `RebufferGuard`。

## Notes / 注意事项

- The app exposes Compose `testTag` values as resource IDs so UIAutomator can find `video_screen`, `feed_pager`, `player_surface`, and `quality_button`.
- App 会把 Compose `testTag` 暴露成 resource ID，因此 UIAutomator 可以定位 `video_screen`、`feed_pager`、`player_surface` 和 `quality_button`。
- The app calls `ReportDrawnWhen` after the first feed page is loaded, so startup reports include a meaningful full-display point.
- 首屏 Feed 数据加载完成后会调用 `ReportDrawnWhen`，因此启动指标包含更有意义的完整展示时间点。
- `qualitySwitchLatency` requires real multi-resolution URLs in asset data; if every quality points to the same URL, the metric may report a near-zero no-op.
- `qualitySwitchLatency` 需要 asset 数据中存在真实多分辨率 URL；如果多个清晰度都指向同一 URL，指标可能是接近 0ms 的 no-op。
- Manual and automatic quality switches emit `manual_quality`, `auto_quality`, and `quality_switch_source` labels in the app metrics report.
- 手动和自动清晰度切换会在 App 指标报告中输出 `manual_quality`、`auto_quality` 和 `quality_switch_source` 标签。
- Cache hit ratio is reported by `MetricsCollector.summary()` from `cache_bytes|source=cache` and `cache_bytes|source=upstream`.
- 缓存命中率由 `MetricsCollector.summary()` 根据 `cache_bytes|source=cache` 和 `cache_bytes|source=upstream` 汇总输出。
- Feed progress updates are scoped to `VideoProgressSlider`, so the 200ms playback tick should not recompose the whole `VideoCard`.
- Feed 播放进度更新已限制在 `VideoProgressSlider` 内部，200ms 播放 tick 不应再触发整张 `VideoCard` 重组。
- `VerticalPager` uses stable item IDs as page keys; validate the effect with `FrameTimingMetric` on target devices.
- `VerticalPager` 使用稳定 item ID 作为页面 key；实际收益需要在目标设备上用 `FrameTimingMetric` 验证。
- Image requests are size-limited by UI slot through `FlowerImageRequests`; inspect memory and dropped frames on target devices after loading real cover thumbnails.
- 图片请求已通过 `FlowerImageRequests` 按 UI 场景限制尺寸；接入真实封面缩略图后，需要在目标设备上观察内存和掉帧情况。
- Macrobenchmark results still need to be collected on target devices; local JVM tests cannot measure these device-only metrics.
- Macrobenchmark 结果仍需要在目标设备上采集；本地 JVM 测试无法测量这些设备端指标。
