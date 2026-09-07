package com.example.flower_show.data.push

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DeviceRegistrationApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DeviceRegistrationApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = DeviceRegistrationApi(
            client = OkHttpClient(),
            baseUrl = server.url("/api/v1/").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun registerUsesExpectedPathAndFidBodyAndParsesRedactedResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": "dev_server_id",
                      "platform": "android",
                      "deviceName": null,
                      "recipientType": "fid",
                      "enabled": true,
                      "lastSeenAt": "2026-07-24T08:00:00Z"
                    }
                    """.trimIndent(),
                ),
        )

        val response = api.register("firebase-installation-id")

        assertEquals("dev_server_id", response.id)
        assertEquals("android", response.platform)
        assertEquals("fid", response.recipientType)
        assertTrue(response.enabled)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/me/devices", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertEquals(
            mapOf(
                "token" to "firebase-installation-id",
                "platform" to "android",
                "recipientType" to "fid",
            ),
            request.stringBody(),
        )
        assertFalse(response.toString().contains("firebase-installation-id"))
    }

    @Test
    fun disableUsesDevicePathAndCapturedBearerAndParsesChangedResponse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"changed":true}"""),
        )

        val response = api.disable("dev_server_id", "captured-access-token")

        assertTrue(response.changed)
        val request = takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/me/devices/dev_server_id", request.path)
        assertEquals("Bearer captured-access-token", request.getHeader("Authorization"))
        assertEquals("", request.body.readUtf8())
    }

    private fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected a device request" }

    private fun RecordedRequest.stringBody(): Map<String, String?> {
        val json = JsonParser.parseString(body.readUtf8()).asJsonObject
        return json.entrySet().associate { (key, value) ->
            key to if (value.isJsonNull) null else value.asString
        }
    }
}
