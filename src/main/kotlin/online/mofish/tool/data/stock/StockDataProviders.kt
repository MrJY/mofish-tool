package online.mofish.tool.data.stock

import online.mofish.tool.data.http.MoFishHttpClient
import online.mofish.tool.domain.QuoteStatus
import online.mofish.tool.domain.StockExchange
import online.mofish.tool.domain.StockQuote
import online.mofish.tool.domain.StockSearchSuggestion
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SinaAShareQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultSinaQuoteUrl,
) : AShareQuoteProvider {
    override val providerName: String = "sina-a-share"

    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        return fetchSinaQuotes(
            httpClient = httpClient,
            quoteUrlProvider = quoteUrlProvider,
            requestedStocks = requestedStocks,
            parser = ::parseSinaAShareQuote,
        )
    }
}

internal class SinaUsQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultSinaQuoteUrl,
) : UsQuoteProvider {
    override val providerName: String = "sina-us"

    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        return fetchSinaQuotes(
            httpClient = httpClient,
            quoteUrlProvider = quoteUrlProvider,
            requestedStocks = requestedStocks,
            parser = ::parseSinaUsQuote,
        )
    }
}

internal class TencentAShareQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultTencentAShareQuoteUrl,
) : AShareQuoteProvider {
    override val providerName: String = "tencent-a-share"

    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        if (requestedStocks.isEmpty()) {
            return emptyMap()
        }

        val responseJson = httpClient.get(
            url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
            responseCharset = TENCENT_CHARSET,
        )
        val payload = httpClient.parseJson(responseJson.body).jsonObject

        return requestedStocks.mapNotNull { requested ->
            val fields = payload[requested.vendorCode]?.jsonArray ?: return@mapNotNull null
            parseTencentAShareQuote(requested, fields)?.let { requested.originalCode to it }
        }.toMap()
    }
}

internal class TencentHongKongQuoteProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val quoteUrlProvider: (List<String>) -> String = ::defaultHongKongQuoteUrl,
) : HongKongQuoteProvider {
    override val providerName: String = "tencent-hk"

    override fun fetchQuotes(requestedStocks: List<RequestedStock>): Map<String, StockQuote> {
        if (requestedStocks.isEmpty()) {
            return emptyMap()
        }

        val responseJson = httpClient.get(
            url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
            responseCharset = TENCENT_CHARSET,
        )
        val payload = httpClient.parseJson(responseJson.body).jsonObject

        return requestedStocks.mapNotNull { requested ->
            val fields = payload["r_${requested.vendorCode}"]?.jsonArray ?: return@mapNotNull null
            parseTencentHongKongQuote(requested, fields)?.let { requested.originalCode to it }
        }.toMap()
    }
}

internal class TencentStockSearchSuggestionProvider(
    private val httpClient: MoFishHttpClient = MoFishHttpClient(),
    private val searchUrlProvider: (String) -> String = ::defaultStockSearchUrl,
) : StockSearchSuggestionProvider {
    override val providerName: String = "tencent-stock-search"

    override fun searchSuggestions(keyword: String): List<StockSearchSuggestion> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isEmpty()) {
            return emptyList()
        }

        val payload = httpClient.getJson(searchUrlProvider(normalizedKeyword)).jsonObject
        val stockItems = payload["data"]
            ?.jsonObject
            ?.get("stock")
            ?.jsonArray
            ?: return emptyList()

        return stockItems.mapNotNull(::toSearchSuggestion)
    }
}

private fun fetchSinaQuotes(
    httpClient: MoFishHttpClient,
    quoteUrlProvider: (List<String>) -> String,
    requestedStocks: List<RequestedStock>,
    parser: (RequestedStock, List<String>) -> StockQuote?,
): Map<String, StockQuote> {
    if (requestedStocks.isEmpty()) {
        return emptyMap()
    }

    val requestedByVendorCode = requestedStocks.associateBy { it.vendorCode }
    val payload = httpClient.get(
        url = quoteUrlProvider(requestedStocks.map { it.vendorCode }),
        headers = mapOf("Referer" to SINA_REFERER),
        responseCharset = SINA_CHARSET,
    ).body

    return payload
        .lineSequence()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            val match = SINA_QUOTE_LINE.find(line) ?: return@mapNotNull null
            val vendorCode = match.groupValues[1].lowercase()
            val requested = requestedByVendorCode[vendorCode] ?: return@mapNotNull null
            val fields = match.groupValues[2].split(',')
            parser(requested, fields)?.let { requested.originalCode to it }
        }
        .toMap()
}

