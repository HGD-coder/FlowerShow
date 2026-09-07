package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 视频卡片的数据模型。
 *
 * 你可以把它理解成“一个视频在 App 里需要展示和播放的全部信息”。
 * 数据来源通常是 assets 里的 JSON，经过 AssetJsonLoader 解析后变成 VideoItem，
 * 再交给 Repository、ViewModel，最后在 VideoScreen / VideoCard 中显示。
 *
 * data class 的作用：
 * - Kotlin 自动生成 equals/hashCode/toString/copy 等方法。
 * - ViewModel 更新某个字段时可以用 copy() 得到一个新对象。
 * - 字段全部是 val，表示创建后不再原地修改，符合 Compose 推荐的不可变状态风格。
 *
 * @Immutable 表示这个模型会被 Compose 当作稳定/不可变数据对待。
 * 重点不是“注解让它自动不可变”，而是提醒我们：不要在里面放可变集合并偷偷修改。
 */
@Immutable
data class VideoItem(
    /**
     * 视频唯一 id。
     *
     * 用途：
     * - 搜索结果点击后，根据 id 跳回视频流中的指定视频。
     * - 列表 Diff / key / 日志统计也会依赖它。
     * - 原始数据里可能叫 aweme_id。
     */
    val id: String,

    /**
     * 视频标题或描述文案。
     *
     * 用途：
     * - 展示在视频卡片下方。
     * - 搜索时参与关键词匹配。
     */
    val title: String,

    /**
     * 作者昵称。
     *
     * 用途：
     * - 展示在 UI。
     * - 搜索时也可能参与匹配。
     */
    val author: String,

    /**
     * 作者头像地址。
     *
     * 用途：
     * - VideoCard 里显示头像。
     * - 如果为空，UI 通常需要兜底头像或占位图。
     */
    val avatarUrl: String,

    /**
     * 视频播放地址。
     *
     * 这是播放器最关键的字段，VideoPlayerManager 最终会拿这个 url 创建 MediaItem。
     * 原始数据里可能叫 video_download_url。
     */
    val videoUrl: String,

    /**
     * 视频封面图地址。
     *
     * 用途：
     * - 视频还没开始播放、正在缓冲、或者列表预览时展示。
     * - 为空字符串表示当前数据没有封面，UI 需要自己处理兜底。
     */
    val coverUrl: String = "",

    /**
     * 更小尺寸的封面缩略图。
     *
     * 用途：
     * - 搜索结果列表或低成本预览中更适合用缩略图。
     * - 通常由数据预处理脚本生成，不一定原始数据就有。
     */
    val coverThumbnailUrl: String = "",

    /**
     * 背景音乐地址。
     *
     * 视频本身通常已经带声音，所以这个字段不一定会被播放层使用；
     * 但如果后续要展示音乐信息或做图文内容配乐，它是预留入口。
     */
    val musicUrl: String? = null,

    /**
     * 点赞数。
     *
     * 用途：
     * - UI 右侧操作栏展示。
     * - 搜索排序时可作为热度分的一部分。
     */
    val likes: Int = 0,

    /**
     * 评论数。
     */
    val comments: Int = 0,

    /**
     * 收藏数。
     */
    val collections: Int = 0,

    /**
     * 分享数。
     */
    val shares: Int = 0,

    /**
     * 内容标签，例如“花束”“婚礼”“园艺”等。
     *
     * 用途：
     * - 搜索匹配。
     * - 相关推荐词。
     * - 详情页或卡片上的标签展示。
     */
    val tags: List<String> = emptyList(),

    /**
     * 推荐搜索词。
     *
     * 用途：
     * - 用户打开搜索页时，系统可以根据这些词给出猜你想搜。
     * - 视频卡片上也可能出现“相关搜索”入口。
     */
    val recommendWords: List<String> = emptyList(),

    /**
     * 内容搜索词集合。
     *
     * 可以理解成“为了搜索专门整理出来的一组关键词”。
     * 它不一定直接展示给用户，但能帮助 SearchMatcher / Repository 找到更相关的内容。
     */
    val contentSearches: List<String> = emptyList(),

    /**
     * 多清晰度播放地址。
     *
     * key 通常是 "1080p"、"720p" 这类名称，value 是对应清晰度的视频 url。
     * VideoQualitySelector 会把它转换成 List<VideoQuality>，用于自动/手动清晰度切换。
     *
     * 为 null 表示当前视频只有默认 videoUrl。
     */
    val qualityUrls: Map<String, String>? = null,

    /** Adaptive HLS master playlist. MP4 fields remain available as a fallback. */
    val hlsUrl: String? = null,

    /**
     * Stable backend user id for the author.
     *
     * Older local assets and older server responses do not contain this field, so
     * navigation must keep the nullable fallback instead of guessing an id.
     */
    val authorUserId: String? = null,

    /** Interaction state calculated by the backend for the authenticated viewer. */
    val likedByViewer: Boolean = false,
    val favoritedByViewer: Boolean = false,
) : CardItem {
    /**
     * 声明自己是一张视频卡片。
     *
     * 这样 List<CardItem> 中混着视频、图片、图集时，UI 可以快速分流渲染。
     */
    override val itemType: CardItem = CardItem.TypeVideo
}
