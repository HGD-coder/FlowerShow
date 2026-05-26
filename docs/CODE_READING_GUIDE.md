# 代码阅读指南：从零理解整个项目

> 写给初学者的代码阅读手册：需要什么知识、每个文件干什么、为什么这么设计。

---

## 一、你需要哪些知识（按阅读顺序）

### 第一层：Java 基础（必须）

| 知识点 | 用在哪些文件 | 学多久 |
|--------|-------------|:--:|
| 类、接口、继承 | 全部 | 已会 |
| `interface` 定义和 `implements` | `IVideoRepository`, `FakeVideoRepository` | 30min |
| 泛型 `<T>` | `Result.java` | 20min |
| `final` 变量 | `VideoItem.java` | 10min |
| `static` 方法和单例模式 | `FakeVideoRepository`, `ThreadPoolManager` | 30min |
| 匿名内部类 `new Interface(){}` | `VideoAdapter.java` 回调 | 20min |
| `List`, `Map`, `ArrayList` | 全部数据代码 | 已会 |

### 第二层：Android 基础（必须）

| 知识点 | 用在哪些文件 | 学多久 |
|--------|-------------|:--:|
| Activity 生命周期 (`onCreate/Resume/Pause/Destroy`) | `MainActivity`, `VideoFragment` | 30min |
| Fragment 生命周期 (`onViewCreated/Resume/Pause/DestroyView`) | `VideoFragment`, `SearchFragment` | 1h |
| `findViewById` / ViewBinding | 所有 Fragment | 20min |
| `setContentView` / `inflate` | `MainActivity`, Fragment | 20min |
| Intent 和 Bundle 传参 | `SearchResultFragment.newInstance()` | 30min |
| RecyclerView + Adapter + ViewHolder | `VideoAdapter`, `SearchHistoryAdapter` | 2h |
| Fragment 导航 (`FragmentManager`) | `MainActivity.navigateTo()` | 1h |

### 第三层：Android 进阶（理解架构用）

| 知识点 | 用在哪些文件 | 学多久 |
|--------|-------------|:--:|
| ViewModel + LiveData | `VideoViewModel`, `SearchViewModel` | 2h |
| `ViewModelProvider.Factory` (手动依赖注入) | `VideoViewModel.Factory` | 1h |
| Handler + Looper + Runnable | `VideoViewModel` 进度轮询 | 1h |
| ThreadPoolExecutor | `ThreadPoolManager` | 1h |
| SharedPreferences | `SearchHistoryManager` | 30min |

### 第四层：专项库

| 知识点 | 用在哪些文件 | 学多久 |
|--------|-------------|:--:|
| ExoPlayer (Media3) 基础 API | `VideoPlayerManager` | 2h |
| Glide 图片加载 | `VideoAdapter` | 30min |
| Gson JSON 解析 | `AssetJsonLoader` | 20min |
| ViewPager2 垂直滑动 | `VideoFragment` | 30min |

---

## 二、每个文件是干什么的 + 为什么这么设计

### 🟢 第一优先级：先读这些（核心流程）

#### 1. `MainActivity.java` — App 入口

```
作用:   App 启动的第一个页面。加载 VideoFragment，提供所有 Fragment 的导航方法。
为什么: Activity 是整个 App 的"房子"。只有一个 Activity 承载多个 Fragment 切换，
       比每个页面一个 Activity 更流畅（不用重建整个窗口），是 Google 推荐的做法。
设计:  navigateTo() 用 addToBackStack，保证用户按返回键能回到上一页。
```

**关键代码**：
```java
// 行 25-28: 默认加载视频流
getSupportFragmentManager().beginTransaction()
    .replace(R.id.main_container, new VideoFragment()).commit();

// 行 36-41: 统一的 Fragment 导航，带返回栈
public void navigateTo(Fragment fragment) {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.main_container, fragment)
        .addToBackStack(null).commit();
}
```

#### 2. `VideoFragment.java` — 视频流页面（最核心）

