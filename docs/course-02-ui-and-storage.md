# Android 培训第二课时：UI 组件与页面交互、数据存储

## 一、课程介绍

本课程是 Android 培训系列的第二课时，重点内容涉及 Android UI 组件与布局、页面数据交互方式、数据存储技术等，是开发 Android 应用程序所需的必备技能。

| 课时 | 内容 |
|------|------|
| 课程一 | 客户端发展、企业价值、客户端开发基础与环境实战 |
| **课程二** | **UI 组件与页面交互、数据存储** |
| 课程三 | 服务端交互、接口协作、多线程与网络 |
| 课程四 | 客户端常见架构、性能优化、架构优化及 AI 应用 |

## 二、课程目标

1. 熟练掌握安卓常用 UI 组件的属性与使用场景，能根据需求选择组件搭建页面布局。
2. 掌握页面交互的核心实现方式（如点击事件、页面跳转、数据传递），实现客户端基础交互功能。
3. 理解客户端数据存储的必要性，掌握 SharedPreferences、SQLite 使用方法，能实现本地数据的增删改查。

---

## 三、课程内容拆解

### 第一部分：Android 页面 Activity 与 Intent

#### App 应用界面：Activity

Activity 就是一个独立的屏幕或窗口，负责展示用户界面（UI），并处理用户的交互操作，不同的 Activity 呈现给用户不同的操作体验。每当我们打开一个 Android 的 app，我们都会接触到它。

#### Activity 构建与生命周期

##### Activity 创建与 Manifest 文件

每个 Activity 都必须在 `AndroidManifest.xml` 文件中进行注册：

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

##### 布局与 setContentView

通过 `setContentView()` 将 Activity 与布局文件关联：

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
}
```

##### Activity 生命周期图解

| 方法 | 时机 | 用途 |
|------|------|------|
| `onCreate()` | Activity 第一次被创建 | 整个生命周期只调用一次。执行初始化：setContentView、findViewById、设置监听器 |
| `onStart()` | Activity 即将变得可见 | UI 相关的准备工作 |
| `onResume()` | Activity 完全可见，获得焦点 | 开启动画、打开摄像头、注册监听器 |
| `onPause()` | Activity 即将失去焦点 | 暂停动画/视频、保存草稿、释放系统资源 |
| `onStop()` | Activity 完全不可见 | 重量级资源释放 |
| `onRestart()` | onStop 后重新回前台 | 恢复性工作。调用顺序：onRestart → onStart → onResume |
| `onDestroy()` | Activity 即将被销毁 | 最终清理：取消网络请求、反注册监听器、清理线程 |

**典型场景**：

| 场景 | 调用顺序 |
|------|---------|
| 首次启动 | onCreate → onStart → onResume |
| 按 Home 键 | onPause → onStop |
| 重新打开 | onRestart → onStart → onResume |
| 按返回键退出 | onPause → onStop → onDestroy |
| 屏幕旋转 | onPause → onStop → onDestroy → onCreate → onStart → onResume |

**配置变更与状态保存**：

```java
// 保存临时数据
@Override
protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt("counter_value", counter);
}

// 恢复数据（在 onCreate 中）
if (savedInstanceState != null) {
    counter = savedInstanceState.getInt("counter_value", 0);
}
```

#### Activity 跳转与 Intent 数据传输

Intent 分为两种：
- **显式 Intent**：明确指定要启动的组件的类名（应用内部跳转）
- **隐式 Intent**：描述一个想执行的动作，系统寻找能处理的组件

##### 显式 Intent 跳转

```java
Intent intent = new Intent(MainActivity.this, SecondActivity.class);
startActivity(intent);
```

##### Bundle 传递数据

```java
// 发送方
Intent intent = new Intent(MainActivity.this, SecondActivity.class);
intent.putExtra("USER_NAME", "Aime");
intent.putExtra("USER_AGE", 25);
startActivity(intent);

// 接收方
Intent intent = getIntent();
String name = intent.getStringExtra("USER_NAME");
int age = intent.getIntExtra("USER_AGE", 0);
```

##### 传递自定义对象：Serializable vs Parcelable

| | Serializable | Parcelable |
|---|---|---|
| 性能 | 较慢（反射） | 快（手动序列化） |
| 实现难度 | 极简单 | 稍复杂 |
| 推荐场景 | 磁盘持久化、网络传输 | **Android 组件间传递（推荐）** |

##### 隐式 Intent 示例

```java
// 打开网页
Intent webIntent = new Intent(Intent.ACTION_VIEW);
webIntent.setData(Uri.parse("https://www.android.com"));
startActivity(webIntent);

