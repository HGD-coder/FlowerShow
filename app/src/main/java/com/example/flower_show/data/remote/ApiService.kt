package com.example.flower_show.data.remote

/**
 * ApiService - REST API contract documentation skeleton
 *
 * When Retrofit is added to build.gradle, implement:
 *   @GET("/api/v1/videos")
 *   suspend fun getVideos(@Query("page") page: Int, @Query("pageSize") size: Int): List<VideoItem>
 */
object ApiService {
    const val BASE_URL = "http://10.0.2.2:8080/"
    const val API_PREFIX = "/api/v1"
}
