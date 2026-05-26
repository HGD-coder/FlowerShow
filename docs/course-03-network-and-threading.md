# Android 培训第三课时：服务端交互、接口协作、多线程与网络

## 一、课程介绍（5min）

### 开篇介绍

课程二《UI 组件与页面交互、数据存储》里，我们主要学习了三件事：
1. **UI 组件** — 学了常见控件、布局方式，能做简单的页面。
2. **页面交互** — 会写点击事件、Activity 跳转、数据传递。
3. **本地数据存储** — 用 SharedPreferences 等保存一些离线数据。

但真正的互联网应用，不可能只靠本地数据；所有核心内容——用户信息、列表内容、评论、统计数据——都来自服务端。所以今天正式进入客户端最关键的一块：**客户端与服务端的数据交互**。

### 课程时间计划

| 主题 | 时长 |
|------|------|
| 基础知识讲解 | 40min |
| 实战演练 | 15min |
| 总结与答疑 | 10min |
| **总时长** | **60-90min** |

### 基础概念一览表

| 分类 | 概念 | 说明 | 示例 / 关键点 |
|------|------|------|-------------|
| **网络通信** | 客户端(Client) / 服务端(Server) | 客户端发起请求并展示结果；服务端处理请求并返回数据 | 抖音 App ↔ 抖音服务器 |
| | HTTP / HTTPS | 通信协议，HTTPS 加 TLS/SSL 加密 | `GET /api/news`、端口 443 |
| | WebSocket | 持久双向连接，实时消息推送 | 聊天消息、直播间弹幕 |
| | RESTful API | 资源化接口设计风格 | `POST /users`、`GET /users/123` |
| **请求响应** | HTTP 结构 | 请求行、头、体；响应含状态码 | 200（成功）、404（未找到） |
| | JSON / Protobuf | 数据交换格式 | JSON 可读，Protobuf 高效二进制 |
| **并发线程** | 线程 (Thread) | 程序执行最小单元；主线程 UI，子线程耗时 | `new Thread(...).start()` |
| | 多线程 | 多个线程并行，提高响应速度 | 网络 + UI 并行 |
| | 阻塞 / 非阻塞 | 阻塞等待完成 vs 立即返回 | `InputStream.read()` vs `OkHttp.enqueue()` |
| **调试工具** | Postman / Charles | 接口调试与抓包工具 | 查看请求头、响应体 |

---

## 二、客户端与服务端交互基础（10min）

### 1. HTTPS 请求的数据传输过程

TCP/IP 五层模型：应用层 → 传输层 → 网络层 → 数据链路层 → 物理层

1. App 发起 HTTPS 请求到 `www.tiktok.com`
2. 服务器返回 SSL 证书（CA 认证机构签发）
3. 客户端验证证书合法性
4. TLS 协商对称加密密钥
5. 加密通信开始

### 2. HTTP vs HTTPS

| | HTTP | HTTPS |
|---|---|---|
| 加密 | 明文传输 | TLS/SSL 加密 |
| 端口 | 80 | 443 |
| 证书 | 不需要 | 需要 CA 证书 |
| 安全性 | 低 | 高 |

### 3. 数据响应格式

#### Content-Type（响应头）

| 类型 | 说明 |
|------|------|
| `text/html` | 网页内容 |
| `application/json` | JSON 格式数据 |
| `image/jpeg` | 图片 |
| `application/x-protobuf` | 二进制 Protobuf 数据 |

#### JSON vs Protobuf

| | JSON | Protobuf |
|---|---|---|
| 格式 | 文本（人类可读） | 二进制 |
| 体积 | 较大（冗余字段名） | 更小（二进制编码） |
| 传输效率 | 一般 | 高 |
| 解析速度 | 较慢（字符串解析） | 较快（二进制解码） |
| 跨语言 | 原生支持几乎所有语言 | 需编译 `.proto` 文件 |
| 适用场景 | Web API、配置文件 | 微服务间通信、gRPC、移动端 RPC |
| Content-Type | `application/json` | `application/x-protobuf` |

### 4. 网络调试工具

| 工具 | 用途 |
|------|------|
| **Charles** | 网络流量抓包工具（中间人代理） |
| **Postman** | 网络请求调试工具 |
| **curl** | 命令行 HTTP 客户端 |

> ⚠️ Charles 能劫持 HTTPS 是因为用户主动安装并信任了其根证书。正式客户端使用 **SSL Pinning**（证书固定）防止中间人攻击。

### 5. WebSocket

HTTP 是"请求-响应"模式，客户端不发请求，服务器无法主动推送。WebSocket 解决实时通信问题：

