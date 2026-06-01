package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.StockCompanyProfile
import online.mofish.tool.domain.StockNewsItem
import online.mofish.tool.domain.StockResearchReportItem
import online.mofish.tool.domain.StockValuationMetrics
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class TencentStockMetricsProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultTencentAShareQuoteUrl,
) {
    /**
     * 从远程或本地数据源获取Metrics数据。
     * @param requested 已经规范化后的资产请求对象。
     * @return 处理后的结果或当前状态。
     */
    fun fetchMetrics(requested: RequestedStock): StockValuationMetrics? {
        val response = httpClient.get(
            url = quoteUrlProvider(listOf(requested.vendorCode)),
            responseCharset = TENCENT_DETAIL_CHARSET,
        )
        val fields = httpClient.parseJson(response.body).jsonObject[requested.vendorCode]?.jsonArray ?: return null
        return StockValuationMetrics(
            turnoverRatePercent = fields.decimalValue(38),
            peTtm = fields.decimalValue(39),
            amplitudePercent = fields.decimalValue(43),
            totalMarketCapYi = fields.decimalValue(44),
            floatMarketCapYi = fields.decimalValue(45),
            pb = fields.decimalValue(46),
            limitUpPrice = fields.decimalValue(47),
            limitDownPrice = fields.decimalValue(48),
            volumeRatio = fields.decimalValue(49),
            peStatic = fields.decimalValue(52),
        )
    }
}

internal class EastmoneyStockProfileProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
) {
    /**
     * 从远程或本地数据源获取Profile数据。
     * @param requested 已经规范化后的资产请求对象。
     * @return 处理后的结果或当前状态。
     */
    fun fetchProfile(requested: RequestedStock): StockCompanyProfile? {
        val data = httpClient.getJson(defaultEastmoneyStockProfileUrl(requested)).jsonObject["data"]?.jsonObject
            ?: return null
        return StockCompanyProfile(
            industry = data.stringValue("f127"),
            listDate = formatListDate(data.stringValue("f189")),
            totalShares = data.decimalValue("f84"),
            floatShares = data.decimalValue("f85"),
            totalMarketCap = data.decimalValue("f116"),
            floatMarketCap = data.decimalValue("f117"),
        )
    }
}

internal class EastmoneyStockReportProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
) {
    /**
     * 从远程或本地数据源获取Reports数据。
     * @param code 资产代码或业务标识。
     * @param maxItems maxItems。
     * @return 处理后的结果或当前状态。
     */
    fun fetchReports(code: String, maxItems: Int): List<StockResearchReportItem> {
        val payload = httpClient.getJson(defaultEastmoneyReportUrl(code)).jsonObject
        val rows = payload["data"]?.jsonArray ?: return emptyList()
        return rows.mapNotNull(::toReportItem).take(maxItems)
    }
}