```
作用:   承载全屏视频流。包含 ViewPager2 纵向滑动、搜索框、推荐词栏。
为什么: 这是用户看到的第一页。ViewPager2 是 Google 官方推荐的翻页控件，
       每个"页"就是一个视频卡的 ViewHolder。纵向模式模拟抖音/头条的滑动体验。
设计:
  - 通过 RepositoryFactory 获取数据（不直接 new，遵循依赖倒置原则）
  - 通过 ViewModel 管理播放器（单实例，不是 3 个池）
  - onPageSelected: 切换页面时暂停旧视频、播放新视频
  - 推荐词动态创建 TextView，点击跳转搜索结果页
```

**需求对应**：
| 行号 | 需求 |
|:--|------|
| 71-77 | [全屏播放页] ViewPager2 纵向 |
| 80-87 | [上下滑切换] onPageSelected |
| 90-97 | [数据分页] 滑到底部触发 |
| 102-108 | [搜索框] 点击跳转 |
| 115-132 | [数据观察] LiveData 更新 UI |
| 169-213 | [推荐词] 动态生成+点击搜索 |

**依赖**: `VideoViewModel`, `VideoAdapter`, `RepositoryFactory`, `MainActivity`

#### 3. `VideoAdapter.java` — 视频流适配器

```
作用:   ViewPager2 的每一页内容。负责创建视频卡/图片卡的 ViewHolder 并绑定数据。
为什么: Adapter 是 RecyclerView/ViewPager2 的标准模式。将数据(Item)和视图(ViewHolder)
       解耦。这里只做 UI 绑定，不管理播放器（播放器在 ViewModel 中）。
设计:
  - 多 ViewType: TYPE_VIDEO 和 TYPE_IMAGE 两种卡片混排
  - 播放器回调(PlayerCallback): 在 onReady 中连接 PlayerView 到 ExoPlayer
    (这是关键——因为播放器是延迟初始化的，Viewholder 创建时可能还没有)
  - DiffUtil: 高效更新列表，只刷新变化的项（不是整个列表重建）
  - Glide: 加载头像和封面图，diskCache 避免重复网络请求
```

**需求对应**：
| 行号 | 需求 |
|:--|------|
| 57-66 | [上下滑切换] ViewHolder 创建 |
| 69-79 | [上下滑切换] 数据绑定 |
| 156-169 | [进度条] SeekBar 拖拽 |
| 175-190 | [播放/暂停] 画面控制 |
| 209-225 | [头像/作者/标题/互动] bindStaticData |
| 230-260 | [进度条] onReady/onProgress/onComplete 回调 |
| 317-345 | [图片卡] ImageCardViewHolder |

**依赖**: `Glide`, `VideoPlayerManager`(接口), `CardItem`/`VideoItem`/`ImageCardItem`

#### 4. `VideoViewModel.java` — 视频流业务逻辑

```
作用:   视频流的大脑。管理数据加载（分页）、播放器状态、进度轮询。
为什么: MVVM 的 VM 层。把业务逻辑从 Fragment 中抽出来。
       Fragment 只负责 UI 渲染，ViewModel 负责:
       1) 什么时候加载数据 2) 什么时候播放/暂停 3) 错误处理
       这样 Fragment 代码简洁，且数据不会因屏幕旋转而丢失。
设计:
  - 单 ExoPlayer 实例（行业标准——SumTea_Android 等均用此法）
    不是 3 个实例池，而是 1 个实例 + 切换 URL
  - 延迟初始化: ExoPlayer.build() 很慢(加载.so)，不在构造器里做
    首次 playPosition() 时在后台线程初始化，避免启动卡顿
  - 进度轮询: 每 200ms 通过 Handler 检查播放进度并通知 UI
  - Repository 依赖注入: 构造器接收 IVideoRepository 接口，
    不依赖具体实现（以后换后端只改 Factory 一行）
```