| 特性 | 说明 |
|------|------|
| 全双工通信 | 客户端和服务器可同时收发 |
| 持久连接 | 建立后保持开放，避免反复连接 |
| 低开销 | 消息以帧形式传输 |
| 典型场景 | 直播弹幕、聊天消息、在线人数 |

### 6. 网络接口开发流程

```
产品需求 → 前后端对齐接口定义 → 编写 API 文档 → 后端开发 → 客户端对接 → 联调测试 → 上线
```

---

## 三、使用 OkHttp 发送网络请求（25min）

### 1. OkHttp 背景

- **开发者**：Square 公司（Jesse Wilson、Jake Wharton）
- **相关项目**：Okio（I/O 库）、Retrofit（HTTP 客户端）、Picasso（图片加载）、LeakCanary（内存检测）
- Glide、Retrofit 底层都基于 OkHttp

### 2. HttpURLConnection vs OkHttp

| | HttpURLConnection | OkHttp |
|---|---|---|
| 来源 | Android 标准库 | Square 开源库 |
| 开发复杂度 | 代码冗长，需手动管理连接 | 简洁易用，封装完善 |
| 异步支持 | 无（需手动开线程） | 内置 `enqueue()` |
| 连接复用 | 基础支持 | 内置连接池自动复用 |
| 拦截器 | 无 | 全局拦截器链（日志、缓存、重试） |
| 缓存 | 需手动实现 | 内置缓存策略 |
| WebSocket | 无 | 内置 `client.newWebSocket()` |
| **推荐** | 简单场景 | **现代 Android 标准方案** |

### 3. OkHttp 架构分层

| 层级 | 组件 | 说明 |
|------|------|------|
| 应用层 | `OkHttpClient`、`Request`、`Response`、`Call` | 开发者接口入口，全局配置（超时、缓存、拦截器） |
| 调度层 | `Dispatcher`、`ExecutorService` | 管理并发与线程执行，默认 64 请求/主机 |
| 拦截器链 | 一系列 `Interceptor` | 责任链模式，逐层处理请求/响应 |
| 连接层 | `Connection`、`ConnectionPool` | TCP/TLS 连接复用（Keep-Alive） |
| I/O 层 | Okio Stream、Socket | 底层网络读写与加密 |

### 4. OkHttp 请求流程

```
App 发起请求
  → OkHttpClient 创建 Call，交给 Dispatcher
  → Dispatcher 在线程池中执行，进入拦截器链
  → 拦截器链：重试 → 缓存 → 连接建立 → 网络请求
  → Server 返回响应
  → 经拦截器和 Dispatcher 回调主线程
  → App 获取 Response / onResponse()
```

### 5. OkHttp 中的设计模式

#### 建造者模式（Builder Pattern）

```java
OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .addInterceptor(new LoggingInterceptor())
    .build();
```

| 角色 | 类 | 职责 |
|------|-----|------|
| Product | `OkHttpClient` | 最终生成的客户端对象 |
| Builder | `Builder` | 配置参数，`build()` 创建客户端 |
| 辅助 | `Call`、`Interceptor` | 请求执行、拦截处理 |

#### 责任链模式（Chain of Responsibility）

```java
// 自定义拦截器：打印请求耗时
class LoggingInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        long start = System.currentTimeMillis();
        Request request = chain.request();
        Response response = chain.proceed(request);
        long duration = System.currentTimeMillis() - start;
        Log.d("OkHttp", "Request to " + request.url() + " took " + duration + "ms");
        return response;
    }
}
```

---

## 四、从 OkHttp 到多线程 — Android 主线程机制（25min）

### 1. 多线程的挑战

- 屏幕每 **16ms** 刷新一次（60Hz）
- 主线程必须在 16ms 内完成每帧渲染，否则卡顿（Jank）
- 耗时操作（网络、数据库、文件 IO）会阻塞主线程 → **ANR**（5 秒无响应）

Android 使用**三缓冲区模型**：GPU 在后缓冲区完成渲染后等待 VSync，避免 GPU 空闲浪费。

### 2. Android 线程分类

| 线程 | 职责 | 限制 |
|------|------|------|
| **主线程（UI Thread）** | 界面渲染、用户交互、消息循环 | 不能执行耗时操作 |
| **子线程（Worker Thread）** | 网络请求、文件读写、数据库操作 | 不能直接更新 UI |

> ⚠️ 在主线程执行网络请求会抛出 `NetworkOnMainThreadException`

### 3. 子线程与主线程通信

**手动实现的消息机制**：

```
子线程执行任务 → Message → MessageQueue.post() → 主线程 while(true) 循环 → 取出 Message → 更新 UI
```

手动实现的问题：
- `while(true)` 无法优雅退出
- 消息类型单一
- 无线程安全保护
- 无延时、定时、优先级调度

