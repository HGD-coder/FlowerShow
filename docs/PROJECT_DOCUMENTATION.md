# 今日头条视频播放流 + 搜索模块 — 项目文档

> **生成日期**: 2026-05-24 | **更新日期**: 2026-05-28
> **当前架构**: Kotlin 2.0.21 + Jetpack Compose + MVI (Model-View-Intent) + Repository Pattern (DIP)
> **旧版架构**: Java 11 + XML + ViewPager2 + ExoPlayer 2.x — **已归档，见 [附录 A](#附录-a-javaxml-旧版架构-legacy)**
> **开发语言**: Kotlin 2.0.21
> **总文件数**: 22 个 Kotlin 源文件 + 9 个 Vector Drawable
> **AI 辅助标注**: 代码架构设计由 Claude 协助规划；推荐词数据由 Claude 根据标题+标签生成（标注位置: FallbackData.kt）；UI 组件布局方案由 Claude 参照 TikTok-Clone 开源项目设计；搜索加权评分算法由 Claude 协助设计

---

## 目录

1. [当前架构：Kotlin + Jetpack Compose + MVI](#一当前架构kotlin--jetpack-compose--mvi)
   - [1.1 源文件清单](#11-源文件清单)
   - [1.2 技术栈迁移对照](#12-技术栈迁移对照)
   - [1.3 新增功能清单](#13-新增功能清单p0-p2)
   - [1.4 数据结构设计](#14-数据结构设计)
   - [1.5 路由与导航](#15-路由与导航)
   - [1.6 架构设计决策](#16-架构设计决策)
   - [1.7 性能设计目标](#17-性能设计目标)
   - [1.8 AI 参与标注](#18-ai-参与标注)
2. [已知技术债](#二已知技术债)
3. [附录 A: Java/XML 旧版架构 (LEGACY)](#附录-a-javaxml-旧版架构-legacy)
   - [A.1 旧版文件清单](#a1-旧版文件清单)
   - [A.2 旧版需求-代码对照表](#a2-旧版需求-代码对照表)
   - [A.3 旧版架构设计](#a3-旧版架构设计)
   - [A.4 旧版数据结构](#a4-旧版数据结构)
   - [A.5 旧版搜索匹配策略](#a5-旧版推荐词与搜索匹配策略)
   - [A.6 旧版性能优化](#a6-旧版性能优化思路)
   - [A.7 旧版扩展点](#a7-旧版扩展点与后端对接)
   - [A.8 旧版 UML 类图](#a8-旧版-uml-类图)
   - [A.9 旧版流程图](#a9-旧版主要流程图)
   - [A.10 旧版工作拆分](#a10-旧版工作拆分与排期)
   - [A.11 旧版快速索引](#a11-旧版快速索引)

---

## 一、当前架构：Kotlin + Jetpack Compose + MVI

### 1.1 源文件清单

```
app/src/main/java/com/example/flower_show/
│
├── MainActivity.kt                                应用入口 + Compose 路由导航
│
├── model/                                         Model 层 — 数据模型
│   ├── CardItem.kt                               sealed interface 卡片基类型 (TypeVideo/TypeImage/TypeAlbum)
│   ├── VideoItem.kt                              data class 视频数据模型 (17 字段)
│   ├── ImageCardItem.kt                          data class 图片卡数据模型
│   ├── AlbumCardItem.kt                          data class 轮播图卡数据模型
│   ├── AlbumSlide.kt                             data class 单张轮播图
│   └── Result.kt                                 sealed class 泛型异步结果 (Success/Error/Loading)
│
├── data/                                          Data 层 — 数据访问
│   ├── local/
│   │   ├── AssetJsonLoader.kt                    object assets JSON/JSONL 解析器
│   │   └── SearchHistoryManager.kt               SharedPreferences 搜索历史 CRUD
│   ├── remote/
│   │   └── ApiService.kt                         后端 API 契约文档骨架 (Retrofit 预留)
│   └── repository/
│       ├── IVideoRepository.kt                   视频仓库接口 (DIP 核心)
│       ├── ISearchRepository.kt                  搜索仓库接口 (DIP 核心)
│       ├── FakeVideoRepository.kt                本地视频仓库 (Singleton + JSON 缓存 + 加权搜索)
│       ├── FallbackData.kt                       硬编码兜底数据 (9视频+5图片+3轮播)
│       ├── LocalSearchRepository.kt              本地搜索实现
│       ├── RemoteVideoRepository.kt              后端视频仓库骨架
│       ├── RemoteSearchRepository.kt             后端搜索仓库骨架
│       └── RepositoryFactory.kt                  object DI 工厂 (USE_REMOTE 切换)
│
├── viewmodel/                                     ViewModel 层 — MVI
│   ├── VideoIntent.kt                            sealed interface 视频流所有 Intent
│   ├── VideoState.kt                             data class 视频流单一状态
│   ├── VideoViewModel.kt                         AndroidViewModel 视频流业务逻辑
│   ├── SearchIntent.kt                           sealed interface 搜索所有 Intent
│   ├── SearchState.kt                            data class 搜索单一状态
│   └── SearchViewModel.kt                        AndroidViewModel 搜索业务逻辑
│
├── ui/                                            View 层 — Jetpack Compose
│   ├── screen/
│   │   ├── VideoScreen.kt                        @Composable 视频流主页 (VerticalPager)
│   │   ├── SearchScreen.kt                       @Composable 搜索中间页
│   │   └── SearchResultScreen.kt                 @Composable 搜索结果页
│   ├── component/
│   │   ├── VideoCard.kt                          @Composable 视频卡片 (PlayerView + 互动 + 进度)
│   │   ├── ImageCard.kt                          @Composable 图片卡片
│   │   ├── AlbumCard.kt                          @Composable 轮播图卡片
│   │   ├── SearchBar.kt                          @Composable 搜索框浮层
│   │   ├── RecommendWordsBar.kt                  @Composable 推荐词水平滚动栏
│   │   ├── SlideProgressBar.kt                   @Composable 轮播滑动进度条
│   │   ├── FlowerIcons.kt                        @Composable 9 个自定义图标
│   │   └── UiUtils.kt                            工具 Composable (formatCount 等)
│   └── theme/
│       └── Theme.kt                              主题色 (dark_black #0D0D0D + accent #FF2D55)
│
├── player/                                        播放器组件 (Media3)
│   ├── VideoPlayerManager.kt                     ExoPlayer 封装 (初始化/播控/进度/清晰度)
│   └── PlayerCallback.kt                         fun interface 播放器事件回调
│
└── util/
    └── CoroutineDispatchers.kt                   协程调度器
```

### 1.2 技术栈迁移对照

| 方面 | Java 旧版 (已归档) | Kotlin 新版 (当前) |
|------|-----------|-------------|
| 语言 | Java 11 | Kotlin 2.0.21 |
| UI | XML + ViewBinding | Jetpack Compose |
| 滑动 | ViewPager2 + RecyclerView | VerticalPager |
| 播放器 | ExoPlayer 2.13 | Media3 ExoPlayer 1.4.1 |
| 架构 | MVVM (LiveData) | MVI (StateFlow) |
| 图片 | Glide | Coil |
| 导航 | FragmentManager | String-based state routing |
| 异步 | ThreadPoolExecutor | Kotlin Coroutines |
| DI | 手动 ViewModel.Factory | 手动 AndroidViewModel + RepositoryFactory |

### 1.3 新增功能清单（P0-P2）

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
- Claude 根据标题+标签+类别生成 8-10 个搜索意图词 (标注位置: FallbackData.kt)

### 1.4 数据结构设计

#### VideoItem（data class，Kotlin ~25 行）

```kotlin
data class VideoItem(
    val id: String,                 // aweme_id
    val title: String,              // title/desc
    val author: String,             // nickname
    val avatarUrl: String,          // avatar
    val videoUrl: String,           // video_download_url
    val coverUrl: String = "",      // cover_url
    val musicUrl: String? = null,   // music_download_url
    val likes: Int = 0,             // liked_count
    val comments: Int = 0,          // comment_count
    val collections: Int = 0,       // collected_count
    val shares: Int = 0,            // share_count
    val tags: List<String> = emptyList(),
    val recommendWords: List<String> = emptyList(),
    val userId: String? = null,         // user_id
    val creatorSecUid: String? = null,  // sec_uid
    val location: String? = null,       // ip_location
    val sourceUrl: String? = null,      // aweme_url
    val publishTime: Long = 0,          // create_time
    val qualityUrls: Map<String, String>? = null,  // 多清晰度预留
) : CardItem
```

#### CardItem（sealed interface）

```kotlin
sealed interface CardItem {
    data object TypeVideo : CardItem { override val itemType get() = this }
    data object TypeImage : CardItem { override val itemType get() = this }
    data object TypeAlbum : CardItem { override val itemType get() = this }
    val itemType: CardItem
}
```

#### Result\<T\>（sealed class）

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

#### MVI State 示例

```kotlin
data class VideoState(
    val items: List<CardItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = true,
    val currentPosition: Int = 0,
    val isPlayerReady: Boolean = false,
    val error: String? = null,
)

sealed interface VideoIntent {
    data object LoadFirstPage : VideoIntent
    data object LoadNextPage : VideoIntent
    data class PlayPosition(val position: Int) : VideoIntent
    data object PausePlayer : VideoIntent
    data object ResumePlayer : VideoIntent
    data object TogglePlayPause : VideoIntent
    data class SeekTo(val positionMs: Long) : VideoIntent
    data object DismissError : VideoIntent
}
```

### 1.5 路由与导航

基于 String 状态的状态驱动路由（MainActivity.kt AppNavigation composable）:

| Route | 目标 | 说明 |
|------|------|------|
| `"video"` | VideoScreen | 默认首页视频流 |
| `"video:$id"` | VideoScreen(targetVideoId=$id) | 从搜索结果跳转到指定视频 |
| `"search"` | SearchScreen | 搜索中间页 |
| `"result:$word"` | SearchResultScreen(keyword=$word) | 搜索结果页 |

### 1.6 架构设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 播放器实例数 | **1 个** | 行业标准，单实例省内存 |
| 数据访问 | Repository 接口 | DIP：ViewModel 不依赖具体实现 |
| 依赖注入 | 手动 DI (RepositoryFactory) | 无需引入 Hilt/Dagger |
| 滑动容器 | VerticalPager | Compose 原生，内置懒加载 |
| 异步 | Kotlin Coroutines | viewModelScope + Dispatchers.IO |
| 图片加载 | Coil | Compose 原生集成，比 Glide 更适合 Compose |
| 搜索匹配 | 加权 contains + 阈值过滤 | 当前实现；已规划 Levenshtein 容错策略 |
| 数据格式 | 原生 MediaCrawler JSONL | 无需转换，爬虫输出直接可用 |

### 1.7 性能设计目标

| 指标 | 优化前基线 | 设计目标 | 实现方案 | 状态 |
|------|:---:|:---:|------|:--:|
| 视频首帧时间 | ~800ms (估) | ~300ms | Media3 SimpleCache 200MB LRU | ⚠️ 待实现 (Step 3) |
| 缓存命中率 | 0% | ~65% | CacheDataSource.Factory 自动缓存 | ⚠️ 待实现 |
| 滑动缓冲次数 | 每3视频1次 (估) | 每10视频1次 | 缓存 + preloadNextVideo() | ⚠️ 待实现 |
| 清晰度切换延迟 | N/A | <1s | setQuality() 保持进度 | ⚠️ 待实现 (Step 4) |
| 搜索容错率 | 0% (精确匹配) | 90% (1-2字符容错) | Levenshtein 编辑距离 | ⚠️ 待实现 (Step 5) |

> 注: 以上为设计目标。实际数据将通过代码中内置的 FlowerMetrics Logcat 指标采集机制实时测量。

### 1.8 AI 参与标注

| 位置 | 内容 | AI 工具 |
|------|------|---------|
| FallbackData.kt | 推荐词生成 (每视频 8-10 词) | Claude |
| PROJECT_DOCUMENTATION.md | 技术文档草稿 | Claude |
| VideoCard.kt | UI 布局设计方案 | Claude (参照 TikTok-Clone) |
| FakeVideoRepository.kt | 搜索加权评分算法 | Claude |
| Vector Drawable icons | 图标 SVG path 数据 | Claude |

---

## 二、已知技术债

| 优先级 | 问题 | 影响 | 方案 |
|:--:|------|------|------|
| P0 | 搜索结果跳转视频失败 | 搜索结果点击后无法定位到目标视频 | JumpToVideo intent + 分页循环查找 |
| P0 | 搜索历史顺序丢失 | SharedPreferences StringSet 不保序 | JSON 列表序列化 + 兼容迁移 |
| P1 | 视频无磁盘缓存 | 每次播放重新下载，流量浪费 | Media3 SimpleCache 200MB LRU |
| P1 | 清晰度字段闲置 | qualityUrls 已预留但未接通 | 规范数据源 + UI 控件 + setQuality |
| P2 | 搜索无容错 | 错别字搜不到，用户体验差 | SearchMatcher 策略 + Levenshtein |

---

## 附录 A: Java/XML 旧版架构 (LEGACY)

> ⚠️ **以下内容描述的是 2026-05-24 之前的 Java/XML/ViewPager2 版本，已完全被当前 Kotlin/Compose 版本替代。**
> **仅作为历史参考保留。所有新开发请参考 [当前架构](#一当前架构kotlin--jetpack-compose--mvi)。**

### A.1 旧版文件清单

<details>
<summary>旧版 27 个 Java 源文件</summary>

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
</details>

<details>
<summary>旧版 8 个 XML 布局文件</summary>

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
</details>

### A.2 旧版需求-代码对照表

<details>
<summary>展开旧版对照表</summary>

#### 全屏播放页

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| Activity 无 ActionBar 全屏 | `themes.xml` | 5 | `Theme.MaterialComponents.DayNight.NoActionBar` |
| 透明状态栏 | `themes.xml` | 8 | `statusBarColor: transparent` |
| FrameLayout 全屏容器 | `activity_main.xml` | 4-7 | `match_parent` |
| ViewPager2 纵向全屏 | `fragment_video.xml` | 10-15 | `orientation: vertical` |
| 视频卡满屏 | `item_video_card.xml` | 4-7 | FrameLayout `match_parent` |

#### 上下滑切换视频

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| ViewPager2 纵向方向 | `VideoFragment.java` | 64 | `viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL)` |
| Adapter 绑定 | `VideoFragment.java` | 66-67 | `new VideoAdapter()` |
| 多 ViewType 支持 | `VideoAdapter.java` | 55-60 | `onCreateViewHolder()` |
| getItemViewType | `VideoAdapter.java` | 91 | `items.get(position).getItemType()` |
| 视频卡 ViewHolder | `VideoAdapter.java` | 143-313 | `VideoCardViewHolder` |
| 图片卡 ViewHolder | `VideoAdapter.java` | 317-345 | `ImageCardViewHolder` |
| onViewRecycled | `VideoAdapter.java` | 80-85 | 回收时断开播放器连接 |

#### 数据分页拉取

| 实现 | 文件 | 行号 | 说明 |
|------|------|:--:|------|
| 首页加载 | `VideoViewModel.java` | 97-114 | `loadFirstPage()` |
| 下一页加载 | `VideoViewModel.java` | 116-138 | `loadNextPage()` |
| 触发分页 | `VideoFragment.java` | 81-88 | `SCROLL_STATE_IDLE` + 倒数第2条触发 |
| Repository 分页 | `FakeVideoRepository.java` | 62-82 | `loadFeed()` → `paginate()` |
| 分页算法 | `FakeVideoRepository.java` | 148-154 | `paginate()` 子列表截取 |
| 追加数据 | `VideoAdapter.java` | 99-103 | `appendItems()` + `notifyItemRangeInserted` |
| HasMoreData 判断 | `VideoViewModel.java` | 112-113 | `hasMoreData.postValue(!isEmpty)` |
| 加载中互斥锁 | `VideoViewModel.java` | 58 | `isLoadingData` flag |
| DiffUtil 高效更新 | `VideoAdapter.java` | 92-97 | `DiffUtil.calculateDiff()` |

#### 暂停/播放

| 实现 | 文件 | 行号 |
|------|------|:--:|
| 播放 | `VideoPlayerManager.java` | 97-120 |
| 暂停 | `VideoPlayerManager.java` | 124-128 |
| 恢复 | `VideoPlayerManager.java` | 133-138 |
| 切换 | `VideoPlayerManager.java` | 143-149 |
| ViewModel 封装 | `VideoViewModel.java` | 178-182 |
| 画面中心按钮 | `VideoAdapter.java` | 173-177 |
| 自动隐藏动画 | `VideoAdapter.java` | 173-177 |
| 自动播放首个 | `VideoFragment.java` | 101-108 |
| 页面切换播放/暂停 | `VideoFragment.java` | 74-79 |
| 切后台暂停 | `VideoFragment.java` | 141-144 |
| 回前台恢复 | `VideoFragment.java` | 135-138 |
| 销毁释放 | `VideoFragment.java` | 146-149 |
| onCleared 释放 | `VideoViewModel.java` | 217-223 |

#### 进度条

| 实现 | 文件 | 行号 |
|------|------|:--:|
| SeekBar 布局 | `item_video_card.xml` | 161-170 |
| Seeking 标志 | `VideoAdapter.java` | 152 |
| 进度更新(回调) | `VideoAdapter.java` | 215-221 |
| 缓冲进度 | `VideoAdapter.java` | 220 |
| 用户拖拽 | `VideoAdapter.java` | 166-169 |
| 进度轮询 | `VideoViewModel.java` | 200-209 |
| 轮询通知 | `VideoPlayerManager.java` | 228-235 |
| 视频完成 | `VideoAdapter.java` | 226-230 |

#### 互动能力

| 实现 | 文件 | 行号 |
|------|------|:--:|
| 头像 ImageView | `item_video_card.xml` | 45-49 |
| 头像 Glide 加载 | `VideoAdapter.java` | 192-194 |
| 作者名 | `VideoAdapter.java` | 188 |
| 标题 | `VideoAdapter.java` | 189 |
| 点赞图标+数字 | `item_video_card.xml` | 51-63 |
| 评论图标+数字 | `item_video_card.xml` | 65-78 |
| 收藏图标 | `item_video_card.xml` | 80-86 |
| 分享图标+数字 | `item_video_card.xml` | 88-101 |
| 数字格式化 | `VideoAdapter.java` | 311 |

#### 搜索功能

| 实现 | 文件 | 行号 |
|------|------|:--:|
| 搜索框浮层布局 | `fragment_video.xml` | 17-40 |
| 搜索框圆角背景 | `drawable/search_bar_bg.xml` | 1-5 |
| 点击跳转搜索页 | `VideoFragment.java` | 93-97 |
| 推荐词栏容器 | `fragment_video.xml` | 42-58 |
| 推荐词获取 | `FakeVideoRepository.java` | 107-120 |
| 推荐词 UI 构建 | `VideoFragment.java` | 155-207 |
| 搜索中间页布局 | `fragment_search.xml` | 1-98 |
| 历史存储 | `SearchHistoryManager.java` | 1-83 |
| 搜索结果页布局 | `fragment_search_result.xml` | 1-62 |
| 搜索匹配 | `FakeVideoRepository.java` | 85-103 (contains) |

</details>

### A.3 旧版架构设计

<details>
<summary>展开旧版架构图</summary>

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

旧版设计决策:

| 决策 | 选择 | 理由 |
|------|------|------|
| 播放器实例数 | 1 个 | 行业标准 |
| 数据访问 | Repository 接口 | DIP |
| 依赖注入 | 手动 DI (Factory) | 无需 Hilt/Dagger |
| 滑动容器 | ViewPager2 | Google 推荐 |
| 异步 | ThreadPoolExecutor | Java 原生 |
| 适配器更新 | DiffUtil | 只更新变化项 |
| 搜索匹配 | 本地 contains() | 简单可用 |
| 数据格式 | 原生 MediaCrawler JSONL | 无需转换 |

</details>

### A.4 旧版数据结构

<details>
<summary>展开旧版 VideoItem (Java, 144 行)</summary>

```java
public class VideoItem implements CardItem {
    String id;            // aweme_id
    String title;         // 标题
    String author;        // nickname
    String avatarUrl;     // avatar
    String videoUrl;      // video_download_url
    String coverUrl;      // cover_url
    int likes;            // liked_count
    int comments;         // comment_count
    int collections;      // collected_count
    int shares;           // share_count
    List<String> tags;           // 标签
    List<String> recommendWords; // 推荐搜索词
    String userId;        // user_id
    String creatorSecUid; // sec_uid
    String location;      // ip_location
    String sourceUrl;     // aweme_url
    String musicUrl;      // music_download_url
    long   publishTime;   // create_time
    Map<String, String> qualityUrls; // 预留
}
```
</details>

### A.5 旧版推荐词与搜索匹配策略

**旧版推荐词生成**: AI 辅助根据 title + tags 生成，存储在 FallbackData.java，运行时 getRecommendWords(videoId) 返回。

**旧版搜索匹配算法**: `title/tags/recommendWords.toLowerCase().contains(keyword)`，遍历全部视频。

**旧版匹配策略对比**:

| 策略 | 准确度 | 实现成本 | 状态 |
|------|:--:|:--:|:--:|
| `String.contains()` 直接匹配 | 60% | 极低 | ✅ 已实现 |
| 标题+标签+推荐词加权 | 80% | 1h | ⚠️ TODO |
| Levenshtein 编辑距离容错 | 90% | 2h | ❌ 未开始 |

> 注: 加权匹配在 Kotlin 版已实现。Levenshtein 规划为 Step 5。

### A.6 旧版性能优化思路

<details>
<summary>展开</summary>

| 优化点 | 手段 | 代码位置 |
|------|------|------|
| 启动速度 | ExoPlayer 延迟初始化(后台线程) | `VideoViewModel.java:147-158` |
| 滑动流畅 | DiffUtil 替代 notifyDataSetChanged | `VideoAdapter.java:104-137` |
| 滑动流畅 | SeekBar 监听器只创建一次 | `VideoAdapter.java:156-169` |
| 内存控制 | 单 ExoPlayer 实例 | `VideoViewModel.java:43` |
| 重复 IO | JSON 解析结果缓存 | `FakeVideoRepository.java:37-39,124-133` |
| 图片加载 | Glide diskCacheStrategy.ALL + thumbnail(0.1f) | `VideoAdapter.java:192-198` |
| 线程安全 | 双重检查锁单例 | `FakeVideoRepository.java:48-57` |

</details>

### A.7 旧版扩展点与后端对接

**后端切换**: `RepositoryFactory.java:22-23` — `USE_REMOTE` flag

**后端 API 契约**:
```
GET  /api/v1/videos?page=1&pageSize=10    → List<VideoItem>
GET  /api/v1/search?keyword=xxx&page=1    → List<VideoItem>
GET  /api/v1/recommendations?videoId=xxx  → List<String>
POST /api/v1/history                      → save search
GET  /api/v1/history                      → get history
DELETE /api/v1/history                    → clear history
```

**预留扩展**: 清晰度切换 (`VideoItem.java:41` qualityUrls)、直播模块 (`extension/`)、容错搜索 (`data/match/`)、横屏播放。

### A.8 旧版 UML 类图

<details>
<summary>展开旧版 UML</summary>

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
└──────────────┘ └──────────────────┘

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
└───────────────┘  └────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   <<AndroidViewModel>>                       │
│                   VideoViewModel                             │
│ - repository: IVideoRepository                              │
│ - playerManager: VideoPlayerManager (单实例)                 │
└──────────────────────────────┬──────────────────────────────┘
                               │ owns
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  VideoPlayerManager                          │
│ - player: ExoPlayer (单实例)                                 │
│ + initialize() / play() / pause() / resume() / seekTo()     │
└─────────────────────────────────────────────────────────────┘
```
</details>

### A.9 旧版主要流程图

<details>
<summary>展开旧版流程图</summary>

**应用启动流程**:
```
MainActivity.onCreate()
  → FragmentTransaction.replace(new VideoFragment())
    → VideoFragment.onViewCreated()
      → RepositoryFactory.getVideoRepository(ctx)
      → new VideoViewModel.Factory(app, repo)
      → ViewModelProvider.get(VideoViewModel)
      → ViewPager2 + VideoAdapter 初始化
      → viewModel.loadFirstPage()
        → FakeVideoRepository.loadFeed(1, 10)
          → AssetJsonLoader.loadVideos(ctx)
          → 或 FallbackData.createVideos()
        → videoList.postValue(result)
          → adapter.setItems(data)
          → viewPager.post(playPosition(0))
```

**视频滑动播放流程**:
```
用户上滑 → ViewPager2.onPageSelected(position)
  → viewModel.playPosition(position)
    → playerManager.play(newUrl)
    → startProgressPolling()
  → updateRecommendWords(position)
```

**搜索流程**:
```
搜索框点击 → navigateTo(SearchFragment)
  → 输入关键词 → SearchViewModel.search(keyword)
    → ISearchRepository.addHistory(keyword)
    → ISearchRepository.search(keyword)
      → FakeVideoRepository.search(keyword)
        → 遍历视频: title/tags/recommendWords.contains()
    → searchResults.postValue(result)
      → SearchResultFragment 展示结果
```
</details>

### A.10 旧版工作拆分与排期

<details>
<summary>展开旧版排期</summary>

| 阶段 | 任务 | 日期 |
|------|------|------|
| Day 1-2 | 项目脚手架 + MVVM 骨架 + 数据模型 | 5/21-22 |
| Day 3-4 | ExoPlayer 播放 + 播控 + 视频流滑动 | 5/22 |
| Day 5 | 多 ViewType 图片卡混排 + Glide 加载 | 5/22 |
| Day 6 | 推荐词栏 + 搜索 UI 全栈 | 5/23 |
| Day 7 | 架构重构 (Repository + DIP + 单播放器 + 搜索串联) | 5/23-24 |
| Day 8 | Bug 修复 (启动卡顿+视频无法播放+滑动卡顿) | 5/24 |

</details>

### A.11 旧版快速索引

<details>
<summary>展开旧版索引</summary>

- 想改视频播放行为 → `VideoPlayerManager.java`
- 想改视频 UI → `item_video_card.xml` + `VideoAdapter.java`
- 想改数据来源 → `data/repository/`
- 想改搜索 → `ui/search/` + `viewmodel/SearchViewModel.java`
- 想改分页 → `VideoViewModel.java`
- 想改架构 → `model/` + `data/repository/`

</details>
