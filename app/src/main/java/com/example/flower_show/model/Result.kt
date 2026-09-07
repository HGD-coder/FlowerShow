package com.example.flower_show.model

/**
 * 异步操作结果的统一包装。
 *
 * Repository 读取数据、搜索数据时，不应该只返回 List 或 null，
 * 因为调用方还需要知道：
 * - 成功：拿到了数据
 * - 失败：为什么失败
 * - 加载中：当前还在请求
 *
 * 所以这里用 sealed class 把三种状态收拢到一个类型里。
 *
 * 典型用法：
 * ```
 * when (val result = repository.loadFeed(...)) {
 *     is Result.Success -> show(result.data)
 *     is Result.Error -> showError(result.message)
 *     Result.Loading -> showLoading()
 * }
 * ```
 *
 * sealed class 的价值：
 * when 处理 Result 时，编译器能检查你有没有漏掉 Success/Error/Loading。
 */
sealed class Result<out T> {

    /**
     * 成功状态，data 是真正返回的数据。
     *
     * 这里的 T 是泛型：
     * - Result<List<CardItem>> 表示成功时拿到卡片列表
     * - Result<List<VideoItem>> 表示成功时拿到视频列表
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * 失败状态，message 是给日志或 UI 使用的错误信息。
     */
    data class Error(val message: String) : Result<Nothing>()

    /**
     * 加载中状态。
     *
     * data object 表示全局只需要一个 Loading 实例，不需要每次 new。
     */
    data object Loading : Result<Nothing>()

    /**
     * 下面三个布尔属性是便捷判断。
     *
     * 例如 ViewModel 可以写 `if (result.isSuccess)`，
     * 不一定每次都要完整写一个 when。
     */
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /**
     * 如果是 Success，就取出 data；否则返回 null。
     *
     * 适合“不关心失败原因，只想尽量拿数据”的场景。
     */
    fun getOrNull(): T? = (this as? Success)?.data

    companion object {
        /**
         * 创建成功结果。
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * 创建失败结果。
         */
        fun error(message: String): Result<Nothing> = Error(message)

        /**
         * 创建加载中结果。
         */
        fun loading(): Result<Nothing> = Loading
    }
}