**关键代码**：
```java
// 行 64-66: 依赖注入 IVideoRepository（不直接 new FakeVideoRepository）
public VideoViewModel(Application application, IVideoRepository repository) {
    this.repository = repository;

// 行 97-114: 首页加载（后台线程 → 主线程更新 LiveData）
public void loadFirstPage() {
    ThreadPoolManager.getInstance().execute(() -> {
        Result<List<CardItem>> result = repository.loadFeed(1, PAGE_SIZE);
        ThreadPoolManager.getInstance().postToMainThread(() -> {
            videoList.postValue(result);
        });
    });
}

// 行 144-163: 播放——单实例切换 URL（不创建新播放器）
public void playPosition(int position) {
    // ...
    playerManager.play(video.getVideoUrl()); // 同一个播放器，换 URL
}
```

**依赖**: `IVideoRepository`, `VideoPlayerManager`, `ThreadPoolManager`

---

### 🟡 第二优先级：播放器 + 数据

#### 5. `VideoPlayerManager.java` — ExoPlayer 封装

```
作用:   对 ExoPlayer (Media3) 的封装。提供 play/pause/seek/release 控制。
为什么: 把 ExoPlayer 的复杂 API 封装成简单的方法。
       外部只需调用 play(url)，不需要知道 ExoPlayer 的细节。
       这也是"组件化"思想的体现——播放器作为一个独立组件。
设计:
  - 持有单个 ExoPlayer 实例
  - 通过 PlayerCallback 接口通知外部事件(就绪/进度/状态变化/错误)
  - play() 方法: 同 URL 则恢复播放，不同 URL 则重新加载
  - notifyProgress(): 外部定时调用，ExoPlayer 不会自动推送进度
```

**关键方法**:
| 方法 | 行号 | 作用 |
|------|:--|------|
| `initialize()` | 55-88 | 创建 ExoPlayer + 注册内部监听器 |
| `play(url)` | 97-120 | 加载并播放视频 |
| `pause()` | 124-128 | 暂停 |
| `seekTo(ms)` | 156-159 | 跳转进度 |
| `release()` | 244-252 | 释放资源 |

**依赖**: ExoPlayer (Media3)

#### 6. `PlayerCallback.java` — 播放器回调接口

```
作用:   定义播放器事件的回调方法。
为什么: 解耦播放器和 UI。VideoPlayerManager 不需要知道谁在监听，
       任何实现了 PlayerCallback 的类都可以接收播放事件。
       这就是"依赖倒置原则(DIP)"的体现。
```

#### 7. `FakeVideoRepository.java` — 本地数据仓库

```
作用:   提供视频数据。优先读爬虫 JSON，失败用硬编码兜底。
为什么: 实现了 IVideoRepository 接口。以后换成 RemoteVideoRepository
       (调后端 API)时，ViewModel 代码完全不用改。
       Repository 是 MVVM 中 Model 层的核心。
设计:
  - 单例模式(双重检查锁): 整个 App 只有一个实例
  - 缓存 JSON 解析结果: getCachedVideos() 只读一次文件
  - 分页: paginate() 从全量数据中切出当前页
  - 搜索: matches() 对比 title/tags/recommendWords
```

**依赖**: `AssetJsonLoader`, `FallbackData`, `IVideoRepository`

#### 8. `IVideoRepository.java` — 视频仓库接口

```
作用:   定义视频数据访问的契约。ViewModel 只依赖这个接口。
为什么: 这是整个架构改进的核心——依赖倒置原则(DIP)。
       ViewModel 不需要知道数据来自本地 JSON 还是后端 API。
```

#### 9. `RepositoryFactory.java` — 数据源工厂

```
作用:   决定用本地数据还是远程数据。
为什么: 开闭原则(OCP)——对扩展开放(加新的 Repository 实现)，
       对修改关闭(ViewModel 不变)。
       改 USE_REMOTE=true 整个 App 就切到后端。
```

#### 10. `FallbackData.java` — 硬编码兜底数据

```
作用:   当 JSON 文件不存在或解析失败时，用这 9 条视频 + 5 条图片卡兜底。
为什么: 保证 App 在任何情况下都能运行（即使没有爬虫数据）。
```

#### 11. `AssetJsonLoader.java` — JSON 解析器

```
作用:   读取 assets/video_data.json(.jsonl) 并解析为 List<VideoItem>。
为什么: 支持两种格式——MediaCrawler 原生 JSONL（aweme_id/nickname字段）
       和 App 简化 JSON 数组（id/author 字段）。自动检测格式。
```