### 4. Android 消息机制：Handler + Message + MessageQueue + Looper

| 组件 | 职责 |
|------|------|
| **Thread** | 主线程处理 UI；子线程处理耗时任务 |
| **Message** | 消息载体，包含 `what`（类型）、`obj`（数据）和参数 |
| **MessageQueue** | FIFO 队列，存储消息（每个线程一个） |
| **Looper** | 从队列循环取出消息并分发。主线程自动创建，子线程需 `Looper.prepare()` + `Looper.loop()` |
| **Handler** | 绑定 Looper，发送/处理消息，支持 `sendMessage()` 和 `post(Runnable)` |

**工作流程**：

```
子线程 Handler.sendMessage(msg)
    → MessageQueue.enqueueMessage(msg)
    → Looper.loop() 循环取出
    → 主线程 Handler.dispatchMessage(msg)
    → Handler.handleMessage(msg)
    → 更新 UI
```

---

## 五、实战：探索南亚国家的经济增长（15min）

### 需求

使用世界银行 API 获取南亚地区（South Asia, `region=SAS`）所有国家信息，并展示每个国家的历史 GDP 增长率。

### API 接口

**获取国家列表**：
```
GET https://api.worldbank.org/v2/country?format=json&region=SAS
```

**获取 GDP 增长率**：
```
GET https://api.worldbank.org/v2/country/{code}/indicator/NY.GDP.MKTP.KD.ZG?format=json
```

### 流程分析

| 流程节点 | 功能目标 | Android 实现要点 |
|---------|---------|----------------|
| 启动应用 | 初始化页面与网络配置 | 启动首次数据请求，检测网络状态 |
| 加载国家列表 | 获取 `region=SAS` 数据 | OkHttp GET 请求 + JSON 解析 |
| 显示国家卡片 | 列表展示供用户选择 | RecyclerView + 卡片布局 + 点击事件 |
| 用户点击 | 携带国家 ID 跳转 | Intent 传递 `{code}` 至 DetailActivity |
| 请求 GDP 数据 | 获取历年增长率 | OkHttp GET 请求 |
| 解析 JSON | 提取年份与增长率 | Gson 映射到数据模型 |
| 显示历史数据 | 列表展示经济趋势 | RecyclerView 展示 |

### 延伸

- 还能获取哪些经济指标？（人口、通货膨胀率、失业率）
- 如何缓存国家列表？如何用图表可视化数据？

---

## 六、TikTok LIVE 是如何运行的（5min）

### 直播间的数据来源

直播间信息分为三类：

| 类型 | 技术 | 数据 |
|------|------|------|
| HTTP 请求 | Retrofit + OkHttp | 主播信息、礼物列表、进入/离开房间 |
| WebSocket | 长连接 | 公屏消息、礼物消息、分享、观众数变化 |
| SEI 数据 | 直播流携带 | 连麦状态、布局调整（需与画面同步） |

### 直播间工作流程

```
第一阶段：播放器拉流 + 进房请求（并行）
    → 首帧播放 + 获取房间状态、主播信息、观众数

第二阶段：直播间组件加载
    → 并行加载主播信息、公屏、观众榜

第三阶段：实时消息
    → WebSocket 接收聊天、礼物、人数变化，实时渲染

第四阶段：离开直播间
    → 调用 leave() → 服务器广播 → 所有观众界面同步
```

---

## 七、课程回顾

1. **网络通信原理**：HTTP/HTTPS、WebSocket、RESTful API、JSON/Protobuf
2. **OkHttp**：架构分层、Builder 模式、拦截器责任链、同步/异步请求
3. **Android 多线程**：主线程 vs 子线程、Handler + Looper + MessageQueue 机制

---

## 八、学习材料

| 资源 | 链接 |
|------|------|
| RESTful Web API 设计 | https://learn.microsoft.com/zh-cn/azure/architecture/best-practices/api-design |
| OkHttp 官方 | https://github.com/square/okhttp |
| Kotlin 快速上手 | https://developer.android.com/kotlin/learn |
| Android Compose 基础 | https://developer.android.com/courses/android-basics-compose/course |
| Kotlin 协程 | https://developer.android.com/kotlin/coroutines |

---

## 九、课后作业

### 任务

1. **调用高德天气 API**
   - 注册 App Key：https://console.amap.com/dev/key/app
   - 获取城市天气：
     ```
     GET https://restapi.amap.com/v3/weather/weatherInfo?city=110101&extensions=all&key=<your_api_key>
     ```

2. **在 App 中展示返回结果**
   - 解析天气 JSON 数据
   - 使用 RecyclerView 或列表展示天气信息
   - 包含城市名、天气状况、温度、湿度等字段
