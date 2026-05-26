package com.example.flower_show.viewmodel

/**
 * SearchIntent — All search-related user actions / 搜索模块所有用户操作
 */
sealed interface SearchIntent {
    /** Execute search / 执行搜索 */
    data class Search(val keyword: String) : SearchIntent

    /** Load history from storage / 加载搜索历史 */
    data object LoadHistory : SearchIntent

    /** Delete one history entry / 删除一条历史 */
    data class DeleteHistory(val keyword: String) : SearchIntent

    /** Clear all history / 清除全部历史 */
    data object ClearHistory : SearchIntent

    /** Error dismissed / 关闭错误 */
    data object DismissError : SearchIntent
}
