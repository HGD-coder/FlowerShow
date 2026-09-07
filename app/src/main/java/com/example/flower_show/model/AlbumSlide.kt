package com.example.flower_show.model

import androidx.compose.runtime.Immutable

/**
 * 图集中的一页。
 *
 * 一个 AlbumCardItem 里会有多个 AlbumSlide。
 * 每一页既可以是图片，也可以是视频，所以这里用 type 区分媒体类型。
 */
@Immutable
data class AlbumSlide(
    /**
     * 当前页的媒体类型。
     *
     * 取值只应该是：
     * - TYPE_IMAGE：图片页
     * - TYPE_VIDEO：视频页
     *
     * 这里用 Int 是为了方便和 JSON 数据里的数字类型对应。
     */
    val type: Int,

    /**
     * 当前页的媒体地址。
     *
     * 如果 type 是 TYPE_IMAGE，这就是图片地址；
     * 如果 type 是 TYPE_VIDEO，这就是视频地址。
     */
    val mediaUrl: String,
) {
    companion object {
        /**
         * 图片页标记。
         */
        const val TYPE_IMAGE = 0

        /**
         * 视频页标记。
         */
        const val TYPE_VIDEO = 1
    }

    /**
     * 当前页是否是视频。
     *
     * 写成属性后，UI 层可以读 `slide.isVideo`，不用到处写 `slide.type == TYPE_VIDEO`。
     */
    val isVideo: Boolean get() = type == TYPE_VIDEO

    /**
     * 当前页是否是图片。
     */
    val isImage: Boolean get() = type == TYPE_IMAGE
}
