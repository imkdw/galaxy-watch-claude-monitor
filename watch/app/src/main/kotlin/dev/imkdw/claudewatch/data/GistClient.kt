package dev.imkdw.claudewatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FailReason { NETWORK, HTTP, RATE_LIMITED, PARSE, NOT_CONFIGURED }

sealed interface FetchResult {
    data class Updated(val body: String, val etag: String?) : FetchResult
    data object NotModified : FetchResult
    data class Failed(val reason: FailReason, val code: Int? = null) : FetchResult
}

fun interface GistSource {
    suspend fun fetch(etag: String?): FetchResult
}

/** `GET /gists/{id}` 비인증 조회. 워치에는 비밀키가 없다 (PRD 10 보안) */
class GistClient(
    private val gistId: String,
    private val http: OkHttpClient = defaultHttp,
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) : GistSource {

    override suspend fun fetch(etag: String?): FetchResult {
        if (gistId.isBlank()) return FetchResult.Failed(FailReason.NOT_CONFIGURED)
        val url = baseUrl.newBuilder().addPathSegment("gists").addPathSegment(gistId).build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { res ->
                    when {
                        res.code == 304 -> FetchResult.NotModified
                        res.isSuccessful -> FetchResult.Updated(res.body.string(), res.header("ETag"))
                        res.code == 429 || (res.code == 403 && res.header("X-RateLimit-Remaining") == "0") ->
                            FetchResult.Failed(FailReason.RATE_LIMITED, res.code)
                        else -> FetchResult.Failed(FailReason.HTTP, res.code)
                    }
                }
            } catch (_: IOException) {
                FetchResult.Failed(FailReason.NETWORK)
            }
        }
    }

    companion object {
        private val defaultHttp by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }
    }
}