private fun parseSinaAShareQuote(
    requested: RequestedStock,
    fields: List<String>,
): StockQuote? {
    if (fields.size <= 31) {
        return null
    }

    val previousClose = decimalValue(fields.getOrNull(2))
    var currentPrice = decimalValue(fields.getOrNull(3))
    if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) {
        currentPrice = decimalValue(fields.getOrNull(6)) ?: previousClose
    }

    return buildQuote(
        requested = requested,
        name = stringValue(fields.getOrNull(0)) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = currentPrice,
        previousClose = previousClose,
        openPrice = decimalValue(fields.getOrNull(1)),
        highPrice = decimalValue(fields.getOrNull(4)),
        lowPrice = decimalValue(fields.getOrNull(5)),
        volume = decimalValue(fields.getOrNull(8)),
        turnover = decimalValue(fields.getOrNull(9)),
        updatedAt = parseSinaDateTime("${fields.getOrNull(30).orEmpty()} ${fields.getOrNull(31).orEmpty()}"),
    )
}

private fun parseSinaUsQuote(
    requested: RequestedStock,
    fields: List<String>,
): StockQuote? {
    if (fields.size <= 26) {
        return null
    }

    return buildQuote(
        requested = requested,
        name = stringValue(fields.getOrNull(0)) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = decimalValue(fields.getOrNull(1)),
        previousClose = decimalValue(fields.getOrNull(26)),
        openPrice = decimalValue(fields.getOrNull(5)),
        highPrice = decimalValue(fields.getOrNull(6)),
        lowPrice = decimalValue(fields.getOrNull(7)),
        volume = decimalValue(fields.getOrNull(10)),
        turnover = decimalValue(fields.getOrNull(12)),
        updatedAt = parseSinaDateTime(fields.getOrNull(3).orEmpty()),
        explicitChangeAmount = decimalValue(fields.getOrNull(4)),
        explicitChangePercent = decimalValue(fields.getOrNull(2)),
        afterHoursPrice = decimalValue(fields.getOrNull(21)).takeIf { it != null && it > BigDecimal.ZERO },
        afterHoursChangePercent = decimalValue(fields.getOrNull(22)).takeIf { it != null && it != BigDecimal.ZERO },
    )
}

private fun parseTencentAShareQuote(
    requested: RequestedStock,
    fields: JsonArray,
): StockQuote? {
    return buildQuote(
        requested = requested,
        name = fields.stringValue(1) ?: "${requested.originalCode} 暂无数据",
        exchange = requested.exchange,
        currentPrice = fields.decimalValue(3),
        previousClose = fields.decimalValue(4),
        openPrice = fields.decimalValue(5),
        highPrice = fields.decimalValue(33),
        lowPrice = fields.decimalValue(34),
        volume = fields.decimalValue(36)?.multiply(HUNDRED),
        turnover = fields.decimalValue(37)?.multiply(TEN_THOUSAND),
        updatedAt = parseTencentDateTime(fields.stringValue(30).orEmpty()),
        explicitChangeAmount = fields.decimalValue(31),
        explicitChangePercent = fields.decimalValue(32),
    )
}

private fun parseTencentHongKongQuote(
    requested: RequestedStock,
    fields: JsonArray,
): StockQuote? {
    return buildQuote(
        requested = requested,
        name = fields.stringValue(1) ?: "${requested.originalCode} 暂无数据",
        exchange = StockExchange.HKEX,
        currentPrice = fields.decimalValue(3),
        previousClose = fields.decimalValue(4),
        openPrice = fields.decimalValue(5),
        highPrice = fields.decimalValue(33),
        lowPrice = fields.decimalValue(34),
        volume = fields.decimalValue(36),
        turnover = fields.decimalValue(37),
        updatedAt = parseTencentSlashDateTime(fields.stringValue(30).orEmpty()),
        explicitChangeAmount = fields.decimalValue(31),
        explicitChangePercent = fields.decimalValue(32),
    )
}

private fun buildQuote(
    requested: RequestedStock,
    name: String,
    exchange: StockExchange,
    currentPrice: BigDecimal?,
    previousClose: BigDecimal?,
    openPrice: BigDecimal?,
    highPrice: BigDecimal?,
    lowPrice: BigDecimal?,
    volume: BigDecimal?,
    turnover: BigDecimal?,
    updatedAt: LocalDateTime?,
    explicitChangeAmount: BigDecimal? = null,
    explicitChangePercent: BigDecimal? = null,
    afterHoursPrice: BigDecimal? = null,
    afterHoursChangePercent: BigDecimal? = null,
): StockQuote {
    val changeAmount = explicitChangeAmount ?: calculateChangeAmount(currentPrice, previousClose)
    val changePercent = explicitChangePercent ?: calculateChangePercent(changeAmount, previousClose)
    val status = if (currentPrice == null) QuoteStatus.UNAVAILABLE else QuoteStatus.TRADING

    return StockQuote(
        code = requested.originalCode,
        name = name,
        symbol = requested.displaySymbol,
        exchange = exchange,
        currentPrice = currentPrice,
        previousClose = previousClose,
        openPrice = openPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        changeAmount = changeAmount,
        changePercent = changePercent,
        volume = volume,
        turnover = turnover,
        updatedAt = updatedAt,
        status = status,
        afterHoursPrice = afterHoursPrice,
        afterHoursChangePercent = afterHoursChangePercent,
    )
}