internal class EastmoneyStockNewsProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
) {
    /**
     * 从远程或本地数据源获取News数据。
     * @param code 资产代码或业务标识。
     * @param maxItems maxItems。
     * @return 处理后的结果或当前状态。
     */
    fun fetchNews(code: String, maxItems: Int): List<StockNewsItem> {
        val response = httpClient.get(
            url = defaultEastmoneyNewsUrl(code, maxItems),
            headers = mapOf("Referer" to "https://so.eastmoney.com/"),
        ).body
        val jsonPayload = response.substringAfter("(", missingDelimiterValue = "")
            .substringBeforeLast(")", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?: return emptyList()
        val payload = httpClient.parseJson(jsonPayload).jsonObject
        val rows = payload["result"]
            ?.jsonObject
            ?.get("cmsArticleWebOld")
            ?.jsonObject
            ?.get("list")
            ?.jsonArray
            ?: return emptyList()
        return rows.mapNotNull(::toNewsItem).take(maxItems)
    }
}

/**
 * 转换为ReportItem表示。
 * @param item item。
 * @return 处理后的结果或当前状态。
 */
private fun toReportItem(item: JsonElement): StockResearchReportItem? {
    val obj = item.jsonObject
    val title = obj.stringValue("title") ?: return null
    val infoCode = obj.stringValue("infoCode")
    return StockResearchReportItem(
        title = title,
        infoCode = infoCode,
        publishDate = obj.stringValue("publishDate")?.take(10),
        organization = obj.stringValue("orgSName"),
        rating = obj.stringValue("emRatingName"),
        thisYearEps = obj.decimalValue("predictThisYearEps"),
        nextYearEps = obj.decimalValue("predictNextYearEps"),
        nextTwoYearEps = obj.decimalValue("predictNextTwoYearEps"),
    )
}

/**
 * 转换为NewsItem表示。
 * @param item item。
 * @return 处理后的结果或当前状态。
 */
private fun toNewsItem(item: JsonElement): StockNewsItem? {
    val obj = item.jsonObject
    val title = obj.stringValue("title")?.stripHtml() ?: return null
    return StockNewsItem(
        title = title,
        content = obj.stringValue("content")?.stripHtml()?.take(180),
        time = obj.stringValue("date"),
        source = obj.stringValue("mediaName"),
        url = obj.stringValue("url"),
    )
}

/**
 * 处理 defaultEastmoneyStockProfileUrl 相关逻辑，并返回调用方需要的结果。
 * @param requested 已经规范化后的资产请求对象。
 * @return 处理后的结果或当前状态。
 */
private fun defaultEastmoneyStockProfileUrl(requested: RequestedStock): String {
    val marketCode = when (requested.exchange) {
        online.mofish.tool.domain.StockExchange.SSE -> "1"
        else -> "0"
    }
    return "https://push2.eastmoney.com/api/qt/stock/get?fltt=2&invt=2" +
        "&fields=f57,f58,f84,f85,f127,f116,f117,f189,f43&secid=$marketCode.${requested.displaySymbol}"
}

/**
 * 处理 defaultEastmoneyReportUrl 相关逻辑，并返回调用方需要的结果。
 * @param code 资产代码或业务标识。
 * @return 处理后的结果或当前状态。
 */
private fun defaultEastmoneyReportUrl(code: String): String {
    val encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8)
    return "https://reportapi.eastmoney.com/report/list?industryCode=*&pageSize=20&industry=*" +
        "&rating=*&ratingChange=*&beginTime=2000-01-01&endTime=2030-01-01&pageNo=1" +
        "&fields=&qType=0&orgCode=&code=$encodedCode&rcode=&p=1&pageNum=1&pageNumber=1"
}

/**
 * 处理 defaultEastmoneyNewsUrl 相关逻辑，并返回调用方需要的结果。
 * @param code 资产代码或业务标识。
 * @param pageSize pageSize。
 * @return 处理后的结果或当前状态。
 */
private fun defaultEastmoneyNewsUrl(code: String, pageSize: Int): String {
    val param = """{"uid":"","keyword":"$code","type":["cmsArticleWebOld"],"client":"web","clientType":"web","clientVersion":"curr","param":{"cmsArticleWebOld":{"searchScope":"default","sort":"default","pageIndex":1,"pageSize":$pageSize,"preTag":"","postTag":""}}}"""
    return "https://search-api-web.eastmoney.com/search/jsonp?cb=jQuery_mofish&param=" +
        URLEncoder.encode(param, StandardCharsets.UTF_8)
}

private fun JsonObject.stringValue(key: String): String? {
    return get(key)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "--" }
}

private fun JsonObject.decimalValue(key: String): BigDecimal? = stringValue(key)?.toBigDecimalOrNull()

private fun JsonArray.decimalValue(index: Int): BigDecimal? {
    return getOrNull(index)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "--" }?.toBigDecimalOrNull()
}

private fun String.stripHtml(): String {
    return replace(Regex("<[^>]+>"), "").trim()
}

/**
 * 格式化列表日期，用于界面展示。
 * @param value 待解析、格式化或写入的原始值。
 * @return 处理后的结果或当前状态。
 */
private fun formatListDate(value: String?): String? {
    val normalized = value?.trim()?.takeIf { it.length == 8 && it.all(Char::isDigit) } ?: return value
    return "${normalized.substring(0, 4)}-${normalized.substring(4, 6)}-${normalized.substring(6, 8)}"
}

private val TENCENT_DETAIL_CHARSET: Charset = Charset.forName("GBK")
