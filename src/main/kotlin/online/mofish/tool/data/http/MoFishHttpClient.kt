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
    /**
     * 处理 get 相关逻辑，并返回调用方需要的结果。
     * @param url URL。
     * @param headers headers。
     * @param responseCharset 响应Charset。
     * @return 处理后的结果或当前状态。
     */
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

    /**
     * 获取Json。
     * @param url URL。
     * @param headers headers。
     * @return 处理后的结果或当前状态。
     */
    fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = parseJson(get(url, headers).body)

    /**
     * 获取HTML。
     * @param url URL。
     * @param headers headers。
     * @return 处理后的结果或当前状态。
     */
    fun getHtml(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Document = parseHtml(get(url, headers).body, url)

    /**
     * 解析Json数据，并转换为项目内部可用的结构。
     * @param content 需要渲染或包装的内容。
     * @return 处理后的结果或当前状态。
     */
    fun parseJson(content: String): JsonElement = json.parseToJsonElement(content)

    /**
     * 解析HTML数据，并转换为项目内部可用的结构。
     * @param content 需要渲染或包装的内容。
     * @param baseUrl baseURL。
     * @return 处理后的结果或当前状态。
     */
    fun parseHtml(
        content: String,
        baseUrl: String = "",
    ): Document = if (baseUrl.isBlank()) Jsoup.parse(content) else Jsoup.parse(content, baseUrl)

    companion object {
        private const val DEFAULT_USER_AGENT = "MoFish IntelliJ Plugin/0.1"

        /**
         * 处理 defaultJson 相关逻辑，并返回调用方需要的结果。
         * @return 处理后的结果或当前状态。
         */
        fun defaultJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            }
        }

        /**
         * 处理 defaultOkHttpClient 相关逻辑，并返回调用方需要的结果。
         * @return 处理后的结果或当前状态。
         */
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
