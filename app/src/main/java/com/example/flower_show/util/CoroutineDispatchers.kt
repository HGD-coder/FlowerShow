package com.example.flower_show.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * CoroutineDispatchers - Replaces ThreadPoolManager
 * Kotlin coroutines handle threading natively.
 */
object CoroutineDispatchers {
    val IO: CoroutineDispatcher = Dispatchers.IO
    val Main: CoroutineDispatcher = Dispatchers.Main
    val Default: CoroutineDispatcher = Dispatchers.Default
}