#### 12. `VideoItem.java` — 视频数据模型

```
作用:   一条视频的全部信息。18 个字段，与爬虫和后端 API 对齐。
为什么: 数据模型是 MVVM 的 M 部分。字段设计为 final（不可变），
       通过构造器创建，保证数据一致性和线程安全。
```

---

### 🔵 第三优先级：搜索模块

#### 13. `SearchFragment.java` — 搜索中间页

```
作用:   输入框 + 搜索历史列表。
为什么: 用户从视频流的搜索框进入此页面。输入关键词后跳转搜索结果页。
       历史列表通过 SearchViewModel 管理，SharedPreferences 持久化。
```

#### 14. `SearchResultFragment.java` — 搜索结果页

```
作用:   展示搜索结果列表。点击某条结果跳回视频流播放。
为什么: 通过 Fragment Result API 将选中的视频 ID 传回 VideoFragment。
       用 `newInstance(keyword)` 工厂方法创建实例（Android 推荐做法）。
```

#### 15. `SearchViewModel.java` — 搜索业务逻辑

```
作用:   管理搜索状态、历史、结果。注入 ISearchRepository。
为什么: 同 VideoViewModel——MVVM 分离 UI 和业务逻辑。
```

#### 16. `ISearchRepository.java` — 搜索仓库接口

```
作用:   定义搜索数据访问的契约。和 IVideoRepository 一样遵循 DIP。
```

#### 17. `LocalSearchRepository.java` — 本地搜索实现

```
作用:   封装 SearchHistoryManager + 视频搜索匹配。
```

#### 18. `SearchHistoryManager.java` — 搜索历史存储

```
作用:   用 SharedPreferences 存储搜索记录（最近 20 条）。
为什么: SharedPreferences 适合少量键值对存储。课程二讲过。
```

#### 19-20. `SearchHistoryAdapter.java` / `SearchResultAdapter.java` — 搜索列表适配器

```
作用:   RecyclerView 的列表项展示。
为什么: 标准 RecyclerView 模式——Adapter + ViewHolder。
```

---

### 🟣 第四优先级：基础设施

#### 21. `ThreadPoolManager.java` — 线程池

```
作用:   管理后台线程 + 主线程回调。
为什么: 所有耗时操作(读 JSON、搜索)必须在子线程执行。
       单线程池保证任务顺序执行，避免竞态条件。
       如果以后改用 Kotlin，这里可以换成协程。
```

#### 22. `Result.java` — 泛型结果包装

```
作用:   封装 Success / Error / Loading 三种状态。
为什么: 从 Repository → ViewModel → Fragment 统一传递结果。
       Fragment 根据 isSuccess/isError 决定显示内容还是错误提示。
```

#### 23. `CardItem.java` — 卡片基接口

```
作用:   定义视频卡和图片卡的共同类型。支持多 ViewType 混排。
```

#### 24. `ImageCardItem.java` — 图片卡数据模型

```
作用:   图片卡的数据结构。实现 CardItem 接口，type=TYPE_IMAGE。
```

---

### ⚪ 第五优先级：后端预留

#### 25-27. `RemoteVideoRepository.java` / `RemoteSearchRepository.java` / `ApiService.java`

```
作用:   后端对接的骨架代码。当前返回 Result.error("后端未配置")。
为什么: 提前把接口定义好，以后接后端只需填充实现代码。
```

---

## 三、推荐阅读顺序（从零开始）

