package com.example.flower_show.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RecommendationApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val gson: Gson = Gson(),
) {
    private val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    fun feedPages(request: FeedPageRequestDto): FeedPageDataDto =
        post(listOf("feed", "pages"), request)

    fun searchGuesses(request: GuessPageRequestDto): GuessPageDataDto =
        post(listOf("search", "guesses"), request)

    fun searchVideos(request: SearchVideosRequestDto): SearchPageDataDto =
        post(listOf("search", "videos"), request)

    fun reportEvents(request: EventBatchRequestDto): EventBatchDataDto =
        post(listOf("events:batch"), request)

    private inline fun <reified T> post(segments: List<String>, body: Any): T {
        val request = Request.Builder()
            .url(url(segments))
            .header("Accept", JsonMediaType.toString())
            .post(gson.toJson(body).toRequestBody(JsonMediaType))
            .build()
        return execute(request)
    }

    private fun url(segments: List<String>): HttpUrl = baseHttpUrl.newBuilder().apply {
        segments.forEach(::addPathSegment)
    }.build()

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = parseEnvelopeObject(body, response.code)
            if (!response.isSuccessful) {
                throw RecommendationApiException(
                    response.code,
                    root.errorMessage()
                        ?: "Recommendation API request failed with HTTP ${response.code}",
                )
            }
            validateSuccessfulEnvelope(root, response.code)
            return runCatching {
                val envelopeType = TypeToken.getParameterized(
                    ApiEnvelopeDto::class.java,
                    object : TypeToken<T>() {}.type,
                ).type
                val envelope = gson.fromJson<ApiEnvelopeDto<T>>(root, envelopeType)
                require(!envelope.requestId.isNullOrBlank()) { "Blank requestId" }
                require(!envelope.traceId.isNullOrBlank()) { "Blank traceId" }
                requireNotNull(envelope.serverTimeMs) { "Missing serverTimeMs" }
                requireNotNull(envelope.data) { "Missing data" }
            }.getOrElse { cause ->
                throw RecommendationApiException(
                    response.code,
                    "Invalid recommendation API envelope: ${cause.message}",
                )
            }
        }
    }

    private fun parseEnvelopeObject(body: String, statusCode: Int): JsonObject =
        runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
            throw RecommendationApiException(statusCode, "Invalid recommendation API envelope")
        }

    private fun validateSuccessfulEnvelope(root: JsonObject, statusCode: Int) {
        val required = listOf("requestId", "traceId", "serverTimeMs", "data", "error")
        val missing = required.filterNot(root::has)
        if (missing.isNotEmpty()) {
            throw RecommendationApiException(
                statusCode,
                "Invalid recommendation API envelope: missing ${missing.joinToString()}",
            )
        }
        if (!root["error"].isJsonNull) {
            throw RecommendationApiException(
                statusCode,
                root.errorMessage() ?: "Recommendation API returned an error",
            )
        }
        if (root["data"].isJsonNull) {
            throw RecommendationApiException(statusCode, "Invalid recommendation API envelope: null data")
        }
    }

    private fun JsonObject.errorMessage(): String? {
        val error = get("error") ?: return null
        if (!error.isJsonObject) return null
        return error.asJsonObject["message"]
            ?.takeUnless { it.isJsonNull }
            ?.asString
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

class RecommendationApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