internal fun unavailableQuote(requested: RequestedStock): StockQuote {
    return StockQuote(
        code = requested.originalCode,
        name = "${requested.originalCode} 暂无数据",
        symbol = requested.displaySymbol,
        exchange = requested.exchange,
        currentPrice = null,
        previousClose = null,
        openPrice = null,
        highPrice = null,
        lowPrice = null,
        changeAmount = null,
        changePercent = null,
        volume = null,
        turnover = null,
        updatedAt = null,
        status = QuoteStatus.UNAVAILABLE,
    )
}

internal fun toSearchSuggestion(item: JsonElement): StockSearchSuggestion? {
    val fields = item.jsonArray
    val market = fields.stringValue(0)?.lowercase() ?: return null
    val rawCode = fields.stringValue(1) ?: return null
    val name = fields.stringValue(2) ?: return null
    val abbreviation = fields.stringValue(3)
    val category = fields.stringValue(4)?.uppercase().orEmpty()
    if (!isSupportedSearchCategory(category)) {
        return null
    }

    val requested = when (market) {
        "sh", "sz", "bj", "hk" -> normalizeRequestedStock("$market${rawCode.lowercase()}") ?: return null
        "us" -> normalizeRequestedStock("us${trimUsVendorSuffix(rawCode)}") ?: return null
        else -> return null
    }

    val description = buildList {
        add(marketLabel(requested.market))
        abbreviation?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" | ")

    return StockSearchSuggestion(
        code = requested.originalCode,
        name = name,
        exchange = requested.exchange,
        marketLabel = marketLabel(requested.market),
        description = description,
    )
}

internal fun normalizeRequestedStock(code: String): RequestedStock? {
    val normalizedCode = canonicalizeStockInputCode(code) ?: return null
    if (normalizedCode.isBlank()) {
        return null
    }

    return when {
        normalizedCode.startsWith("sh") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("sh"),
            market = RequestedMarket.A,
            exchange = StockExchange.SSE,
        )
        normalizedCode.startsWith("sz") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("sz"),
            market = RequestedMarket.A,
            exchange = StockExchange.SZSE,
        )
        normalizedCode.startsWith("bj") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = normalizedCode,
            displaySymbol = normalizedCode.removePrefix("bj"),
            market = RequestedMarket.A,
            exchange = StockExchange.BSE,
        )
        normalizedCode.startsWith("hk") -> RequestedStock(
            originalCode = normalizedCode,
            vendorCode = buildHongKongVendorCode(normalizedCode.removePrefix("hk")),
            displaySymbol = buildHongKongDisplaySymbol(normalizedCode.removePrefix("hk")),
            market = RequestedMarket.HK,
            exchange = StockExchange.HKEX,
        )
        normalizedCode.startsWith("gb_") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "gb_",
            symbol = normalizedCode.removePrefix("gb_"),
        )
        normalizedCode.startsWith("usr_") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "usr_",
            symbol = normalizedCode.removePrefix("usr_"),
        )
        normalizedCode.startsWith("us") -> createUsRequestedStock(
            originalCode = normalizedCode,
            vendorPrefix = "usr_",
            symbol = normalizedCode.removePrefix("us").trimStart('_'),
        )
        else -> null
    }
}

private fun createUsRequestedStock(
    originalCode: String,
    vendorPrefix: String,
    symbol: String,
): RequestedStock? {
    val normalizedSymbol = symbol.trim().trimStart('_')
    if (normalizedSymbol.isBlank()) {
        return null
    }

    return RequestedStock(
        originalCode = originalCode,
        vendorCode = "$vendorPrefix$normalizedSymbol",
        displaySymbol = normalizedSymbol.uppercase(),
        market = RequestedMarket.US,
        exchange = inferUsExchange(normalizedSymbol),
    )
}

private fun inferUsExchange(symbol: String): StockExchange {
    return when (symbol.lowercase()) {
        "ixic", "ndx" -> StockExchange.NASDAQ
        "dji" -> StockExchange.NYSE
        else -> StockExchange.OTHER
    }
}

