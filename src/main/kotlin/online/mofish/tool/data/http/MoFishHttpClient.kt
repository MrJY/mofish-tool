package online.mofish.tool.data.http

import java.io.IOException
import java.nio.charset.Charset
import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MoFishHttpClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val json: Json = defaultJson(),
) {
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        responseCharset: Charset? = null,
    ): MoFishHttpResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DEFAULT_USER_AGENT)

        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body
            val contentType = responseBody?.contentType()
            val charset = responseCharset ?: contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val body = responseBody?.bytes()?.toString(charset).orEmpty()
            val httpResponse = MoFishHttpResponse(
                requestUrl = url,
                statusCode = response.code,
                headers = response.headers.toMultimap(),
                body = body,
                contentType = contentType?.toString(),
            )
            if (!response.isSuccessful) {
                throw MoFishHttpException(
                    "HTTP ${response.code} 请求失败：$url",
                    httpResponse,
                )
            }
            return httpResponse
        }
    }

    fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = parseJson(get(url, headers).body)

    fun getHtml(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Document = parseHtml(get(url, headers).body, url)

    fun parseJson(content: String): JsonElement = json.parseToJsonElement(content)

    fun parseHtml(
        content: String,
        baseUrl: String = "",
    ): Document = if (baseUrl.isBlank()) Jsoup.parse(content) else Jsoup.parse(content, baseUrl)

    companion object {
        private const val DEFAULT_USER_AGENT = "MoFish IntelliJ Plugin/0.1"

        fun defaultJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            }
        }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(15))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(15))
                .build()
        }
    }
}

data class MoFishHttpResponse(
    val requestUrl: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val contentType: String?,
)

class MoFishHttpException(
    message: String,
    val response: MoFishHttpResponse,
) : IOException(message)
