package dev.imkdw.claudewatch.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GistClientTest {
    private val server = MockWebServer()
    private lateinit var client: GistClient

    @Before
    fun setUp() {
        server.start()
        val http = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        client = GistClient("g123", http, server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val b = MockResponse.Builder().code(code).body(body)
        headers.forEach { (k, v) -> b.addHeader(k, v) }
        server.enqueue(b.build())
    }

    @Test
    fun `H1 200이면 본문과 ETag`() = runTest {
        enqueue(200, """{"files":{}}""", "ETag" to "W/\"abc\"")
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Updated("""{"files":{}}""", "W/\"abc\""))
        val req = server.takeRequest()
        assertThat(req.url.encodedPath).isEqualTo("/gists/g123")
        assertThat(req.headers["Accept"]).isEqualTo("application/vnd.github+json")
        assertThat(req.headers["If-None-Match"]).isNull()
    }

    @Test
    fun `H2 etag가 있으면 If-None-Match`() = runTest {
        enqueue(304)
        client.fetch("W/\"abc\"")
        assertThat(server.takeRequest().headers["If-None-Match"]).isEqualTo("W/\"abc\"")
    }

    @Test
    fun `H3 304는 NotModified`() = runTest {
        enqueue(304)
        assertThat(client.fetch("e")).isEqualTo(FetchResult.NotModified)
    }

    @Test
    fun `H4 한도 초과는 RATE_LIMITED`() = runTest {
        enqueue(403, "{}", "X-RateLimit-Remaining" to "0")
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.RATE_LIMITED, 403))
        enqueue(429, "{}")
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.RATE_LIMITED, 429))
    }

    @Test
    fun `H5 404, 500, 한도와 무관한 403은 HTTP`() = runTest {
        for (code in listOf(404, 500)) {
            enqueue(code)
            assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.HTTP, code))
        }
        enqueue(403, "{}", "X-RateLimit-Remaining" to "12")
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.HTTP, 403))
    }

    @Test
    fun `H6 연결 끊김은 NETWORK, 예외를 던지지 않는다`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.NETWORK))
    }

    @Test
    fun `H6 타임아웃은 NETWORK`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(3, TimeUnit.SECONDS).build())
        assertThat(client.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.NETWORK))
    }

    @Test
    fun `H7 Authorization 헤더를 보내지 않는다`() = runTest {
        enqueue(200, "{}")
        client.fetch(null)
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
    }

    @Test
    fun `gistId가 비어 있으면 요청 없이 NOT_CONFIGURED`() = runTest {
        val c = GistClient("", OkHttpClient(), server.url("/"))
        assertThat(c.fetch(null)).isEqualTo(FetchResult.Failed(FailReason.NOT_CONFIGURED))
        assertThat(server.requestCount).isEqualTo(0)
    }
}
