package com.example.flower_show.model

/**
 * Result - Sealed class for async operation results / 异步结果密封类
 *
 * Kotlin sealed class is more type-safe than Java enum + wrapper.
 * Use when() for exhaustive handling.
 *
 * Usage:
 *   Result.success(data)
 *   Result.error("message")
 *   Result.loading()
 */
sealed class Result<out T> {

    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(message: String): Result<Nothing> = Error(message)
        fun loading(): Result<Nothing> = Loading
    }
}