// 拨打电话
Intent dialIntent = new Intent(Intent.ACTION_DIAL);
dialIntent.setData(Uri.parse("tel:10086"));
startActivity(dialIntent);
```

#### Activity 启动模式

| 模式 | 行为 | 典型场景 |
|------|------|---------|
| `standard`（默认） | 每次都新建实例 | 普通页面 |
| `singleTop` | 栈顶复用，调用 onNewIntent() | 接收通知的页面 |
| `singleTask` | 栈内单例，清空上方 Activity | App 主界面 |
| `singleInstance` | 全局单例，独立任务栈 | 来电界面 |

#### 获取 Activity 的返回结果

**推荐方式：Activity Result API**

```java
// 注册启动器
private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
    new ActivityResultContracts.StartActivityForResult(),
    result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            String data = result.getData().getStringExtra("RETURN_DATA");
        }
    });

// 启动
launcher.launch(intent);
```

```java
// 返回数据
Intent returnIntent = new Intent();
returnIntent.putExtra("RETURN_DATA", input);
setResult(Activity.RESULT_OK, returnIntent);
finish();
```

---

### 第二部分：Android 常见布局与基础 UI 组件

#### 五大布局概述

| 布局 | 核心特点 | 适用场景 |
|------|---------|---------|
| **FrameLayout** | 层叠堆叠，默认左上角对齐 | 图片上叠加按钮、Fragment 容器 |
| **LinearLayout** | 水平/垂直线性排列 | 工具栏、设置页、简单表单 |
| **RelativeLayout** | 相对定位（相对于父容器/兄弟视图） | 复杂图文混排、登录页 |
| **ConstraintLayout** | 约束定位（官方推荐） | 几乎所有现代界面，替代复杂嵌套 |
| **GridLayout** | 规整网格 | 计算器键盘、九宫格功能入口 |

#### 核心 UI 组件

##### ImageView

| 属性 | 说明 |
|------|------|
| `android:src` | 图片资源 |
| `android:scaleType` | `centerCrop`（填满裁剪）、`fitCenter`（完整显示）、`fitXY`（拉伸变形，慎用） |
| `android:tint` | 着色 |

##### EditText

| 属性 | 说明 |
|------|------|
| `android:hint` | 占位提示文字 |
| `android:inputType` | `textPassword`、`number`、`textEmailAddress`、`phone` |
| `android:maxLength` | 最大字符数 |
| `android:maxLines="1"` | 单行输入 |

```java
// 监听文本变化
editText.addTextChangedListener(new TextWatcher() {
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.length() < 6) {
            editText.setError("密码长度不能少于6位");
        }
    }
    @Override public void beforeTextChanged(...) {}
    @Override public void afterTextChanged(Editable s) {}
});
```

##### Toast（轻量级提示）

```java
Toast.makeText(this, "操作成功", Toast.LENGTH_SHORT).show();
```

> 从 Android 11 起自定义 Toast 受限，推荐使用 Snackbar 替代。

##### Snackbar（推荐替代 Toast）

```java
Snackbar.make(view, "文件已删除", Snackbar.LENGTH_LONG)
    .setAction("撤销", v -> { /* 撤销操作 */ })
    .show();
```

| 特性 | Snackbar | Toast |
|------|---------|------|
| 位置 | 屏幕底部 | 可自由设置 |
| 交互 | 支持按钮 + 滑动关闭 | 无交互 |
| 依赖 | 需父容器 | 仅需 Context |
| 时长 | 支持无限期 | 仅 2s / 3.5s |

##### RecyclerView（高效列表组件）

核心组成：
- **Adapter**：数据与视图绑定
- **LayoutManager**：布局排列（Linear/Grid/StaggeredGrid）
- **ViewHolder**：缓存 Item 视图，减少 inflate 和 findViewById 开销
- **ItemAnimator**：增删改查动画

```java
// 初始化
RecyclerView recyclerView = findViewById(R.id.recycler);
recyclerView.setLayoutManager(new LinearLayoutManager(this));
recyclerView.setAdapter(new UserAdapter(userList));
```

Adapter 必须实现的 3 个方法：
- `onCreateViewHolder()` → 加载 Item 布局
- `onBindViewHolder()` → 绑定数据
- `getItemCount()` → 返回数据量

##### Dialog 与 DialogFragment

```java
// AlertDialog
new AlertDialog.Builder(this)
    .setTitle("确认删除")
    .setMessage("确定要删除吗？")
    .setPositiveButton("确定", (dialog, which) -> performDelete())
    .setNegativeButton("取消", null)
    .show();
```

> **推荐使用 DialogFragment**：能正确处理生命周期（如屏幕旋转），直接创建的 Dialog 不能。

#### Shape 与 Selector

##### Shape（自定义形状）

```xml
<shape android:shape="rectangle">
    <solid android:color="#4CAF50" />
    <corners android:radius="8dp" />
    <stroke android:width="1dp" android:color="#ccc" />
</shape>
```

##### Selector（状态选择器）

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:drawable="@color/red" />
    <item android:state_selected="true" android:drawable="@color/blue" />
    <item android:drawable="@color/gray" />  <!-- 默认放最后 -->
</selector>
```

| 状态属性 | 含义 |
|---------|------|
| `state_pressed` | 按压中 |
| `state_selected` | 选中 |
| `state_enabled` | 启用/禁用 |
| `state_focused` | 获得焦点 |
| `state_checked` | 勾选 |

