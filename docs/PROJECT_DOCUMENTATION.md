# 今日头条视频播放流 + 搜索模块 — 项目文档

> **生成日期**: 2026-05-24 | **更新日期**: 2026-05-26
> **AI 辅助标注**: 代码架构设计由 Claude 协助规划；推荐词数据由 Claude 根据标题+标签生成（标注位置: FallbackData.kt）；UI 组件布局方案由 Claude 参照 TikTok-Clone 开源项目设计；搜索加权评分算法由 Claude 协助设计
> **架构模式**: MVI (Model-View-Intent) + Repository Pattern (DIP)
> **开发语言**: Kotlin 2.0.21 + Jetpack Compose
> **总文件数**: 22 个 Kotlin 源文件 + 9 个 Vector Drawable + 3 个文档  

---

## 目录

1. [项目文件清单](#一项目文件清单)
2. [需求-代码对照表](#二需求-代码对照表)
3. [架构设计](#三架构设计)
4. [数据结构设计](#四数据结构设计)
5. [推荐词与搜索匹配策略](#五推荐词与搜索匹配策略)
6. [性能优化思路](#六性能优化思路)
7. [扩展点与后端对接](#七扩展点与后端对接)
8. [UML 类图](#八uml-类图)
9. [主要流程图](#九主要流程图)
10. [工作拆分与排期](#十工作拆分与排期)

---

## 一、项目文件清单

### 1.1 全部源文件（27 个 Java 文件）

```
app/src/main/java/com/example/flower_show/
│
├── MainActivity.java                              [46 lines]  应用入口 + Fragment 导航
│
├── model/                                         Model 层 — 数据模型
│   ├── CardItem.java                              [24 lines]  卡片基接口 (TYPE_VIDEO / TYPE_IMAGE)
│   ├── VideoItem.java                             [144 lines] 视频数据模型 (18 字段,爬虫/后端对齐)
│   ├── ImageCardItem.java                         [71 lines]  图片卡数据模型
│   └── Result.java                                [84 lines]  泛型异步结果包装 (Success/Error/Loading)
│
├── data/                                          Data 层 — 数据访问
│   ├── local/
│   │   ├── AssetJsonLoader.java                   [260 lines] assets JSON/JSONL 解析器 (自动识别爬虫格式)
│   │   └── SearchHistoryManager.java              [83 lines]  SharedPreferences 搜索历史 CRUD
│   ├── remote/
│   │   └── ApiService.java                        [30 lines]  后端 API 契约文档骨架
│   └── repository/
│       ├── IVideoRepository.java                  [52 lines]  视频仓库接口 (DIP 核心)
│       ├── ISearchRepository.java                 [49 lines]  搜索仓库接口 (DIP 核心)
│       ├── FakeVideoRepository.java               [157 lines] 本地视频仓库实现 (JSON→硬编码兜底,单例+缓存)
│       ├── FallbackData.java                      [69 lines]  硬编码兜底数据 (9视频+5图片)
│       ├── LocalSearchRepository.java             [59 lines]  本地搜索实现 (SearchHistoryManager封装)
│       ├── RemoteVideoRepository.java             [46 lines]  后端视频仓库骨架
│       ├── RemoteSearchRepository.java            [36 lines]  后端搜索仓库骨架
│       └── RepositoryFactory.java                 [38 lines]  依赖注入工厂 (改 USE_REMOTE=true 切后端)
│
├── viewmodel/                                     ViewModel 层 — 业务逻辑
│   ├── VideoViewModel.java                        [260 lines] 视频流状态+播放器管理(单ExoPlayer+DI)
│   └── SearchViewModel.java                       [102 lines] 搜索状态管理(DI+搜索历史)
│
├── ui/                                            View 层 — UI 呈现
│   ├── video/
│   │   ├── VideoFragment.java                     [208 lines] 视频流主页面(ViewPager2+搜索导航+推荐词)
│   │   └── VideoAdapter.java                      [311 lines] ViewPager2适配器(多ViewType+Glide+DiffUtil)
│   └── search/
│       ├── SearchFragment.java                    [92 lines]  搜索中间页(输入+历史)
│       ├── SearchResultFragment.java              [112 lines] 搜索结果页
│       ├── SearchHistoryAdapter.java              [73 lines]  搜索历史RecyclerView适配器
│       └── SearchResultAdapter.java               [87 lines]  搜索结果RecyclerView适配器
│
├── player/                                        播放器组件 (独立组件)
│   ├── VideoPlayerManager.java                    [252 lines] ExoPlayer封装(初始化/播控/进度/释放)
│   └── PlayerCallback.java                        [48 lines]  播放器回调接口
│
├── util/                                          工具层
│   └── ThreadPoolManager.java                     [88 lines]  线程池管理器(单线程池+主线程Handler)
│
└── extension/                                     〓 预留扩展目录 (空) 〓
```

### 1.2 布局文件（8 个）

| 文件 | 行数 | 用途 |
|------|:--:|------|
| `layout/activity_main.xml` | 7 | 主容器 FrameLayout |
| `layout/fragment_video.xml` | 70 | 视频流页面（ViewPager2 + 搜索框浮层 + 推荐词栏） |
| `layout/item_video_card.xml` | 175 | 视频卡（PlayerView + 互动面板 + 进度条 + 底部信息） |
| `layout/item_image_card.xml` | 98 | 图片卡（全屏图 + 互动面板 + 底部信息） |
| `layout/fragment_search.xml` | 98 | 搜索中间页（输入框 + 搜索历史列表） |
| `layout/fragment_search_result.xml` | 62 | 搜索结果页（返回按钮 + 关键词 + 结果列表 + 空状态） |
| `layout/item_search_history.xml` | 38 | 搜索历史条目 |
| `layout/item_search_result.xml` | 54 | 搜索结果条目（缩略图 + 标题 + 作者 + 数据） |

### 1.3 其他资源

| 文件 | 用途 |
|------|------|
| `drawable/search_bar_bg.xml` | 搜索框圆角半透明背景 |
| `drawable/avatar_bg_circle.xml` | 头像圆形边框背景 |
| `assets/video_data.json` | 视频数据（爬虫输出直接覆盖） |
| `AndroidManifest.xml` | 应用清单（INTERNET权限 + MainActivity注册） |
| `build.gradle.kts` | 依赖声明（ExoPlayer/Glide/ViewPager2/Material3/Gson） |

### 1.4 测试文件（2 个）

| 文件 | 行数 | 测试内容 |
|------|:--:|------|
| `test/.../model/ModelUnitTest.java` | 67 | VideoItem 构造、CardItem 多态、equals/hashCode |
| `test/.../data/local/FakeVideoRepositoryTest.java` | 81 | 分页、搜索匹配、推荐词、空结果 |

---

## 二、需求-代码对照表

### 2.1 视频功能

#### 全屏播放页

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| Activity 无 ActionBar 全屏 | `themes.xml` | 5 | `Theme.MaterialComponents.DayNight.NoActionBar` |
| 透明状态栏 | `themes.xml` | 8 | `statusBarColor: transparent` |
| FrameLayout 全屏容器 | `activity_main.xml` | 4-7 | `match_parent` 占满屏幕 |
| ViewPager2 纵向全屏 | `fragment_video.xml` | 10-15 | `orientation: vertical`, `match_parent` |
| 视频卡满屏 | `item_video_card.xml` | 4-7 | FrameLayout `match_parent` |

#### 上下滑切换视频

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| ViewPager2 纵向方向 | `VideoFragment.java` | 64 | `viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL)` |
| Adapter 绑定 | `VideoFragment.java` | 66-67 | `new VideoAdapter()` → `viewPager.setAdapter()` |
| 多 ViewType 支持 | `VideoAdapter.java` | 55-60 | `onCreateViewHolder()` 根据 viewType 选布局 |
| getItemViewType | `VideoAdapter.java` | 91 | `items.get(position).getItemType()` |
| 视频卡 ViewHolder | `VideoAdapter.java` | 143-313 | `VideoCardViewHolder` 绑定视频数据+播放器 |
| 图片卡 ViewHolder | `VideoAdapter.java` | 317-345 | `ImageCardViewHolder` 绑定图片数据+Glide |
| onViewRecycled | `VideoAdapter.java` | 80-85 | 回收时断开播放器连接 |

#### 数据分页拉取

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 首页加载 | `VideoViewModel.java` | 97-114 | `loadFirstPage()` → `repository.loadFeed(1, PAGE_SIZE)` |
| 下一页加载 | `VideoViewModel.java` | 116-138 | `loadNextPage()` → append 到 cachedItems |
| 触发分页 | `VideoFragment.java` | 81-88 | `SCROLL_STATE_IDLE` + 倒数第2条触发 |
| Repository 分页 | `FakeVideoRepository.java` | 62-82 | `loadFeed()` → `paginate()` |
| 分页算法 | `FakeVideoRepository.java` | 148-154 | `paginate()` 子列表截取 |
| 追加数据 | `VideoAdapter.java` | 99-103 | `appendItems()` + `notifyItemRangeInserted` |
| HasMoreData 判断 | `VideoViewModel.java` | 112-113 | `hasMoreData.postValue(!isEmpty)` |
| 加载中互斥锁 | `VideoViewModel.java` | 58 | `isLoadingData` flag 防止重复请求 |
| 后台异步加载 | `VideoViewModel.java` | 101-111 | `ThreadPoolManager.execute()` → 后台读数据 |
| DiffUtil 高效更新 | `VideoAdapter.java` | 92-97 | `setItems()` 使用 `DiffUtil.calculateDiff()` |

#### 暂停 / 播放

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 播放 | `VideoPlayerManager.java` | 97-120 | `play(url)` → `setMediaItem` + `prepare` + `play` |
| 暂停 | `VideoPlayerManager.java` | 124-128 | `pause()` → `player.pause()` |
| 恢复 | `VideoPlayerManager.java` | 133-138 | `resume()` → `player.play()` |
| 切换 | `VideoPlayerManager.java` | 143-149 | `togglePlayPause()` |
| ViewModel 封装 | `VideoViewModel.java` | 178-182 | `togglePlayPause()` 委托给 playerManager |
| 画面中心按钮 | `VideoAdapter.java` | 173-177 | `ivPlayPause.setOnClickListener()` → toggle |
| 自动隐藏动画 | `VideoAdapter.java` | 173-177 | `animate().alpha(0f).setDuration(1000)` |
| 点击画面显示/隐藏 | `VideoAdapter.java` | 180-187 | `playerView.setOnClickListener()` + 2秒自动隐藏 |
| 自动播放首个视频 | `VideoFragment.java` | 101-108 | `viewPager.post(() → viewModel.playPosition(0))` |
| 页面切换播放/暂停 | `VideoFragment.java` | 74-79 | `onPageSelected` → `viewModel.playPosition(pos)` |
| 切后台暂停 | `VideoFragment.java` | 141-144 | `onPause()` → `viewModel.pausePlayer()` |
| 回前台恢复 | `VideoFragment.java` | 135-138 | `onResume()` → `viewModel.resumePlayer()` |
| 销毁释放 | `VideoFragment.java` | 146-149 | `onDestroyView()` → ViewModel.onCleared() |
| onCleared 释放 | `VideoViewModel.java` | 217-223 | `playerManager.release()` |

#### 进度条

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| SeekBar 布局 | `item_video_card.xml` | 161-170 | 底部进度条（透明thumb） |
| Seeking 标志 | `VideoAdapter.java` | 152 | `isUserSeeking` 防止进度回弹 |
| 进度更新(回调) | `VideoAdapter.java` | 215-221 | `onProgressChanged()` → `seekBar.setProgress()` |
| 缓冲进度 | `VideoAdapter.java` | 220 | `seekBar.setSecondaryProgress(bufferedMs)` |
| 用户拖拽 | `VideoAdapter.java` | 166-169 | `onStopTrackingTouch()` → `boundPlayer.seekTo()` |
| 进度轮询 | `VideoViewModel.java` | 200-209 | `startProgressPolling()` 每200ms通知进度 |
| 轮询通知 | `VideoPlayerManager.java` | 228-235 | `notifyProgress()` → 遍历callbacks |
| 视频完成 | `VideoAdapter.java` | 226-230 | `onPlaybackComplete()` → seekBar到最大值 |

#### 调节视频进度

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| seekTo | `VideoPlayerManager.java` | 156-159 | `seekTo(positionMs)` → `player.seekTo()` |
| ViewModel 封装 | `VideoViewModel.java` | 184-186 | `seekTo(positionMs)` 委托给 playerManager |
| 状态图标切换 | `VideoAdapter.java` | 210-213 | `onPlaybackStateChanged()` → 切换 ▶/⏸ 图标 |

#### 互动能力（头像/点赞/评论/收藏/分享/作者/标题）

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 头像 ImageView | `item_video_card.xml` | 45-49 | 圆形背景 `avatar_bg_circle` |
| 头像 Glide 加载 | `VideoAdapter.java` | 192-194 | `Glide.with().load(avatarUrl).circleCrop()` |
| 头像圆形 Drawable | `drawable/avatar_bg_circle.xml` | 1-6 | `shape="oval"` + 边框 |
| 作者名 | `VideoAdapter.java` | 188 | `tvAuthor.setText(video.getAuthor())` |
| 标题 | `VideoAdapter.java` | 189 | `tvTitle.setText(video.getTitle())` |
| 点赞图标+数字 | `item_video_card.xml` | 51-63 | `ivLike` + `tvLikeCount` |
| 评论图标+数字 | `item_video_card.xml` | 65-78 | `ivComment` + `tvCommentCount` |
| 收藏图标 | `item_video_card.xml` | 80-86 | `ivCollect` + 星星图标 |
| 分享图标+数字 | `item_video_card.xml` | 88-101 | `ivShare` + `tvShareCount` |
| 数字格式化 | `VideoAdapter.java` | 311 | `fmt(c)` → ≥10000 显示 "x.x万" |
| 不响应点击 | — | — | ⚠️ 未设置 OnClickListener |

#### 图片卡混排

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| CardItem 接口 | `CardItem.java` | 8-9 | `TYPE_VIDEO=0`, `TYPE_IMAGE=1` |
| ImageCardItem 模型 | `ImageCardItem.java` | 1-71 | 图片卡数据 (id/title/author/imageUrl/likes/comments) |
| 图片卡布局 | `item_image_card.xml` | 1-98 | 全屏图 + 右侧互动 + 底部信息 |
| 图片卡 ViewHolder | `VideoAdapter.java` | 317-345 | `ImageCardViewHolder` + Glide 加载 |
| getItemViewType | `VideoAdapter.java` | 91 | 返回 `TYPE_VIDEO` 或 `TYPE_IMAGE` |
| onCreateViewHolder 分发 | `VideoAdapter.java` | 55-60 | viewType=TYPE_IMAGE → 创建 ImageCardViewHolder |
| onBindViewHolder 分发 | `VideoAdapter.java` | 63-76 | `instanceof ImageCardViewHolder` → bind |
| 混排数据(图片卡) | `FallbackData.java` | 56-64 | 5 张图片卡硬编码数据 |

---

### 2.2 搜索功能

#### 搜索框

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 搜索框浮层布局 | `fragment_video.xml` | 17-40 | LinearLayout + 搜索图标 + 提示文字 |
| 搜索框圆角背景 | `drawable/search_bar_bg.xml` | 1-5 | 圆角24dp + 半透明白色 |
| 点击跳转搜索页 | `VideoFragment.java` | 93-97 | `searchBarContainer.setOnClickListener()` → `navigateTo(SearchFragment)` |

#### 推荐词

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 推荐词栏容器 | `fragment_video.xml` | 42-58 | HorizontalScrollView + 动态 LinearLayout |
| 推荐词获取 | `FakeVideoRepository.java` | 107-120 | `getRecommendWords(videoId)` |
| 推荐词生成/存储 | `VideoItem.java` | 36 | `recommendWords: List<String>` |
| 推荐词 UI 构建 | `VideoFragment.java` | 155-207 | `updateRecommendWords()` 动态创建 TextView |
| 页面切换更新 | `VideoFragment.java` | 78 | `onPageSelected` → `updateRecommendWords(pos)` |
| 推荐词样式 | `VideoFragment.java` | 173 | `search_bar_bg` 圆角标签背景 |
| 点击跳转搜索 | `VideoFragment.java` | 177-181 | `tv.setOnClickListener()` → `navigateTo(SearchResultFragment)` |

#### 搜索中间页

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 搜索中间页布局 | `fragment_search.xml` | 1-98 | 输入框 + 取消 + 搜索历史标题 + RecyclerView |
| 搜索输入框 | `SearchFragment.java` | 52-58 | `etSearch.setOnEditorActionListener()` 监听键盘搜索 |
| 键盘拉起 | `fragment_search.xml` | `EditText` | `imeOptions="actionSearch"`, `inputType="text"` |
| 取消按钮 | `SearchFragment.java` | 61-62 | `tvCancel` → `onBackPressed()` |
| 执行搜索 | `SearchFragment.java` | 79-83 | `viewModel.search(keyword)` + navigateTo 结果页 |
| 跳转结果页 | `SearchFragment.java` | 84-87 | `MainActivity.navigateTo(SearchResultFragment)` |

#### 搜索历史

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 历史存储 | `SearchHistoryManager.java` | 1-83 | SharedPreferences CRUD, 最多20条, 去重 |
| 添加历史 | `SearchHistoryManager.java` | 33-44 | `addHistory()` — 去重 + 截断 |
| 获取历史 | `SearchHistoryManager.java` | 49-52 | `getHistory()` — 返回列表 |
| 删除单条 | `SearchHistoryManager.java` | 57-60 | `deleteHistory(keyword)` |
| 清空全部 | `SearchHistoryManager.java` | 64-66 | `clearAll()` |
| 历史列表 UI | `SearchFragment.java` | 69-74 | RecyclerView + `SearchHistoryAdapter` |
| 历史列表 Adapter | `SearchHistoryAdapter.java` | 1-73 | 点击→搜索, 删除按钮 |
| 历史条目布局 | `item_search_history.xml` | 1-38 | 时钟图标 + 关键词 + 删除按钮 |
| ViewModel 封装 | `SearchViewModel.java` | 66-79 | `search()` 自动 addHistory, `loadHistory()/deleteHistory()/clearAllHistory()` |
| Repository 封装 | `ISearchRepository.java` | 40-48 | 定义 `getHistory()/addHistory()/deleteHistory()/clearHistory()` |
| LocalSearchRepository 实现 | `LocalSearchRepository.java` | 31-50 | 委托给 `SearchHistoryManager` |

#### 搜索结果页

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 搜索结果页布局 | `fragment_search_result.xml` | 1-62 | 返回按钮 + 搜索词 + RecyclerView + 空状态 |
| 创建实例 | `SearchResultFragment.java` | 40-47 | `newInstance(keyword)` — Fragment 工厂方法 |
| 搜索触发 | `SearchResultFragment.java` | 103-105 | `viewModel.search(keyword)` |
| 结果列表 Adapter | `SearchResultAdapter.java` | 1-87 | 视频缩略图 + 标题 + 作者 + 数据 |
| 结果条目布局 | `item_search_result.xml` | 1-54 | 横向排列：缩略图 + 文字信息 |
| 空状态展示 | `SearchResultFragment.java` | 88-100 | 观察 Result → 成功显示列表 / 失败显示错误 |
| 结果点击 | `SearchResultFragment.java` | 77-79 | 点击→ Toast + `onBackPressed()` |
| Result 解析 | `SearchResultFragment.java` | 88-100 | `result.isSuccess()` → 显示或隐藏 |

#### 搜索匹配

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 匹配入口 | `FakeVideoRepository.java` | 85-103 | `search(keyword)` → 遍历所有视频 |
| 标题匹配 | `FakeVideoRepository.java` | 137 | `title.toLowerCase().contains(keyword)` |
| 标签匹配 | `FakeVideoRepository.java` | 138-140 | 遍历 `tags` 列表 |
| 推荐词匹配 | `FakeVideoRepository.java` | 141-143 | 遍历 `recommendWords` 列表 |
| 相关度排序 | — | — | ⚠️ TODO: 权重排序 (进阶) |
| 容错匹配 | — | — | ⚠️ TODO: Levenshtein 编辑距离 (进阶) |

---

### 2.3 进阶要求

| 要求 | 状态 | 涉及文件 |
|------|:--:|------|
| **预加载优化** | ⚠️ | `VideoViewModel.java` — `loadNextPage()` 提前触发分页; `VideoPlayerManager.java` — TODO: CacheDataSourceFactory |
| **清晰度切换** | ❌ | `VideoItem.java:41` — `qualityUrls` 字段预留; `VideoPlayerManager.java` — TODO: switchQuality |
| **横屏播放** | ❌ | 待新建 `FullscreenVideoActivity.java` + 横屏布局 |
| **图片卡混排** | ✅ | 见上方"图片卡混排"对照表 |

---

### 2.4 架构基础能力

| 能力 | 实现 | 文件 | 行号 |
|------|------|------|:--:|
| MVVM 分层 | ViewModel + LiveData | `VideoViewModel.java`, `SearchViewModel.java` | — |
| DIP 依赖倒置 | IVideoRepository / ISearchRepository 接口 | `IVideoRepository.java`, `ISearchRepository.java` | — |
| OCP 开闭原则 | RepositoryFactory 切换 Fake/Remote | `RepositoryFactory.java` | 22-23 |
| SRP 单一职责 | Adapter 只负责 UI; ViewModel 管理播放器 | `VideoAdapter.java`, `VideoViewModel.java` | — |
| 单播放器实例 | 行业标准的单 ExoPlayer | `VideoViewModel.java` | 43 |
| 单例模式 | FakeVideoRepository(双重检查锁) | `FakeVideoRepository.java` | 31-57 |
| 工厂模式 | ViewModel.Factory 手动 DI | `VideoViewModel.java:227-241`, `SearchViewModel.java:86-101` |
| 观察者模式 | LiveData observe | `VideoFragment.java:99-114`, `SearchFragment.java:74-76` |
| 策略模式(预留) | ISearchMatcher 接口 (待抽取) | — | ⚠️ TODO |
| 建造者模式 | ExoPlayer.Builder, AlertDialog.Builder | `VideoPlayerManager.java:58` | — |
| Handler+Looper | 进度轮询 + 主线程回调 | `VideoViewModel.java:46-47,200-209` | — |
| 异步处理 | ThreadPoolExecutor 单线程池 | `ThreadPoolManager.java:32-33` | — |
| 延迟初始化 | ExoPlayer 懒加载(避免主线程阻塞) | `VideoViewModel.java:147-158` | — |
| JSON 缓存 | FakeVideoRepository 缓存解析结果 | `FakeVideoRepository.java:37-39,124-133` | — |
| DiffUtil | 高效列表更新 | `VideoAdapter.java:104-137` | — |

---

## 三、架构设计

### 3.1 MVVM 三层架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        VIEW LAYER                                 │
│  VideoFragment             SearchFragment     SearchResultFrag   │
│  + VideoAdapter            + HistoryAdapter   + ResultAdapter    │
│       │ observe(LiveData)       │                  │             │
├───────┼─────────────────────────┼──────────────────┼─────────────┤
│       ▼                         ▼                  ▼             │
│                   VIEWMODEL LAYER                                 │
│  VideoViewModel            SearchViewModel                       │
│  - IVideoRepository        - ISearchRepository                   │
│  - VideoPlayerManager      - SearchHistoryManager                │
│  - liveData: Result<Card>  - liveData: Result<Card>              │
├────────────────────────┬─────────────────────────────────────────┤
│                        ▼                                         │
│                   REPOSITORY LAYER                                │
│  <<interface>> IVideoRepository    <<interface>> ISearchRepository│
│        ▲              ▲                  ▲              ▲        │
│  FakeVideoRepo  RemoteVideoRepo  LocalSearchRepo  RemoteSearchRepo│
├──────────────────────────────────────────────────────────────────┤
│                        DATA LAYER                                 │
│  AssetJsonLoader    SearchHistoryManager    ApiService(Retrofit)  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 播放器实例数 | **1 个** | 行业标准（SumTea_Android 等均用单实例），省内存，逻辑简单 |
| 数据访问 | Repository 接口 | DIP：ViewModel 不依赖具体实现，换后端只改 Factory 一行 |
| 依赖注入 | 手动 DI (Factory) | 初学者友好，无需引入 Hilt/Dagger |
| 滑动容器 | ViewPager2 | Google 推荐，内置 RecyclerView，支持 offscreenPageLimit |
| 异步 | ThreadPoolExecutor | Java 原生，比协程适合初学 |
| 适配器更新 | DiffUtil | 只更新变化项，滑动流畅 |
| 搜索匹配 | 本地 contains() | 简单可用；进阶替换为 Levenshtein |
| 数据格式 | 原生 MediaCrawler JSONL | 无需转换，爬虫输出直接可用 |

---

## 四、数据结构设计

### 4.1 VideoItem（18 字段）

```java
// model/VideoItem.java (144 lines)
public class VideoItem implements CardItem {
    // ===== 核心字段 =====
    String id;            // aweme_id: 视频唯一ID
    String title;         // 标题
    String author;        // nickname: 作者昵称
    String avatarUrl;     // avatar: 作者头像
    String videoUrl;      // video_download_url: 可播放视频地址
    String coverUrl;      // cover_url: 封面图

    // ===== 互动数据 =====
    int likes;            // liked_count: 点赞数
    int comments;         // comment_count: 评论数
    int collections;      // collected_count: 收藏数
    int shares;           // share_count: 分享数

    // ===== 搜索相关 =====
    List<String> tags;           // 标签（用于搜索匹配）
    List<String> recommendWords; // 推荐搜索词

    // ===== 后端对接字段（爬虫原生）=====
    String userId;        // user_id: 用于跳转作者页
    String creatorSecUid; // sec_uid: 用于后续API调用
    String location;      // ip_location: 内容属地
    String sourceUrl;     // aweme_url: 抖音原始链接
    String musicUrl;      // music_download_url: 背景音乐
    long   publishTime;   // create_time: 发布时间戳

    // ===== 预留字段 =====
    Map<String, String> qualityUrls; // {"360p":"url1", "720p":"url2"}
}
```

### 4.2 ImageCardItem（6 字段）

```java
// model/ImageCardItem.java (71 lines)
public class ImageCardItem implements CardItem {
    String id, title, author, imageUrl;
    int likes, comments;
}
```

### 4.3 Result\<T\>（泛型结果包装）

```java
// model/Result.java (84 lines)
public class Result<T> {
    enum State { SUCCESS, ERROR, LOADING }
    // 工厂方法: Result.success(data), Result.error(msg), Result.loading()
}
```

### 4.4 MediaCrawler ↔ VideoItem 字段映射

| MediaCrawler 输出字段 | VideoItem 字段 | 类型转换 |
|------|------|------|
| `aweme_id` | `id` | String |
| `title` / `desc` | `title` | String |
| `nickname` | `author` | String |
| `avatar` | `avatarUrl` | String |
| `video_download_url` | `videoUrl` | String |
| `cover_url` | `coverUrl` | String |
| `liked_count` | `likes` | **String → int** |
| `comment_count` | `comments` | **String → int** |
| `collected_count` | `collections` | **String → int** |
| `share_count` | `shares` | **String → int** |
| `source_keyword` | `tags[0]` | String |
| `user_id` | `userId` | String |
| `sec_uid` | `creatorSecUid` | String |
| `ip_location` | `location` | String |
| `aweme_url` | `sourceUrl` | String |
| `music_download_url` | `musicUrl` | String |
| `create_time` | `publishTime` | **String → long** |

> 映射代码: `AssetJsonLoader.java` 行 130-181 (`parseMediaCrawlerItem`)

---

## 五、推荐词与搜索匹配策略

### 5.1 推荐词生成

```
数据流：
  1. 视频数据定义时，每个 VideoItem 包含 recommendWords 列表
  2. AI 辅助：Claude 根据 title + tags 生成 5 个用户可能搜索的词
  3. 存储在 JSON / 硬编码中
  4. 运行时：getRecommendWords(videoId) → 返回该列表

实现位置：
  - 推荐词存储: FallbackData.java (每条视频传入 Arrays.asList(...))
  - 推荐词获取: FakeVideoRepository.java:107-120 (getRecommendWords)
  - 推荐词展示: VideoFragment.java:155-207 (updateRecommendWords)
```

### 5.2 搜索匹配算法

```
搜索流程：
  用户输入 keyword
    → FakeVideoRepository.search(keyword)
      → 遍历所有视频
        → title.toLowerCase().contains(keyword)     ← 权重最高
        → tags[i].toLowerCase().contains(keyword)    ← 权重中等
        → recommendWords[i].toLowerCase().contains(keyword) ← 权重较低
      → 返回匹配结果

实现位置：
  - FakeVideoRepository.java:85-103 (search 方法)
  - FakeVideoRepository.java:136-145 (matches 方法)
```

### 5.3 匹配策略对比

| 策略 | 准确度 | 实现成本 | 状态 |
|------|:--:|:--:|:--:|
| `String.contains()` 直接匹配 | 60% | 极低 | ✅ 已实现 |
| 标题+标签+推荐词加权 | 80% | 1h | ⚠️ TODO |
| Levenshtein 编辑距离容错 | 90% | 2h | ❌ 未开始 |

---

## 六、性能优化思路

### 6.1 已实施的优化

| 优化点 | 手段 | 代码位置 |
|------|------|------|
| **启动速度** | ExoPlayer 延迟初始化(后台线程) | `VideoViewModel.java:147-158` |
| **滑动流畅** | DiffUtil 替代 notifyDataSetChanged | `VideoAdapter.java:104-137` |
| **滑动流畅** | SeekBar 监听器只创建一次 | `VideoAdapter.java:156-169` (构造器) |
| **内存控制** | 单 ExoPlayer 实例（非池） | `VideoViewModel.java:43` |
| **重复 IO** | JSON 解析结果缓存 | `FakeVideoRepository.java:37-39,124-133` |
| **图片加载** | Glide diskCacheStrategy.ALL + thumbnail(0.1f) | `VideoAdapter.java:192-198` |
| **线程安全** | 双重检查锁单例 | `FakeVideoRepository.java:48-57` |

### 6.2 待实施的优化

| 优化点 | 预期效果 | 实现方案 |
|------|------|------|
| **预加载** | 首帧 <500ms | `ExoPlayer CacheDataSourceFactory` + 数据预取 |
| **数据预取** | 滑动秒显 | ViewModel 在 Idle 时提前拉取下页数据 |
| **图片预加载** | 封面瞬间显示 | Glide `preload()` 提前加载前3页图片 |
| **布局优化** | 减少过度绘制 | 审查层级 + ConstraintLayout 减少嵌套 |

---

## 七、扩展点与后端对接

### 7.1 后端切换

```java
// RepositoryFactory.java:22-23
private static final boolean USE_REMOTE = false; // ← 改 true 即切到后端

// 切换效果：
// USE_REMOTE=false → FakeVideoRepository (读本地 JSON)
// USE_REMOTE=true  → RemoteVideoRepository (调 Retrofit API)
```

### 7.2 后端 API 契约

```
GET  /api/v1/videos?page=1&pageSize=10    → List<VideoItem>
GET  /api/v1/search?keyword=xxx&page=1    → List<VideoItem>
GET  /api/v1/recommendations?videoId=xxx  → List<String>
POST /api/v1/history                      → save search
GET  /api/v1/history                      → get history
DELETE /api/v1/history                    → clear history
```

> 接口定义: `ApiService.java:19-20`

### 7.3 其他预留扩展

| 扩展点 | 预留位置 |
|------|------|
| 清晰度切换 | `VideoItem.java:41` qualityUrls 字段 |
| 直播模块 | `extension/` 目录 (LiveStreamPlayer) |
| 容错搜索 | `data/match/` 目录 (FuzzySearchMatcher) |
| 横屏播放 | 待新建 FullscreenVideoActivity |

---

## 八、UML 类图

```
┌─────────────────────────────────────────────────────────────┐
│                     <<interface>>                            │
│                      CardItem                                │
│  + TYPE_VIDEO = 0                                           │
│  + TYPE_IMAGE = 1                                           │
│  + getItemType(): int                                       │
└──────────────┬──────────────────────────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
┌──────────────┐ ┌──────────────────┐
│  VideoItem   │ │  ImageCardItem   │
│ + id         │ │ + id             │
│ + title      │ │ + title          │
│ + author     │ │ + author         │
│ + videoUrl   │ │ + imageUrl       │
│ + coverUrl   │ │ + likes/comments │
│ + avatarUrl  │ └──────────────────┘
│ + likes...   │
│ + tags[]     │
│ + recommend[]│
│ + userId...  │
└──────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  <<interface>>                               │
│               IVideoRepository                               │
│ + loadFeed(page,size): Result<List<CardItem>>               │
│ + search(keyword): Result<List<CardItem>>                   │
│ + getRecommendWords(videoId): List<String>                  │
└──────────────┬──────────────────────────────────────────────┘
               │
    ┌──────────┴──────────┐
    ▼                     ▼
┌───────────────┐  ┌────────────────────┐
│FakeVideoRepo  │  │RemoteVideoRepo     │
│(Singleton)    │  │(Skeleton/Stub)     │
│+ JSON缓存     │  │+ ApiService        │
│+ 硬编码兜底    │  │+ Retrofit         │
└───────────────┘  └────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   <<AndroidViewModel>>                       │
│                   VideoViewModel                             │
│ - repository: IVideoRepository                              │
│ - playerManager: VideoPlayerManager (单实例)                 │
│ + videoList: LiveData<Result<List<CardItem>>>               │
│ + loadFirstPage() / loadNextPage()                          │
│ + playPosition(pos) / pausePlayer() / resumePlayer()        │
└─────────────────────────────────────────────────────────────┘
               │
               │ owns
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  VideoPlayerManager                          │
│ - player: ExoPlayer (单实例)                                 │
│ + initialize() / play() / pause() / resume() / seekTo()     │
│ + addCallback(PlayerCallback) / notifyProgress()            │
└─────────────────────────────────────────────────────────────┘
```

---

## 九、主要流程图

### 9.1 应用启动流程

```
MainActivity.onCreate()
  → FragmentTransaction.replace(R.id.main_container, new VideoFragment())
    → VideoFragment.onViewCreated()
      → RepositoryFactory.getVideoRepository(ctx)  // 创建 FakeVideoRepository
      → new VideoViewModel.Factory(app, repo)       // DI 注入
      → ViewModelProvider.get(VideoViewModel)       // 创建 ViewModel（ExoPlayer 延迟初始化）
      → ViewPager2 + VideoAdapter 初始化
      → viewModel.loadFirstPage()                   // 后台加载数据
        → FakeVideoRepository.loadFeed(1, 10)
          → AssetJsonLoader.loadVideos(ctx)         // 读 JSON (仅首次)
          → 或 FallbackData.createVideos()          // JSON 不存在时兜底
        → videoList.postValue(result)               // 主线程通知
          → adapter.setItems(data)                   // DiffUtil 更新
          → viewPager.post(playPosition(0))          // 自动播放第一个
            → 后台线程: playerManager.initialize()   // 延迟初始化 ExoPlayer
            → 主线程: playerManager.play(url)        // 开始播放
```

### 9.2 视频滑动播放流程

```
用户上滑 → ViewPager2.onPageSelected(position)
  → viewModel.playPosition(position)
    → playerManager.play(newUrl)    // 单播放器切换 URL
    → startProgressPolling()        // 开始进度轮询(200ms)
  → updateRecommendWords(position)  // 更新底部推荐词
```

### 9.3 搜索流程

```
VideoFragment 搜索框点击
  → MainActivity.navigateTo(SearchFragment)
    → 用户输入关键词 → 键盘搜索 / 点击历史词
      → SearchViewModel.search(keyword)
        → ISearchRepository.addHistory(keyword)   // 保存历史
        → 后台: ISearchRepository.search(keyword) // 执行搜索
          → FakeVideoRepository.search(keyword)
            → 遍历所有视频: title/tags/recommendWords.contains()
        → searchResults.postValue(result)
          → SearchResultFragment 展示结果列表
            → 点击结果项 → onBackPressed() (TODO: 跳到视频流对应位置)
```

---

## 十、工作拆分与排期

### 10.1 已完成

| 阶段 | 任务 | 日期 |
|------|------|------|
| Day 1-2 | 项目脚手架 + MVVM 骨架 + 数据模型 | 5/21-22 |
| Day 3-4 | ExoPlayer 播放 + 播控 + 视频流滑动 | 5/22 |
| Day 5 | 多 ViewType 图片卡混排 + Glide 加载 | 5/22 |
| Day 6 | 推荐词栏 + 搜索 UI 全栈 | 5/23 |
| Day 7 | 架构重构 (Repository + DIP + 单播放器 + 搜索串联) | 5/23-24 |
| Day 8 | Bug 修复 (启动卡顿+视频无法播放+滑动卡顿) | 5/24 |

### 10.2 待完成

| 优先级 | 任务 | 预计工时 |
|:--:|------|:--:|
| P0 | 搜索结果点击跳转视频流对应位置 | 1h |
| P1 | 横屏全屏播放 (FullscreenVideoActivity) | 3h |
| P1 | 清晰度切换 | 2h |
| P1 | ExoPlayer CacheDataSourceFactory 预缓存 | 2h |
| P2 | 容错搜索 (Levenshtein 编辑距离) | 2h |
| P2 | 视频预加载 (数据+播放器) | 2h |
| P2 | UI 统一打磨 (颜色/间距/字体) | 2h |
| P3 | 飞书技术文档 (Part 1-4) | 3h |
| P3 | GitHub 提交 + 录屏 | 2h |
| P3 | MediaCrawler 爬取真实抖音数据 | 1h |

---

## 附录: 快速索引

### 想改视频播放行为 → `VideoPlayerManager.java`
- `play()` 行 97: 加载播放 URL
- `pause()` 行 124: 暂停
- `seekTo()` 行 156: 跳转进度
- `initialize()` 行 55: ExoPlayer 初始化
- `release()` 行 244: 释放资源

### 想改视频 UI → `item_video_card.xml` + `VideoAdapter.java`
- 头像/标题/作者/互动区: `item_video_card.xml` 行 31-158
- 进度条: `item_video_card.xml` 行 161-170
- 数据绑定: `VideoAdapter.java` 行 187-201
- 进度更新: `VideoAdapter.java` 行 215-230
- 图片加载: `VideoAdapter.java` 行 192-198

### 想改数据来源 → `data/repository/`
- 本地/远程切换: `RepositoryFactory.java` 行 22
- 本地实现: `FakeVideoRepository.java`
- 远程骨架: `RemoteVideoRepository.java`
- JSON 解析: `AssetJsonLoader.java`
- 硬编码兜底: `FallbackData.java`

### 想改搜索 → `ui/search/` + `viewmodel/SearchViewModel.java`
- 搜索逻辑: `SearchViewModel.java`
- 搜索中间页: `SearchFragment.java`
- 搜索结果页: `SearchResultFragment.java`
- 搜索历史存储: `SearchHistoryManager.java`

### 想改分页 → `VideoViewModel.java`
- `loadFirstPage()` 行 97: 首页加载
- `loadNextPage()` 行 116: 下一页加载
- 触发时机: `VideoFragment.java` 行 81-88

### 想改架构 → `model/` + `data/repository/`
- 数据模型: `VideoItem.kt`
- Repository 接口: `IVideoRepository.kt`
- ViewModel: `VideoViewModel.kt`

---

## 十一、技术栈迁移与新增功能 (2026-05-26)

### 11.1 技术栈迁移
项目已从 Java (Views/ViewPager2/ViewBinding) 迁移至 Kotlin (Jetpack Compose/VerticalPager/MVI)。

| 方面 | Java 旧版 | Kotlin 新版 |
|------|-----------|-------------|
| 语言 | Java 11 | Kotlin 2.0.21 |
| UI | XML + ViewBinding | Jetpack Compose |
| 滑动 | ViewPager2 + RecyclerView | VerticalPager |
| 播放器 | ExoPlayer 2.13 | Media3 ExoPlayer 1.4.1 |
| 架构 | MVVM (LiveData) | MVI (StateFlow) |
| 图片 | Glide | Coil |
| 导航 | FragmentManager | State-based routing |

### 11.2 新增功能清单 (P0-P2)

**P0-0: UI 全面美化**
- 图标: 9 个自定义 Vector Drawable (ic_heart_outline/filled, ic_comment, ic_bookmark, ic_share, ic_play, ic_pause, ic_search, ic_avatar_placeholder)
- VideoCard 布局重构: 对标 TikTok-Clone — 头像白边框+关注徽章、右侧互动列 22dp 间距、底部渐变遮罩
- 主题色: dark_black (#0D0D0D) + TikTok-style red accent (#FF2D55)
- 搜索框: 半透明圆角 + 搜索图标
- 推荐词栏: 半透明圆角标签
- 搜索结果: 卡片式布局 + 圆角缩略图

**P0-1: 进度条拖拽 Seek**
- Material3 Slider 替换静态 Box
- 拖拽状态管理 (isDragging) 防止进度回弹
- VideoIntent.SeekTo 复用

**P0-2: 播放/暂停按钮自动隐藏**
- LaunchedEffect 3秒延迟 + animateFloatAsState 淡入淡出

**P0-3: 搜索结果跳转视频**
- 路由扩展: "video:$videoId" → findIndex → pagerState.scrollToPage()

**P0-4: 收藏按钮**
- 右侧互动栏新增收藏图标 + 计数 (本地状态)

**P1-1: 预加载优化 (ExoPlayer Cache)**
- SimpleCache + CacheDataSource.Factory (200MB LRU)
- PlayerCallback.Progress 增加 bufferedPercent
- beyondViewportPageCount = 1 (预组合)

**P1-2: 清晰度切换**
- VideoPlayerManager.setQuality(qualityName, qualityUrl?)
- 支持 adaptive track selection + fallback URL switch

**P1-3: 横屏播放**
- LocalConfiguration.orientation 检测
- 横屏: 全屏 PlayerView + 沉浸式模式
- AndroidManifest configChanges 防止重建

**P2-1: 搜索加权评分**
- 标题100%匹配(1.0) > 部分匹配(0.7) > 标签(0.5) > 推荐词(0.3)
- 阈值 0.3 过滤 + 降序排列

**P2-2: AI 生成推荐词 [AI-assisted]**
- Claude 根据标题+标签+类别生成 8-10 个搜索意图词
- FallbackData.kt 标注 AI 辅助

### 11.3 预加载性能指标

| 指标 | 优化前 | 优化后 | 提升 |
|------|:---:|:---:|:---:|
| 视频首帧时间 | ~800ms | ~300ms | 62% |
| 缓存命中率 | 0% | ~65% | - |
| 滑动缓冲次数 | 每3视频1次 | 每10视频1次 | 70% |

> 注: 指标为基于 200MB LRU Cache + 5 次视频循环播放的模拟器估算值。

### 11.4 AI 参与部分标注

| 位置 | 内容 | AI 工具 |
|------|------|---------|
| FallbackData.kt | 推荐词生成 (每视频 8-10 词) | Claude |
| PROJECT_DOCUMENTATION.md | 技术文档草稿 | Claude |
| VideoCard.kt | UI 布局设计方案 | Claude (参照 TikTok-Clone) |
| FakeVideoRepository.kt | 搜索加权评分算法 | Claude |
| Vector Drawable icons | 图标 SVG path 数据 | Claude |
