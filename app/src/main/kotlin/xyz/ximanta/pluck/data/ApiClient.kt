package xyz.ximanta.pluck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import xyz.ximanta.pluck.BuildConfig
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

data class FetchResult(
    val filename: String,
    val contentType: String,
    val bytesWritten: Long,
    val file: File
)

class ApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/'),
    private val apiKeyProvider: () -> String?
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val downloadClient: OkHttpClient = client.newBuilder()
        .readTimeout(15, TimeUnit.MINUTES)
        .build()

    suspend fun plans(): Result<List<Plan>> = callGet("/api/plans", auth = false)

    suspend fun requestQR(planId: String, name: String): Result<QRResponse> =
        callPost("/api/qr", json.encodeToString(QRRequest.serializer(), QRRequest(planId, name)), auth = false)

    suspend fun verify(finalAmount: String): Result<VerifyResponse> =
        callPost("/api/verify", json.encodeToString(VerifyRequest.serializer(), VerifyRequest(finalAmount)), auth = false)

    suspend fun history(): Result<HistoryResponse> = callGet("/api/history", auth = true)

    suspend fun resolve(url: String): Result<ResolveResponse> =
        callPost("/api/resolve", json.encodeToString(ResolveRequest.serializer(), ResolveRequest(url)), auth = true)

    suspend fun fetch(
        req: FetchRequest,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): Result<FetchResult> = withContext(Dispatchers.IO) {
        try {
            destination.parentFile?.mkdirs()
            val body = json.encodeToString(FetchRequest.serializer(), req)
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$baseUrl/api/fetch")
                .applyAuth()
                .post(body)
                .build()

            val response = downloadClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    return@withContext Result.failure(
                        ApiException(resp.code, extractErrorMessage(errorBody) ?: "HTTP ${resp.code}")
                    )
                }
                val responseBody = resp.body
                    ?: return@withContext Result.failure(ApiException(-1, "empty response body"))

                val totalBytes = responseBody.contentLength().takeIf { it > 0 }
                val filename = parseFilename(resp.header("Content-Disposition"))
                    ?: destination.name
                val contentType = resp.header("Content-Type") ?: "application/octet-stream"

                var bytesRead = 0L
                var lastTick = 0L
                responseBody.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            bytesRead += n
                            if (bytesRead - lastTick >= 256L * 1024L) {
                                onProgress(bytesRead, totalBytes)
                                lastTick = bytesRead
                            }
                        }
                    }
                }
                onProgress(bytesRead, totalBytes)
                Result.success(FetchResult(filename, contentType, bytesRead, destination))
            }
        } catch (e: Exception) {
            runCatching { if (destination.exists() && destination.length() == 0L) destination.delete() }
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T> callGet(path: String, auth: Boolean): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(baseUrl + path).get()
                val request = if (auth) builder.applyAuth().build() else builder.build()
                execute(request)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend inline fun <reified T> callPost(path: String, body: String, auth: Boolean): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(baseUrl + path)
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                val request = if (auth) builder.applyAuth().build() else builder.build()
                execute(request)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private inline fun <reified T> execute(request: Request): Result<T> {
        val response = client.newCall(request).execute()
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = extractErrorMessage(raw) ?: "HTTP ${resp.code}"
                return Result.failure(ApiException(resp.code, msg))
            }
            return try {
                Result.success(json.decodeFromString<T>(raw))
            } catch (e: Exception) {
                Result.failure(ApiException(resp.code, "parse error: ${e.message}"))
            }
        }
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val key = apiKeyProvider()
        if (!key.isNullOrBlank()) {
            header("X-App-Key", key.uppercase())
        }
        return this
    }

    private fun parseFilename(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val star = STAR_FILENAME_RE.find(header)
        if (star != null) {
            return runCatching { URLDecoder.decode(star.groupValues[1], "UTF-8") }.getOrNull()
        }
        return PLAIN_FILENAME_RE.find(header)?.groupValues?.getOrNull(1)
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val map = json.decodeFromString<Map<String, String>>(body)
            map["error"] ?: map["message"]
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val STAR_FILENAME_RE = Regex("""filename\*=UTF-8''([^;]+)""")
        private val PLAIN_FILENAME_RE = Regex("filename=\"([^\"]+)\"")
    }
}