---

### 第三部分：Android 数据存储

#### SharedPreferences（轻量级键值对存储）

**适用场景**：用户偏好设置、状态记录、简单缓存

```java
// 写入
SharedPreferences.Editor editor = getSharedPreferences("app_config", MODE_PRIVATE).edit();
editor.putString("username", "AndroidDev");
editor.putInt("login_count", 5);
editor.putBoolean("is_vip", true);
editor.apply(); // 异步提交，不阻塞主线程

// 读取
SharedPreferences prefs = getSharedPreferences("app_config", MODE_PRIVATE);
String username = prefs.getString("username", "default_user");
```

| 方法 | 执行方式 | 阻塞 | 推荐 |
|------|---------|------|:--:|
| `apply()` | 异步 | 不阻塞 | ✅ |
| `commit()` | 同步 | 阻塞主线程 | 仅需确认结果时 |

> **加密安全**：敏感数据使用 `EncryptedSharedPreferences`

#### SQLite 数据库（结构化数据存储）

**适用场景**：大量结构化数据、复杂查询、事务支持

```java
// 创建 SQLiteOpenHelper
public class MyDatabaseHelper extends SQLiteOpenHelper {
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY, title TEXT, content TEXT);");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notes");
        onCreate(db);
    }
}
```

##### CRUD 操作

```java
// 插入
ContentValues values = new ContentValues();
values.put("title", "学习 Android");
long newRowId = database.insert("notes", null, values);

// 查询
Cursor cursor = database.query("notes", null, "title = ?", 
    new String[]{"学习 Android"}, null, null, "created_at DESC");
while (cursor.moveToNext()) {
    String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
}
cursor.close(); // ⚠️ 必须关闭

// 更新
ContentValues updateValues = new ContentValues();
updateValues.put("content", "更新内容");
database.update("notes", updateValues, "_id = ?", new String[]{String.valueOf(id)});

// 删除
database.delete("notes", "_id = ?", new String[]{String.valueOf(id)});
```

##### 事务

```java
database.beginTransaction();
try {
    // 多个操作...
    database.setTransactionSuccessful();
} finally {
    database.endTransaction();
}
```

> **推荐**：实际项目中使用 Room 持久性库（ORM），编译时 SQL 校验，减少模板代码。

---

## 四、随堂测试答案

| 题号 | 答案 | 解析 |
|:--:|:--:|------|
| 1 | **B** | onPause() → onStop() |
| 2 | **D** | onDestroy() 是销毁时执行 |
| 3 | **D** | ViewManager 不是 RecyclerView 的布局管理器 |
| 4 | **A, B, C** | LinearLayout、GridLayout、RelativeLayout 都可以添加子 View；ImageView 不可以 |
| 5 | **B** | 写入需要通过 Editor 对象完成 |

---

## 五、课后作业

### 需求描述
完成登录页面和个人中心页面。

### 功能要求

**登录页面**：
- App 启动先进入登录页面，使用 EditText 输入用户信息和密码
- 用户信息采用数据库方式存储，首次登录预埋账号密码
- 具备账号和密码验证逻辑
- 微信登录和 Apple 登录实现 UI 和点击事件（Toast 提示）

**个人中心页面**：
- 显示圆形头像，用户名称、签名使用 SharedPreferences 存取
- 每个信息条目可点击，点击后 Toast 提示

---

## 六、学习资料与进阶方向

### 学习资料
- Android Studio 调试应用指南
- Material Design
- ViewPager2 滑动窗口入门
- Fragment 全解析
- Room 持久性库：[官方文档](https://developer.android.com/training/data-storage/room)
- ConstraintLayout 详解
- 图标查找：https://www.iconfont.cn/
- 进阶书籍：《Android 开发艺术探索》

### 进阶方向
1. **Jetpack 组件**：ViewModel、LiveData、Room
2. **Kotlin 协程**：简化异步操作
3. **应用架构**：MVVM、MVI
4. **Material Design 3**：最新设计指南
5. **应用性能优化**：内存管理、布局优化、电池优化

---

## 七、Android 工程 Res 目录说明

| 目录 | 存放内容 | 说明 |
|------|---------|------|
| `res/drawable/` | 图片、Shape、Selector XML | 支持限定符（如 `drawable-hdpi`） |
| `res/layout/` | 布局 XML 文件 | Activity、Fragment、列表 Item 布局 |
| `res/mipmap/` | 启动器图标 | 不同密度下自动选择 |
| `res/values/` | 字符串、颜色、尺寸、样式 | `strings.xml`、`colors.xml`、`styles.xml` |
| `res/raw/` | 原始文件（音频、视频） | 以二进制形式打包，不压缩 |
| `res/xml/` | 任意 XML 文件 | 配置文件等 |
| `res/anim/` | 补间动画 | `alpha`、`scale`、`translate`、`rotate` |