```
第1步: 理解数据模型
  model/CardItem.java         (24行，接口)
  model/VideoItem.java        (144行，18字段)
  model/ImageCardItem.java    (71行)
  model/Result.java           (84行，泛型包装)

第2步: 理解数据怎么来的
  data/repository/IVideoRepository.java    (52行，接口定义)
  data/repository/FakeVideoRepository.java (157行，本地实现)
  data/repository/RepositoryFactory.java   (38行，切换开关)
  data/local/AssetJsonLoader.java          (260行，JSON解析)
  data/repository/FallbackData.java        (69行，兜底数据)

第3步: 理解业务逻辑
  viewmodel/VideoViewModel.java   (260行，核心)
  viewmodel/SearchViewModel.java  (102行)

第4步: 理解UI怎么渲染
  ui/video/VideoFragment.java   (208行，视频流页面)
  ui/video/VideoAdapter.java    (311行，适配器)
  ui/search/SearchFragment.java (92行)
  ui/search/SearchResultFragment.java (112行)

第5步: 理解播放器
  player/VideoPlayerManager.java (252行)
  player/PlayerCallback.java     (48行)

第6步: 理解辅助设施
  util/ThreadPoolManager.java         (88行)
  data/local/SearchHistoryManager.java (83行)
  MainActivity.java                   (46行)
```

---

## 四、关键设计决策问答

### Q1: 为什么用单 ExoPlayer 而不是 3 个实例池？

```
3个池: 每个页面一个播放器 → 3个同时存在 → 内存 3x → 切换时 pause/resume 复杂
1个单例: 只创建1个 → 内存省 → 切换时只换 URL → ExoPlayer 内部优化(复用解码器)

这是抖音/TikTok 克隆项目的行业标准做法。
参考: SumTea_Android (GitHub 1000+ stars)
```

### Q2: 为什么 ViewModel 不直接 new FakeVideoRepository？

```
如果 ViewModel 直接 new FakeVideoRepository():
  VideoViewModel 就绑死了本地数据源
  以后接后端 → 必须改 VideoViewModel 代码 → 容易引入 bug

现在:
  VideoViewModel 只依赖 IVideoRepository 接口
  换后端 → 只改 RepositoryFactory.java 一行 → ViewModel 不动
  这就是"依赖倒置原则(DIP)"
```

### Q3: 为什么播放器要在后台线程初始化？

```
ExoPlayer.Builder().build() 会做:
  - 加载 .so 原生库 (几十MB)
  - 初始化解码器
  - 创建渲染线程
在 Android 中端设备上，这可能需要 500ms–3秒

如果在主线程做 → UI 卡死 → ANR
延迟初始化: 先显示 UI(封面图) → 后台初始化 → 就绪后自动连接 PlayerView
用户感受: App 立即打开，视频 1-2 秒后开始播放
```

### Q4: 为什么 Adapter 不管播放器？

```
单一职责原则(SRP): 一个类只做一件事
  Adapter 的职责: 创建 ViewHolder，绑定数据到 View
  播放器的职责: 管理 ExoPlayer 生命周期

如果混在一起:
  代码难读、难改、难测试
  Adapter 和播放器生命周期绑定 → 回收时容易泄漏
```

### Q5: 为什么 Fragment 不直接调 Repository？

```
MVVM 分层:
  Fragment (View层) → 只管 UI
  ViewModel (业务层) → 管数据加载、播放控制、状态管理
  Repository (数据层) → 管数据来源

好处:
  - Fragment 代码简洁
  - 屏幕旋转时 ViewModel 存活，数据不丢失
  - 可以单独测试 ViewModel（不需要模拟器）
```

---

## 五、关键代码速查

| 想看什么 | 去哪个文件 | 多少行 |
|---------|-----------|:--:|
| App 怎么启动的 | `MainActivity.java` | 46 |
| 视频怎么播放的 | `VideoPlayerManager.java:97` | play() |
| 怎么上下滑的 | `VideoFragment.java:71-87` | ViewPager2 设置 |
| 怎么自动加载下一页的 | `VideoFragment.java:90-97` | 滚动监听 |
| 怎么暂停/恢复的 | `VideoFragment.java:156-170` | 生命周期 |
| 进度条怎么更新的 | `VideoViewModel.java:200-209` | 轮询 |
| 推荐词怎么显示的 | `VideoFragment.java:169-213` | updateRecommendWords |
| 搜索怎么匹配的 | `FakeVideoRepository.java:136-145` | matches() |
| 怎么切后端 | `RepositoryFactory.java:22` | USE_REMOTE |
| 数据怎么缓存的 | `FakeVideoRepository.java:37-39` | getCachedVideos |