private fun buildHongKongVendorCode(symbol: String): String {
    val normalizedSymbol = symbol.trim()
    if (normalizedSymbol.isBlank()) {
        return "hk"
    }
    return "hk" + if (normalizedSymbol.all(Char::isDigit)) normalizedSymbol else normalizedSymbol.uppercase()
}

private fun buildHongKongDisplaySymbol(symbol: String): String {
    val normalizedSymbol = symbol.trim()
    return if (normalizedSymbol.all(Char::isDigit)) normalizedSymbol else normalizedSymbol.uppercase()
}

internal fun marketLabel(market: RequestedMarket): String {
    return when (market) {
        RequestedMarket.A -> "A股"
        RequestedMarket.HK -> "港股"
        RequestedMarket.US -> "美股"
    }
}

private fun calculateChangeAmount(
    currentPrice: BigDecimal?,
    previousClose: BigDecimal?,
): BigDecimal? {
    if (currentPrice == null || previousClose == null) {
        return null
    }
    return currentPrice.subtract(previousClose)
}

private fun calculateChangePercent(
    changeAmount: BigDecimal?,
    previousClose: BigDecimal?,
): BigDecimal? {
    if (changeAmount == null || previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
        return null
    }
    return changeAmount
        .divide(previousClose, 6, RoundingMode.HALF_UP)
        .multiply(HUNDRED)
        .stripTrailingZeros()
}

private fun parseSinaDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }

    return SINA_DATE_TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
        runCatching { LocalDateTime.parse(normalizedValue, formatter) }.getOrNull()
    }
}

private fun parseTencentDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }
    return runCatching { LocalDateTime.parse(normalizedValue, TENCENT_DATE_TIME_FORMATTER) }.getOrNull()
}

private fun parseTencentSlashDateTime(value: String): LocalDateTime? {
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank()) {
        return null
    }
    return runCatching { LocalDateTime.parse(normalizedValue, TENCENT_SLASH_DATE_TIME_FORMATTER) }.getOrNull()
}

private fun stringValue(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it != "--" }

private fun decimalValue(value: String?): BigDecimal? {
    val normalizedValue = stringValue(value) ?: return null
    return normalizedValue.toBigDecimalOrNull()
}

private fun JsonArray.stringValue(index: Int): String? {
    return getOrNull(index)?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
}

private fun JsonArray.decimalValue(index: Int): BigDecimal? = stringValue(index)?.toBigDecimalOrNull()

private fun trimUsVendorSuffix(rawCode: String): String {
    val parts = rawCode.split('.')
    if (parts.size <= 1) {
        return rawCode.lowercase()
    }
    return parts.dropLast(1).joinToString(".").lowercase()
}

internal fun defaultSinaQuoteUrl(codes: List<String>): String {
    return "https://hq.sinajs.cn/list=${codes.joinToString(",")}"
}

internal fun defaultTencentAShareQuoteUrl(codes: List<String>): String {
    return "https://qt.gtimg.cn/q=${codes.joinToString(",")}&fmt=json"
}

internal fun defaultHongKongQuoteUrl(codes: List<String>): String {
    val query = codes.joinToString(",") { "r_$it" }
    return "https://qt.gtimg.cn/q=$query&fmt=json"
}

internal fun defaultStockSearchUrl(keyword: String): String {
    val encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8)
    return "https://proxy.finance.qq.com/ifzqgtimg/appstock/smartbox/search/get?q=$encodedKeyword"
}

private fun isSupportedSearchCategory(category: String): Boolean {
    val normalizedCategory = category.trim().uppercase()
    if (normalizedCategory.isBlank()) {
        return false
    }

    return normalizedCategory in setOf("GP", "GP-A", "ZS") ||
        normalizedCategory.contains("ETF") ||
        normalizedCategory.contains("LOF")
}

internal data class RequestedStock(
    val originalCode: String,
    val vendorCode: String,
    val displaySymbol: String,
    val market: RequestedMarket,
    val exchange: StockExchange,
)

internal enum class RequestedMarket {
    A,
    HK,
    US,
}

private val SINA_CHARSET: Charset = Charset.forName("GB18030")
private val TENCENT_CHARSET: Charset = Charset.forName("GBK")
private val HUNDRED = BigDecimal("100")
private val TEN_THOUSAND = BigDecimal("10000")
private val SINA_DATE_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
)
private val TENCENT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val TENCENT_SLASH_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
private val SINA_QUOTE_LINE = Regex("""var hq_str_([^=]+)="(.*)";?""")
private const val SINA_REFERER = "https://finance.sina.com.cn/"
